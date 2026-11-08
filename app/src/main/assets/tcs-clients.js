'use strict';
/* Public safety adapter: no embedded operational client ranges or routing tables.
 * May be backed by an explicitly selected, local user file in a future release. */
(function(root){
 const api={get:()=>null,fromRecord:()=>null,normalizeGroup:()=>null,normalizeAdq:()=>null,groups:[],maxAdq:()=>0};
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.CTTcs=api;
 if(!root.document)return;
 api.renderRecord=function(){
  const results=root.document.getElementById('tcs-results');
  const message=root.document.getElementById('tcs-message');
  if(results){results.replaceChildren();results.hidden=true;}
  if(message)message.textContent='Información de clientes de red no disponible sin una configuración privada importada localmente.';
 };
})(typeof window!=='undefined'?window:globalThis);
