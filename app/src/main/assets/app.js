'use strict';
(()=>{
// Android 30 may ship Chromium older than Element.replaceChildren (Chrome 86).
if(!Element.prototype.replaceChildren){Element.prototype.replaceChildren=function(...nodes){while(this.firstChild)this.removeChild(this.firstChild);this.append(...nodes);};}
const el=id=>document.getElementById(id);
const fieldNames=['MATRÍCULA','NOMBRE','CÓDIGO BDI','N TRAFOS','TI1 BT REL.TRANSFORMACIÓN','TI2 BT REL.TRANSFORMACIÓN','MUNICIPIO','CALLE','ALIMENTACIÓN','GST','CPU','TIPO RFID','CAA','IP LAN','IP TC'];
let active=null;let currentScreen='home',previousScreen='home';
const safeGet=(key,fallback)=>{try{const v=localStorage.getItem(key);return v===null?fallback:JSON.parse(v)}catch{return fallback}};
const safeSet=(key,value)=>{try{localStorage.setItem(key,JSON.stringify(value));return true}catch{return false}};
let visibleFields=safeGet('telecontrol-fields-v1',null);
if(!Array.isArray(visibleFields))visibleFields=fieldNames.slice();else visibleFields=visibleFields.filter(x=>typeof x==='string');
let extraFieldNames=[];let availableFields=fieldNames.slice();
let fieldIndices=new Map(fieldNames.map((name,index)=>[name,index]));
const fieldIndex=name=>fieldIndices.get(name)??-1;
visibleFields=[...new Set([...fieldNames,...visibleFields])];
const sortedFields=fields=>{const defaults=fields.filter(name=>fieldNames.includes(name));const extra=fields.filter(name=>!fieldNames.includes(name));extra.sort((a,b)=>fieldIndex(a)-fieldIndex(b));return [...defaults,...extra]};const sortVisibleFields=()=>{visibleFields=sortedFields(visibleFields)};
function saveFields(next){if(!safeSet('telecontrol-fields-v1',next)){el('field-status').textContent='No se pudo guardar la selección. Se conserva la configuración anterior.';return false;}visibleFields=next;return true;}
sortVisibleFields();
function switchScreen(id){if(currentScreen==='weekly'&&id!=='weekly'&&window.CTWeeklyUI&&!CTWeeklyUI.canLeave())return false;if(currentScreen==='work'&&id!=='work'&&window.CTWorkUI&&!CTWorkUI.canLeave(()=>switchScreen(id)))return false;['home','detail','config','help','work','feedback','reminders','weekly'].forEach(x=>el(x).hidden=x!==id);currentScreen=id;if(window.CTSpeech?.clearStatus)window.CTSpeech.clearStatus();window.scrollTo(0,0);return true;}
function openScreen(id){if(!flushNotes())return false;previousScreen=currentScreen;return switchScreen(id);}
function returnScreen(){const target=previousScreen==='config'||previousScreen==='help'||previousScreen==='feedback'?'home':previousScreen;switchScreen(target);if(target==='detail'&&active)showDetail(active)}
function renderSelection(){const box=el('selected-fields');const fragment=document.createDocumentFragment();visibleFields.forEach(name=>{const row=document.createElement('div');row.style.padding='9px';row.textContent=name+' ';const btn=document.createElement('button');btn.className='action-small';btn.textContent='QUITAR';btn.addEventListener('click',()=>{if(saveFields(visibleFields.filter(x=>x!==name))){el('field-status').textContent='Campo quitado';renderSelection();}});if(!fieldNames.includes(name))row.append(btn);fragment.append(row)});box.replaceChildren(fragment);}
function showConfig(){if(!openScreen('config'))return;el('config').classList.remove('config-detail-open');document.querySelectorAll('.config-section').forEach(section=>section.hidden=true);const list=el('field-options');list.replaceChildren();availableFields.forEach(name=>{const opt=document.createElement('option');opt.value=name;list.append(opt)});renderSelection()}
function flushNotes(){return true;}
const {norm,validateCatalog,MAX_BYTES}=CTCatalog;
let DB=null;
const catalogStatus=el('catalog-status');
const {readCatalog,saveCatalog}=CTStore.createStore(indexedDB);
function applyCatalog(data){DB=data.records;extraFieldNames=data.fieldNames;fieldIndices=new Map(fieldNames.map((name,index)=>[name,index]));extraFieldNames.forEach((name,index)=>{if(!fieldIndices.has(name))fieldIndices.set(name,17+index);});availableFields=[...new Set([...fieldNames,...extraFieldNames])];visibleFields=[...new Set([...fieldNames,...visibleFields.filter(name=>fieldIndex(name)>=0)])];sortVisibleFields();if(!safeSet('telecontrol-fields-v1',visibleFields))el('field-status').textContent='El catálogo está guardado, pero no se pudo guardar la selección de campos.';active=null;catalogStatus.textContent='Catálogo instalado: '+Object.keys(DB).length+' CT · '+extraFieldNames.length+' campos.';}
let importing=false;
el('catalog-file').disabled=true;
const catalogReady=readCatalog().then(data=>{if(data)applyCatalog(validateCatalog(data));}).catch(()=>{catalogStatus.textContent='No se pudo leer el catálogo local. Importa de nuevo el JSON.';}).finally(()=>{el('catalog-file').disabled=false;});
function parseFile(file){return new Promise((resolve,reject)=>{
 const worker=new Worker('catalog-worker.js');
 const finish=(error,data)=>{clearTimeout(timer);worker.terminate();error?reject(Error(error)):resolve(data);};
 const timer=setTimeout(()=>finish('La importación ha tardado demasiado. Inténtalo de nuevo.'),120000);
 worker.onmessage=event=>{if(event.data.stage){catalogStatus.textContent=event.data.stage==='reading'?'Leyendo archivo privado...':'Validando estructura del catálogo...';return;}finish(event.data.error,event.data.data);};
 worker.onerror=()=>finish('No se pudo procesar el archivo');
 worker.postMessage(file);
});}
async function importCatalog(file){
 if(importing)return false;
 importing=true;el('catalog-file').disabled=true;el('catalog-panel').setAttribute('aria-busy','true');
 try{
  await catalogReady;
  catalogStatus.textContent='Validando catálogo privado...';
  if(file.size>MAX_BYTES)throw Error('El archivo supera el límite de 96 MB');
  const data=await parseFile(file);
  catalogStatus.textContent='Guardando catálogo en este dispositivo...';
  await saveCatalog(data);applyCatalog(data);return true;
 }catch(error){catalogStatus.textContent='No se ha importado. El catálogo anterior se conserva. '+error.message;return false;}
 finally{importing=false;el('catalog-file').disabled=false;el('catalog-file').value='';el('catalog-panel').setAttribute('aria-busy','false');}
}
el('catalog-file').addEventListener('change',event=>{const file=event.target.files?.[0];if(file)importCatalog(file);});
function searchOpen(){el('open-search').setAttribute('aria-expanded','true');el('bad').hidden=true;el('load-error').hidden=!!DB;el('search-panel').hidden=false;el('query').value='';el('query').focus();}
function searchClose(){el('search-panel').hidden=true;el('open-search').setAttribute('aria-expanded','false');if(currentScreen==='home')el('open-search').focus();}
function showError(){el('bad').textContent='⚠ CT no encontrado';el('bad').hidden=false;el('query').focus();}
function normalizeSearchMatricula(value){const cleaned=String(value??'').toUpperCase().replace(/\s+/g,'').replace(/[^A-Z0-9]/g,'');if(!cleaned)return '';if(cleaned[0]!=='2'&&cleaned[0]!=='4')return '';if(cleaned.length===1)return cleaned;return ((cleaned.startsWith('28')||cleaned.startsWith('42'))?cleaned:cleaned[0]).slice(0,40);}
function search(){el('bad').hidden=true;el('load-error').hidden=!!DB;const q=normalizeSearchMatricula(el('query').value);el('query').value=q;if(!/^(28|42)[A-Z0-9]+$/.test(q)||!DB||!Object.prototype.hasOwnProperty.call(DB,q)){showError();return;}showDetail(DB[q]);}
function makeField(label,value){
 const line=document.createElement('div');line.className='field';
 const name=document.createElement('div');name.className='name';name.textContent=label;
 const val=document.createElement('div');val.className='value';val.textContent=value===undefined||value===null||String(value).trim()===''?'—':String(value);
 if(val.textContent==='—')val.classList.add('missing');line.append(name,val);return line;
}
function showDetail(record){
 if(!flushNotes())return;active=record;const container=el('fields');const frag=document.createDocumentFragment();
 visibleFields.forEach(field=>{const i=fieldIndex(field);if(i>=0&&i<record.length)frag.append(makeField(field,record[i]))});
 container.replaceChildren(frag);
 if(window.CTTcs?.renderRecord)window.CTTcs.renderRecord(record);
 const lat=record[15],lon=record[16];const ok=validCoordinates(lat,lon);
 el('map').disabled=!ok;el('coords-warning').hidden=ok;
 const counter=el('notes-count');CTWorkUI.count(norm(record[0])).then(n=>{if(active===record)counter.textContent=String(n);}).catch(()=>{counter.textContent='!';});searchClose();switchScreen('detail');
}
function goHome(){if(!flushNotes())return false;if(!switchScreen('home'))return false;active=null;return true;}
function validCoordinates(lat,lon){return typeof lat==='number'&&typeof lon==='number'&&Number.isFinite(lat)&&Number.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
function openMaps(){if(!active||!validCoordinates(active[15],active[16]))return;sendMapsCoordinates(active[15],active[16]);}
function sendMapsCoordinates(lat,lon){
 const url='https://www.google.com/maps/search/?api=1&query='+encodeURIComponent(lat+','+lon);
 if(window.AppInventor&&typeof window.AppInventor.setWebViewString==='function')window.AppInventor.setWebViewString(url);
 else window.open(url,'_blank','noopener,noreferrer');
}
el('open-reminders').addEventListener('click',()=>{if(openScreen('reminders'))CTRemindersUI.refresh();});
el('reminder-back').addEventListener('click',returnScreen);
el('open-journal').addEventListener('click',()=>{if(!switchScreen('work'))return;active=null;CTWorkUI.open(null,'journal').catch(()=>{});});
el('open-search').addEventListener('click',searchOpen);
el('cancel').addEventListener('click',searchClose);
el('submit').addEventListener('click',search);
el('query').addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();searchClose();}if(e.key==='Enter'){e.preventDefault();search();}});
el('query').addEventListener('input',()=>{const input=el('query');const cursor=input.selectionStart;const previous=input.value;const cleaned=normalizeSearchMatricula(previous);input.value=cleaned;try{const pos=normalizeSearchMatricula(previous.slice(0,cursor)).length;input.setSelectionRange(pos,pos);}catch{}el('bad').hidden=true;});
el('top-back').addEventListener('click',goHome);
el('map').addEventListener('click',openMaps);
el('open-notes').addEventListener('click',()=>{if(!active||!switchScreen('work'))return;CTWorkUI.open(norm(active[0])).catch(()=>{});});
['home-feedback','detail-feedback'].forEach(id=>el(id).addEventListener('click',()=>openScreen('feedback')));
el('feedback-back').addEventListener('click',returnScreen);
el('feedback-form').addEventListener('submit',event=>{
 event.preventDefault();
 const name=el('feedback-name').value.trim(),email=el('feedback-email').value.trim(),type=el('feedback-type').value,description=el('feedback-description').value.trim();
 if(!name||!email||!description||!['ERROR','SUGERENCIA','COMENTARIO'].includes(type)){el('feedback-status').textContent='Completa todos los campos.';return;}
 const subject='[APP TELECONTROL - '+type+']';
 const body='Nombre: '+name+'\nEmail de contacto: '+email+'\nTipo: '+type+'\nVersión: TELECONTROL v2.1\n\nDescripción:\n'+description;
 if(window.AppInventor&&typeof window.AppInventor.sendFeedback==='function'){
  window.AppInventor.sendFeedback(subject,body);
  el('feedback-status').textContent='Se abrirá tu correo para revisar y confirmar el envío.';
 }else{
  el('feedback-status').textContent='El envío requiere la aplicación Android con correo configurado.';
 }
});
['home-help','detail-help'].forEach(id=>el(id).addEventListener('click',()=>openScreen('help')));
['home-config','detail-config'].forEach(id=>el(id).addEventListener('click',showConfig));
document.querySelectorAll('[data-config-target]').forEach(button=>button.addEventListener('click',()=>{const target=button.getAttribute('data-config-target');document.querySelectorAll('.config-section').forEach(section=>section.hidden=section.id!==target);el('config').classList.add('config-detail-open');window.scrollTo(0,0);}));
el('config-back').addEventListener('click',()=>{if(el('config').classList.contains('config-detail-open')){el('config').classList.remove('config-detail-open');document.querySelectorAll('.config-section').forEach(section=>section.hidden=true);window.scrollTo(0,0);}else returnScreen();});el('help-back').addEventListener('click',returnScreen);
el('add-field').addEventListener('click',()=>{const value=el('field-search').value.trim();const name=availableFields.find(x=>x.toLowerCase()===value.toLowerCase());if(!name){el('field-status').textContent='Campo no disponible en el catálogo instalado';return}if(!visibleFields.includes(name)&&!saveFields(sortedFields([...visibleFields,name])))return;el('field-status').textContent='Campo añadido';el('field-search').value='';renderSelection()});
function closeWork(){if(active)showDetail(active);else if(window.CTWeeklyUI?.journalActive()){switchScreen('weekly');CTWeeklyUI.returnFromJournal();}else goHome();}
function handleBack(){
 if(currentScreen==='weekly'){if(!CTWeeklyUI.back())goHome();return true;}
 if(currentScreen==='work'){if(CTWorkUI.canLeave(()=>closeWork()))closeWork();return true;}
 if(currentScreen==='config'){el('config-back').click();return true;}
 if(currentScreen==='help'||currentScreen==='feedback'||currentScreen==='reminders'){returnScreen();return true;}
 if(!el('search-panel').hidden){searchClose();return true;}
 if(!el('detail').hidden){goHome();return true;}
 return false;
}
function utmCoordinates(x,y,zone){x=Number(x);y=Number(y);if(!Number.isFinite(x)||!Number.isFinite(y)||x<100000||x>900000||y<3800000||y>4900000||![30,31].includes(zone))return null;x-=500000;const a=6378137,e=.00669437999014,k=.9996,e1=(1-Math.sqrt(1-e))/(1+Math.sqrt(1-e)),mu=(y/k)/(a*(1-e/4-3*e**2/64-5*e**3/256)),phi=mu+(3*e1/2-27*e1**3/32)*Math.sin(2*mu)+(21*e1**2/16-55*e1**4/32)*Math.sin(4*mu)+151*e1**3/96*Math.sin(6*mu)+1097*e1**4/512*Math.sin(8*mu),ep=e/(1-e),c=ep*Math.cos(phi)**2,t=Math.tan(phi)**2,n=a/Math.sqrt(1-e*Math.sin(phi)**2),r=a*(1-e)/(1-e*Math.sin(phi)**2)**1.5,d=x/(n*k),lat=phi-n*Math.tan(phi)/r*(d*d/2-(5+3*t+10*c-4*c*c-9*ep)*d**4/24+(61+90*t+298*c+45*t*t-252*ep-3*c*c)*d**6/720),lon=(d-(1+2*t+c)*d**3/6+(5-2*c+28*t-3*c*c+8*ep+24*t*t)*d**5/120)/Math.cos(phi),out=[lat*180/Math.PI,(zone-1)*6-177+lon*180/Math.PI];return out[0]>=35&&out[0]<=45&&out[1]>=-10&&out[1]<=5?out:null;}
function openMapsForMatricula(matricula){const record=DB?.[norm(matricula)];if(!record)return 'Ubicación del CT no disponible';if(validCoordinates(record[15],record[16])){sendMapsCoordinates(record[15],record[16]);return '';}const get=names=>{for(const [name,i]of fieldIndices)if(names.includes(name.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]/g,'')))return record[i];};const epsg=String(get(['EPSG','SISTEMADEREFERENCIA','CRS'])||'').replace(/^EPSG[: ]*/i,'');const zone=['25830','32630'].includes(epsg)?30:['25831','32631'].includes(epsg)?31:null;const converted=zone&&utmCoordinates(get(['XBDI','COORDENADAXUTM']),get(['YBDI','COORDENADAYUTM']),zone);if(!converted)return 'Ubicación del CT no disponible';sendMapsCoordinates(...converted);return '';}
function centreInfo(matricula){const r=DB?.[norm(matricula)];if(!r)return {bdiName:'No disponible',ipLan:'No disponible'};const clean=n=>n.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]/g,'');const find=aliases=>{for(const [name,i]of fieldIndices)if(aliases.includes(clean(name))&&r[i]!==null&&r[i]!==undefined&&String(r[i]).trim())return String(r[i]);return 'No disponible';};const named=find(['NOMBREBDI','NOMBRECENTROBDI']);return {bdiName:named!=='No disponible'?named:(r[1]===null||r[1]===undefined||!String(r[1]).trim()?'No disponible':String(r[1])),ipLan:find(['IPLAN','IPLANBDI','IPLANEDIT'])};}
function installedBuild(){try{const v=JSON.parse(AppInventor.packageVersion());if(v.packageName==='es.josxtm.telecontrol'&&Number.isInteger(v.versionCode))document.querySelectorAll('.installed-build').forEach(el=>el.textContent=String(v.versionCode));}catch{}}
installedBuild();
// Android-native SSH never receives the full catalogue, shell commands or credentials.
function openMangoForMatricula(matricula=''){
 const code=normalizeSearchMatricula(matricula),record=code&&DB&&Object.prototype.hasOwnProperty.call(DB,code)?DB[code]:null;
 const info=record?centreInfo(code):null;
 const name=record?String(record[1]??'').slice(0,120):'';
 const lan=info&&info.ipLan!=='No disponible'?String(info.ipLan):'';
 if(window.CTMangoNative&&typeof CTMangoNative.open==='function')CTMangoNative.open(record?code:'',name,lan);
 else if(window.document){const button=el('home-mango');button.title='MangoSSH requiere la APK Android';}
}
el('home-mango').addEventListener('click',()=>openMangoForMatricula());
el('detail-mango').addEventListener('click',()=>openMangoForMatricula(active?active[0]:''));
window.CTApp={openScreen,openMapsForMatricula,openMangoForMatricula,searchOpen,search,goHome,norm,handleBack,importCatalog,catalogReady,closeWork,centreInfo,utmCoordinates};
})();
