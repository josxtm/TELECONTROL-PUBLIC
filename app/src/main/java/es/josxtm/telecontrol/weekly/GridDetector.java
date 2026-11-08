package es.josxtm.telecontrol.weekly;
import android.graphics.Bitmap;
import java.util.*;
/** Conservative straight-grid detector. Ambiguous optics fail closed instead of guessing cells. */
public final class GridDetector {
 public static List<Integer> lines(Bitmap image,boolean vertical){
  int width=image.getWidth(),height=image.getHeight(),outer=vertical?width:height,inner=vertical?height:width;
  List<Integer> result=new ArrayList<>();int start=-1;
  for(int p=0;p<outer;p++){
   int run=0,longest=0;for(int q=2;q<inner-2;q++){if(dark(image.getPixel(vertical?p:q,vertical?q:p))){run++;longest=Math.max(longest,run);}else run=0;}
   // Vertical grid need not span camera chrome/ribbon; cell closure is checked separately.
   boolean line=longest>Math.max(vertical?80:160,inner*(vertical?0.18:0.60));
   if(line&&start<0)start=p;
   if(start>=0&&(!line||p==outer-1)){int end=line?p+1:p;if(end-start<=Math.max(10,outer/150))result.add((start+end-1)/2);start=-1;}
  }
  return result;
 }
 private static boolean dark(int c){return (((c>>16)&255)*299+((c>>8)&255)*587+(c&255)*114)/1000<224;}
 private static boolean segment(Bitmap b,int x1,int y1,int x2,int y2){
  boolean vertical=x1==x2;int from=vertical?y1:x1,to=vertical?y2:x2,good=0,total=0;
  for(int p=from+2;p<to-2;p+=2){boolean hit=false;for(int offset=-2;offset<=2;offset++){int x=vertical?x1+offset:p,y=vertical?p:y1+offset;if(x>=0&&y>=0&&x<b.getWidth()&&y<b.getHeight()&&dark(b.getPixel(x,y)))hit=true;}if(hit)good++;total++;}
  return total>0&&good>=total*0.85;
 }
 public static boolean closedCell(Bitmap b,int left,int top,int right,int bottom){return segment(b,left,top,right,top)&&segment(b,left,bottom,right,bottom)&&segment(b,left,top,left,bottom)&&segment(b,right,top,right,bottom);}
}
