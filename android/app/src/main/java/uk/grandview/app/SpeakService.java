package uk.grandview.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

/**
 * Shows the reminder as a notification and reads it out with the phone's voice,
 * then stops. Runs briefly in the foreground so it can speak with the screen locked.
 */
public class SpeakService extends Service {
    static final String CHANNEL = "reminders";
    private static final int ONGOING_ID = 1;
    private TextToSpeech tts;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel(this);
        ArrayList<String> titles = intent != null ? intent.getStringArrayListExtra("titles") : null;
        ArrayList<String> texts = intent != null ? intent.getStringArrayListExtra("texts") : null;
        if (titles == null || texts == null || texts.isEmpty()) { stopSelf(); return START_NOT_STICKY; }

        Notification ongoing = base(this, titles.get(0), texts.get(0)).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ONGOING_ID, ongoing);
        }
        // a separate, lasting notification for each reminder that stays after the voice finishes
        NotificationManager nm = getSystemService(NotificationManager.class);
        for (int i = 0; i < texts.size(); i++) {
            nm.notify((int) (System.currentTimeMillis() % 100000) + i, base(this, titles.get(i), texts.get(i)).build());
        }

        SharedPreferences p = AlarmStore.prefs(this);
        if (!p.getBoolean("speak", true)) { finish(); return START_NOT_STICKY; }
        speak(android.text.TextUtils.join(" ", texts), p.getFloat("rate", 0.85f), p.getFloat("pitch", 1f), p.getFloat("volume", 1f));
        return START_NOT_STICKY;
    }

    private void speak(String text, float rate, float pitch, float volume) {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) { finish(); return; }
            Voices.choose(tts);
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { finish(); }
                @Override public void onError(String id) { finish(); }
            });
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "reminder");
        });
    }

    private void finish() {
        if (tts != null) { tts.shutdown(); tts = null; }
        stopForeground(true);
        stopSelf();
    }

    static NotificationCompat.Builder base(Context c, String title, String text) {
        PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("GrandView task reminders and family messages");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }
}
