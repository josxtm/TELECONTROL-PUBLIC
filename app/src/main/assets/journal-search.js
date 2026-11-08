(function(root){
'use strict';
const fold=text=>text.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es');
function ranges(text,query){const needle=fold(query.trim());if(!needle)return [];let normalized='',map=[],offset=0;for(const ch of text){const part=fold(ch);for(let i=0;i<part.length;i++)map.push([offset,offset+ch.length]);normalized+=part;offset+=ch.length;}const found=[];let start=0;while((start=normalized.indexOf(needle,start))!==-1){found.push([map[start][0],map[start+needle.length-1][1]]);start+=needle.length;}return found;}
function filter(entries,query){const q=fold(query.trim());return q?entries.filter(e=>fold(e.text).includes(q)):entries;}
function highlight(document,node,text,query){const matches=ranges(text,query);let pos=0;for(const [start,end] of matches){node.append(document.createTextNode(text.slice(pos,start)));const mark=document.createElement('mark');mark.textContent=text.slice(start,end);node.append(mark);pos=end;}node.append(document.createTextNode(text.slice(pos)));}
const api={fold,ranges,filter,highlight};if(typeof module==='object')module.exports=api;else root.CTJournalSearch=api;
})(globalThis);
