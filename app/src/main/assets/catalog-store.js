(function(root){
'use strict';
function createStore(indexedDB){
 function open(){return new Promise((resolve,reject)=>{
  const req=indexedDB.open('telecontrol-private-catalog',1);
  req.onupgradeneeded=()=>req.result.createObjectStore('catalog');
  req.onerror=()=>reject(Error('No se puede abrir el almacenamiento local'));
  req.onblocked=()=>reject(Error('Cierra otras ventanas de TELECONTROL e inténtalo de nuevo'));
  req.onsuccess=()=>resolve(req.result);
 });}
 async function transaction(mode,data){
  const db=await open();
  return new Promise((resolve,reject)=>{
   let value=null,tx;
   try{
    tx=db.transaction('catalog',mode);
    const store=tx.objectStore('catalog');
    const req=mode==='readonly'?store.get('current'):store.put(data,'current');
    req.onsuccess=()=>{value=req.result??null;};
    tx.oncomplete=()=>{db.close();resolve(value);};
    tx.onabort=tx.onerror=()=>{db.close();reject(Error('No se pudo guardar o leer el catálogo. Comprueba el espacio disponible.'));};
   }catch(error){db.close();reject(Error('Almacenamiento local no disponible'));}
  });
 }
 return {readCatalog:()=>transaction('readonly'),saveCatalog:data=>transaction('readwrite',data)};
}
if(typeof module==='object')module.exports={createStore};else root.CTStore={createStore};
})(globalThis);
