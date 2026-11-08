package es.josxtm.telecontrol.sync;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.Collections;
import org.json.*;

/** Offline-only recovery of previous local transport ledgers. Never reads a token or deletes data. */
public final class LegacyMigration {
    private LegacyMigration(){}
    private static void retireServiceCredential(Context c,String settings){
        // Only after a verified local copy. Keep historical encrypted recovery keys/account context.
        if(!c.getSharedPreferences(settings,0).edit().remove("credential").putBoolean("connected",false).putBoolean("auto",false).commit())throw new IllegalStateException("Legacy settings unavailable");
    }
    public static int run(Context c){int problems=0;
        String[][] previous={{"telecontrol-github-v1.db","telecontrol-github-settings-v1","TELECONTROL_GITHUB_OP_V1"},{"telecontrol-drive-v1.db","telecontrol-drive-settings-v1","TELECONTROL_DRIVE_OP_V1"}};
        try(SyncDatabase target=new SyncDatabase(c)){
            for(String[] source:previous){String marker="migration:"+source[0];if("done".equals(target.meta(marker))){retireServiceCredential(c,source[1]);continue;}File path=c.getDatabasePath(source[0]);if(!path.isFile()){target.meta(marker,"done");retireServiceCredential(c,source[1]);continue;}
                int failed=0;try(SQLiteDatabase old=SQLiteDatabase.openDatabase(path.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor rows=old.rawQuery("SELECT id,raw,envelope FROM revisions",null)){
                    SharedPreferences settings=c.getSharedPreferences(source[1],0);SyncCrypto.Vault legacy=null;int count=0;
                    while(rows.moveToNext()){
                        if(++count>100000){failed++;break;}
                        try{
                            String plain;
                            if(!rows.isNull(1))plain=SyncCrypto.unwrap(rows.getString(1));
                            else {if(legacy==null)legacy=SyncCrypto.legacy(SyncCrypto.unwrap(settings.getString("phrase","")),settings.getString("account",""),source[2]);plain=legacy.decrypt(rows.getString(0),rows.getString(2));}
                            JSONObject op=SyncDatabase.canonical(plain);if(!rows.getString(0).equals(op.getString("id")))throw new JSONException("Legacy identity mismatch");
                            target.stage(Collections.singletonList(op),true,false);
                        }catch(Exception ignored){failed++;}
                    }
                }catch(Exception ignored){failed++;}
                if(failed==0){target.meta(marker,"done");retireServiceCredential(c,source[1]);}problems+=failed;
            }
        }catch(Exception ignored){problems++;}return problems;
    }
}
