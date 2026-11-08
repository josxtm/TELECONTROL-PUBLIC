package es.josxtm.telecontrol.sync;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.regex.*;

/** Append-only authenticated publications. A filename hash and GCM replace rename atomicity. */
public final class FolderExchange {
    public static final int MAX_FILE=512*1024;
    private static final Pattern NAME=Pattern.compile("tcst1-([a-f0-9]{32})-([a-f0-9]{32})-([a-f0-9]{64})\\.stenc");
    public interface Guard {void check();}
    public static final class Result {public int imported,exported,incomplete;public boolean more;}
    private FolderExchange(){}
    private static String text(byte[] bytes)throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    private static String complete(ExchangeFolder store,ExchangeFolder.Entry e)throws Exception{
        Matcher name=NAME.matcher(e.name);if(!name.matches()||e.size>MAX_FILE)throw new IOException("Incomplete or oversized publication");
        byte[] bytes=store.read(e,MAX_FILE);String envelope=text(bytes);if(!SyncCrypto.hash(envelope).equals(name.group(3)))throw new IOException("Incomplete publication hash");return envelope;
    }
    public static Result exchange(Context c,ExchangeFolder store,SyncCrypto.Vault vault,Guard guard,boolean verifyAll)throws Exception{
        Result result=new Result();String device=SyncCoordinator.device(c),salt=SyncCoordinator.prefs(c).getString("salt","");
        guard.check();List<ExchangeFolder.Entry> files=store.list();
        try(SyncDatabase db=new SyncDatabase(c)){
            for(ExchangeFolder.Entry file:files){guard.check();if(file.name==null)continue;Matcher name=NAME.matcher(file.name);if(!name.matches()||!verifyAll&&db.seen(file))continue;
                String envelope;try{envelope=complete(store,file);}catch(IOException e){result.incomplete++;continue;}
                JSONObject cipher=new JSONObject(envelope);String batch=cipher.getString("op");if(!batch.matches("[a-f0-9]{32}"))throw new JSONException("Invalid publication identity");
                JSONObject payload=new JSONObject(vault.decrypt(batch,envelope));
                if(!"TELECONTROL_ST_BATCH_V1".equals(payload.getString("format"))||!batch.equals(payload.getString("batch"))||!name.group(1).equals(payload.getString("device")))throw new GeneralSecurityException("Publication identity mismatch");
                JSONArray operations=payload.getJSONArray("operations");if(operations.length()<1||operations.length()>100)throw new JSONException("Publication operation limit");
                int unknown=0;List<JSONObject> validated=new ArrayList<>();Set<String> ids=new HashSet<>();for(int i=0;i<operations.length();i++){JSONObject op=SyncDatabase.canonical(operations.getJSONObject(i).toString());if(!ids.add(op.getString("id")))throw new JSONException("Repeated revision in publication");if(!db.validateKnown(op))unknown++;validated.add(op);}
                guard.check();db.stage(validated,false,false,guard);db.meta("establishedKey","1");db.markSeen(file);if(unknown>0)result.imported++;
            }
            // A few immutable files per run; further local batches schedule another permitted job.
            for(int i=0;i<8;i++){
                guard.check();JSONObject batch=db.prepare(vault,device,salt,guard);if(batch==null)break;
                vault.decrypt(batch.getString("id"),batch.getString("envelope"));String oldName=batch.getString("filename");boolean valid=false;
                for(ExchangeFolder.Entry file:files)if(file.name.equals(oldName))try{if(complete(store,file).equals(batch.getString("envelope")))valid=true;}catch(IOException ignored){}
                if(!valid){
                    // Always a NEW document. Never truncate, rename, replace or delete another attempt.
                    String filename="tcst1-"+device+"-"+SyncCoordinator.randomId()+"-"+batch.getString("digest")+".stenc";
                    db.attempt(batch.getString("id"),filename);guard.check();ExchangeFolder.Entry target=store.create(filename);
                    if(!filename.equals(target.name))throw new IOException("Provider changed publication name");
                    store.write(target,batch.getString("envelope").getBytes(StandardCharsets.UTF_8));guard.check();
                    if(!complete(store,target).equals(batch.getString("envelope")))throw new IOException("Publication read-back failed");
                }
                guard.check();db.published(batch.getString("id"),guard);result.exported++;
            }
            result.more=db.more();
        }return result;
    }
}
