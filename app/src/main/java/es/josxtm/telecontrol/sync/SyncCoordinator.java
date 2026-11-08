package es.josxtm.telecontrol.sync;

import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import androidx.work.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** Local SAF exchange only. Syncthing-Fork transports the files independently. */
public final class SyncCoordinator {
    static final ReentrantLock EXCHANGE=new ReentrantLock();
    public static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static ContentObserver observer;private static String observed="";private static boolean initialized;
    private SyncCoordinator(){}
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("telecontrol-syncthing-settings-v1",0);}
    private static void write(SharedPreferences.Editor e){if(!e.commit())throw new IllegalStateException("Sync settings unavailable");}
    public static String randomId(){StringBuilder s=new StringBuilder();for(byte b:SyncCrypto.random(16))s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    public static synchronized String device(Context c){String id=prefs(c).getString("device","");if(id.isEmpty()){id=randomId();write(prefs(c).edit().putString("device",id));}return id;}
    public static void state(Context c,String state){write(prefs(c).edit().putString("state",state));}
    public static long generation(Context c){return prefs(c).getLong("generation",0);}
    public static Uri tree(Context c){String value=prefs(c).getString("tree","");return value.isEmpty()?null:Uri.parse(value);}
    public static boolean connected(Context c){try{return SafFolder.granted(c,tree(c));}catch(RuntimeException e){return false;}}
    public static boolean usable(Context c){return connected(c)&&prefs(c).contains("phrase");}
    public static void selectTree(Context c,Uri tree,int offered)throws Exception{
        int flags=offered&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if(flags!=(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION))throw new SecurityException("Read/write folder access required");
        c.getContentResolver().takePersistableUriPermission(tree,flags);if(!SafFolder.granted(c,tree))throw new SecurityException("Persistent folder access required");
        String name=new SafFolder(c,tree).directoryName();
        synchronized(SyncCoordinator.class){SharedPreferences p=prefs(c);if(!tree.toString().equals(p.getString("tree",""))){write(p.edit().putLong("generation",generation(c)+1).putLong("lastImport",0).putLong("lastExport",0));try(SyncDatabase db=new SyncDatabase(c)){db.resetTarget();}}
            write(p.edit().putString("tree",tree.toString()).putString("folderName",name).putString("state",p.contains("phrase")?"READY":"KEY_REQUIRED"));}
        device(c);observe(c.getApplicationContext());schedule(c);trigger(c,false);
    }
    public static synchronized void phrase(Context c,String value)throws Exception{
        if(value==null||value.length()<16||value.length()>1024)throw new SecurityException("Recovery phrase required");
        SharedPreferences p=prefs(c);
        // Serialize key changes with publication preparation: no old-key batch can slip in after the check.
        try(SyncDatabase ledger=new SyncDatabase(c)){
            android.database.sqlite.SQLiteDatabase db=ledger.getWritableDatabase();db.beginTransaction();try{
                if(p.contains("phrase")&&!SyncCrypto.unwrap(p.getString("phrase","")).equals(value)&&(!ledger.meta("publishedKey").isEmpty()||!ledger.meta("establishedKey").isEmpty()||ledger.unpublished()!=null))throw new SecurityException("Changing an established recovery phrase requires explicit migration");
                SharedPreferences.Editor e=p.edit().putString("phrase",SyncCrypto.wrap(value)).putLong("generation",generation(c)+1).putString("state",connected(c)?"READY":"FOLDER_REQUIRED");
                if(!p.contains("salt"))e.putString("salt",SyncCrypto.b64(SyncCrypto.random(16)));write(e);db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        device(c);schedule(c);trigger(c,false);
    }
    public static SyncCrypto.Vault vault(Context c)throws Exception{return new SyncCrypto.Vault(SyncCrypto.unwrap(prefs(c).getString("phrase","")),"TELECONTROL_SYNCTHING_WORK_V1");}
    public static synchronized void automatic(Context c,boolean enabled){write(prefs(c).edit().putBoolean("auto",enabled).putLong("generation",generation(c)+1));if(!enabled)WorkManager.getInstance(c).cancelUniqueWork("tc-st-now");schedule(c);if(enabled)trigger(c,false);}
    public static void schedule(Context c){if(usable(c)&&prefs(c).getBoolean("auto",true))WorkManager.getInstance(c).enqueueUniquePeriodicWork("tc-st-periodic",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(FolderSyncWorker.class,15,TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());else WorkManager.getInstance(c).cancelUniqueWork("tc-st-periodic");}
    public static void trigger(Context context,boolean manual){
        Context c=context.getApplicationContext();if(!usable(c)||!manual&&!prefs(c).getBoolean("auto",true))return;
        IO.execute(()->{try{if(!usable(c)||!manual&&!prefs(c).getBoolean("auto",true))return;WorkManager manager=WorkManager.getInstance(c);
            String job=manual?"tc-st-manual":"tc-st-now";boolean waiting=false;for(WorkInfo info:manager.getWorkInfosForUniqueWork(job).get(10,TimeUnit.SECONDS))if(info.getState()==WorkInfo.State.ENQUEUED||info.getState()==WorkInfo.State.BLOCKED)waiting=true;
            if(!waiting)manager.enqueueUniqueWork(job,ExistingWorkPolicy.APPEND_OR_REPLACE,new OneTimeWorkRequest.Builder(FolderSyncWorker.class).setInputData(new Data.Builder().putBoolean("manual",manual).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()).getResult().get(10,TimeUnit.SECONDS);
        }catch(Exception ignored){/* Native queue remains durable for foreground/periodic retries. */}});
    }
    private static synchronized void observe(Context c){Uri selected=tree(c);String value=selected==null?"":selected.toString();if(value.equals(observed))return;if(observer!=null)c.getContentResolver().unregisterContentObserver(observer);observer=null;observed=value;
        if(selected!=null)try{observer=new ContentObserver(new Handler(Looper.getMainLooper())){@Override public void onChange(boolean self){trigger(c,false);}};c.getContentResolver().registerContentObserver(DocumentsContract.buildChildDocumentsUriUsingTree(selected,DocumentsContract.getTreeDocumentId(selected)),true,observer);}catch(RuntimeException ignored){/* Foreground polling also detects providers without notifications. */}
    }
    public static synchronized void initialize(Context c){Context app=c.getApplicationContext();
        for(String job:new String[]{"tc-drive-now","tc-drive-periodic","tc-drive-revoke","tc-github-now","tc-github-periodic"})WorkManager.getInstance(app).cancelUniqueWork(job);
        device(app);observe(app);schedule(app);if(!initialized){initialized=true;IO.execute(()->{int problems=LegacyMigration.run(app);write(prefs(app).edit().putInt("migrationProblems",problems));});}
    }
    public static JSONObject status(Context c)throws Exception{SharedPreferences p=prefs(c);boolean selected=connected(c);String state=p.getString("state","FOLDER_REQUIRED");if(!selected)state=tree(c)==null?"FOLDER_REQUIRED":"PERMISSION_REQUIRED";
        try(SyncDatabase db=new SyncDatabase(c)){return new JSONObject().put("connected",selected).put("localQueue",true).put("account",p.getString("folderName","")).put("key",p.contains("phrase")).put("auto",p.getBoolean("auto",true)).put("state",state).put("outgoing",db.count("outgoing=1 AND uploaded=0")).put("incoming",db.count("applied=0")).put("lastImport",Math.max(p.getLong("lastImport",0),db.metaTime("lastImport"))).put("lastExport",Math.max(p.getLong("lastExport",0),db.metaTime("lastExport"))).put("lastSync",p.getLong("lastSync",0)).put("incomplete",p.getInt("incomplete",0)).put("migrationProblems",p.getInt("migrationProblems",0));}
    }
    public static void acknowledge(Context c,JSONArray ids)throws Exception{try(SyncDatabase db=new SyncDatabase(c)){db.acknowledge(ids);if(db.count("applied=0")==0&&prefs(c).getBoolean("consistent",false))write(prefs(c).edit().putLong("lastSync",Math.max(prefs(c).getLong("lastImport",0),db.metaTime("lastImport"))));}}
}
