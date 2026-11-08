'use strict';
(()=>{
const palettes={light:['#f5f5f7','#ffffff','#ffe69a','#705000'],dark:['#141416','#29292d','#ffe69a','#ffdb67'],red:['#8c343d','#71303a','#ffe69a','#ffd95e'],olive:['#454c32','#303826','#ffe69a','#ffdb67'],blue:['#154778','#10345c','#ffe69a','#ffdb67'],custom:['#8c343d','#71303a','#ffe69a','#ffd95e']};
const names=['background','surface','button','accent'];
const labels=['Fondo','Superficies','Botones','Acentos'];
const valid=x=>typeof x==='string'&&/^#[0-9a-f]{6}$/i.test(x);
function ink(hex){const rgb=hex.slice(1).match(/../g).map(x=>parseInt(x,16)/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722>.179?'#000000':'#ffffff';}
let saved;try{if(typeof window.AppInventor?.loadAppearance==='function')saved=JSON.parse(window.AppInventor.loadAppearance());}catch{}
if(!saved||!Object.prototype.hasOwnProperty.call(palettes,saved.mode)||!Array.isArray(saved.custom)||saved.custom.length!==4||!saved.custom.every(valid)){try{saved=JSON.parse(localStorage.getItem('telecontrol-theme-v1'));}catch{saved=null;}}
let mode=Object.prototype.hasOwnProperty.call(palettes,saved?.mode)?saved.mode:'red';
let custom=Array.isArray(saved?.custom)&&saved.custom.length===4&&saved.custom.every(valid)?saved.custom:palettes.custom.slice();
const select=document.getElementById('theme-select'),box=document.getElementById('custom-colors');
function apply(){const colors=mode==='custom'?custom:palettes[mode];const style=document.documentElement.style;names.forEach((key,i)=>style.setProperty('--'+key,colors[i]));style.setProperty('--ink',ink(colors[0]));style.setProperty('--surface-ink',ink(colors[1]));style.setProperty('--button-ink',ink(colors[2]));document.documentElement.dataset.theme=mode;document.querySelector('meta[name="theme-color"]')?.setAttribute('content',colors[0]);if(window.AppInventor&&typeof window.AppInventor.setThemeColor==='function')window.AppInventor.setThemeColor(colors[0]);select.value=mode;box.hidden=mode!=='custom';try{const snapshot=JSON.stringify({mode,custom});localStorage.setItem('telecontrol-theme-v1',snapshot);if(typeof window.AppInventor?.saveAppearance==='function'&&window.AppInventor.saveAppearance(snapshot)!==true)throw Error('Native preference commit failed');document.getElementById('theme-status').textContent='';}catch{document.getElementById('theme-status').textContent='No se pudo guardar la apariencia';}}
labels.forEach((label,i)=>{const row=document.createElement('label');row.textContent=label;const input=document.createElement('input');input.type='color';input.value=custom[i];input.setAttribute('aria-label',label);input.addEventListener('input',()=>{if(valid(input.value)){custom[i]=input.value;apply();}});row.append(input);box.append(row);});
select.addEventListener('change',()=>{mode=Object.prototype.hasOwnProperty.call(palettes,select.value)?select.value:'red';apply();});apply();
})();
