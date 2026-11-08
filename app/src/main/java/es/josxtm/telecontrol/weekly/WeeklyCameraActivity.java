package es.josxtm.telecontrol.weekly;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Bundle;
import android.util.Size;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Continuous offline analysis, no ImageCapture, image file, gallery or network access. */
public final class WeeklyCameraActivity extends ComponentActivity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final LinkedHashMap<String,JSONObject> accepted=new LinkedHashMap<>();
 private final Map<String,String> previous=new HashMap<>();
 private PreviewView preview;private TextView status;private LocalOcr ocr;private ProcessCameraProvider provider;
 private volatile boolean closed,active,captureRequested;private long lastFrame;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_SECURE);
  FrameLayout layout=new FrameLayout(this);layout.setBackgroundColor(Color.rgb(25,29,32));layout.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  preview=new PreviewView(this);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);layout.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setBackgroundColor(0xe6191d20);status.setTextSize(16);status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setMaxLines(6);status.setText("OCR LOCAL · Muestra las cabeceras de la categoría elegida y bordes completos de Excel. Activa bordes de celda y fija la cabecera al desplazarte. No se guardan imágenes.");FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);layout.addView(status,top);
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setBackgroundColor(0xe6191d20);controls.setPadding(dp(8),dp(8),dp(8),dp(8));
  Button capture=new Button(this);capture.setText("CAPTURAR");capture.setMinHeight(dp(56));capture.setOnClickListener(v->{captureRequested=true;status.setText("Captura en memoria solicitada. Puedes capturar más vistas antes de terminar. Revisa los campos ilegibles.");});controls.addView(capture,new LinearLayout.LayoutParams(0,-2,1));
  Button done=new Button(this);done.setText("TERMINAR Y REVISAR");done.setMinHeight(dp(56));done.setOnClickListener(v->finishScan());controls.addView(done,new LinearLayout.LayoutParams(0,-2,1));
  Button cancel=new Button(this);cancel.setText("Cancelar");cancel.setMinHeight(dp(56));cancel.setMinWidth(dp(88));cancel.setOnClickListener(v->cancelScan());controls.addView(cancel,new LinearLayout.LayoutParams(-2,-2));layout.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(layout);layout.requestApplyInsets();
  getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){cancelScan();}});
  if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();else requestPermissions(new String[]{Manifest.permission.CAMERA},3201);
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==3201){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();else{status.setText("Sin permiso de cámara. Puedes cancelar y añadir trabajos manualmente. Los datos existentes se conservan.");}}}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private void startCamera(){
  com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
  future.addListener(()->{if(closed||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;try{provider=future.get();Preview view=new Preview.Builder().build();view.setSurfaceProvider(preview.getSurfaceProvider());ImageAnalysis analysis=new ImageAnalysis.Builder().setTargetResolution(new Size(1920,1080)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(worker,this::analyze);provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,view,analysis);}catch(Exception e){status.setText("Cámara no disponible. Cancela para introducir y revisar los trabajos manualmente.");}},ContextCompat.getMainExecutor(this));
 }
 private void analyze(ImageProxy image){
  Bitmap bitmap=null,rotated=null;
  try{
   long now=android.os.SystemClock.elapsedRealtime();if(closed||!active||(!captureRequested&&now-lastFrame<1600))return;boolean manual=captureRequested;captureRequested=false;lastFrame=now;
   if(ocr==null)ocr=new LocalOcr(this,"routers".equals(getIntent().getStringExtra("category")));bitmap=image.toBitmap();Matrix matrix=new Matrix();matrix.postRotate(image.getImageInfo().getRotationDegrees());rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
   JSONArray rows=ocr.recognize(rotated);Map<String,String> current=new HashMap<>();
   synchronized(accepted){for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String key=row.getString("act")+"|"+row.getString("matricula"),value=row.toString();current.put(key,value);if((manual||value.equals(previous.get(key)))&&!accepted.containsKey(key)&&accepted.size()<10000)accepted.put(key,row);}previous.clear();previous.putAll(current);}
   final int count=accepted.size(),visible=rows.length();runOnUiThread(()->{if(!closed)status.setText((count>=10000?"Límite de 10000 filas: termina y revisa antes de iniciar otra sesión.\n":"")+"OCR LOCAL · "+count+" filas estables en sesión · "+visible+" visibles\n"+(visible==0?"Acerca la cámara. Cabeceras, bordes rectos y filas completas; evita reflejos. No se adivinan columnas ni filas.":"Mantén la cámara quieta dos lecturas. Desplaza Excel para añadir filas; conserva la cabecera visible. Revisa todos los campos al terminar."));});
  }catch(Exception e){runOnUiThread(()->{if(!closed)status.setText("No se pudo reconocer esta vista. Ajusta luz, enfoque y bordes. Puedes cancelar sin perder los trabajos guardados.");});}
  finally{if(rotated!=null&&rotated!=bitmap)rotated.recycle();if(bitmap!=null)bitmap.recycle();image.close();}
 }
 private void finishScan(){JSONArray rows=new JSONArray();synchronized(accepted){for(JSONObject row:accepted.values())rows.put(row);}Intent result=new Intent();result.putExtra("weekly-scan-token",ScanResults.put(rows));setResult(RESULT_OK,result);finish();}
 private void cancelScan(){synchronized(accepted){if(accepted.isEmpty()){finish();return;}}new AlertDialog.Builder(this).setMessage("¿Descartar las filas reconocidas sin guardarlas?").setPositiveButton("Descartar",(d,w)->finish()).setNegativeButton("Continuar",null).show();}
 @Override protected void onResume(){super.onResume();active=true;}
 @Override protected void onPause(){active=false;super.onPause();}
 @Override protected void onDestroy(){closed=true;if(provider!=null)provider.unbindAll();worker.execute(()->{if(ocr!=null){ocr.close();ocr=null;}synchronized(accepted){accepted.clear();previous.clear();}});worker.shutdown();super.onDestroy();}
}
