'use strict';
importScripts('catalog.js');
self.onmessage=async event=>{
 try{
  const file=event.data;
  if(file.size>CTCatalog.MAX_BYTES)throw Error('El archivo supera el límite de 96 MB');
  self.postMessage({stage:'reading'});
  const text=typeof file.text==='function'?await file.text():new FileReaderSync().readAsText(file);
  self.postMessage({stage:'validating'});
  let data;try{data=JSON.parse(text);}catch{throw Error('El archivo no contiene un JSON válido');}
  self.postMessage({data:CTCatalog.validateCatalog(data)});
 }catch(error){self.postMessage({error:error.message==='El catálogo está vacío'?'El catálogo está vacío':error.message==='El archivo supera el límite de 96 MB'?error.message:error.message==='El archivo no contiene un JSON válido'?error.message:'Formato de catálogo no válido'});}
};
