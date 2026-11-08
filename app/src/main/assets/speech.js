'use strict';
(()=>{
const allowed=['query','ct-notes','entry-text'];let pending=null;
const status=document.createElement('p');status.id='speech-status';status.setAttribute('role','status');document.body.append(status);
function clearStatus(){status.textContent='';status.hidden=true;}
function showStatus(message,target){status.hidden=false;status.textContent=message;const screen=document.getElementById(target==='query'?'home':'work');if(screen)screen.append(status);}
clearStatus();
function normalizeSpokenMatricula(text){
 const ones={cero:0,uno:1,una:1,dos:2,tres:3,cuatro:4,cinco:5,seis:6,siete:7,ocho:8,nueve:9};
 const tens={treinta:30,cuarenta:40,cincuenta:50,sesenta:60,setenta:70,ochenta:80,noventa:90};
 const basic={diez:10,once:11,doce:12,trece:13,catorce:14,quince:15,dieciseis:16,diecisiete:17,dieciocho:18,diecinueve:19,veinte:20,cien:100};
 let value=text.toLocaleLowerCase('es-ES').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[.,;:]/g,' ').trim();
 value=value.replace(/\b(treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa) y (uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve)\b/g,(_,a,b)=>String(tens[a]+ones[b]));
 value=value.replace(/\bveinti(uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve)\b/g,(_,n)=>String(20+ones[n]));
 value=value.replace(/\b(cero|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciseis|diecisiete|dieciocho|diecinueve|veinte|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien)\b/g,w=>String(ones[w]??tens[w]??basic[w]??w));
 return value.toUpperCase().replace(/[^A-Z0-9]/g,'');
}
function receive(target,text,error){
 if(target!==pending)return;
 pending=null;
 const input=document.getElementById(target);
 if(error||!input){showStatus(error||'No se encuentra el campo de dictado',target);return;}
 const value=typeof text==='string'?text.trim():'';
 if(!value){showStatus('No se ha reconocido texto. Puedes escribir manualmente.',target);return;}
 if(target==='query')input.value=normalizeSpokenMatricula(value);
 else input.value=(input.value?input.value+'\n':'')+value;
 input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();showStatus('Dictado añadido. Revisa y confirma el texto.',target);
}
function request(target){
 if(!allowed.includes(target)||pending)return;
 if(!window.AppInventor||typeof window.AppInventor.dictate!=='function'){showStatus('Dictado no disponible. Puedes escribir manualmente.',target);return;}
 pending=target;showStatus('Abriendo reconocimiento de voz...',target);
 try{window.AppInventor.dictate(target);}catch{receive(target,'','No se pudo iniciar el dictado. Puedes escribir manualmente.');}
}
function attach(target){const input=document.getElementById(target);if(!input||document.getElementById('mic-'+target))return;const button=document.createElement('button');button.type='button';button.id='mic-'+target;button.className='action-small icon-button';button.setAttribute('aria-label','Dictar en español');button.title='Dictar en español';button.textContent='🎙';button.addEventListener('click',()=>request(target));input.after(button);}
window.CTSpeech={receive,request,attach,clearStatus};allowed.forEach(attach);
})();
