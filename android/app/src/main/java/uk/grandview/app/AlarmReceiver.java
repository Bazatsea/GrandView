package uk.grandview.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Fires at a reminder's time: hands it to SpeakService, then sets the next alarm. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        List<JSONObject> due = AlarmStore.takeDue(context);
        if (!due.isEmpty()) {
            ArrayList<String> titles = new ArrayList<>(), texts = new ArrayList<>();
            for (JSONObject a : due) {
                titles.add(a.optString("title", "GrandView"));
                texts.add(a.optString("text", ""));
            }
            Intent s = new Intent(context, SpeakService.class)
                .putStringArrayListExtra("titles", titles)
                .putStringArrayListExtra("texts", texts);
            ContextCompat.startForegroundService(context, s);
        }
        AlarmStore.scheduleNext(context);
    }
}
