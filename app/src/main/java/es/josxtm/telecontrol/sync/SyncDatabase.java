package es.josxtm.telecontrol.sync;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Private durable operation ledger. NEVER shared as an Android database file. */
public final class SyncDatabase extends SQLiteOpenHelper {
    public SyncDatabase(Context c){super(c,"telecontrol-syncthing-v1.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE revisions(id TEXT PRIMARY KEY,hash TEXT NOT NULL,raw TEXT NOT NULL,outgoing INTEGER NOT NULL,uploaded INTEGER NOT NULL,applied INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE batches(id TEXT PRIMARY KEY,envelope TEXT NOT NULL,digest TEXT NOT NULL,filename TEXT NOT NULL,published INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE batch_ops(operation TEXT PRIMARY KEY,batch TEXT NOT NULL)");
        db.execSQL("CREATE TABLE seen(document TEXT PRIMARY KEY,stamp TEXT NOT NULL)");
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported exchange schema");}
    public static JSONObject canonical(String json)throws Exception{
        if(json==null||json.length()>250000)throw new JSONException("Operation too large");JSONObject op=new JSONObject(json);String id=op.getString("id"),entity=op.getString("entity");JSONArray parents=op.getJSONArray("parents");
        if(!id.matches("[a-f0-9]{32}")||entity.isEmpty()||entity.length()>150||parents.length()>100)throw new JSONException("Invalid operation");
        TreeSet<String> sorted=new TreeSet<>();for(int i=0;i<parents.length();i++){String p=parents.getString(i);if(!p.matches("[a-f0-9]{32}")||p.equals(id)||!sorted.add(p))throw new JSONException("Invalid parents");}
        boolean deleted=op.getBoolean("deleted");Object entry=JSONObject.NULL;
        if(!deleted){JSONObject e=op.getJSONObject("entry");String kind=e.getString("kind"),text=e.getString("text");if(!entity.equals(e.getString("id"))||!("note".equals(kind)||"journal".equals(kind))||text.trim().isEmpty()||text.length()>20000)throw new JSONException("Invalid work entry");
            Object ct=e.get("ct");if("note".equals(kind)&&(!(ct instanceof String)||!((String)ct).matches("[A-Z0-9]+")))throw new JSONException("Invalid CT");if("journal".equals(kind)&&ct!=JSONObject.NULL)throw new JSONException("Invalid journal");
            entry=new JSONObject().put("id",entity).put("kind",kind).put("ct",ct).put("text",text).put("createdAt",date(e.get("createdAt"))).put("modifiedAt",date(e.get("modifiedAt")));
        }
        return new JSONObject().put("id",id).put("entity",entity).put("parents",new JSONArray(sorted)).put("deleted",deleted).put("entry",entry);
    }
    private static Object date(Object value)throws Exception{if(value==JSONObject.NULL)return value;if(!(value instanceof String)||!((String)value).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))throw new JSONException("Invalid timestamp");return value;}

    public synchronized boolean contains(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM revisions WHERE id=?",new String[]{id})){return c.moveToFirst();}}
    public synchronized boolean validateKnown(JSONObject op)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT hash FROM revisions WHERE id=?",new String[]{op.getString("id")})){if(!c.moveToFirst())return false;if(!SyncCrypto.hash(canonical(op.toString()).toString()).equals(c.getString(0)))throw new JSONException("Immutable revision collision");return true;}}
    public synchronized void enqueue(String json)throws Exception{stage(Collections.singletonList(canonical(json)),true,true);}
    public synchronized void stage(List<JSONObject> values,boolean outgoing,boolean applied)throws Exception{stage(values,outgoing,applied,null);}
    public synchronized void stage(List<JSONObject> values,boolean outgoing,boolean applied,FolderExchange.Guard guard)throws Exception{
        List<ContentValues> prepared=new ArrayList<>();for(JSONObject value:values){JSONObject op=canonical(value.toString());String plain=op.toString();ContentValues v=new ContentValues();v.put("id",op.getString("id"));v.put("hash",SyncCrypto.hash(plain));v.put("raw",SyncCrypto.wrap(plain));v.put("outgoing",outgoing?1:0);v.put("uploaded",outgoing?0:1);v.put("applied",applied?1:0);prepared.add(v);}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{if(guard!=null)guard.check();boolean inserted=false;
            for(ContentValues v:prepared){String id=v.getAsString("id");boolean exists;try(Cursor c=db.rawQuery("SELECT hash FROM revisions WHERE id=?",new String[]{id})){exists=c.moveToFirst();if(exists&&!v.getAsString("hash").equals(c.getString(0)))throw new JSONException("Immutable revision collision");}
                if(!exists){db.insertOrThrow("revisions",null,v);inserted=true;}else{ContentValues refresh=new ContentValues();refresh.put("raw",v.getAsString("raw"));if(applied)refresh.put("applied",1);db.update("revisions",refresh,"id=?",new String[]{id});}
            }if(inserted&&!outgoing&&!applied)meta("lastImport",Long.toString(System.currentTimeMillis()));db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized JSONArray missing(JSONArray ids)throws Exception{if(ids.length()>500)throw new JSONException("Batch limit");JSONArray result=new JSONArray();for(int i=0;i<ids.length();i++){String id=ids.getString(i);try(Cursor c=getReadableDatabase().rawQuery("SELECT raw FROM revisions WHERE id=?",new String[]{id})){if(!c.moveToFirst())result.put(id);else try{SyncCrypto.unwrap(c.getString(0));}catch(Exception e){result.put(id);}}}return result;}
    public synchronized JSONArray inbox()throws Exception{JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT raw FROM revisions WHERE applied=0 LIMIT 50",null)){while(c.moveToNext())rows.put(canonical(SyncCrypto.unwrap(c.getString(0))));}return rows;}
    public synchronized void acknowledge(JSONArray ids)throws Exception{SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<ids.length();i++){ContentValues v=new ContentValues();v.put("applied",1);db.update("revisions",v,"id=?",new String[]{ids.getString(i)});}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized String meta(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}}
    public synchronized long metaTime(String key){String value=meta(key);return value.isEmpty()?0:Long.parseLong(value);}
    public synchronized void meta(String key,String value){ContentValues v=new ContentValues();v.put("key",key);v.put("value",value);getWritableDatabase().insertWithOnConflict("meta",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized boolean seen(ExchangeFolder.Entry e){try(Cursor c=getReadableDatabase().rawQuery("SELECT stamp FROM seen WHERE document=?",new String[]{e.key})){return c.moveToFirst()&&e.stamp().equals(c.getString(0));}}
    public synchronized void markSeen(ExchangeFolder.Entry e){ContentValues v=new ContentValues();v.put("document",e.key);v.put("stamp",e.stamp());getWritableDatabase().insertWithOnConflict("seen",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    public synchronized JSONObject unpublished()throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT id,envelope,digest,filename FROM batches WHERE published=0 ORDER BY rowid LIMIT 1",null)){return c.moveToFirst()?new JSONObject().put("id",c.getString(0)).put("envelope",c.getString(1)).put("digest",c.getString(2)).put("filename",c.getString(3)):null;}}
    public synchronized JSONObject prepare(SyncCrypto.Vault vault,String device,String salt,FolderExchange.Guard guard)throws Exception{
        JSONObject existing=unpublished();if(existing!=null)return existing;
        JSONArray operations=new JSONArray();int bytes=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT raw FROM revisions WHERE outgoing=1 AND uploaded=0 AND id NOT IN (SELECT operation FROM batch_ops) ORDER BY rowid LIMIT 100",null)){
            while(c.moveToNext()){JSONObject op=canonical(SyncCrypto.unwrap(c.getString(0)));int size=op.toString().getBytes(StandardCharsets.UTF_8).length;if(bytes+size>256*1024)break;bytes+=size;operations.put(op);}
        }
        if(operations.length()==0)return null;
        String id=SyncCoordinator.randomId(),plain=new JSONObject().put("format","TELECONTROL_ST_BATCH_V1").put("device",device).put("batch",id).put("operations",operations).toString();
        // Derivation/encryption happens before obtaining the write transaction, keeping local saves responsive.
        String envelope=vault.encrypt(id,plain,salt),digest=SyncCrypto.hash(envelope);if(envelope.getBytes(StandardCharsets.UTF_8).length>FolderExchange.MAX_FILE)throw new JSONException("Exchange batch limit");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{guard.check();ContentValues v=new ContentValues();v.put("id",id);v.put("envelope",envelope);v.put("digest",digest);v.put("filename","");v.put("published",0);db.insertOrThrow("batches",null,v);for(int i=0;i<operations.length();i++){ContentValues link=new ContentValues();link.put("operation",operations.getJSONObject(i).getString("id"));link.put("batch",id);db.insertOrThrow("batch_ops",null,link);}db.setTransactionSuccessful();}finally{db.endTransaction();}
        return unpublished();
    }
    public synchronized void attempt(String id,String filename){ContentValues v=new ContentValues();v.put("filename",filename);getWritableDatabase().update("batches",v,"id=?",new String[]{id});}
    public synchronized void published(String id,FolderExchange.Guard guard){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{guard.check();ContentValues v=new ContentValues();v.put("published",1);meta("publishedKey","1");meta("lastExport",Long.toString(System.currentTimeMillis()));db.update("batches",v,"id=?",new String[]{id});db.execSQL("UPDATE revisions SET uploaded=1 WHERE id IN (SELECT operation FROM batch_ops WHERE batch=?)",new Object[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized void resetTarget(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.execSQL("UPDATE batches SET published=0,filename='' ");db.execSQL("UPDATE revisions SET outgoing=1,uploaded=0");db.delete("seen",null,null);meta("lastImport","0");meta("lastExport","0");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized boolean more(){return count("outgoing=1 AND uploaded=0")>0;}
    public synchronized int count(String where){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM revisions WHERE "+where,null)){c.moveToFirst();return c.getInt(0);}}
}
