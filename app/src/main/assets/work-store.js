(function(root){
'use strict';
const canonical=s=>String(s).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]/g,'');
function validate(e){
 const date=x=>x===null||(typeof x==='string'&&Number.isFinite(Date.parse(x))&&new Date(x).toISOString()===x);
 if(!e||typeof e.id!=='string'||!e.id||e.id.length>150||!['note','journal'].includes(e.kind)||typeof e.text!=='string'||!e.text.trim()||e.text.length>20000||!date(e.createdAt)||!date(e.modifiedAt)||e.kind==='note'&&(typeof e.ct!=='string'||!e.ct||canonical(e.ct)!==e.ct)||e.kind==='journal'&&e.ct!==null)throw Error('Entrada no válida');
 return {id:e.id,kind:e.kind,ct:e.ct,text:e.text,createdAt:e.createdAt,modifiedAt:e.modifiedAt};
}
function operation(value){
 if(!value||!/^[a-f0-9]{32}$/.test(value.id)||typeof value.entity!=='string'||!value.entity||value.entity.length>150||!Array.isArray(value.parents)||value.parents.length>100||new Set(value.parents).size!==value.parents.length||value.parents.some(p=>!/^[a-f0-9]{32}$/.test(p)||p===value.id)||typeof value.deleted!=='boolean')throw Error('Revisión no válida');
 const entry=value.deleted?null:validate(value.entry);if(entry&&entry.id!==value.entity)throw Error('Entidad no válida');
 return {id:value.id,entity:value.entity,parents:value.parents.slice().sort(),deleted:value.deleted,entry};
}
function createStore(indexedDB,crypto,clock=()=>new Date().toISOString()){
 const uuid=()=>Array.from(crypto.getRandomValues(new Uint8Array(16)),x=>x.toString(16).padStart(2,'0')).join('');
 function notify(name,operations=[]){if(typeof root.dispatchEvent==='function')root.dispatchEvent(new root.CustomEvent(name,{detail:{operations}}));}
 function open(){return new Promise((resolve,reject)=>{
  const r=indexedDB.open('telecontrol-work-v1',2);
  r.onupgradeneeded=()=>{
   const db=r.result,t=r.transaction;
   if(!db.objectStoreNames.contains('entries'))db.createObjectStore('entries',{keyPath:'id'});
   if(!db.objectStoreNames.contains('meta'))db.createObjectStore('meta');
   if(!db.objectStoreNames.contains('ops')){
    const ops=db.createObjectStore('ops',{keyPath:'id'});ops.createIndex('entity','entity');db.createObjectStore('heads',{keyPath:'entity'});
    const cursor=t.objectStore('entries').openCursor();cursor.onsuccess=()=>{const c=cursor.result;if(!c)return;const op=operation({id:uuid(),entity:c.value.id,parents:[],deleted:false,entry:c.value});ops.add(op);t.objectStore('heads').put({entity:op.entity,ids:[op.id],visible:op.id});c.continue();};
   }
  };
  r.onerror=()=>reject(Error('No se puede abrir el almacenamiento de trabajo'));r.onblocked=()=>reject(Error('Cierra otras ventanas de TELECONTROL'));r.onsuccess=()=>resolve(r.result);
 });}
 async function tx(mode,callback,stores=['entries','meta','ops','heads'],event='telecontrol-work-changed'){
  const db=await open();return new Promise((resolve,reject)=>{let value;try{const t=db.transaction(stores,mode);t.oncomplete=()=>{db.close();if(mode==='readwrite'&&event)notify(event,t._syncOps||[]);resolve(value);};t.onabort=()=>{db.close();reject(Error('No se pudo guardar. Conserva el texto y comprueba el espacio disponible.'));};t.onerror=()=>{};callback(t,v=>value=v);}catch{db.close();reject(Error('Almacenamiento no disponible'));}});
 }
 function append(t,entity,entry,resolveAll=false){const heads=t.objectStore('heads'),r=heads.get(entity);r.onsuccess=()=>{const h=r.result||{ids:[],visible:null};const parents=resolveAll?h.ids:(h.visible?[h.visible]:[]);const op=operation({id:uuid(),entity,parents,deleted:entry===null,entry});t.objectStore('ops').add(op);(t._syncOps||(t._syncOps=[])).push(op);const ids=resolveAll?[op.id]:h.ids.filter(x=>!parents.includes(x)).concat(op.id);heads.put({entity,ids,visible:op.id});};}
 async function list(kind,ct=null){const all=await tx('readonly',(t,set)=>{const r=t.objectStore('entries').getAll();r.onsuccess=()=>{const h=t.objectStore('heads').getAll();h.onsuccess=()=>{const map=new Map(h.result.map(x=>[x.entity,x.visible]));set(r.result.map(e=>({...e,_syncRevision:map.get(e.id)})));};};});return all.filter(x=>x.kind===kind&&(kind!=='note'||x.ct===canonical(ct))).sort((a,b)=>(b.createdAt||'').localeCompare(a.createdAt||'')||a.id.localeCompare(b.id));}
 async function create(kind,ct,text){const entry=validate({id:uuid(),kind,ct:kind==='note'?canonical(ct):null,text,createdAt:clock(),modifiedAt:null});await tx('readwrite',t=>{t.objectStore('entries').add(entry);append(t,entry.id,entry);});return entry;}
 async function update(entry,text){return tx('readwrite',(t,set)=>{const store=t.objectStore('entries'),r=store.get(entry.id);r.onsuccess=()=>{const current=r.result;if(!current||current.text!==entry.text||current.modifiedAt!==entry.modifiedAt){t.abort();return;}try{const next=validate({...current,text,modifiedAt:clock()});store.put(next);append(t,next.id,next);set(next);}catch{t.abort();}};});}
 async function remove(entry){return tx('readwrite',t=>{const store=t.objectStore('entries'),r=store.get(entry.id);r.onsuccess=()=>{if(!r.result||r.result.text!==entry.text||r.result.modifiedAt!==entry.modifiedAt){t.abort();return;}store.delete(entry.id);append(t,entry.id,null);};});}
 async function migrate(storage){const completed=await tx('readonly',(t,set)=>{const r=t.objectStore('meta').get('legacy-migrated');r.onsuccess=()=>set(r.result===true);},['meta']);if(completed)return;const rows=[];for(let i=0;i<storage.length;i++){const key=storage.key(i);if(!key.startsWith('telecontrol-note-'))continue;let text;try{text=JSON.parse(storage.getItem(key));}catch{throw Error('Nota antigua no válida; no se ha borrado');}if(typeof text!=='string')throw Error('Nota antigua no válida; no se ha borrado');if(text.trim())rows.push(validate({id:'legacy-'+key,kind:'note',ct:canonical(key.slice(16)),text,createdAt:null,modifiedAt:null}));}
 await tx('readwrite',t=>{const meta=t.objectStore('meta'),r=meta.get('legacy-migrated');r.onsuccess=()=>{if(r.result)return;for(const e of rows){t.objectStore('entries').add(e);append(t,e.id,e);}meta.put(true,'legacy-migrated');};});}
 async function all(){return tx('readonly',(t,set)=>{const r=t.objectStore('entries').getAll();r.onsuccess=()=>set(r.result);});}
 function backupEntries(data){if(!data||data.format!=='TELECONTROL_WORK_V1'||data.version!==1||!Array.isArray(data.entries)||data.entries.length>50000)throw Error('Copia no válida');const entries=data.entries.map(validate);if(new Set(entries.map(e=>e.id)).size!==entries.length)throw Error('Identificadores duplicados');return entries;}
 async function preview(data){const entries=backupEntries(data),existing=new Map((await all()).map(e=>[e.id,e]));let added=0,identical=0,conflicts=0;for(const e of entries){if(!existing.has(e.id))added++;else if(JSON.stringify(validate(existing.get(e.id)))===JSON.stringify(e))identical++;else conflicts++;}return {added,identical,conflicts};}
 async function merge(data){const entries=backupEntries(data);return tx('readwrite',(t,set)=>{const store=t.objectStore('entries');const r=store.getAll();r.onsuccess=()=>{const existing=new Map(r.result.map(e=>[e.id,e]));let added=0;for(const e of entries){if(existing.has(e.id)){if(JSON.stringify(validate(existing.get(e.id)))!==JSON.stringify(e)){t.abort();return;}}else{store.add(e);append(t,e.id,e);added++;}}set(added);};});}
 async function revisions(){return tx('readonly',(t,set)=>{const r=t.objectStore('ops').getAll();r.onsuccess=()=>set(r.result);});}
 async function receive(values){const incoming=values.map(operation);return tx('readwrite',(t,set)=>{
  const r=t.objectStore('ops').getAll();r.onsuccess=()=>{try{
   const map=new Map(r.result.map(op=>[op.id,op])),affected=new Set();
   for(const op of incoming){const old=map.get(op.id);if(old&&JSON.stringify(old)!==JSON.stringify(op))throw Error('Revisión modificada');if(!old){t.objectStore('ops').add(op);map.set(op.id,op);affected.add(op.entity);}}
   for(const op of map.values())for(const parent of op.parents){const ancestor=map.get(parent);if(ancestor&&ancestor.entity!==op.entity)throw Error('Padre ajeno');}
   for(const entity of affected){
    const nodes=Array.from(map.values()).filter(op=>op.entity===entity),degrees=new Map(),children=new Map(),complete=new Map(),queue=[];
    for(const op of nodes){let degree=0,known=true;for(const id of op.parents){const parent=map.get(id);if(parent&&parent.entity!==entity)throw Error('Padre ajeno');if(parent){degree++;if(!children.has(id))children.set(id,[]);children.get(id).push(op.id);}else known=false;}degrees.set(op.id,degree);complete.set(op.id,known);if(!degree)queue.push(op.id);}
    let processed=0;for(let i=0;i<queue.length;i++){const id=queue[i];processed++;for(const child of children.get(id)||[]){if(!complete.get(id))complete.set(child,false);degrees.set(child,degrees.get(child)-1);if(!degrees.get(child))queue.push(child);}}
    if(processed!==nodes.length)throw Error('Ciclo');
    const valid=nodes.filter(op=>complete.get(op.id)),referenced=new Set(valid.flatMap(op=>op.parents));const ids=valid.filter(op=>!referenced.has(op.id)).map(op=>op.id).sort();
    if(!ids.length)continue;
    const h=t.objectStore('heads').get(entity);h.onsuccess=()=>{const old=h.result;let visible=old?.visible;if(ids.length===1)visible=ids[0];else if(!visible)visible=ids.find(id=>!map.get(id).deleted)||ids[0];const op=map.get(visible);if(op){if(op.deleted)t.objectStore('entries').delete(entity);else t.objectStore('entries').put(op.entry);}t.objectStore('heads').put({entity,ids,visible});};
   }set(incoming.length);
  }catch{t.abort();}};
 },undefined,'telecontrol-work-received');}
 async function fork(entry,text){const next=validate({...entry,text,modifiedAt:clock()});if(!entry._syncRevision)throw Error('Falta revisión del borrador');return tx('readwrite',t=>{const r=t.objectStore('ops').get(entry._syncRevision);r.onsuccess=()=>{if(!r.result||r.result.entity!==entry.id){t.abort();return;}const h=t.objectStore('heads').get(entry.id);h.onsuccess=()=>{const op=operation({id:uuid(),entity:entry.id,parents:[entry._syncRevision],deleted:false,entry:next});t.objectStore('ops').add(op);(t._syncOps||(t._syncOps=[])).push(op);const ids=(h.result?.ids||[]).filter(id=>id!==entry._syncRevision).concat(op.id);t.objectStore('heads').put({entity:entry.id,ids,visible:op.id});t.objectStore('entries').put(next);};};});}
 async function conflicts(){const [ops,heads]=await Promise.all([revisions(),tx('readonly',(t,set)=>{const r=t.objectStore('heads').getAll();r.onsuccess=()=>set(r.result);})]);const map=new Map(ops.map(op=>[op.id,op]));return heads.filter(h=>h.ids.length>1).map(h=>({entity:h.entity,versions:h.ids.map(id=>map.get(id))}));}
 async function resolve(entity,selected,expectedHeads){return tx('readwrite',t=>{const r=t.objectStore('heads').get(entity);r.onsuccess=()=>{const h=r.result;if(!h||JSON.stringify(h.ids.slice().sort())!==JSON.stringify(expectedHeads.slice().sort())||!h.ids.includes(selected)){t.abort();return;}const o=t.objectStore('ops').get(selected);o.onsuccess=()=>{const op=o.result;if(op.deleted)t.objectStore('entries').delete(entity);else t.objectStore('entries').put(op.entry);append(t,entity,op.entry,true);};};});}
 return {list,create,update,remove,migrate,all,preview,merge,revisions,receive,conflicts,resolve,fork,async unresolved(){const ops=await revisions(),ids=new Set(ops.map(op=>op.id));return new Set(ops.flatMap(op=>op.parents).filter(id=>!ids.has(id))).size;},async backup(){return {format:'TELECONTROL_WORK_V1',version:1,entries:await all()};}};
}
const api={createStore,validate,operation,canonical};if(typeof module==='object')module.exports=api;else root.CTWork=api;
})(globalThis);
