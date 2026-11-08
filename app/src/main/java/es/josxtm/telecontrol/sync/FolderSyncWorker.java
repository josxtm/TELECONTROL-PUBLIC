package es.josxtm.telecontrol.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.*;
import java.util.concurrent.CancellationException;

/** Persistent local work, with no network constraint and no permanent service. */
public final class FolderSyncWorker extends Worker {
    public FolderSyncWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){Context c=getApplicationContext();if(!SyncCoordinator.EXCHANGE.tryLock())return Result.retry();long generation=SyncCoordinator.generation(c);
        try{
            boolean manual=getInputData().getBoolean("manual",false);if(!SyncCoordinator.usable(c)||!manual&&!SyncCoordinator.prefs(c).getBoolean("auto",true))return Result.success();
            SyncCoordinator.state(c,"RUNNING");
            FolderExchange.Result result=FolderExchange.exchange(c,new SafFolder(c,SyncCoordinator.tree(c)),SyncCoordinator.vault(c),()->{if(isStopped()||SyncCoordinator.generation(c)!=generation)throw new CancellationException();},manual);
            if(SyncCoordinator.generation(c)!=generation)return Result.success();long now=System.currentTimeMillis();android.content.SharedPreferences.Editor update=SyncCoordinator.prefs(c).edit().putInt("incomplete",result.incomplete).putString("state",result.incomplete>0?"INCOMPLETE_FILES":result.more?"MORE":"DONE");if(result.imported>0)update.putLong("lastImport",now);if(result.exported>0)update.putLong("lastExport",now);if(!update.commit())throw new IllegalStateException("Settings unavailable");
            SyncCoordinator.acknowledge(c,new JSONArray());if(result.more)SyncCoordinator.trigger(c,manual);return Result.success();
        }catch(CancellationException e){return Result.success();}
        catch(SecurityException e){if(SyncCoordinator.generation(c)==generation)SyncCoordinator.state(c,"PERMISSION_REQUIRED");return Result.success();}
        catch(java.security.GeneralSecurityException e){if(SyncCoordinator.generation(c)==generation)SyncCoordinator.state(c,"KEY_ERROR");return Result.success();}
        catch(JSONException|IllegalArgumentException e){if(SyncCoordinator.generation(c)==generation)SyncCoordinator.state(c,"PROTOCOL_ERROR");return Result.success();}
        catch(Exception e){if(SyncCoordinator.generation(c)==generation)SyncCoordinator.state(c,"FOLDER_UNAVAILABLE");return Result.retry();}
        finally{SyncCoordinator.EXCHANGE.unlock();}
    }
}
