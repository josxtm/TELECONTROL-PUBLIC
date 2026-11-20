/* Offline Grid MT projection and delimited text reader. */
'use strict';
const CTGrid = (() => {
 const {norm} = CTCatalog;
 const baseKeys = ['MATRICULA','NOMBRE','CODIGOBDI','NTRAFOS','TI1BTRELTRANSFORMACION',
  'TI2BTRELTRANSFORMACION','MUNICIPIO','CALLE','ALIMENTACION','GST','CPU','TIPORFID','CAA','IPLAN','IPTC'];
 const aliases = new Map(Object.entries({
  MATRICULACT:'MATRICULA', MATRICULABDI:'MATRICULA', MATRICULACENTRO:'MATRICULA',
  MATRICULACENTROTRANSFORMACION:'MATRICULA', CODIGO:'CODIGOBDI', TIPORFIS:'TIPORFID',
  NOMBREBDI:'NOMBRE', NUMEROTRAFOS:'NTRAFOS', LAT:'LATITUD', LON:'LONGITUD', LONG:'LONGITUD'
 }));
 const technical = new Set((baseKeys.join(' ') + ' ' +
  'CENTRORESPONSABLE NSALIDAS TIPOCENTRO TIPOEDIFICIO PROPIEDAD BBDD PROVINCIA PORTAL CP X Y EPSG CRS SISTEMADEREFERENCIA COORDENADAXUTM COORDENADAYUTM LATITUD LONGITUD ' +
  'PINSTKVA NCLIENTES TELEMANDOINST TELEMANDOACT ESTADOTG ESTADOTC FECHAPESTG FECHADC FECHAINSTALACIONTC FECHAEXPLOTACIONTC ARMARIOGCTTIPO ' +
  'EQCOMFABRICANTE EQCOMMODELO EQCOMNSERIE EQCOMOPERADOR1 EQCOMNSIM1 EQCOMOPERADOR2 EQCOMNSIM2 EQCOMTCFABRICANTE EQCOMTCMODELO EQCOMTCNSERIE POTENCIAMOVISTAR POTENCIAORANGE ' +
  'EQCOMMODELOANTENA EQCOMTGTIPOANTENAALTAGANANCIA EQCOMTGUBICACIONANTENA EQCOMTG ARMARIOGCTUBICACION FABRICANTEGCT T1MODELOCC T1NSERIECC T1NSERIESBT T1MATRICULA ' +
  'T2MODELOCC T2NSERIECC T2NSERIESBT T2MATRICULA FABATN FA1FABRICANTE FA1MODELO FA1NSERIE FA1VERSIONFW BAT1CAPACIDAD BAT1FABRICANTE BAT1MODELO BAT1FECHA ' +
  'BAT2CAPACIDAD BAT2FABRICANTE BAT2MODELO BAT2FECHA RTUN RTU1FABRICANTE RTU1MODELO RTU1NSERIE RTU1VERSIONFW IPWAN APNTC TECNOLOGIATC CRITICIDADTG CRITICIDADTC ' +
  'IPFA1 IPFA2 POTENCIANOMINALT1 POTENCIANOMINALT2 CNCT1 CNCT2 IPT1 IPT2 ESTADOCNCT1 ESTADOCNCT2 SBTT1 SBTT2 ESTADOSBTT1 ESTADOSBTT2').split(' '));
 const maxBytes = 768*1024*1024;
 function keyOf(raw) {
  if (/CONTRAS|PASSWORD|PASSWD|CLAVE|CREDENC|CREDENT|SECRET|TOKEN|APIKEY|PASSCODE|PINCODE/.test(norm(raw))) return '';
  const base = norm(raw.replace(/\s*\[[^\]]*\]\s*$/, ''));
  return aliases.get(base) || base;
 }
 function coordinate(raw, limit) {
  if (raw == null || !String(raw).trim()) return null;
  const n = Number(String(raw).trim().replace(',', '.'));
  return Number.isFinite(n) && Math.abs(n)<=limit ? n : null;
 }
 function collector(progress) {
  let fields, source, base, width, count=0, selected=0, lat, lon;
  const records=Object.create(null);
  return {
   row(values) {
    if (!values.some(v=>v!=null && v!=='')) return;
    if (!fields) {
     width=values.length;
     if (width>600) throw Error('Demasiadas columnas en el Grid MT');
     const names=Array.from(values, v=>String(v??'').trim());
     const keys=names.map(keyOf);
     if (!keys.includes('MATRICULA')) throw Error('Falta la columna MATRÍCULA del Grid MT');
     fields=[]; source=[]; const seen=new Set();
     base=baseKeys.map(key=>{
      // Prefer a source value to a calculated alternative when both are present.
      const candidates=keys.map((k,i)=>k===key?i:-1).filter(i=>i>=0);
      return candidates.find(i=>!/\[CAL\]/i.test(names[i])) ?? candidates[0] ?? -1;
     });
     lat=keys.indexOf('LATITUD'); lon=keys.indexOf('LONGITUD');
     names.forEach((name,i)=>{
      if (!technical.has(keys[i]) || base.includes(i)) return;
      if (name.length>500 || seen.has(name)) throw Error('Cabeceras técnicas duplicadas o no válidas');
      seen.add(name); fields.push(name); source.push(i);
     });
     return;
    }
    if (++count%2000===0) progress(count,selected);
    if (values.length>width) throw Error('Una fila contiene más columnas que la cabecera');
    const id=norm(values[base[0]]);
    if (!/^(28|45|82)[A-Z0-9]+$/.test(id)) return;
    if (id.length>40 || Object.prototype.hasOwnProperty.call(records,id)) throw Error('Matrícula duplicada o no válida en el Grid MT');
    const cell=i=>values[i]==null || values[i]==='' ? null : String(values[i]).trim() || null;
    const row=base.map(cell);
    row[0]=id; row.push(coordinate(values[lat],90),coordinate(values[lon],180));
    for (const i of source) row.push(cell(i));
    records[id]=row; selected++;
   },
   finish() {
    if (!selected) throw Error('El Grid MT no contiene centros con prefijo 28, 45 u 82');
    return {format:'TELECONTROL_GRID_MT',version:1,fieldNames:fields,records};
   }
  };
 }
 function delimiter(line) {
  const counts={';':0,',':0,'\t':0};
  let quoted=false;
  for (const char of line) {
   if (char==='"') quoted=!quoted;
   else if (!quoted && char in counts) counts[char]++;
  }
  const best=Object.keys(counts).sort((a,b)=>counts[b]-counts[a])[0];
  if (!counts[best]) throw Error('Separador de columnas no reconocido');
  return best;
 }
 function csvParser(sep, accept) {
  let row=[], value='', quoted=false, afterQuote=false, skipLf=false, width=0;
  function endCell() { row.push(value); value=''; afterQuote=false; if(row.length>600) throw Error('Demasiadas columnas en el Grid MT'); }
  function endRow() {
   endCell();
   if (row.length!==1 || row[0].trim()) {
    if (width && row.length!==width) throw Error('El número de columnas no coincide con la cabecera');
    width=row.length; accept(row);
   }
   row=[];
  }
  return {
   write(text) {
    for (let i=0;i<text.length;i++) {
     const c=text[i];
     if (skipLf) {skipLf=false; if(c==='\n') continue;}
     if(quoted) {
      if(c==='"'){quoted=false;afterQuote=true;} else value+=c;
     } else if(c===sep) endCell();
     else if(c==='\r'||c==='\n'){endRow();skipLf=c==='\r';}
     else if(c==='"' && (value===''||afterQuote)){if(afterQuote)value+='"';afterQuote=false;quoted=true;}
     else if(afterQuote || c==='"') throw Error('Comillas no válidas en el CSV');
     else value+=c;
     if(value.length>65536) throw Error('Una celda del Grid MT es demasiado larga');
    }
   },
   end(){if(quoted) throw Error('El CSV termina dentro de una celda'); if(row.length||value||afterQuote) endRow();}
  };
 }
 async function readCsv(file,gzip,progress) {
  const first= new Uint8Array(await file.slice(0,2).arrayBuffer());
  const encoding=first[0]===255 && first[1]===254?'utf-16le':first[0]===254 && first[1]===255?'utf-16be':'utf-8';
  const retry=Symbol('encoding');
  async function attempt(charset) {
   const out=collector(progress), decoder=new TextDecoder(charset,{fatal:true});
   let stream=file.stream();
   if(gzip) stream=stream.pipeThrough(new DecompressionStream('gzip'));
   const reader=stream.getReader();
   let prefix='', parser, size=0;
   function feed(text,end) {
    if(!parser) {
     prefix+=text;
     const pos=prefix.search(/[\r\n]/);
     if(pos<0 && !end){if(prefix.length>262144) throw Error('Cabecera demasiado larga');return;}
     const head=prefix.slice(0,pos<0?undefined:pos).trim();
     const hint=/^sep=([;,\t])$/i.exec(head);
     parser=csvParser(hint?hint[1]:delimiter(head),out.row);
     text=hint?prefix.slice(pos+1):prefix;prefix='';
    }
    parser.write(text);
   }
   try {
    while(true) {
     const {value,done}=await reader.read();
     size+=value?.length||0;
     if(size>maxBytes) throw Error('El CSV expandido supera 768 MB');
     let decoded;
     try{decoded=decoder.decode(value,{stream:!done});}catch{throw retry;}
     feed(decoded,done);
     if(done) break;
    }
    parser.end();return out.finish();
   } finally {await reader.cancel().catch(()=>{});}
  }
  try{return await attempt(encoding);}catch(e){if(e!==retry) throw e;}
  return attempt('windows-1252');
 }
 return {maxBytes,collector,readCsv,keyOf};
})();
