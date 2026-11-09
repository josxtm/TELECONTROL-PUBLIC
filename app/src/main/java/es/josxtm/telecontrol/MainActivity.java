package es.josxtm.telecontrol;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.speech.RecognizerIntent;
import org.json.JSONObject;
import java.util.ArrayList;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;

/** Offline catalogue; optional work synchronization is isolated in the native folder bridge. */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "appassets.androidplatform.net";
    private WebView webView;
    private volatile boolean webViewClosed;
    private FrameLayout rootLayout;
    private int currentThemeColor = Color.rgb(140, 52, 61);
    private ValueCallback<Uri[]> pendingFileSelection;
    private String pendingExport;
    private String pendingWeeklyText;
    private volatile String pendingWeeklyResult;
    private boolean weeklyScanning,weeklySelecting;
    private volatile boolean weeklyImporting;
    private static final int WEEKLY_SCAN_REQUEST=1081, WEEKLY_EXPORT_REQUEST=1082, WEEKLY_EXCEL_REQUEST=1083;
    private static final int EXPORT_REQUEST = 1053;
    static final int FOLDER_REQUEST = 1070;
    private String speechTarget;
    private static final int SPEECH_REQUEST = 1052;
    private static final int PICK_JSON_REQUEST = 1051;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        rootLayout = root;
        root.setBackgroundColor(currentThemeColor);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        webView = new WebView(this);
        webView.setBackgroundColor(currentThemeColor);
        if(android.os.Build.VERSION.SDK_INT>=26)webView.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.requestApplyInsets();
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        WebView.setWebContentsDebuggingEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        es.josxtm.telecontrol.sync.SyncCoordinator.initialize(this);
        webView.addJavascriptInterface(new MangoBridge(), "CTMangoNative");
        webView.addJavascriptInterface(new WeeklyBridge(), "CTWeeklyNative");
        webView.addJavascriptInterface(new FolderBridge(), "CTFolderSync");
        webView.addJavascriptInterface(new ReminderBridge(), "CTNativeReminders");
        webView.addJavascriptInterface(new MapsBridge(), "AppInventor");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileSelection != null) pendingFileSelection.onReceiveValue(null);
                pendingFileSelection = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                    startActivityForResult(intent, PICK_JSON_REQUEST);
                } catch (ActivityNotFoundException e) {
                    pendingFileSelection = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "No hay selector de archivos disponible.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !ORIGIN.equals(uri.getHost())) {
                    return emptyResponse();
                }
                WebResourceResponse response = loader.shouldInterceptRequest(uri);
                return response == null ? emptyResponse() : response;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && ORIGIN.equals(uri.getHost())) return false;
                openMaps(uri.toString());
                return true;
            }
        });
        webView.loadUrl("https://" + ORIGIN + "/assets/index.html");
    }

    /** Confirm a document only after write AND close succeeded; local data is never removed. */
    static boolean writeExport(java.io.OutputStream target, String payload) {
        if (target == null) return false;
        try (java.io.OutputStream out = target) {
            if (payload == null) return false;
            out.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException writeFailure) {
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==FOLDER_REQUEST&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            final Uri selected=data.getData();final int flags=data.getFlags();
            es.josxtm.telecontrol.sync.SyncCoordinator.IO.execute(()->{try{es.josxtm.telecontrol.sync.SyncCoordinator.selectTree(MainActivity.this,selected,flags);}catch(Exception e){es.josxtm.telecontrol.sync.SyncCoordinator.state(MainActivity.this,"PERMISSION_REQUIRED");}runOnUiThread(()->{if(!webViewClosed&&!isDestroyed())webView.evaluateJavascript("window.CTSyncUI && window.CTSyncUI.folderResult()",null);});});
        }
        if(requestCode==WEEKLY_EXCEL_REQUEST){
            weeklySelecting=false;
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
                final Uri selected=data.getData();
                weeklyImporting=true;
                new Thread(()->{
                    JSONObject result=new JSONObject();
                    try{result.put("category",weeklyCategory);result.put("origin","excel");}catch(org.json.JSONException ignored){}
                    try{result.put("rows",es.josxtm.telecontrol.weekly.WeeklyExcelReader.read(getContentResolver(),selected,weeklyCategory));}
                    catch(Exception e){try{result.put("error","No se pudo importar el Excel: "+e.getMessage());}catch(org.json.JSONException ignored){}}
                    weeklyImporting=false;
                    pendingWeeklyResult=result.toString();
                    runOnUiThread(()->{if(!webViewClosed&&!isDestroyed())webView.evaluateJavascript("window.CTWeeklyUI && window.CTWeeklyUI.receiveNative()",null);});
                },"weekly-excel-import").start();
            }
        }
        if(requestCode==WEEKLY_SCAN_REQUEST){
            weeklyScanning=false;
            org.json.JSONArray rows=resultCode==RESULT_OK&&data!=null?es.josxtm.telecontrol.weekly.ScanResults.take(data.getStringExtra("weekly-scan-token")):null;
            try{JSONObject result=new JSONObject();if(rows==null)result.put("error","Escaneo cancelado o sesión interrumpida. Los trabajos guardados se conservan.");else{result.put("rows",rows);result.put("origin","camera");result.put("category",weeklyCategory);}pendingWeeklyResult=result.toString();
                if(!webViewClosed&&webView!=null)webView.evaluateJavascript("window.CTWeeklyUI && window.CTWeeklyUI.receiveNative()",null);
            }catch(org.json.JSONException ignored){}
        }
        if(requestCode==WEEKLY_EXPORT_REQUEST&&pendingWeeklyText!=null){
            String payload=pendingWeeklyText;pendingWeeklyText=null;
            if(resultCode!=RESULT_OK||data==null||data.getData()==null){weeklyExportResult(false);}else{
                Uri target=data.getData();new Thread(()->{boolean ok=false;try{ok=writeExport(getContentResolver().openOutputStream(target,"wt"),payload);}catch(java.io.IOException|SecurityException ignored){}final boolean success=ok;runOnUiThread(()->weeklyExportResult(success));},"weekly-txt-export").start();
            }
        }
        if (requestCode == EXPORT_REQUEST && pendingExport != null) {
            String payload = pendingExport;
            pendingExport = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                webView.evaluateJavascript("window.CTBackup && window.CTBackup.exportResult(false)", null);
            } else {
                Uri uri = data.getData();
                new Thread(() -> {
                    boolean success = false;
                    try {
                        success = writeExport(getContentResolver().openOutputStream(uri, "wt"), payload);
                    } catch (java.io.IOException | SecurityException ignored) { }
                    final boolean result = success;
                    runOnUiThread(() -> { if (!isDestroyed()) webView.evaluateJavascript("window.CTBackup && window.CTBackup.exportResult(" + result + ")", null); });
                }).start();
            }
        }
        if (requestCode == SPEECH_REQUEST && speechTarget != null) {
            String target = speechTarget;
            speechTarget = null;
            ArrayList<String> results = resultCode == RESULT_OK && data != null
                ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
            deliverSpeech(target, results != null && !results.isEmpty() ? results.get(0) : "",
                results != null && !results.isEmpty() ? "" : "Dictado cancelado o sin resultado. Puedes escribir manualmente.");
        }
        if (requestCode == PICK_JSON_REQUEST && pendingFileSelection != null) {
            Uri selected = resultCode == RESULT_OK && data != null ? data.getData() : null;
            pendingFileSelection.onReceiveValue(selected == null ? null : new Uri[]{selected});
            pendingFileSelection = null;
        }
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
            java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void deliverSpeech(String target, String text, String error) {
        if (webView == null || isFinishing()) return;
        webView.evaluateJavascript("window.CTSpeech && window.CTSpeech.receive("
            + JSONObject.quote(target) + "," + JSONObject.quote(text) + "," + JSONObject.quote(error) + ")", null);
    }

    private void selectSyncFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if(android.os.Build.VERSION.SDK_INT>=26&&es.josxtm.telecontrol.sync.SyncCoordinator.tree(this)!=null)intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,es.josxtm.telecontrol.sync.SyncCoordinator.tree(this));
        try{startActivityForResult(intent,FOLDER_REQUEST);}catch(ActivityNotFoundException e){es.josxtm.telecontrol.sync.SyncCoordinator.state(this,"FOLDER_UNAVAILABLE");}
    }
    private void weeklyExportResult(boolean ok){if(!webViewClosed&&webView!=null&&!isDestroyed())webView.evaluateJavascript("window.CTWeeklyUI && window.CTWeeklyUI.exportResult("+ok+")",null);}
    static Intent weeklyExcelPickerIntent(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
        // A common */* base is required for providers to show BOTH XLSX and XLSM MIME types.
        intent.setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel.sheet.macroEnabled.12","application/vnd.ms-excel.sheet.macroenabled.12","application/octet-stream"});return intent;
    }
    private String weeklyCategory="weekly";
    /** Only accepts bounded CT metadata from the trusted local WebView; no command or credential transport. */
    private final class MangoBridge {
        @JavascriptInterface public void open(String matricula, String name, String ipLan) {
            runOnUiThread(() -> {
                if (webViewClosed || isFinishing() || isDestroyed()) return;
                if (android.os.Build.VERSION.SDK_INT < 26) {
                    Toast.makeText(MainActivity.this, "MangoSSH requiere Android 8 o posterior", Toast.LENGTH_LONG).show();
                    return;
                }
                String code = matricula == null ? "" : matricula.toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
                if (!code.isEmpty() && !code.matches("(28|42)[A-Z0-9]{1,38}")) {
                    Toast.makeText(MainActivity.this, "Matrícula CT inválida", Toast.LENGTH_LONG).show();
                    return;
                }
                String lan = ipLan == null ? "" : ipLan.trim();
                if (!lan.isEmpty()) {
                    String[] octets = lan.split("\\.", -1);
                    if (octets.length != 4) lan = "";
                    else for (String part : octets) {
                        if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) {
                            lan = ""; break;
                        }
                    }
                }
                String title = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
                if (title.length() > 120) title = title.substring(0, 120);
                Intent intent = new Intent(MainActivity.this, io.github.josxtm.mangossh.MainActivity.class);
                intent.putExtra("telecontrol.ct.matricula", code);
                intent.putExtra("telecontrol.ct.name", title);
                intent.putExtra("telecontrol.ct.lan", lan);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); }
                catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(MainActivity.this, "No se pudo abrir MangoSSH", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private final class WeeklyBridge {
        @JavascriptInterface public void importExcelFor(String category){if(!"weekly".equals(category)&&!"routers".equals(category))return;runOnUiThread(()->{if(weeklySelecting||weeklyScanning||weeklyImporting)return;weeklyCategory=category;importExcel();});}
        @JavascriptInterface public void scanFor(String category){if(!"weekly".equals(category)&&!"routers".equals(category))return;runOnUiThread(()->{if(weeklySelecting||weeklyScanning||weeklyImporting)return;weeklyCategory=category;scan();});}
        @JavascriptInterface public synchronized String takeScanResult(){String result=pendingWeeklyResult;pendingWeeklyResult=null;return result==null?"null":result;}
        @JavascriptInterface public void importExcel(){runOnUiThread(()->{
            if(webViewClosed||isDestroyed()||weeklySelecting||weeklyImporting||weeklyScanning)return;
            weeklySelecting=true;
            try{startActivityForResult(weeklyExcelPickerIntent(),WEEKLY_EXCEL_REQUEST);}
            catch(ActivityNotFoundException|SecurityException e){weeklySelecting=false;Toast.makeText(MainActivity.this,"Selector de archivos no disponible",Toast.LENGTH_LONG).show();}
        });}
        @JavascriptInterface public void scan(){runOnUiThread(()->{if(weeklyScanning||weeklySelecting||weeklyImporting||webViewClosed)return;weeklyScanning=true;try{startActivityForResult(new Intent(MainActivity.this,es.josxtm.telecontrol.weekly.WeeklyCameraActivity.class).putExtra("category",weeklyCategory),WEEKLY_SCAN_REQUEST);}catch(ActivityNotFoundException|SecurityException e){weeklyScanning=false;Toast.makeText(MainActivity.this,"Cámara no disponible",Toast.LENGTH_LONG).show();}});}
        @JavascriptInterface public void exportText(String text){if(text==null||text.length()>8000000)return;runOnUiThread(()->{if(webViewClosed||isDestroyed()||pendingWeeklyText!=null)return;pendingWeeklyText=text;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TITLE,"TELECONTROL-trabajos-semanales.txt");try{startActivityForResult(intent,WEEKLY_EXPORT_REQUEST);}catch(ActivityNotFoundException|SecurityException e){pendingWeeklyText=null;weeklyExportResult(false);}});}
        @JavascriptInterface public void shareText(String text){if(text==null||text.length()>8000000)return;new Thread(()->{try{java.io.File folder=new java.io.File(getCacheDir(),"weekly-export");if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Export unavailable");java.io.File file=new java.io.File(folder,"TELECONTROL-trabajos-semanales-"+java.util.UUID.randomUUID()+".txt");try(java.io.OutputStream out=new java.io.FileOutputStream(file)){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}Uri uri=androidx.core.content.FileProvider.getUriForFile(MainActivity.this,getPackageName()+".weekly-files",file);runOnUiThread(()->{if(isDestroyed())return;Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(android.content.ClipData.newRawUri("Trabajos semanales",uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(send,"Compartir TXT confidencial por medios autorizados"));}catch(ActivityNotFoundException e){weeklyExportResult(false);}});}catch(java.io.IOException|SecurityException|IllegalArgumentException e){runOnUiThread(()->weeklyExportResult(false));}},"weekly-txt-share").start();}
    }
    private final class FolderBridge {
        @JavascriptInterface public void chooseFolder(){runOnUiThread(MainActivity.this::selectSyncFolder);}
        @JavascriptInterface public String status(){try{return es.josxtm.telecontrol.sync.SyncCoordinator.status(MainActivity.this).toString();}catch(Exception e){return "{\"error\":\"STORAGE_ERROR\"}";}}
        @JavascriptInterface public String setPhrase(String phrase){try{es.josxtm.telecontrol.sync.SyncCoordinator.phrase(MainActivity.this,phrase);return "{\"ok\":true}";}catch(Exception e){return "{\"ok\":false,\"error\":\"KEY_OR_ACCOUNT_REQUIRED\"}";}}
        @JavascriptInterface public String missing(String json){try(es.josxtm.telecontrol.sync.SyncDatabase db=new es.josxtm.telecontrol.sync.SyncDatabase(MainActivity.this)){return db.missing(new org.json.JSONArray(json)).toString();}catch(Exception e){return "{\"error\":\"STORAGE_ERROR\"}";}}
        @JavascriptInterface public String enqueue(String json){try{try(es.josxtm.telecontrol.sync.SyncDatabase db=new es.josxtm.telecontrol.sync.SyncDatabase(MainActivity.this)){db.enqueue(json);}return "{\"ok\":true}";}catch(Exception e){return "{\"ok\":false,\"error\":\"QUEUE_ERROR\"}";}}
        @JavascriptInterface public void inboxAsync(String request){
            if(request==null||!request.matches("[a-f0-9]{32}"))return;
            es.josxtm.telecontrol.sync.SyncCoordinator.IO.execute(()->{String payload;
                try{try(es.josxtm.telecontrol.sync.SyncDatabase db=new es.josxtm.telecontrol.sync.SyncDatabase(MainActivity.this)){payload=db.inbox().toString();}
                }catch(Exception e){payload="{\"error\":\"KEY_OR_STORAGE_ERROR\"}";}
                final String response=payload;runOnUiThread(()->{if(!webViewClosed&&!isDestroyed())webView.evaluateJavascript("window.CTSyncUI && window.CTSyncUI.inboxResult("+JSONObject.quote(request)+","+JSONObject.quote(response)+")",null);});
            });
        }
        @JavascriptInterface public String acknowledge(String json){try{org.json.JSONObject request=new org.json.JSONObject(json);if(!es.josxtm.telecontrol.sync.SyncCoordinator.prefs(MainActivity.this).edit().putBoolean("consistent",request.getBoolean("consistent")).commit())return "{\"ok\":false}";es.josxtm.telecontrol.sync.SyncCoordinator.acknowledge(MainActivity.this,request.getJSONArray("ids"));return "{\"ok\":true}";}catch(Exception e){return "{\"ok\":false}";}}
        @JavascriptInterface public void automatic(boolean enabled){es.josxtm.telecontrol.sync.SyncCoordinator.automatic(MainActivity.this,enabled);}
        @JavascriptInterface public void syncNow(){es.josxtm.telecontrol.sync.SyncCoordinator.trigger(MainActivity.this,true);}
        @JavascriptInterface public void trigger(){es.josxtm.telecontrol.sync.SyncCoordinator.trigger(MainActivity.this,false);}
    }

    private final class ReminderBridge {
        @JavascriptInterface public String list(){try{return ReminderScheduler.records(MainActivity.this).toString();}catch(RuntimeException e){return "{\"error\":true}";}}
        @JavascriptInterface public String save(String json){return ReminderScheduler.save(MainActivity.this,json);}
        @JavascriptInterface public String remove(String id){return ReminderScheduler.remove(MainActivity.this,id);}
        @JavascriptInterface public String status(){return "{\"exact\":"+ReminderScheduler.exact(MainActivity.this)+",\"notifications\":"+ReminderScheduler.notifications(MainActivity.this)+"}";}
        @JavascriptInterface public void permissions(){runOnUiThread(()->{
            if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1054);
            else if(!ReminderScheduler.notifications(MainActivity.this)){
                Intent settings=android.os.Build.VERSION.SDK_INT>=26?new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()):new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
                try{startActivity(settings);}catch(ActivityNotFoundException ignored){}
            }
            else if(android.os.Build.VERSION.SDK_INT>=31 && !ReminderScheduler.exact(MainActivity.this)){
                try{startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException ignored){}
            } else startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        });}
    }

    private final class MapsBridge {
        @JavascriptInterface public String packageVersion(){try{android.content.pm.PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);return new JSONObject().put("packageName",p.packageName).put("versionName",p.versionName).put("versionCode",android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode).toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public String loadAppearance(){return getSharedPreferences("telecontrol-appearance-v1",MODE_PRIVATE).getString("appearance",null);}
        @JavascriptInterface public boolean saveAppearance(String value){
            if(value==null||value.length()>2000)return false;
            try{JSONObject input=new JSONObject(value);String mode=input.getString("mode");if(!java.util.Arrays.asList("red","light","dark","olive","blue","custom").contains(mode))return false;org.json.JSONArray colors=input.getJSONArray("custom");if(colors.length()!=4)return false;org.json.JSONArray validated=new org.json.JSONArray();for(int i=0;i<4;i++){String color=colors.getString(i);if(!color.matches("#[0-9a-fA-F]{6}"))return false;validated.put(color);}String safe=new JSONObject().put("mode",mode).put("custom",validated).toString();return getSharedPreferences("telecontrol-appearance-v1",MODE_PRIVATE).edit().putString("appearance",safe).commit();}catch(org.json.JSONException e){return false;}
        }

        @JavascriptInterface
        public void setThemeColor(String hex) {
            if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return;
            runOnUiThread(() -> {
                int color = Color.parseColor(hex);
                currentThemeColor = color;
                if (rootLayout != null) rootLayout.setBackgroundColor(color);
                if (webView != null) webView.setBackgroundColor(color);
                getWindow().setStatusBarColor(color);
                getWindow().setNavigationBarColor(color);
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    getWindow().setStatusBarContrastEnforced(false);
                    getWindow().setNavigationBarContrastEnforced(false);
                }
                getWindow().getDecorView().setSystemUiVisibility(
                    ((Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 > 170)
                        ? android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        : 0);
            });
        }

        @JavascriptInterface
        public void sendFeedback(String subject, String body) {
            if (subject == null || body == null || subject.length() > 100 || body.length() > 6000
                || !subject.matches("\\[APP TELECONTROL - (ERROR|SUGERENCIA|COMENTARIO)\\]")) return;
            runOnUiThread(() -> {
                Intent email = new Intent(Intent.ACTION_SENDTO);
                email.setData(Uri.parse("mailto:joseluis.mansoloredo@eiffage.com"));
                email.putExtra(Intent.EXTRA_EMAIL, new String[]{"joseluis.mansoloredo@eiffage.com"});
                email.putExtra(Intent.EXTRA_SUBJECT, subject);
                email.putExtra(Intent.EXTRA_TEXT, body);
                try { startActivity(email); }
                catch (ActivityNotFoundException | SecurityException error) {
                    Toast.makeText(MainActivity.this, "No hay aplicación de correo disponible.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void exportWork(String json) {
            if (json == null || json.length() > 20000000) return;
            runOnUiThread(() -> {
                if (pendingExport != null) return;
                pendingExport = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "TELECONTROL-trabajo.json");
                try { startActivityForResult(intent, EXPORT_REQUEST); }
                catch (ActivityNotFoundException | SecurityException e) {
                    pendingExport = null;
                    webView.evaluateJavascript("window.CTBackup && window.CTBackup.exportResult(false)", null);
                }
            });
        }

        @JavascriptInterface
        public void dictate(String target) {
            if (!"query".equals(target) && !"ct-notes".equals(target) && !"entry-text".equals(target)) return;
            runOnUiThread(() -> {
                if (speechTarget != null) { deliverSpeech(target, "", "Ya hay un dictado activo."); return; }
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Dictado en español")
                    .setMessage("Se solicita reconocimiento sin conexión, pero el servicio de Android puede necesitar Internet y transmitir audio a su proveedor. TELECONTROL no guarda audio. Puedes cancelar y escribir manualmente.")
                    .setNegativeButton("Cancelar", (dialog, which) -> deliverSpeech(target, "", "Dictado cancelado"))
                    .setPositiveButton("Continuar", (dialog, which) -> {
                        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
                        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                        speechTarget = target;
                        try { startActivityForResult(intent, SPEECH_REQUEST); }
                        catch (ActivityNotFoundException | SecurityException e) {
                            speechTarget = null;
                            deliverSpeech(target, "", "Reconocimiento no disponible o permiso denegado. Puedes escribir manualmente.");
                        }
                    }).setOnCancelListener(dialog -> deliverSpeech(target, "", "Dictado cancelado")).show();
            });
        }

        @JavascriptInterface
        public void setWebViewString(String url) {
            runOnUiThread(() -> openMaps(url));
        }
    }

    private void openMaps(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) || !"www.google.com".equals(uri.getHost())
                || !"/maps/search/".equals(uri.getPath()) || !"1".equals(uri.getQueryParameter("api"))) return;
        String query = uri.getQueryParameter("query");
        if (query == null || !query.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) return;
        String[] parts = query.split(",");
        try {
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            if (Double.isNaN(lat) || Double.isInfinite(lat) || Double.isNaN(lon) || Double.isInfinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return;
            Uri safeUri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
                .appendQueryParameter("api", "1").appendQueryParameter("query", query).build();
            startActivity(new Intent(Intent.ACTION_VIEW, safeUri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Instala Google Maps o un navegador para abrir la ubicación.", Toast.LENGTH_LONG).show();
        } catch (NumberFormatException ignored) { }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        webView.evaluateJavascript("window.CTApp ? window.CTApp.handleBack() : false", handled -> {
            if (!"true".equals(handled)) finish();
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==1054)ReminderScheduler.restore(this);
    }
    @Override
    public void onPause() { if(webView!=null)webView.onPause(); super.onPause(); }
    @Override
    public void onResume() { super.onResume(); ReminderScheduler.restore(this); es.josxtm.telecontrol.sync.SyncCoordinator.trigger(this,false); if (webView != null) {webView.onResume();webView.evaluateJavascript("window.CTSyncUI && window.CTSyncUI.resume()",null);} }
    @Override
    public void onDestroy() {
        webViewClosed=true;
        if (pendingFileSelection != null) {
            pendingFileSelection.onReceiveValue(null);
            pendingFileSelection = null;
        }
        if(webView!=null){
            webView.removeJavascriptInterface("AppInventor");
            webView.removeJavascriptInterface("CTFolderSync");
            webView.removeJavascriptInterface("CTWeeklyNative");
            webView.removeJavascriptInterface("CTNativeReminders");
            webView.stopLoading();
            android.view.ViewParent parent=webView.getParent();
            if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(webView);
            webView.destroy();webView=null;
        }
        super.onDestroy();
    }
}
