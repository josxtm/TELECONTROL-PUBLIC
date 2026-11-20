/* Read the saved values in the Grid MT worksheet. Formula code is never evaluated. */
'use strict';
async function readGridExcel(file,progress) {
 const invalid=()=>Error('Libro Excel no válido o dañado');
 let zip;
 try{zip=await JSZip.loadAsync(await file.arrayBuffer());}catch{throw invalid();}
 if(Object.keys(zip.files).length>3000) throw Error('El Excel contiene demasiados elementos');
 let total=0;
 async function parseEntry(path, handlers, limit) {
  const entry=zip.file(path);
  if(!entry) throw invalid();
  const parser=sax.parser(true,{strictEntities:true});
  parser.ondoctype=()=>{throw invalid();};
  parser.onerror=()=>{throw invalid();};
  parser.onopentag=node=>handlers.open?.(node.name.split(':').pop(),node.attributes);
  parser.onclosetag=name=>handlers.close?.(name.split(':').pop());
  parser.ontext=parser.oncdata=text=>handlers.text?.(text);
  const decoder=new TextDecoder('utf-8',{fatal:true});
  await new Promise((resolve,reject)=>{
   const stream=entry.internalStream('uint8array');
   let bytes=0,failed=false;
   stream.on('data',chunk=>{
    if(failed)return;
    try{
     bytes+=chunk.length;total+=chunk.length;
     if(bytes>limit||total>CTGrid.maxBytes) throw Error('El Excel expandido supera el límite de lectura');
     parser.write(decoder.decode(chunk,{stream:true}));
    }catch(e){failed=true;stream.pause();reject(e);}
   }).on('error',()=>reject(invalid())).on('end',()=>{
    if(failed)return;
    try{parser.write(decoder.decode()).close();resolve();}catch(e){reject(e);}
   }).resume();
  });
 }
 const sheets=[],links=new Map(),small=2*1024*1024;
 await parseEntry('xl/workbook.xml',{open(tag,a){
  if(tag==='sheet'){const ref=Object.keys(a).find(k=>k.endsWith(':id'));sheets.push({name:a.name,ref:a[ref]});}
 }},small);
 await parseEntry('xl/_rels/workbook.xml.rels',{open(tag,a){
  if(tag==='Relationship' && /\/worksheet$/.test(a.Type) && !a.TargetMode) {
   const path=a.Target.startsWith('/')?a.Target.slice(1):'xl/'+a.Target;
   if(/^xl\/worksheets\/[A-Za-z0-9_.-]+\.xml$/.test(path)) links.set(a.Id,path);
  }
 }},small);
 const shared=[];
 if(zip.file('xl/sharedStrings.xml')){
  let item=null,textDepth=false,chars=0;
  await parseEntry('xl/sharedStrings.xml',{
   open(tag){if(tag==='si')item='';if(tag==='t')textDepth=true;},
   text(s){if(textDepth&&item!==null){item+=s;chars+=s.length;if(item.length>65536||chars>40000000) throw Error('Demasiado texto en Excel');}},
   close(tag){if(tag==='t')textDepth=false;if(tag==='si'){shared.push(item);item=null;if(shared.length>1500000)throw Error('Demasiadas cadenas en Excel');}}
  },160*1024*1024);
 }
 const named=sheets.filter(sheet=>CTCatalog.norm(sheet.name).includes('GRIDMT'));
 const candidates=named.length?named:sheets;
 let result=null;
 for(const sheet of candidates) {
  const path=links.get(sheet.ref);
  if(!path) continue;
  const out=CTGrid.collector(progress);
  let header=false, row=null, index=-1, type='',value='',reading=false, rows=0;
  const skip=Symbol('skip');
  try{
   await parseEntry(path,{
    open(tag,a){
     if(tag==='row'){row=[];index=-1;}
     if(tag==='c' && row){
      const ref=/^([A-Z]{1,3})[1-9]\d*$/.exec(a.r||'');
      let col=index+1;
      if(ref){col=0;for(const ch of ref[1])col=col*26+ch.charCodeAt(0)-64;col--;}
      if(col<=index || col>=600) throw Error('Columna Excel duplicada o fuera de rango');
      index=col;type=a.t||'n';value='';
     }
     if(row && (tag==='v'||(type==='inlineStr'&&tag==='t')))reading=true;
    },
    text(s){if(reading){value+=s;if(value.length>65536)throw Error('Una celda Excel es demasiado larga');}},
    close(tag){
     if(tag==='v'||tag==='t')reading=false;
     if(tag==='c'&&row){
      if(type==='s'){
       if(!/^\d+$/.test(value)||Number(value)>=shared.length) throw invalid();
       value=shared[Number(value)];
      }
      if(value!=='')row[index]=value;
     }
     if(tag==='row' && row){
      rows++;
      if(!header)header=row.some(x=>CTGrid.keyOf(x)==='MATRICULA');
      if(header)out.row(row);else if(rows>=30)throw skip;
      row=null;
     }
    }
   },CTGrid.maxBytes);
  }catch(e){if(e===skip)continue;throw e;}
  if(header) {
   if(result) throw Error('El Excel contiene varias hojas de catálogo. Deja una sola hoja Grid MT');
   result=out.finish();
  }
 }
 if(!result)throw Error('No se ha encontrado una hoja con la columna MATRÍCULA');
 return result;
}
