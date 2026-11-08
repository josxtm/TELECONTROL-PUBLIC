package es.josxtm.telecontrol;
import android.content.*;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if("es.josxtm.telecontrol.REMIND".equals(intent.getAction()))
            ReminderScheduler.deliver(context,intent.getStringExtra("id"),intent.getLongExtra("due",-1));
        else ReminderScheduler.restore(context);
    }
}
