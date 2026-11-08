package es.josxtm.telecontrol.weekly;
import android.content.Context;
import android.graphics.Bitmap;
import com.googlecode.tesseract.android.*;
import java.io.*;
import java.util.*;
import org.json.JSONArray;
/** All frame data stays in RAM. Only the public, pinned OCR language model is copied to disk. Single worker ownership. */
public final class LocalOcr implements AutoCloseable {
 private final TessBaseAPI tess=new TessBaseAPI();
 private final boolean routers;
 public LocalOcr(Context context)throws IOException{this(context,false);}
 public LocalOcr(Context context,boolean routers)throws IOException{this.routers=routers;
  File root=new File(context.getFilesDir(),"weekly-ocr"),dir=new File(root,"tessdata");
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("OCR model directory");
  // Re-copy the bundled verified model, never download anything on the device.
  for(String language:new String[]{"eng","spa"})try(InputStream in=context.getAssets().open("ocr/"+language+".traineddata");OutputStream out=new FileOutputStream(new File(dir,language+".traineddata"))){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  if(!tess.init(root.getAbsolutePath(),"eng+spa")){tess.recycle();throw new IOException("OCR model unavailable");}
  tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);tess.setVariable("debug_file","/dev/null");
 }
 public List<TableRecognizer.Word> words(Bitmap bitmap){return read(bitmap,TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);}
 private List<TableRecognizer.Word> read(Bitmap bitmap,int mode){
  tess.setPageSegMode(mode);tess.setImage(bitmap);tess.getUTF8Text();List<TableRecognizer.Word> words=new ArrayList<>();ResultIterator it=tess.getResultIterator();
  try{if(it!=null){it.begin();do{String text=it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD);int[] r=it.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD);if(text!=null&&!text.trim().isEmpty()&&r!=null&&r.length==4&&words.size()<5000)words.add(new TableRecognizer.Word(text.trim(),r[0],r[1],r[2],r[3],it.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)));}while(it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));}}finally{if(it!=null)it.delete();tess.clear();}
  return words;
 }
 private List<TableRecognizer.Word> cell(Bitmap image,int left,int top,int right,int bottom){
  List<TableRecognizer.Word> result=new ArrayList<>();int x=left+4,y=top+4,w=right-left-8,h=bottom-top-8;if(w<8||h<8)return result;
  Bitmap crop=Bitmap.createBitmap(image,x,y,w,h),scaled=crop;int scale=h<180&&w<1000?3:1;
  try{if(scale>1)scaled=Bitmap.createScaledBitmap(crop,w*scale,h*scale,true);for(TableRecognizer.Word word:read(scaled,TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK))result.add(new TableRecognizer.Word(word.text,x+word.left/scale,y+word.top/scale,x+word.right/scale,y+word.bottom/scale,word.confidence));}
  finally{if(scaled!=crop)scaled.recycle();crop.recycle();}return result;
 }
 public JSONArray recognize(Bitmap bitmap)throws Exception{
  List<Integer> xs=GridDetector.lines(bitmap,true),ys=GridDetector.lines(bitmap,false);if(xs.size()<(routers?7:8)||xs.size()>25||ys.size()<3||ys.size()>100)return new JSONArray();
  // A page-wide pass locates candidate headers; small isolated cells preserve short numeric weeks
  // which sparse page recognition can otherwise skip. Every cell keeps its original bounds.
  List<TableRecognizer.Word> global=words(bitmap);LinkedHashSet<Integer> candidates=new LinkedHashSet<>();
  for(int row=0;row+1<ys.size();row++){Set<String> names=new HashSet<>();for(TableRecognizer.Word word:global)if(word.y()>ys.get(row)&&word.y()<ys.get(row+1)&&TableRecognizer.header(word.text)!=null)names.add(TableRecognizer.header(word.text));if(names.size()>=4)candidates.add(row);}
  for(int row=0;row<Math.min(5,ys.size()-1);row++)candidates.add(row);
  for(int headerRow:candidates){
   List<TableRecognizer.Word> all=new ArrayList<>();Map<String,Integer> map=new HashMap<>();
   for(int col=0;col+1<xs.size();col++){
    if(!GridDetector.closedCell(bitmap,xs.get(col),ys.get(headerRow),xs.get(col+1),ys.get(headerRow+1)))continue;
    List<TableRecognizer.Word> header=cell(bitmap,xs.get(col),ys.get(headerRow),xs.get(col+1),ys.get(headerRow+1));all.addAll(header);if(!header.isEmpty()){StringBuilder joined=new StringBuilder();float confidence=100;for(TableRecognizer.Word word:header){joined.append(word.text).append(' ');confidence=Math.min(confidence,word.confidence);}String name=TableRecognizer.header(joined.toString());if(name!=null)header.add(new TableRecognizer.Word(joined.toString(),xs.get(col)+6,ys.get(headerRow)+6,xs.get(col+1)-6,ys.get(headerRow+1)-6,confidence));all.add(header.get(header.size()-1));}
    for(TableRecognizer.Word word:header){String name=TableRecognizer.header(word.text);if(name!=null&&word.confidence>=45)map.put(name,col);}
   }
   if(!TableRecognizer.completeHeaders(map,routers))continue;
   for(int row=headerRow+1;row+1<ys.size()&&row<=headerRow+12;row++){
    boolean closed=true;for(int col:map.values())if(!GridDetector.closedCell(bitmap,xs.get(col),ys.get(row),xs.get(col+1),ys.get(row+1)))closed=false;
    for(int col:map.values())for(TableRecognizer.Word word:global)if(TableRecognizer.crosses(word,xs.get(col),ys.get(row),xs.get(col+1),ys.get(row+1)))closed=false;
    if(closed)for(int col:map.values())all.addAll(cell(bitmap,xs.get(col),ys.get(row),xs.get(col+1),ys.get(row+1)));
   }
   return new TableRecognizer(routers).parse(all,xs,ys,bitmap.getWidth(),bitmap.getHeight(),bitmap);
  }
  return new JSONArray();
 }
 @Override public void close(){tess.recycle();}
}
