package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class TTSLanguageManager {
    private static final String PREFS = "BilaWogaTTS";
    private static final String KEY_LANG = "tts_language"; // "en" or "sw"
    private static final String KEY_AUTO_READ = "tts_auto_read_enabled";
    private static final String KEY_FIRST_LAUNCH_INIT = "tts_first_launch_init";
    private static final String KEY_TTS_INFO_SHOWN = "tts_info_shown";
    private static final String KEY_TTS_PREFERENCE_ASKED = "tts_preference_asked"; // Track if user has been asked about TTS

    public static void initDefaultOnFirstLaunch(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_FIRST_LAUNCH_INIT, false)) {
            // Default to disabled - user can enable if they want
            p.edit()
                .putString(KEY_LANG, "sw")
                .putBoolean(KEY_AUTO_READ, false) // Changed to false - make it optional
                .putBoolean(KEY_FIRST_LAUNCH_INIT, true)
                .putBoolean(KEY_TTS_INFO_SHOWN, false) // Show info pop-up on first launch
                .apply();
        }
    }
    
    /**
     * Check if TTS info pop-up should be shown
     */
    public static boolean shouldShowTTSInfo(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !p.getBoolean(KEY_TTS_INFO_SHOWN, false);
    }
    
    /**
     * Mark TTS info as shown
     */
    public static void markTTSInfoShown(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TTS_INFO_SHOWN, true).apply();
    }

    public static Locale getSelectedLocale(Context context) {
        String code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "sw");
        if ("sw".equals(code)) {
            // Return Kenya Swahili locale for better TTS support
            return new Locale("sw", "KE");
        }
        return Locale.ENGLISH;
    }

    public static String getSelectedLanguageName(Context context) {
        String code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "en");
        return "sw".equals(code) ? "Swahili" : "English";
    }

    public static void toggleLanguage(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String code = p.getString(KEY_LANG, "en");
        p.edit().putString(KEY_LANG, "en".equals(code) ? "sw" : "en").apply();
    }

    public static boolean isAutoReadEnabled(Context context) {
        // Default to false - user must opt-in
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_READ, false);
    }

    public static void setAutoReadEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_READ, enabled).apply();
    }
    
    /**
     * Check if TTS preference dialog should be shown (only once on first launch)
     */
    public static boolean shouldShowTTSPreferenceDialog(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !p.getBoolean(KEY_TTS_PREFERENCE_ASKED, false);
    }
    
    /**
     * Mark TTS preference dialog as shown
     */
    public static void markTTSPreferenceAsked(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TTS_PREFERENCE_ASKED, true).apply();
    }

    public static int setTtsLanguage(TextToSpeech tts, Locale locale) {
        if (tts == null) return TextToSpeech.ERROR;
        
        // Check availability
        int availability = tts.isLanguageAvailable(locale);
        
        // If Swahili is not available, try alternative Swahili locales
        if (locale.getLanguage().equals("sw")) {
            if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try other Swahili locales
                Locale[] alternatives = {
                    new Locale("sw", "TZ"), // Tanzania
                    new Locale("sw"),     // Generic
                };
                for (Locale alt : alternatives) {
                    int altAvail = tts.isLanguageAvailable(alt);
                    if (altAvail >= TextToSpeech.LANG_AVAILABLE) {
                        return tts.setLanguage(alt);
                    }
                }
            }
        }
        
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            return availability;
        }
        return tts.setLanguage(locale);
    }
}
