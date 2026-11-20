"""Exercise the packaged Web Worker with synthetic documents, including OOXML."""
import base64
import gzip
import io
import json
from pathlib import Path
import subprocess
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WORKER_DRIVER = r"""
const fs=require('node:fs'), vm=require('node:vm'), path=require('node:path');
const cases=JSON.parse(fs.readFileSync(0,'utf8'));
(async()=>{
 const results=[];
 for(const item of cases){
  const file=new Blob([Buffer.from(item.bytes,'base64')]);file.name=item.name;
  const context=vm.createContext({Blob,Uint8Array,Uint16Array,Uint32Array,Int32Array,ArrayBuffer,
   TextDecoder,TextEncoder,DecompressionStream,setTimeout,clearTimeout,setImmediate,
   postMessage:message=>{if(!message.stage)results.push(message);}});
  context.self=context;
  context.importScripts=(...names)=>names.forEach(n=>vm.runInContext(fs.readFileSync(path.join(process.cwd(),n),'utf8'),context,{filename:n}));
  context.importScripts('catalog-worker.js');
  await context.onmessage({data:{file,allowGrid:item.mobile}});
 }
 process.stdout.write(JSON.stringify(results));
})().catch(()=>process.exit(1));
"""


def workbook():
    from xml.sax.saxutils import escape
    content = io.BytesIO()
    with zipfile.ZipFile(content, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('xl/workbook.xml', '<workbook xmlns:r="urn:rel"><sheets>'
                   '<sheet name="Grid MT" r:id="rId1"/></sheets></workbook>')
        z.writestr('xl/_rels/workbook.xml.rels', '<Relationships><Relationship Id="rId1" '
                   'Type="urn:test/worksheet" Target="/xl/worksheets/sheet2.xml"/></Relationships>')
        z.writestr('xl/sharedStrings.xml', '<sst><si><r><t>MATRÍ</t></r><r><t>CULA [BDI]</t></r></si></sst>')
        rows = ['<row><c r="A1" t="s"><v>0</v></c><c r="B1" t="inlineStr"><is><t>Nombre</t></is></c>'
                '<c r="D1" t="inlineStr"><is><t>Contraseña</t></is></c></row>']
        for index, prefix in enumerate(('28', '82', '45', '99'), start=2):
            row = ''
            for col, value in [('A', prefix+'TEST'), ('B', 'Prueba & muestra'), ('D', 'must-be-omitted')]:
                row += f'<c r="{col}{index}" t="inlineStr"><is><t>{escape(value)}</t></is></c>'
            rows.append('<row>'+row+'</row>')
        z.writestr('xl/worksheets/sheet2.xml', '<worksheet><sheetData>'+''.join(rows)+'</sheetData></worksheet>')
    return content.getvalue()


class MobileGridTest(unittest.TestCase):
    def test_formats_projection_scope_and_failure(self):
        csv = ('Matrícula [BDI];NOMBRE [BDI];CÓDIGO [BDI];PROVINCIA;PASSWORD;RTU1 MODELO;LAT;LON\r\n'
               '28TEST;"Muñoz; ""norte""";001;A;must-be-omitted;ABC;"40,5";-3\r\n'
               '82TEST;Dos;002;B;must-be-omitted;DEF;;\r\n'
               '45TEST;"Dos\nlíneas";003;C;must-be-omitted;GHI;;\r\n'
               '99TEST;Fuera;004;D;must-be-omitted;JKL;;\r\n')
        legacy = {'format': 'TELECONTROL_CT_V1', 'version': 1, 'fieldNames': [f'F{i}' for i in range(363)],
                  'records': {'42TEST': ['42TEST']+[None]*379}}
        sources = [('grid.csv', csv.encode(), True), ('esir.csv.gz', gzip.compress(csv.encode('cp1252')), True),
                   ('grid.xlsx', workbook(), True), ('catalog.json', json.dumps(legacy).encode(), True),
                   ('grid.csv', csv.encode(), False), ('grid.csv', (csv+'28TEST;X;;;;;;\n').encode(), True),
                   ('grid.csv', b'MATRICULA,NOMBRE\n99TEST,None', True), ('grid.xlsx', b'PKinvalid', True)]
        cases = [dict(name=n, bytes=base64.b64encode(data).decode(), mobile=mobile) for n,data,mobile in sources]
        result = subprocess.run(['node','-e',WORKER_DRIVER], cwd=ROOT/'app/src/main/assets',
                                input=json.dumps(cases), capture_output=True, text=True, check=True)
        answers = json.loads(result.stdout)
        for entry in answers[:3]:
            self.assertIn('data', entry, entry)
            self.assertEqual(set(entry['data']['records']), {'28TEST','82TEST','45TEST'})
            self.assertNotIn('must-be-omitted', str(entry))
            self.assertNotIn('PASSWORD', str(entry))
            self.assertNotIn('Contraseña', str(entry))
        self.assertEqual(answers[0], answers[1])
        row = answers[0]['data']['records']['28TEST']
        self.assertEqual(row[1:3], ['Muñoz; "norte"', '001'])
        self.assertEqual(row[15:17], [40.5, -3])
        self.assertIn('RTU1 MODELO', answers[0]['data']['fieldNames'])
        self.assertEqual(answers[2]['data']['records']['45TEST'][1], 'Prueba & muestra')
        self.assertEqual(answers[3]['data'], legacy)
        for invalid in answers[4:]:
            self.assertIn('error', invalid)
