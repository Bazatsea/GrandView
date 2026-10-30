package uk.grandview.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are cleared when the phone restarts or the clock changes; set the next one again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmStore.scheduleNext(context);
    }
}
