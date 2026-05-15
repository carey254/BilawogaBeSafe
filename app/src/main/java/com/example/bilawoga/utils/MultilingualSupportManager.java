package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MULTILINGUAL SUPPORT MANAGER
 * 
 * Implements Security by Design (SbD) feature to mitigate language access risks:
 * - Comprehensive multilingual support
 * - Language detection and switching
 * - Localized content management
 * - Accessibility for non-dominant language speakers
 * - Fallback language support
 */
public class MultilingualSupportManager {
    private static final String TAG = "MultilingualSupportManager";
    private static final String PREFS_NAME = "multilingual_prefs";
    private static final String KEY_SELECTED_LANGUAGE = "selected_language";
    private static final String KEY_AUTO_DETECT = "auto_detect_language";
    
    // Supported languages
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_SWAHILI = "sw";
    public static final String LANGUAGE_FRENCH = "fr";
    public static final String LANGUAGE_ARABIC = "ar";
    public static final String LANGUAGE_SPANISH = "es";
    
    // Language metadata
    private static final Map<String, LanguageInfo> SUPPORTED_LANGUAGES = new HashMap<>();
    
    static {
        SUPPORTED_LANGUAGES.put(LANGUAGE_ENGLISH, new LanguageInfo(
            "English", "en", "US", "English", "English"
        ));
        SUPPORTED_LANGUAGES.put(LANGUAGE_SWAHILI, new LanguageInfo(
            "Swahili", "sw", "KE", "Kiswahili", "Swahili"
        ));
        SUPPORTED_LANGUAGES.put(LANGUAGE_FRENCH, new LanguageInfo(
            "French", "fr", "FR", "Français", "French"
        ));
        SUPPORTED_LANGUAGES.put(LANGUAGE_ARABIC, new LanguageInfo(
            "Arabic", "ar", "SA", "العربية", "Arabic"
        ));
        SUPPORTED_LANGUAGES.put(LANGUAGE_SPANISH, new LanguageInfo(
            "Spanish", "es", "ES", "Español", "Spanish"
        ));
    }
    
    private final Context context;
    
    public MultilingualSupportManager(Context context) {
        this.context = context;
    }
    
    /**
     * Get list of all supported languages
     */
    public static List<LanguageInfo> getSupportedLanguages() {
        return new ArrayList<>(SUPPORTED_LANGUAGES.values());
    }
    
    /**
     * Get language info for a language code
     */
    public static LanguageInfo getLanguageInfo(String languageCode) {
        return SUPPORTED_LANGUAGES.get(languageCode);
    }
    
    /**
     * Set app language
     */
    public void setLanguage(String languageCode) {
        try {
            LanguageInfo langInfo = SUPPORTED_LANGUAGES.get(languageCode);
            if (langInfo == null) {
                Log.w(TAG, "Unsupported language code: " + languageCode);
                languageCode = LANGUAGE_ENGLISH; // Fallback to English
                langInfo = SUPPORTED_LANGUAGES.get(languageCode);
            }
            
            Locale locale = new Locale(langInfo.code, langInfo.country);
            Locale.setDefault(locale);
            
            Configuration config = context.getResources().getConfiguration();
            config.setLocale(locale);
            
            // Save preference
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString(KEY_SELECTED_LANGUAGE, languageCode)
                .apply();
            
            Log.i(TAG, "Language set to: " + langInfo.displayName + " (" + languageCode + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error setting language: " + e.getMessage());
        }
    }
    
    /**
     * Get current language
     */
    public String getCurrentLanguage() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, null);
            
            if (savedLanguage != null) {
                return savedLanguage;
            }
            
            // Auto-detect if enabled
            if (isAutoDetectEnabled()) {
                return detectSystemLanguage();
            }
            
            // Default to English
            return LANGUAGE_ENGLISH;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current language: " + e.getMessage());
            return LANGUAGE_ENGLISH;
        }
    }
    
    /**
     * Detect system language
     */
    public String detectSystemLanguage() {
        try {
            Locale systemLocale = Locale.getDefault();
            String systemLang = systemLocale.getLanguage();
            
            // Check if system language is supported
            if (SUPPORTED_LANGUAGES.containsKey(systemLang)) {
                return systemLang;
            }
            
            // Try to match by language family
            for (String langCode : SUPPORTED_LANGUAGES.keySet()) {
                if (langCode.startsWith(systemLang) || systemLang.startsWith(langCode)) {
                    return langCode;
                }
            }
            
            // Fallback to English
            return LANGUAGE_ENGLISH;
        } catch (Exception e) {
            Log.e(TAG, "Error detecting system language: " + e.getMessage());
            return LANGUAGE_ENGLISH;
        }
    }
    
    /**
     * Enable/disable auto-detect language
     */
    public void setAutoDetectEnabled(boolean enabled) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putBoolean(KEY_AUTO_DETECT, enabled)
                .apply();
            
            if (enabled) {
                String detectedLang = detectSystemLanguage();
                setLanguage(detectedLang);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting auto-detect: " + e.getMessage());
        }
    }
    
    /**
     * Check if auto-detect is enabled
     */
    public boolean isAutoDetectEnabled() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_AUTO_DETECT, true); // Default to enabled
        } catch (Exception e) {
            Log.e(TAG, "Error checking auto-detect: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Get localized string (with fallback)
     */
    public String getLocalizedString(int stringResId, String... formatArgs) {
        try {
            Resources resources = context.getResources();
            String language = getCurrentLanguage();
            
            // Try to get string in current language
            try {
                Configuration config = new Configuration(resources.getConfiguration());
                Locale locale = new Locale(language);
                config.setLocale(locale);
                Context localizedContext = context.createConfigurationContext(config);
                Resources localizedResources = localizedContext.getResources();
                
                String localizedString = localizedResources.getString(stringResId, (Object[]) formatArgs);
                if (localizedString != null && !localizedString.isEmpty()) {
                    return localizedString;
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get localized string for language: " + language);
            }
            
            // Fallback to English
            if (!language.equals(LANGUAGE_ENGLISH)) {
                try {
                    Configuration config = new Configuration(resources.getConfiguration());
                    config.setLocale(Locale.ENGLISH);
                    Context englishContext = context.createConfigurationContext(config);
                    Resources englishResources = englishContext.getResources();
                    return englishResources.getString(stringResId, (Object[]) formatArgs);
                } catch (Exception e) {
                    Log.w(TAG, "Could not get English fallback string");
                }
            }
            
            // Final fallback to default
            return resources.getString(stringResId, (Object[]) formatArgs);
        } catch (Exception e) {
            Log.e(TAG, "Error getting localized string: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Check if language is fully supported (has all strings translated)
     */
    public boolean isLanguageFullySupported(String languageCode) {
        // For now, English and Swahili are fully supported
        return LANGUAGE_ENGLISH.equals(languageCode) || LANGUAGE_SWAHILI.equals(languageCode);
    }
    
    /**
     * Get language coverage percentage
     */
    public float getLanguageCoverage(String languageCode) {
        if (isLanguageFullySupported(languageCode)) {
            return 1.0f; // 100% coverage
        }
        
        // Partial support languages
        if (SUPPORTED_LANGUAGES.containsKey(languageCode)) {
            return 0.5f; // 50% coverage (basic strings only)
        }
        
        return 0.0f; // Not supported
    }
    
    /**
     * Initialize language on first launch
     */
    public void initializeOnFirstLaunch() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean firstLaunch = !prefs.contains(KEY_SELECTED_LANGUAGE);
            
            if (firstLaunch) {
                // Auto-detect and set language
                String detectedLang = detectSystemLanguage();
                setLanguage(detectedLang);
                setAutoDetectEnabled(true);
                
                Log.i(TAG, "Language initialized on first launch: " + detectedLang);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing language: " + e.getMessage());
        }
    }
    
    /**
     * Language information class
     */
    public static class LanguageInfo {
        public final String displayName;
        public final String code;
        public final String country;
        public final String nativeName;
        public final String englishName;
        
        public LanguageInfo(String displayName, String code, String country, 
                           String nativeName, String englishName) {
            this.displayName = displayName;
            this.code = code;
            this.country = country;
            this.nativeName = nativeName;
            this.englishName = englishName;
        }
        
        public Locale getLocale() {
            return new Locale(code, country);
        }
    }
}




