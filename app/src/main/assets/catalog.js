/* TELECONTROL_CT_V1: pure validation shared by the import worker and tests. */
(function(root){
'use strict';
const norm=value=>String(value??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]/g,'');
const MAX_BYTES=96*1024*1024;
function validateCatalog(data){
 const invalid=()=>{throw Error('Formato de catálogo no válido');};
 if(!data||data.format!=='TELECONTROL_CT_V1'||data.version!==1||!Array.isArray(data.fieldNames)||data.fieldNames.length!==363||!data.records||typeof data.records!=='object'||Array.isArray(data.records))invalid();
 if(data.fieldNames.some(x=>typeof x!=='string'||!x.trim()||x.length>500)||new Set(data.fieldNames).size!==363)invalid();
 const keys=Object.keys(data.records);if(!keys.length)throw Error('El catálogo está vacío');
 for(const key of keys){
  const row=data.records[key];
  if(!key||key.length>80||norm(key)!==key||!Array.isArray(row)||row.length!==380||typeof row[0]!=='string'||norm(row[0])!==key)invalid();
  if(row.some(x=>x!==null&&typeof x!=='string'&&(typeof x!=='number'||!Number.isFinite(x))))invalid();
  for(const [i,limit] of [[15,90],[16,180]])if(row[i]!==null&&(typeof row[i]!=='number'||!Number.isFinite(row[i])||Math.abs(row[i])>limit))invalid();
 }
 return data;
}
const api={norm,MAX_BYTES,validateCatalog};
if(typeof module==='object')module.exports=api;else root.CTCatalog=api;
})(globalThis);
