package es.josxtm.telecontrol.weekly;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.*;

/** Fail-closed geometric parser: cell boundaries, never flattened page text. */
public final class TableRecognizer {
 private final boolean routers;public TableRecognizer(){this(false);}public TableRecognizer(boolean routers){this.routers=routers;}
 public static final class Word {
  public final String text;public final int left,top,right,bottom;public final float confidence;
  public Word(String t,int l,int y,int r,int b,float c){text=t;left=l;top=y;right=r;bottom=b;confidence=c;}
  int x(){return (left+right)/2;}int y(){return (top+bottom)/2;}
 }
 public static String norm(String v){return Normalizer.normalize(v,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
 public static String header(String text){switch(norm(text)){case "CODIGOBASECENTRO":case "CODIGOBASE":return "baseCode";case "GDACODIGOSOLICITUDACT":case "SOLICITUD":return "act";case "DESCRIPCIONDEACTUACION":return "description";case "MATRICULACT":return "matricula";case "FECHAREALIZACION":return "date";case "DOCUMENTACIONPRL":return "prl";case "CPARGOS":return "cpArgos";case "TERMINADAENSISTEMAS":return "systemsFinished";case "COMENTARIOS":return "sourceComments";case "TIPOLOGIADETRABAJO":case "TIPOLOGIA":return "typology";case "EXPEDIENTEACT":case "EXPEDIENTE":return "act";case "DESCRIPICONDELTRABAJO":case "DESCRIPICON":case "DESCRIPCIONDELTRABAJO":case "DESCRIPCION":return "description";case "CTMATRICULA":case "CT":case "MATRICULA":return "matricula";case "MUNICIPIO":return "municipality";case "SEMANA":return "week";case "FECHAEJECUCION":case "FECHA":return "date";case "TERED":return "tered";case "REALIZADO":return "realizado";default:return null;}}
 public static boolean completeHeaders(Map<String,Integer> map){return completeHeaders(map,false);}
 public static boolean completeHeaders(Map<String,Integer> map,boolean routers){return map.keySet().containsAll(routers?Arrays.asList("baseCode","act","description","matricula","municipality","date"):Arrays.asList("typology","act","description","matricula","municipality","week","date"))&&new HashSet<>(map.values()).size()==map.size();}
 public static boolean crosses(Word w,int left,int top,int right,int bottom){return w.right>left+2&&w.left<right-2&&w.bottom>top+2&&w.top<bottom-2&&(w.left<left-2||w.right>right+2||w.top<top-2||w.bottom>bottom+2);}
 private static int interval(int p,List<Integer> lines){for(int i=0;i+1<lines.size();i++)if(p>lines.get(i)&&p<lines.get(i+1))return i;return -1;}
 public JSONArray parse(List<Word> words,List<Integer> xs,List<Integer> ys,int width,int height)throws Exception{
  return parse(words,xs,ys,width,height,null);
 }
 public JSONArray parse(List<Word> words,List<Integer> xs,List<Integer> ys,int width,int height,android.graphics.Bitmap image)throws Exception{
  JSONArray result=new JSONArray();if(words.size()>5000||xs.size()<(routers?7:8)||ys.size()<3)return result;
  // Require all headers in one bounded row. Municipality is located ONLY by its header.
  Map<String,Integer> cols=null;int headerRow=-1;
  for(int row=0;row+1<ys.size();row++){
   Map<String,Integer> map=new HashMap<>();Set<String> ambiguous=new HashSet<>();
   for(Word w:words){if(w.confidence<45||interval(w.y(),ys)!=row)continue;String name=header(w.text);int col=interval(w.x(),xs);if(name!=null&&(!routers||!name.equals("cpArgos"))&&col>=0){if(map.containsKey(name)&&map.get(name)!=col)ambiguous.add(name);map.put(name,col);}}
   boolean closed=true;if(image!=null)for(int col:map.values())if(!GridDetector.closedCell(image,xs.get(col),ys.get(row),xs.get(col+1),ys.get(row+1)))closed=false;
   if(closed&&completeHeaders(map,routers)&&ambiguous.isEmpty()){cols=map;headerRow=row;break;}
  }
  if(cols==null)return result;
  for(int row=headerRow+1;row+1<ys.size()&&result.length()<100;row++){
   int top=ys.get(row),bottom=ys.get(row+1);if(top<2||bottom>height-3||bottom-top<18)continue;
   JSONObject job=new JSONObject();boolean uncertain=false;
   for(Map.Entry<String,Integer> entry:cols.entrySet()){
    int col=entry.getValue(),left=xs.get(col),right=xs.get(col+1);if(image!=null&&!GridDetector.closedCell(image,left,top,right,bottom))uncertain=true;List<Word> cell=new ArrayList<>();
    for(Word w:words){if(crosses(w,left,top,right,bottom))uncertain=true;}
    for(Word w:words)if(interval(w.x(),xs)==col&&interval(w.y(),ys)==row){
     // A word crossing a separator signals a partial/tilted/merged cell: do not mix rows.
     if(w.left<left-2||w.right>right+2||w.top<top-2||w.bottom>bottom+2||w.confidence<35)uncertain=true;
     cell.add(w);
    }
    Collections.sort(cell,(a,b)->Integer.compare(a.top,b.top));List<List<Word>> lines=new ArrayList<>();
    for(Word w:cell){if(lines.isEmpty()||w.top-lines.get(lines.size()-1).get(0).top>Math.max(6,(w.bottom-w.top)/2))lines.add(new ArrayList<>());lines.get(lines.size()-1).add(w);}
    StringBuilder out=new StringBuilder();for(List<Word> line:lines){Collections.sort(line,(a,b)->Integer.compare(a.left,b.left));if(out.length()>0)out.append('\n');for(Word w:line){if(out.length()>0&&out.charAt(out.length()-1)!='\n')out.append(' ');out.append(w.text);}}
    String value=out.toString().trim();if(value.length()>((entry.getKey().equals("description")||entry.getKey().equals("sourceComments"))?4000:200))uncertain=true;job.put(entry.getKey(),value);
   }
   String act=job.getString("act").replaceAll("\\s","").toUpperCase(Locale.ROOT),ct=job.getString("matricula").replaceAll("\\s","").toUpperCase(Locale.ROOT),week=job.optString("week").replaceAll("(?i)SEMANA|\\s","");
   if(uncertain||!act.matches(routers?"[A-Z0-9][A-Z0-9/-]{2,59}":"ACT[0-9]{4,30}")||!ct.matches("[A-Z0-9][A-Z0-9-]{2,39}")||(!routers&&!week.matches("[0-9]{1,2}")))continue;
   int n=routers?0:Integer.parseInt(week);if(!routers&&(n<1||n>53||job.optString("typology").isEmpty()||job.optString("municipality").isEmpty()))continue;
   if(!job.has("realizado"))job.put("realizado","");if(!job.has("tered")||job.getString("tered").isEmpty())job.put("tered",job.getString("realizado"));
   String type=norm(job.optString("typology"));if(!type.equals("PESTC")&&!type.equals("INCIDENCIASTC"))job.put("tered","");
   String date=job.getString("date").replaceAll("\\s","");if(date.matches("[0-9]{2}[/.-][0-9]{2}[/.-][0-9]{4}"))date=date.substring(6)+"-"+date.substring(3,5)+"-"+date.substring(0,2);
   // Even when a date is read, require the operator to verify the planning's ISO year.
   job.put("act",act);job.put("matricula",ct);job.put("category",routers?"routers":"weekly");if(!routers){job.put("week",n);job.put("year","");}job.put("date",date);result.put(job);
  }
  return result;
 }
}
