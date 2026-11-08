package es.josxtm.telecontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.*;

/** Local durable reminders. No network, no catalogue, one PendingIntent per UUID. */
public final class ReminderScheduler {
    private static final String PREFS = "telecontrol-reminders-v1";
    private static final String CHANNEL = "telecontrol-reminders";
    private ReminderScheduler() { }
    static synchronized JSONObject records(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREFS,0).getString("records","{}")); }
        catch (JSONException e) { throw new IllegalStateException("Reminder storage unreadable"); }
    }
    private static void persist(Context c, JSONObject data) {
        if (!c.getSharedPreferences(PREFS,0).edit().putString("records",data.toString()).commit())
            throw new IllegalStateException("Reminder storage unavailable");
    }
    private static PendingIntent intent(Context c,String id,long due,int flags) {
        Intent i=new Intent(c,ReminderReceiver.class).setAction("es.josxtm.telecontrol.REMIND")
            .setData(Uri.parse("telecontrol://reminder/"+id)).putExtra("id",id).putExtra("due",due);
        return PendingIntent.getBroadcast(c,0,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean exact(Context c) {
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms();
    }
    public static boolean notifications(Context c) {
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=24 && !manager.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=manager.getNotificationChannel(CHANNEL);if(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;}
        return true;
    }
    private static void cancel(Context c,String id) {
        PendingIntent pi=intent(c,id,0,PendingIntent.FLAG_NO_CREATE);
        if(pi!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id,0);
    }
    @SuppressLint("ScheduleExactAlarm") // Explicit capability check and inexact fallback.
    static void schedule(Context c,JSONObject entry) throws JSONException {
        String id=entry.getString("id");cancel(c,id);
        if(entry.getBoolean("completed") || entry.optBoolean("delivered"))return;
        long due=entry.getLong("due"),when=Math.max(System.currentTimeMillis()+1000,due);
        PendingIntent pi=intent(c,id,due,PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(exact(c)){
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);return; }
            catch(SecurityException ignored){ /* Permission may have changed concurrently. */ }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }
    public static synchronized String save(Context c,String json) {
        try {
            JSONObject e=new JSONObject(json);String id=e.getString("id"),text=e.getString("description"),ct=e.optString("ct","");
            if(!id.matches("[a-f0-9-]{32,36}")||text.trim().isEmpty()||text.length()>2000||ct.length()>80||e.getLong("due")<=0)throw new JSONException("Invalid reminder");
            JSONObject all=records(c);JSONObject previous=all.optJSONObject(id);boolean keepDelivered=previous!=null&&previous.optLong("due")==e.getLong("due")&&previous.optBoolean("completed")==e.getBoolean("completed")&&previous.optBoolean("delivered");if(!all.has(id)&&all.length()>=500)throw new JSONException("Limit");
            JSONObject clean=new JSONObject().put("id",id).put("description",text).put("ct",ct).put("due",e.getLong("due")).put("completed",e.getBoolean("completed")).put("delivered",keepDelivered);
            all.put(id,clean);persist(c,all);
            // Persistence succeeded even if Android temporarily refuses to schedule an alarm.
            // Keep that distinction so a retry cannot look like a missing/deleted reminder.
            boolean scheduled=true,canExact=false,canNotify=false;
            try { schedule(c,clean); } catch(RuntimeException schedulingFailure) { scheduled=false; }
            try { canExact=exact(c); } catch(RuntimeException ignored) { }
            try { canNotify=notifications(c); } catch(RuntimeException ignored) { }
            return new JSONObject().put("ok",true).put("scheduled",scheduled).put("exact",canExact).put("notifications",canNotify).toString();
        }catch(JSONException|RuntimeException e){return "{\"ok\":false,\"error\":\"No se pudo guardar o programar el aviso\"}";}
    }
    public static synchronized String remove(Context c,String id){
        try{JSONObject all=records(c);all.remove(id);persist(c,all);cancel(c,id);return "{\"ok\":true}";}
        catch(RuntimeException e){return "{\"ok\":false}";}
    }
    public static synchronized void restore(Context c){
        try{JSONObject all=records(c);java.util.Iterator<String> keys=all.keys();while(keys.hasNext())schedule(c,all.getJSONObject(keys.next()));}
        catch(JSONException|RuntimeException ignored){ /* Original records are never cleared. */ }
    }
    @SuppressLint("MissingPermission") // Runtime permission and global notification setting checked below.
    public static synchronized void deliver(Context c,String id,long expectedDue){
        try{
            JSONObject all=records(c),e=all.optJSONObject(id);
            if(e==null||e.getBoolean("completed")||e.optBoolean("delivered")||e.getLong("due")!=expectedDue||!notifications(c))return;
            if(e.getLong("due")>System.currentTimeMillis()+2000){schedule(c,e);return;}
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Avisos TELECONTROL",NotificationManager.IMPORTANCE_DEFAULT));
            Intent open=new Intent(c,MainActivity.class).setData(Uri.parse("telecontrol://open-reminder/"+id));
            PendingIntent click=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
            Notification notification=builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("TELECONTROL · AVISO")
                .setContentText(e.getString("description")).setStyle(new Notification.BigTextStyle().bigText(e.getString("description")))
                .setContentIntent(click).setOnlyAlertOnce(true).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
            nm.notify(id,0,notification); // Same tag replaces retries rather than duplicating notifications.
            e.put("delivered",true);persist(c,all);
        }catch(JSONException|RuntimeException ignored){ /* Never erase pending reminders on failure. */ }
    }
}
