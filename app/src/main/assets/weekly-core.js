'use strict';
/* Weekly work is deliberately independent of catalogue and Syncthing work stores. */
((root)=>{
const text=(v,max=4000)=>{if(typeof v!=='string'||v.length>max||/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(v))throw Error('Texto no válido o demasiado largo');return v.trim();};
const norm=v=>text(v,120).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/\s+/g,'');
function iso(date=new Date()){const d=new Date(Date.UTC(date.getFullYear(),date.getMonth(),date.getDate()));d.setUTCDate(d.getUTCDate()+4-(d.getUTCDay()||7));const year=d.getUTCFullYear();return {year,week:Math.ceil((((d-new Date(Date.UTC(year,0,1)))/86400000)+1)/7)};}
function weeks(year){return iso(new Date(year,11,28)).week;}
function resolvePlanning(input,batchYear){const r={...input};if(r.category==='routers'){delete r.week;delete r.year;return r;}const w=String(r.week??'').trim().match(/^(?:SEMANA\s*)?(\d{1,2})(?:[.,]0+)?(?:\s*[/\-]\s*(20\d{2}))?$/i);if(w){r.week=Number(w[1]);if(!r.year&&w[2])r.year=Number(w[2]);}return r;}
function importPolicy(r){
 if(r.category==='routers'||r.origin==='manual')return 'include';
 let sourceYear=Number(r.year)||0;
 if(!sourceYear&&/^\d{4}-\d{2}-\d{2}$/.test(r.date||'')){
  const d=new Date(r.date+'T12:00:00');
  if(!isNaN(d)&&d.getFullYear()===Number(r.date.slice(0,4))&&d.getMonth()+1===Number(r.date.slice(5,7))&&d.getDate()===Number(r.date.slice(8,10))){const plan=iso(d);if(plan.week===Number(r.week))sourceYear=plan.year;}
 }
 if(!sourceYear)sourceYear=Number(r.referenceYear)||0;
 return sourceYear===2026&&Number.isInteger(Number(r.week))&&Number(r.week)>=1&&Number(r.week)<35?'excluded':'include';
}
function sourceIdentity(r){const prefix=[r.category||'weekly',r.origin||'excel',r.manualToken||'',String(r.act??'').trim().toUpperCase().replace(/\s/g,''),String(r.matricula??'').trim().toUpperCase().replace(/\s/g,'')];if(r.sourceRow)return 'source-v1:'+JSON.stringify([...prefix,'row:'+String(r.sourceRow).trim()]);return 'source-v1:'+JSON.stringify([...prefix,r.typology||r.baseCode||'',r.description||'',r.date||'',r.category==='routers'?null:Number(r.week),null]);}
function validate(row){
 if(!row||typeof row!=='object')throw Error('Registro no válido');
 row=resolvePlanning(row);const r={category:row.category==='routers'?'routers':'weekly'};for(const field of ['typology','act','description','matricula','municipality','date','tered','realizado','baseCode','sourceComments','prl','cpArgos','systemsFinished'])r[field]=text(row[field]??'',['description','sourceComments'].includes(field)?4000:200);
 if(Object.values(r).some(v=>typeof v==='string'&&/^#(VALUE!|REF!|DIV\/0!|N\/A|NAME\?|NUM!|NULL!)/.test(v)))throw Error('Corrige la celda Excel con error antes de guardar esta fila');
 r.act=r.act.toUpperCase().replace(/\s/g,'');r.matricula=r.matricula.toUpperCase().replace(/\s/g,'');
 if(r.category==='routers'&&!/^[A-Z0-9][A-Z0-9/-]{2,59}$/.test(r.act))throw Error('Revisa GDA Código Solicitud');
 if(r.category==='weekly'&&r.act&&!/^[A-Z0-9][A-Z0-9/-]{1,59}$/.test(r.act))throw Error('Revisa EXPEDIENTE / ACT; conserva todos los ceros');
 if(r.matricula&&!/^[A-Z0-9][A-Z0-9-]{2,39}$/.test(r.matricula))throw Error('Revisa la matrícula');
 if(!r.matricula&&!row.sourceRow)throw Error('Falta matrícula o referencia de fila para identificar el trabajo');
 // Blank typology, description and municipality must not discard a valid work order.
 if(r.category==='weekly'){r.year=Number(row.year)||0;r.week=Number(row.week);
 if(!Number.isInteger(r.week)||r.week<1||r.week>53)throw Error('Selecciona una SEMANA válida (1–53)');}
 if(r.date&&r.category==='routers'){if(!/^\d{4}-\d{2}-\d{2}$/.test(r.date))throw Error('Fecha: usa AAAA-MM-DD o déjala vacía');const d=new Date(r.date+'T12:00:00');if(Number.isNaN(d.valueOf())||d.getFullYear()!==Number(r.date.slice(0,4))||d.getMonth()+1!==Number(r.date.slice(5,7))||d.getDate()!==Number(r.date.slice(8,10)))throw Error('Fecha inexistente');}
 if(!r.tered)r.tered=r.realizado;
 if(!['PESTC','INCIDENCIASTC'].includes(norm(r.typology).replace(/[^A-Z0-9]/g,'')))r.tered='';
 if(Number(row.referenceYear)===2026)r.referenceYear=2026;
 if(row.recordIdentity)r.recordIdentity=text(row.recordIdentity,12000);if(row.sourceRow)r.sourceRow=text(String(row.sourceRow),30);if(row.origin)r.origin=text(row.origin,40);
 if(r.category==='routers')delete r.cpArgos;
 return r;
}
const states=Object.freeze({not_started:'SIN EMPEZAR',in_progress:'PENDIENTE DE ACABAR',completed:'REALIZADO'});
const state=r=>Object.prototype.hasOwnProperty.call(states,r.status)?r.status:(r.completed?'completed':'not_started');
const searchable=v=>String(v??'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase();
function periods(rows,date=new Date()){const current=iso(date),previous=iso(new Date(date.getFullYear(),date.getMonth(),date.getDate()-7));return Array.from(new Set([current.week,previous.week,...rows.filter(r=>r.category!=='routers').map(r=>Number(r.week))].filter(w=>Number.isInteger(w)&&w>=1&&w<=53))).sort((a,b)=>b-a).map(String);}
const key=r=>(r.category==='routers'?'routers|':'')+norm(r.act)+'|'+norm(r.matricula)+(r.recordIdentity?'|'+r.recordIdentity:'');
const legacyKey=r=>(r.category==='routers'?'routers|':'')+norm(r.act||'')+'|'+norm(r.matricula||'');
function sameRecord(old,r){
 if(old.key===key(r))return true;
 if(old.key!==legacyKey(r))return false;
 const prior=old.sourceData||old;
 if(prior.sourceRow&&r.sourceRow&&String(prior.sourceRow)!==String(r.sourceRow))return false;
 if(prior.year&&r.year&&Number(prior.year)!==Number(r.year))return false;
 if(prior.date&&r.date&&prior.date!==r.date)return false;
 return norm(prior.act||'')===norm(r.act||'')&&norm(prior.matricula||'')===norm(r.matricula||'')&&String(prior.description||'').trim()===String(r.description||'').trim()&&(r.category==='routers'||Number(prior.week)===Number(r.week));
}
// A legacy record without row provenance may be adopted only by ONE unambiguous input row.
// Ambiguous records remain intact alongside the newly identified rows; never merge their states.
function existingMatches(existing,input){
 const byKey=new Map(existing.map(r=>[r.key,r])),byIdentity=new Map(),matches=new Map(),claimed=new Set(),groups=new Map();
 for(const old of existing)if(old.recordIdentity){if(byIdentity.has(old.recordIdentity))byIdentity.set(old.recordIdentity,null);else byIdentity.set(old.recordIdentity,old);}
 for(const r of input){const old=byKey.get(key(r))||(r.recordIdentity&&byIdentity.get(r.recordIdentity));if(old&&!claimed.has(old.key)){matches.set(key(r),old);claimed.add(old.key);}else{const k=legacyKey(r);if(!groups.has(k))groups.set(k,[]);groups.get(k).push(r);}}
 for(const [k,group]of groups){const old=byKey.get(k);if(!old||claimed.has(k))continue;const candidates=group.filter(r=>sameRecord(old,r));if(candidates.length===1){matches.set(key(candidates[0]),old);claimed.add(k);}}
 return matches;
}
function filter(rows,year,week,query='',order='pending',status='all',category='weekly'){const rank={not_started:0,in_progress:1,completed:2};return rows.filter(r=>(category==='routers'?r.category==='routers':r.category!=='routers'&&r.week===Number(week))&&(status==='all'||state(r)===status)&&['matricula','act','description'].some(k=>searchable(r[k]).includes(searchable(query)))).sort((a,b)=>(order==='done'?-1:1)*(rank[state(a)]-rank[state(b)])||a.matricula.localeCompare(b.matricula)||a.act.localeCompare(b.act));}
function txt(rows){return '\uFEFFTELECONTROL — GESTIÓN DE TRABAJOS\r\nCONFIDENCIAL: compartir únicamente por medios autorizados.\r\n\r\n'+rows.slice().sort((a,b)=>b.year-a.year||b.week-a.week||a.matricula.localeCompare(b.matricula)).map(r=>`${r.category==='routers'?'CAMPAÑA ROUTERS':`SEMANA ${r.week}`}\r\n[${state(r)==='completed'?'X':state(r)==='in_progress'?'~':' '}] ${r.matricula} - ${r.act}\r\nEstado: ${states[state(r)]}\r\n${r.category==='routers'?'Código Base/Centro: '+r.baseCode:'Tipología: '+r.typology+'\r\nTERED: '+(r.tered||'Sin dato')}\r\nDescripción: ${r.description||'SIN DESCRIPCIÓN'}\r\nFecha: ${r.date||'Sin fecha'}\r\nMunicipio: ${r.municipality}\r\nComentarios de origen: ${r.sourceComments||''}\r\n${r.category==='routers'?'':`Documentación PRL: ${r.prl||''}\r\nTerminada en sistemas: ${r.systemsFinished||''}\r\n`}Anotaciones del técnico: ${r.technicianNotes||''}\r\n`).join('\r\n');}
function store(factory){
 let opening;const open=()=>opening||(opening=new Promise((resolve,reject)=>{const req=factory.open('telecontrol-weekly-v1',3);req.onupgradeneeded=e=>{if(e.oldVersion<3)req.result.createObjectStore('routers',{keyPath:'key'});if(e.oldVersion<1)req.result.createObjectStore('jobs',{keyPath:'key'});if(e.oldVersion<2){const cursor=req.transaction.objectStore('jobs').openCursor();cursor.onsuccess=()=>{const c=cursor.result;if(c){const r=c.value;r.status=state(r);r.completed=r.status==='completed';c.update(r);c.continue();}};}};req.onblocked=()=>reject(Error('Cierra otras ventanas de TELECONTROL para actualizar el almacenamiento sin borrar datos'));req.onerror=()=>reject(Error('No se pudo abrir el almacenamiento de trabajos'));req.onsuccess=()=>{const db=req.result;db.onversionchange=()=>{db.close();opening=null;};resolve(db);};}));
 async function transaction(mode,fn,category='weekly'){const db=await open();return new Promise((resolve,reject)=>{const tx=db.transaction(category==='routers'?'routers':'jobs',mode);let result;tx.oncomplete=()=>resolve(result);tx.onabort=tx.onerror=()=>reject(tx._saveError||Error('No se guardaron los cambios. Los datos anteriores se conservan.'));try{fn(tx.objectStore(category==='routers'?'routers':'jobs'),v=>result=v);}catch(e){tx.abort();reject(e);}});}
 const all=(category='weekly')=>transaction('readonly',(s,done)=>{s.getAll().onsuccess=e=>done(e.target.result);},category);
 async function importRows(input,allowUpdate=false){if(!Array.isArray(input)||!input.length||input.length>10000)throw Error('Importa entre 1 y 10000 trabajos por sesión');const rows=input.map(validate);if(new Set(rows.map(r=>r.category)).size!==1)throw Error('Importa cada categoría por separado');const keys=new Set();for(const r of rows){if(keys.has(key(r)))throw Error('Registro exactamente duplicado en la revisión');keys.add(key(r));}
 return transaction('readwrite',(s,done)=>{const request=s.getAll();request.onsuccess=()=>{
  const matches=existingMatches(request.result,rows);
  if(!allowUpdate&&matches.size){s.transaction.abort();return;}
  for(const r of rows){const old=matches.get(key(r)),history={};
   if(old?.year&&r.category==='weekly'&&!r.year)history.year=old.year;
   if(old?.date&&!r.date)history.date=old.date;
   s.put({...old,...r,...history,...(old?.overrides||{}),sourceData:{...r},technicianNotes:old?.technicianNotes??'',key:old?.key||key(r),id:old?.id||cryptoId(),status:old?state(old):'not_started',completed:old?state(old)==='completed':false,createdAt:old?.createdAt||new Date().toISOString(),updatedAt:new Date().toISOString()});
  }done(rows.length);
 };},rows[0].category);}


 const setStatus=(k,status)=>{if(!Object.prototype.hasOwnProperty.call(states,status))return Promise.reject(Error('Estado no válido'));return transaction('readwrite',(s,done)=>{const req=s.get(k);req.onsuccess=()=>{const r=req.result;if(!r){s.transaction.abort();return;}r.status=status;r.completed=status==='completed';r.updatedAt=new Date().toISOString();s.put(r);done(r);};},k.startsWith('routers|')?'routers':'weekly');};
 const toggle=(k,completed)=>setStatus(k,completed?'completed':'not_started');
 const edit=(k,changes)=>transaction('readwrite',(s,done)=>{const req=s.get(k);req.onsuccess=()=>{
  const old=req.result;if(!old){s.transaction.abort();return;}
  try{
   const fields=['act','matricula','description','date','municipality','typology','tered','sourceComments','prl','systemsFinished','week','year'];
   const hasFields=fields.some(f=>Object.prototype.hasOwnProperty.call(changes,f));
   const clean=hasFields?validate({...old,...changes}):old;
   const overrides={...old.overrides};for(const f of fields)if(Object.prototype.hasOwnProperty.call(changes,f))overrides[f]=clean[f];
   const r={...old,...overrides,sourceData:old.sourceData||{...old},overrides,updatedAt:new Date().toISOString()};
   if(Object.prototype.hasOwnProperty.call(changes,'technicianNotes'))r.technicianNotes=text(changes.technicianNotes);
   s.put(r);done(r);
  }catch(e){s.transaction._saveError=e;s.transaction.abort();}
 };},k.startsWith('routers|')?'routers':'weekly');
 const notes=(k,value)=>edit(k,{technicianNotes:value});

 return {all,importRows,toggle,setStatus,notes,edit};
}
function cryptoId(){if(root.crypto?.randomUUID)return root.crypto.randomUUID();if(!root.crypto?.getRandomValues)throw Error('No hay generador seguro de identificadores');return Array.from(root.crypto.getRandomValues(new Uint8Array(16)),v=>v.toString(16).padStart(2,'0')).join('');}
root.CTWeekly={iso,weeks,validate,key,filter,txt,store,states,state,periods,resolvePlanning,importPolicy,sourceIdentity,sameRecord,existingMatches};
})(typeof window!=='undefined'?window:globalThis);
