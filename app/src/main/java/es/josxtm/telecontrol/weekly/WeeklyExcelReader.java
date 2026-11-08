package es.josxtm.telecontrol.weekly;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded offline OOXML; no VBA/formula execution, external resources or data uploads. API 23. */
public final class WeeklyExcelReader {
 private WeeklyExcelReader(){}
 private static final int MAX_COMPRESSED=12*1024*1024,MAX_TOTAL=24*1024*1024,MAX_ENTRY=5*1024*1024;
 private static final class Limited extends FilterInputStream {
  private int count;Limited(InputStream in){super(in);}private void add(int n)throws IOException{if(n>0&&(count+=n)>MAX_COMPRESSED)throw new IOException("Excel comprimido demasiado grande (12 MB)");}
  @Override public int read()throws IOException{int n=in.read();if(n>=0)add(1);return n;}
  @Override public int read(byte[] b,int o,int n)throws IOException{int r=in.read(b,o,n);add(r);return r;}
 }
 private static Document xml(byte[] bytes)throws Exception{
  // Android's JAXP does not implement desktop Xerces security feature URIs consistently.
  // Validate tokens with Android's local pull parser BEFORE DOM; reject DTD/entities in any encoding.
  XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(new ByteArrayInputStream(bytes),null);
  int tokens=0;
  for(int t=parser.nextToken();t!=XmlPullParser.END_DOCUMENT;t=parser.nextToken()){
   if(++tokens>200000||parser.getDepth()>64)throw new IOException("XML Excel demasiado complejo");
   if(t==XmlPullParser.DOCDECL)throw new IOException("DTD no admitido en Excel");
   if(t==XmlPullParser.ENTITY_REF&&parser.getText()==null)throw new IOException("Entidad XML no admitida");
   if(t==XmlPullParser.START_TAG&&"http://www.w3.org/2001/XInclude".equals(parser.getNamespace()))throw new IOException("Inclusión externa no admitida");
  }
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
  javax.xml.parsers.DocumentBuilder builder=f.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("Recurso externo no admitido");});
  builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){@Override public void error(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw new org.xml.sax.SAXException("XML Excel no válido");}@Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw new org.xml.sax.SAXException("XML Excel no válido");}});
  return builder.parse(new ByteArrayInputStream(bytes));
 }
 private static List<Element> elements(Element e,String local){NodeList nodes=e.getElementsByTagNameNS("*",local);ArrayList<Element> out=new ArrayList<>();for(int i=0;i<nodes.getLength();i++)out.add((Element)nodes.item(i));return out;}
 private static String childText(Element e,String local){List<Element> nodes=elements(e,local);return nodes.isEmpty()?"":nodes.get(0).getTextContent();}
 private static String richText(Element e){StringBuilder out=new StringBuilder();for(Element t:elements(e,"t"))out.append(t.getTextContent());return out.toString();}
 private static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
 private static String field(String name){switch(normalize(name)){
  case "CODIGOBASECENTRO":case "CODBASECENTRO":return "baseCode";case "GDACODIGOSOLICITUDACT":case "GDACODIGOSOLICITUD":case "GDACODSOLICITUD":case "GDACODSOLICITUDACT":return "act";case "DESCRIPCIONDEACTUACION":case "DESCACTUACION":return "description";case "MATRICULACT":case "INSTSUPCTACT":case "INSTSUPCT":return "matricula";case "FECHAREALIZACION":return "date";case "COMENTARIOS":return "sourceComments";case "DOCUMENTACIONPRL":return "prl";case "CPARGOS":return "cpArgos";case "TERMINADAENSISTEMAS":return "systemsFinished";case "TIPOLOGIADETRABAJO":case "TIPOLOGIA":return "typology";case "EXPEDIENTEACT":case "EXPEDIENTE":case "ACT":case "ACTEXPEDIENTE":return "act";
  case "DESCRIPICONDELTRABAJO":case "DESCRIPICON":case "DESCRIPCIONDELTRABAJO":case "DESCRIPCION":return "description";case "CTMATRICULA":case "CT":case "MATRICULA":return "matricula";
  case "MUNICIPIO":return "municipality";case "SEMANA":case "SEMANAISO":return "week";
  case "ANO":case "ANOISO":case "ANODEPLANIFICACION":case "EJERCICIO":return "year";
  case "FECHA":case "FECHAEJECUCION":case "FECHADEEJECUCION":return "date";
  case "REALIZADO":return "realizado";case "TERED":case "CODIGOTERED":return "tered";default:return "";
 }}
 private static String column(String ref)throws IOException{
  if(!ref.matches("[A-Za-z]{1,3}[1-9][0-9]{0,6}"))throw new IOException("Referencia de celda inválida");
  String col=ref.replaceAll("[0-9]","").toUpperCase(Locale.ROOT);int n=0;for(int i=0;i<col.length();i++)n=n*26+col.charAt(i)-'A'+1;
  if(n>16384||Integer.parseInt(ref.substring(col.length()))>1048576)throw new IOException("Referencia fuera de Excel");return col;
 }
 private static String date(String value,boolean epoch1904){
  if(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}([T ].*)?"))return value.substring(0,10);
  if(value.matches("[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{4}")){String[] p=value.split("[/.-]");return String.format(Locale.ROOT,"%04d-%02d-%02d",Integer.parseInt(p[2]),Integer.parseInt(p[1]),Integer.parseInt(p[0]));}
  try{double serial=Double.parseDouble(value);if(Double.isNaN(serial)||Double.isInfinite(serial)||serial<0||serial>109000||!epoch1904&&Math.floor(serial)==60)return value;
   Calendar c=new GregorianCalendar(TimeZone.getTimeZone("UTC"),Locale.ROOT);c.clear();c.set(epoch1904?1904:1899,epoch1904?0:11,epoch1904?1:30);int days=(int)Math.floor(serial);if(!epoch1904&&days<60)days++;c.add(Calendar.DAY_OF_YEAR,days);return String.format(Locale.ROOT,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));
  }catch(NumberFormatException ignored){return value;}
 }
 private static List<String> styles(Map<String,byte[]> entries)throws Exception{
  ArrayList<String> result=new ArrayList<>();if(!entries.containsKey("xl/styles.xml"))return result;
  Document doc=xml(entries.get("xl/styles.xml"));Map<String,String> formats=new HashMap<>();
  for(Element f:elements(doc.getDocumentElement(),"numFmt"))formats.put(f.getAttribute("numFmtId"),f.getAttribute("formatCode"));
  for(Element xfs:elements(doc.getDocumentElement(),"cellXfs"))for(Element xf:elements(xfs,"xf")){String format=formats.get(xf.getAttribute("numFmtId"));result.add(format==null?"":format);}
  return result;
 }
 private static String displayCode(String raw,String format){
  // Preserve exact text; restore zeros only from an explicit integer display mask, never guess.
  if(!format.matches("(?:\"[^\";]{0,40}\")?0{1,80}"))return raw;
  try{String digits=new BigDecimal(raw).toBigIntegerExact().toString();if(digits.startsWith("-")||digits.length()>200)return raw;int start=format.lastIndexOf('"')+1;String prefix=start>0?format.substring(1,start-1):"";int width=format.length()-start;StringBuilder out=new StringBuilder(prefix);for(int i=digits.length();i<width;i++)out.append('0');return out.append(digits).toString();}catch(ArithmeticException|NumberFormatException ignored){return raw;}
 }
 public static JSONArray read(ContentResolver resolver,Uri uri)throws Exception{return read(resolver,uri,"weekly");}
 public static JSONArray read(ContentResolver resolver,Uri uri,String category)throws Exception{
  boolean routers="routers".equals(category);String sheetName=routers?"CAMPANAROUTERMADRID":"TRABAJOSSEMANALES";
  Map<String,byte[]> entries=new HashMap<>();Set<String> seen=new HashSet<>();int total=0,count=0;
  try(InputStream source=resolver.openInputStream(uri)){
   if(source==null)throw new IOException("No se pudo abrir el archivo");Limited in=new Limited(source);
   try(ZipInputStream zip=new ZipInputStream(in)){
    byte[] buffer=new byte[8192];ZipEntry entry;
    while((entry=zip.getNextEntry())!=null){if(++count>1500)throw new IOException("Demasiados elementos en Excel");String name=entry.getName();
     if(name.contains("..")||name.startsWith("/")||name.contains("\\")||!seen.add(name))throw new IOException("Ruta duplicada o no válida");
     boolean needed=name.equals("xl/workbook.xml")||name.equals("xl/_rels/workbook.xml.rels")||name.equals("xl/sharedStrings.xml")||name.equals("xl/styles.xml")||name.startsWith("xl/worksheets/")&&name.endsWith(".xml");
     ByteArrayOutputStream out=needed?new ByteArrayOutputStream():null;int n,size=0;
     while((n=zip.read(buffer))!=-1){size+=n;total+=n;if(total>MAX_TOTAL||size>MAX_ENTRY)throw new IOException("Excel expandido demasiado grande");if(needed)out.write(buffer,0,n);}
     if(needed)entries.put(name,out.toByteArray());
    }
    // Count trailing/central-directory bytes as well as compressed entries.
    while(in.read(buffer,0,buffer.length)!=-1){}
   }
  }
  byte[] workbook=entries.get("xl/workbook.xml"),rels=entries.get("xl/_rels/workbook.xml.rels");if(workbook==null||rels==null)throw new IOException("Archivo XLSX/XLSM no válido");Document book=xml(workbook);String relId=null;
  for(Element sheet:elements(book.getDocumentElement(),"sheet"))if(normalize(sheet.getAttribute("name")).equals(sheetName)){if(relId!=null)throw new IOException("Hoja TRABAJOS SEMANALES ambigua");relId=sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");}
  if(relId==null||relId.isEmpty())throw new IOException(routers?"No existe la hoja CAMPAÑA ROUTER MADRID":"No existe la hoja TRABAJOS SEMANALES");String target=null;
  for(Element rel:elements(xml(rels).getDocumentElement(),"Relationship"))if(relId.equals(rel.getAttribute("Id"))){if(target!=null||"External".equalsIgnoreCase(rel.getAttribute("TargetMode")))throw new IOException("Relación de hoja no admitida");target=rel.getAttribute("Target");}
  if(target==null||target.contains("..")||target.contains(":")||target.contains("\\"))throw new IOException("Hoja Excel no válida");String path=target.startsWith("/")?target.substring(1):"xl/"+target;
  if(!path.startsWith("xl/worksheets/")||!entries.containsKey(path))throw new IOException("Hoja Excel no encontrada");
  boolean epoch1904=false;for(Element props:elements(book.getDocumentElement(),"workbookPr"))epoch1904="1".equals(props.getAttribute("date1904"))||"true".equals(props.getAttribute("date1904"));
  ArrayList<String> shared=new ArrayList<>();if(entries.containsKey("xl/sharedStrings.xml"))for(Element si:elements(xml(entries.get("xl/sharedStrings.xml")).getDocumentElement(),"si")){shared.add(richText(si));if(shared.size()>100000)throw new IOException("Demasiadas cadenas");}
  List<String> formats=styles(entries);Map<String,String> headers=null;JSONArray output=new JSONArray();int rowCount=0,cells=0;
  for(Element row:elements(xml(entries.get(path)).getDocumentElement(),"row")){
   if(++rowCount>20000)throw new IOException("Demasiadas filas");String rowId=row.getAttribute("r");Map<String,String> values=new HashMap<>(),types=new HashMap<>(),masks=new HashMap<>();Set<String> formulaCols=new HashSet<>();
   for(Element cell:elements(row,"c")){if(++cells>200000)throw new IOException("Demasiadas celdas");String ref=cell.getAttribute("r"),col=column(ref);if(rowId.isEmpty())rowId=ref.substring(col.length());if(!ref.substring(col.length()).equals(rowId))throw new IOException("Celda asociada a otra fila");if(values.containsKey(col))throw new IOException("Celda duplicada");
    String type=cell.getAttribute("t"),value=childText(cell,"v");if(type.equals("inlineStr"))value=richText(cell);
    if(type.equals("s")){try{value=shared.get(Integer.parseInt(value));}catch(RuntimeException e){throw new IOException("Cadena Excel inválida");}}
    if(!cell.getAttribute("s").isEmpty()){try{masks.put(col,formats.get(Integer.parseInt(cell.getAttribute("s"))));}catch(RuntimeException e){throw new IOException("Estilo Excel inválido");}}
    if(!elements(cell,"f").isEmpty())formulaCols.add(col);values.put(col,value.trim());types.put(col,type);
   }
   if(headers==null){Map<String,String> candidate=new HashMap<>();for(Map.Entry<String,String> cell:values.entrySet()){String key=field(cell.getValue());if((!key.equals("cpArgos")||!routers)&&!key.isEmpty()&&candidate.put(key,cell.getKey())!=null)throw new IOException("Cabecera duplicada o ambigua");}
    if(candidate.keySet().containsAll(routers?Arrays.asList("baseCode","act","description","matricula","municipality"):Arrays.asList("typology","act","description","matricula","municipality","week")))headers=candidate;continue;
   }
   JSONObject item=new JSONObject();boolean any=false,formula=false;
   for(String key:Arrays.asList("typology","act","description","matricula","municipality","week","year","date","tered","realizado","baseCode","sourceComments","prl","cpArgos","systemsFinished")){String col=headers.get(key),value=col==null?null:values.get(col);if(value==null)value="";
    if(!value.isEmpty()&&(key.equals("act")||key.equals("matricula")||key.equals("tered")||key.equals("realizado")||key.equals("baseCode"))&&(types.get(col)==null||types.get(col).isEmpty()||"n".equals(types.get(col)))){String mask=masks.get(col);if(mask!=null)value=displayCode(value,mask);}
    if(key.equals("date")&&!value.isEmpty())value=date(value,epoch1904);if(col!=null&&formulaCols.contains(col))formula=true;if(!value.isEmpty())any=true;item.put(key,value);
   }
   if(item.getString("tered").isEmpty())item.put("tered",item.getString("realizado"));
   if(formula)item.put("importWarning","Contiene resultados guardados de fórmulas. No se ejecutan; verifica sus valores antes de guardar.");
   if(any){item.put("sourceRow",rowId);item.put("category",routers?"routers":"weekly");if(routers){item.remove("year");item.remove("week");item.remove("cpArgos");}output.put(item);if(output.length()>10000)throw new IOException("Máximo 10000 trabajos por importación; divide el archivo");}
  }
  if(headers==null)throw new IOException(routers?"No se reconocen las cabeceras de CAMPAÑA ROUTERS":"No se reconocen las cabeceras de trabajos semanales");if(output.length()==0)throw new IOException("La hoja no contiene trabajos");return output;
 }
}
