package uk.grandview.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the upcoming spoken reminders the app hands over, and keeps exactly one
 * system alarm set: the next one due. Each alarm, when it fires, sets the next.
 */
public final class AlarmStore {
    private static final String PREFS = "grandview_alarms";

    private AlarmStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Replaces the whole list (sorted by time) and the speech settings. */
    static void save(Context c, JSONArray alarms, boolean speak, float rate, float pitch, float volume) {
        prefs(c).edit()
            .putString("alarms", alarms.toString())
            .putBoolean("speak", speak)
            .putFloat("rate", rate)
            .putFloat("pitch", pitch)
            .putFloat("volume", volume)
            .apply();
        scheduleNext(c);
    }

    static List<JSONObject> load(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("alarms", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (JSONException ignored) {}
        return out;
    }

    /** Removes and returns every alarm due by now (allowing a little slack). */
    static List<JSONObject> takeDue(Context c) {
        long now = System.currentTimeMillis();
        List<JSONObject> due = new ArrayList<>();
        JSONArray rest = new JSONArray();
        for (JSONObject a : load(c)) {
            long at = a.optLong("at");
            if (at <= now + 30_000) {
                // only speak ones that are recent; a phone switched off overnight shouldn't read out the morning
                if (now - at < 15 * 60_000) due.add(a);
            } else {
                rest.put(a);
            }
        }
        prefs(c).edit().putString("alarms", rest.toString()).apply();
        return due;
    }

    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, AlarmReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        for (JSONObject a : load(c)) {
            long at = a.optLong("at");
            if (at > now - 60_000 && at < next) next = at;
        }
        if (next == Long.MAX_VALUE) return;
        next = Math.max(next, now + 1000);
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exact) {
            // an alarm-clock alarm fires on time even in battery-saving doze mode
            PendingIntent show = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(next, show), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }
}
