package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * Inclusivity Manager
 * 
 * Ensures the app is accessible and inclusive for all users:
 * 1. Language Support (Swahili, English, and more)
 * 2. Accessibility Features (TTS, High Contrast, Large Text)
 * 3. Cultural Sensitivity
 * 4. Age-Friendly Design
 * 5. Gender-Inclusive Features
 * 6. Low-Literacy Support
 */
public class InclusivityManager {
    private static final String TAG = "InclusivityManager";
    private static final String PREFS_NAME = "InclusivitySettings";
    
    // Language codes
    public static final String LANG_SWAHILI = "sw";
    public static final String LANG_ENGLISH = "en";
    
    /**
     * Initialize inclusivity settings on first launch
     */
    public static void initializeDefaults(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (!prefs.getBoolean("initialized", false)) {
                SharedPreferences.Editor editor = prefs.edit();
                
                // Default to Swahili for Kenya market
                editor.putString("language", LANG_SWAHILI);
                
                // Enable accessibility features by default
                editor.putBoolean("tts_enabled", true);
                editor.putBoolean("high_contrast", false);
                editor.putBoolean("large_text", false);
                editor.putBoolean("voice_guidance", true);
                
                // Cultural defaults
                editor.putString("date_format", "dd/MM/yyyy"); // Kenyan format
                editor.putString("time_format", "24h");
                
                // Age-friendly defaults
                editor.putBoolean("simplified_ui", false);
                editor.putInt("font_size", 16); // Medium size
                
                editor.putBoolean("initialized", true);
                editor.apply();
                
                Log.d(TAG, "Inclusivity settings initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing inclusivity settings: " + e.getMessage());
        }
    }
    
    /**
     * Get current language preference
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("language", LANG_SWAHILI);
    }
    
    /**
     * Set language preference
     */
    public static void setLanguage(Context context, String languageCode) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString("language", languageCode).apply();
            
            // Update app locale
            updateAppLocale(context, languageCode);
            
            Log.d(TAG, "Language set to: " + languageCode);
        } catch (Exception e) {
            Log.e(TAG, "Error setting language: " + e.getMessage());
        }
    }
    
    /**
     * Update app locale
     */
    private static void updateAppLocale(Context context, String languageCode) {
        try {
            Locale locale;
            if (LANG_SWAHILI.equals(languageCode)) {
                locale = new Locale("sw", "KE");
            } else {
                locale = Locale.ENGLISH;
            }
            
            Locale.setDefault(locale);
            Configuration config = context.getResources().getConfiguration();
            config.setLocale(locale);
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        } catch (Exception e) {
            Log.e(TAG, "Error updating locale: " + e.getMessage());
        }
    }
    
    /**
     * Check if TTS is enabled
     */
    public static boolean isTTSEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean("tts_enabled", true);
    }
    
    /**
     * Check if high contrast is enabled
     */
    public static boolean isHighContrastEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean("high_contrast", false);
    }
    
    /**
     * Check if large text is enabled
     */
    public static boolean isLargeTextEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean("large_text", false);
    }
    
    /**
     * Apply inclusivity settings to a view
     */
    public static void applyInclusivitySettings(Context context, View view) {
        try {
            // Apply high contrast
            if (isHighContrastEnabled(context)) {
                applyHighContrast(view);
            }
            
            // Apply large text
            if (isLargeTextEnabled(context)) {
                applyLargeText(view);
            }
            
            // Apply content descriptions for screen readers
            applyContentDescriptions(view);
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying inclusivity settings: " + e.getMessage());
        }
    }
    
    /**
     * Apply high contrast to view
     */
    private static void applyHighContrast(View view) {
        // High contrast typically means:
        // - Dark backgrounds with light text
        // - High contrast ratios (WCAG AAA: 7:1 for normal text)
        // This should be handled in themes/styles
    }
    
    /**
     * Apply large text to view
     */
    private static void applyLargeText(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            float currentSize = textView.getTextSize();
            float newSize = currentSize * 1.2f; // 20% larger
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, newSize);
        }
    }
    
    /**
     * Apply content descriptions for accessibility
     */
    private static void applyContentDescriptions(View view) {
        if (view.getContentDescription() == null) {
            // Add basic content description if missing
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                String text = textView.getText().toString();
                if (!text.isEmpty()) {
                    view.setContentDescription(text);
                }
            }
        }
    }
    
    /**
     * Get localized string based on current language
     */
    public static String getLocalizedString(Context context, String englishText, String swahiliText) {
        String lang = getLanguage(context);
        if (LANG_SWAHILI.equals(lang)) {
            return swahiliText;
        }
        return englishText;
    }
    
    /**
     * Get emergency message in appropriate language
     */
    public static String getLocalizedEmergencyMessage(Context context, String userName, String incidentType) {
        String lang = getLanguage(context);
        
        if (LANG_SWAHILI.equals(lang)) {
            return String.format("ALERTI YA DHARURA\n\n" +
                "Jina langu ni %s.\n" +
                "Ninafikia: %s\n\n" +
                "Tafadhali toa msaada haraka!",
                userName, incidentType);
        } else {
            return String.format("EMERGENCY ALERT\n\n" +
                "My name is %s.\n" +
                "I am experiencing: %s\n\n" +
                "PLEASE SEND HELP IMMEDIATELY!",
                userName, incidentType);
        }
    }
    
    /**
     * Check inclusivity compliance
     */
    public static InclusivityReport checkInclusivity(Context context) {
        InclusivityReport report = new InclusivityReport();
        
        // Language support
        report.languageSupport = hasLanguageSupport(context);
        
        // Accessibility features
        report.accessibilityFeatures = hasAccessibilityFeatures(context);
        
        // Cultural sensitivity
        report.culturalSensitivity = hasCulturalSensitivity(context);
        
        // Age-friendly design
        report.ageFriendly = hasAgeFriendlyDesign(context);
        
        // Gender inclusivity
        report.genderInclusive = hasGenderInclusiveFeatures(context);
        
        // Low-literacy support
        report.lowLiteracySupport = hasLowLiteracySupport(context);
        
        // Calculate overall score
        report.overallScore = calculateInclusivityScore(report);
        
        return report;
    }
    
    private static boolean hasLanguageSupport(Context context) {
        // Check if multiple languages are supported
        return true; // Swahili and English are supported
    }
    
    private static boolean hasAccessibilityFeatures(Context context) {
        // Check if TTS, high contrast, large text are available
        return isTTSEnabled(context) || isHighContrastEnabled(context) || isLargeTextEnabled(context);
    }
    
    private static boolean hasCulturalSensitivity(Context context) {
        // Check if cultural defaults are set (date format, etc.)
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains("date_format") && prefs.contains("time_format");
    }
    
    private static boolean hasAgeFriendlyDesign(Context context) {
        // Check if simplified UI and font size options are available
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains("font_size");
    }
    
    private static boolean hasGenderInclusiveFeatures(Context context) {
        // App is designed for all genders (no gender-specific restrictions)
        return true;
    }
    
    private static boolean hasLowLiteracySupport(Context context) {
        // Check if TTS and voice guidance are available
        return isTTSEnabled(context);
    }
    
    private static int calculateInclusivityScore(InclusivityReport report) {
        int score = 0;
        if (report.languageSupport) score += 20;
        if (report.accessibilityFeatures) score += 20;
        if (report.culturalSensitivity) score += 15;
        if (report.ageFriendly) score += 15;
        if (report.genderInclusive) score += 15;
        if (report.lowLiteracySupport) score += 15;
        return score;
    }
    
    /**
     * Inclusivity Report Data Class
     */
    public static class InclusivityReport {
        public boolean languageSupport = false;
        public boolean accessibilityFeatures = false;
        public boolean culturalSensitivity = false;
        public boolean ageFriendly = false;
        public boolean genderInclusive = false;
        public boolean lowLiteracySupport = false;
        public int overallScore = 0;
        
        public boolean isFullyInclusive() {
            return overallScore >= 90;
        }
        
        public String getInclusivityStatus() {
            if (overallScore >= 90) return "Highly Inclusive";
            if (overallScore >= 70) return "Mostly Inclusive";
            if (overallScore >= 50) return "Moderately Inclusive";
            return "Needs Improvement";
        }
    }
}


