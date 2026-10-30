package uk.grandview.app;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The page's window.GVNative: speak now, and set the upcoming spoken reminders. */
@CapacitorPlugin(name = "SpeakAlarm")
public class SpeakAlarmPlugin extends Plugin {
    private TextToSpeech tts;
    private boolean ready = false;
    private final List<Runnable> waiting = new ArrayList<>();

    @Override
    public void load() {
        tts = new TextToSpeech(getContext(), status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) Voices.choose(tts);
            for (Runnable r : waiting) r.run();
            waiting.clear();
        });
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        float rate = call.getFloat("rate", 0.85f), pitch = call.getFloat("pitch", 1f), volume = call.getFloat("volume", 1f);
        Runnable go = () -> {
            if (!ready) { call.reject("Speech isn't available on this phone"); return; }
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);
            tts.speak(text, TextToSpeech.QUEUE_ADD, params, "app-" + System.nanoTime());
            call.resolve();
        };
        if (tts == null) { call.reject("Speech isn't available"); return; }
        synchronized (waiting) { if (ready) go.run(); else waiting.add(go); }
    }

    @PluginMethod
    public void setAlarms(PluginCall call) {
        JSArray alarms = call.getArray("alarms", new JSArray());
        JSONArray clean = new JSONArray();
        try {
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject a = alarms.getJSONObject(i);
                clean.put(new JSONObject()
                    .put("at", a.getLong("at"))
                    .put("title", a.optString("title", "GrandView"))
                    .put("text", a.optString("text", "")));
            }
        } catch (JSONException e) {
            call.reject("Alarm list wasn't readable");
            return;
        }
        AlarmStore.save(getContext(), clean, call.getBoolean("speak", true),
            call.getFloat("rate", 0.85f), call.getFloat("pitch", 1f), call.getFloat("volume", 1f));
        JSObject ret = new JSObject();
        ret.put("count", clean.length());
        call.resolve(ret);
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) tts.shutdown();
    }
}
