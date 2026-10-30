package uk.grandview.app;

import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

/** Picks the phone voice: a British English one on the device if possible, else any English. */
final class Voices {
    private Voices() {}

    static void choose(TextToSpeech tts) {
        try {
            Voice best = null;
            Set<Voice> all = tts.getVoices();
            if (all != null) {
                for (Voice v : all) {
                    if (v.isNetworkConnectionRequired()) continue;
                    Locale l = v.getLocale();
                    if (!"en".equals(l.getLanguage())) continue;
                    boolean gb = "GB".equals(l.getCountry());
                    if (best == null || (gb && !"GB".equals(best.getLocale().getCountry()))) best = v;
                }
            }
            if (best != null) tts.setVoice(best); else tts.setLanguage(Locale.UK);
        } catch (Exception e) {
            tts.setLanguage(Locale.UK);
        }
    }
}
