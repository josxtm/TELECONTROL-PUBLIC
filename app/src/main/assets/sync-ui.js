'use strict';
(()=>{
const el=id=>document.getElementById(id),bridge=()=>window.CTFolderSync,alive=()=>!!window.document;
let busy=false,again=false,lastFingerprint='',conflictSignature='',resolution=null;
const messages={FOLDER_REQUIRED:'Selecciona la carpeta local compartida mediante Syncthing-Fork. Los datos siguen guardados en este dispositivo.',PERMISSION_REQUIRED:'La carpeta necesita permiso persistente de lectura y escritura. Selecciónala de nuevo; no se borran datos.',KEY_REQUIRED:'Introduce la misma frase de recuperación en los dos dispositivos.',READY:'Carpeta preparada para el intercambio cifrado.',RUNNING:'Importando/exportando archivos cifrados...',MORE:'Quedan operaciones por exportar; se continuará cuando Android lo permita.',DONE:'Intercambio local completado. Consulta Syncthing para el estado de transferencia al otro dispositivo.',KEY_ERROR:'No se pudo autenticar un archivo completo. Revisa frase y carpeta; no se eliminará ni se sobrescribirá.',PROTOCOL_ERROR:'Archivo de intercambio completo no válido. Se conservan archivos y datos locales.',INCOMPLETE_FILES:'Hay archivos incompletos o corruptos pendientes. No se incorporan; los válidos se procesan.',FOLDER_UNAVAILABLE:'La carpeta no está disponible o falló una escritura. Se conserva la cola y se reintentará.'};
let inboxRequests=new Map();
function readInbox(){if(!bridge().inboxAsync)return Promise.resolve(JSON.parse(bridge().inbox()));return new Promise((resolve,reject)=>{const id=Array.from(crypto.getRandomValues(new Uint8Array(16)),n=>n.toString(16).padStart(2,'0')).join('');const timer=setTimeout(()=>{inboxRequests.delete(id);reject(Error('La recepción continúa pendiente; se reintentará'));},30000);inboxRequests.set(id,{resolve,reject,timer});bridge().inboxAsync(id);});}

function nativeStatus(){if(!bridge()?.status)return null;const status=JSON.parse(bridge().status());if(status.error)throw Error('No se puede leer la cola local');return status;}
function display(status,unsubmitted=0,incomplete=0){if(!alive())return;
 if(!status){el('sync-account').textContent='Carpeta sin seleccionar';el('sync-status').textContent='BLOQUEADO: el selector SAF y los trabajos locales requieren la APK Android. No se simula una transferencia.';el('sync-auto').checked=false;el('sync-auto').disabled=true;el('sync-last').textContent='Última importación: nunca';el('sync-export').textContent='Última exportación: nunca';return;}
 el('sync-account').textContent=status.connected?'Carpeta local: '+status.account:'Carpeta sin acceso'+(status.account?' · '+status.account:'');
 const pending=status.outgoing+unsubmitted;
 if(el('work-sync-status'))el('work-sync-status').textContent=(status.state==='RUNNING'?'Intercambiando archivos…':status.connected&&status.key?'Syncthing · '+pending+' pendientes de exportar':'Solo local · configura carpeta y frase');
 el('sync-auto').disabled=false;el('sync-auto').checked=!!status.auto;
 el('sync-status').textContent=(messages[status.state]||'Estado: '+status.state)+(status.migrationProblems?' Hay operaciones antiguas pendientes de recuperación; se conservan sus almacenes.':'');
 el('sync-last').textContent='Última importación validada: '+(status.lastImport?new Date(status.lastImport).toLocaleString('es-ES'):'nunca');
 el('sync-export').textContent='Última exportación validada en esta carpeta: '+(status.lastExport?new Date(status.lastExport).toLocaleString('es-ES'):'nunca');
 el('sync-pending').textContent='Operaciones locales sin preparar: '+unsubmitted+' · Pendientes de exportar: '+status.outgoing+' · Recibidas pendientes de incorporar: '+status.incoming+(incomplete?' · Antecesores pendientes: '+incomplete:'')+(status.incomplete?' · Archivos incompletos: '+status.incomplete:'');
}
async function showConflicts(){const conflicts=await CTWorkUI.store.conflicts();if(!alive())return;const signature=JSON.stringify(conflicts.map(c=>[c.entity,c.versions.map(v=>v.id)]));if(signature===conflictSignature)return;conflictSignature=signature;const box=el('sync-conflicts');box.replaceChildren();for(const conflict of conflicts){const section=document.createElement('section'),title=document.createElement('h3');title.textContent='Conflicto de trabajo: '+conflict.entity;section.append(title);for(const version of conflict.versions){const text=document.createElement('p');text.textContent=version.deleted?'Versión de borrado':version.entry.text;const choose=document.createElement('button');choose.textContent='Elegir esta versión';choose.onclick=()=>{resolution={entity:conflict.entity,id:version.id,heads:conflict.versions.map(v=>v.id)};el('sync-resolve-preview').textContent=version.deleted?'BORRADO':version.entry.text;el('sync-resolve-dialog').hidden=false;};section.append(text,choose);}box.append(section);}}
async function pump(){
 if(!alive())return;
 if(busy){again=true;return;}busy=true;
 try{
  await CTWorkUI.ready;if(!alive())return;let status=nativeStatus();const ops=await CTWorkUI.store.revisions();
  if(!status){display(status,ops.length);await showConflicts();return;}
  // The native queue is durable and idempotent; never acknowledge before the IDB transaction commits.
  let received=0;
  for(let i=0;i<20;i++){const incoming=await readInbox();if(!Array.isArray(incoming))throw Error('No se pudo descifrar la bandeja; revisa frase/protección local');if(!incoming.length)break;await CTWorkUI.store.receive(incoming);const consistent=(await CTWorkUI.store.unresolved())===0;if(!JSON.parse(bridge().acknowledge(JSON.stringify({ids:incoming.map(o=>o.id),consistent}))).ok)throw Error('No se pudo confirmar la aplicación local');received+=incoming.length;}
  const current=received?await CTWorkUI.store.revisions():ops;let queued=0;
  for(let start=0;start<current.length;start+=200){const batch=current.slice(start,start+200),missing=JSON.parse(bridge().missing(JSON.stringify(batch.map(o=>o.id))));if(!Array.isArray(missing))throw Error('No se puede leer la cola persistente');const ids=new Set(missing);for(const op of batch){if(!ids.has(op.id))continue;if(!JSON.parse(bridge().enqueue(JSON.stringify(op))).ok)throw Error('No se pudo preparar el cambio; permanece en el diario local');queued++;if(queued%10===0)await new Promise(resolve=>setTimeout(resolve,0));}}
  const incomplete=await CTWorkUI.store.unresolved();bridge().acknowledge(JSON.stringify({ids:[],consistent:incomplete===0}));status=nativeStatus();display(status,0,incomplete);await showConflicts();
  const fingerprint=current.length+':'+current.map(op=>op.id).sort().join(',');
  if(queued||fingerprint!==lastFingerprint){lastFingerprint=fingerprint;bridge().trigger();}
 }catch(error){if(!alive())return;el('sync-status').textContent=error.message||'No se pudo sincronizar. Tus datos locales se conservan.';if(el('work-sync-status'))el('work-sync-status').textContent='Pendiente de sincronizar · datos guardados localmente';}
 finally{busy=false;if(again&&alive()){again=false;setTimeout(pump,0);}}
}
el('sync-folder').onclick=()=>{if(bridge()?.chooseFolder)bridge().chooseFolder();else display(null);};
el('sync-auto').onchange=()=>{if(bridge()?.automatic)bridge().automatic(el('sync-auto').checked);pump();};
el('sync-now').onclick=async()=>{await pump();if(bridge()?.syncNow)bridge().syncNow();};
el('sync-unlock').onclick=()=>{try{if(!bridge()?.setPhrase)throw Error();const result=JSON.parse(bridge().setPhrase(el('sync-phrase').value));if(!result.ok)throw Error();el('sync-phrase').value='';lastFingerprint='';pump();}catch{el('sync-status').textContent='Introduce una frase de 16–1024 caracteres. No se cambia automáticamente una clave ya utilizada para intercambiar archivos. Los datos se conservan.';}};
el('sync-resolve-cancel').onclick=()=>{resolution=null;el('sync-resolve-dialog').hidden=true;};
el('sync-resolve-confirm').onclick=async()=>{if(!resolution)return;try{await CTWorkUI.store.resolve(resolution.entity,resolution.id,resolution.heads);resolution=null;el('sync-resolve-dialog').hidden=true;await pump();}catch{el('sync-status').textContent='El conflicto cambió o no se pudo guardar. No se sobrescribió ninguna versión.';conflictSignature='';await showConflicts();}};
window.addEventListener('telecontrol-work-changed',event=>{
 // IDB has committed; durable native handoff precedes the local Saved confirmation.
 // Bulk migrations/imports use the yielding reconciler instead of blocking the interface.
 const operations=event.detail?.operations||[],b=bridge();
 if(operations.length&&operations.length<=10&&b?.enqueue)try{const status=nativeStatus();if(status?.localQueue||status?.connected&&status.key){for(const op of operations){if(!JSON.parse(b.enqueue(JSON.stringify(op))).ok)break;}b.trigger();}}catch{/* IDB revisions remain durable for the next reconciliation. */}
 pump();
});window.addEventListener('telecontrol-work-opened',()=>{pump();if(bridge()?.trigger)bridge().trigger();});window.addEventListener('online',()=>{pump();if(bridge()?.trigger)bridge().trigger();});document.addEventListener('visibilitychange',()=>{if(!document.hidden)pump();});
window.CTSyncUI={inboxResult(id,payload){const pending=inboxRequests.get(id);if(!pending)return;inboxRequests.delete(id);clearTimeout(pending.timer);try{pending.resolve(JSON.parse(payload));}catch{pending.reject(Error('No se pudo recibir la bandeja local'));}},resume(){lastFingerprint='';pump();if(bridge()?.trigger)bridge().trigger();},folderResult(){lastFingerprint='';pump();},pump};
display(null);
setInterval(()=>{if(!document.hidden){pump();if(bridge()?.trigger)bridge().trigger();}},15000);pump();
})();
