'use strict';
importScripts('catalog.js');
self.onmessage=async event=>{
 const {file,allowGrid}=event.data;
 try{
  self.postMessage({stage:'reading'});
  const start=new Uint8Array(await file.slice(0,4).arrayBuffer());
  const gzip=start[0]===31 && start[1]===139;
  const workbook=start[0]===80 && start[1]===75;
  const name=String(file.name||'').toLowerCase();
  const signature=await file.slice(0,256).text();
  const looksJson=signature.trimStart().startsWith('{');
  const grid=allowGrid && (gzip||workbook||(!looksJson && !name.endsWith('.json')));
  let data;
  if(grid){
   importScripts('grid-import.js');
   if(file.size>(workbook?CTCatalog.MAX_BYTES:CTGrid.maxBytes)) throw Error('El Grid MT supera el tamaño admitido');
   const progress=(rows,selected)=>self.postMessage({stage:'grid',rows,selected});
   if(workbook){
    importScripts('grid-vendor/jszip.min.js','grid-vendor/sax.js','grid-excel.js');
    data=await readGridExcel(file,progress);
   }else{
    if(/\.(xlsx|gz)$/.test(name)&&!gzip) throw Error('El archivo comprimido no es válido');
    data=await CTGrid.readCsv(file,gzip,progress);
   }
  }else{
   if(file.size>CTCatalog.MAX_BYTES) throw Error('El archivo supera el límite de 96 MB');
   const text=typeof file.text==='function'?await file.text():new FileReaderSync().readAsText(file);
   self.postMessage({stage:'validating'});
   try{data=JSON.parse(text);}catch{throw Error('El archivo no contiene un JSON válido');}
   data=CTCatalog.validateCatalog(data);
  }
  self.postMessage({data});
 }catch(error){
  // Only fixed importer messages are shown. Browser and XML errors may contain cell text.
  const message=String(error?.message||'');
  const safe=/^(El (archivo|catálogo|Grid MT|CSV|Excel|número)|Formato de catálogo|Falta la columna|Demasiad[oa]s? |Cabecera[s]? |Matrícula duplicada|Una (fila|celda)|Separador de columnas|Comillas no válidas|Libro Excel|Columna Excel|No se ha encontrado)/.test(message);
  self.postMessage({error:safe?message:'No se pudo leer el archivo seleccionado'});
 }
};
