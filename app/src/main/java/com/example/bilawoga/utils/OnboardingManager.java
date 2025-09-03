package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Onboarding and tutorial management system
 * Guides new users through app setup and features
 */
public class OnboardingManager {
    private static final String TAG = "OnboardingManager";
    private static final String PREF_NAME = "onboarding_prefs";
    
    // Onboarding step keys
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_WELCOME_SHOWN = "welcome_shown";
    private static final String KEY_PERMISSIONS_EXPLAINED = "permissions_explained";
    private static final String KEY_CONTACTS_SETUP = "contacts_setup";
    private static final String KEY_AI_EXPLAINED = "ai_explained";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_APP_VERSION = "app_version";
    
    private Context context;
    private SharedPreferences prefs;
    
    public OnboardingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
    }
    
    /**
     * Check if user is new and needs onboarding
     */
    public boolean isNewUser() {
        return !prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }
    
    /**
     * Check if this is the first app launch
     */
    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }
    
    /**
     * Mark first launch as completed
     */
    public void markFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        Log.d(TAG, "First launch marked as completed");
    }
    
    /**
     * Check if welcome screen has been shown
     */
    public boolean isWelcomeShown() {
        return prefs.getBoolean(KEY_WELCOME_SHOWN, false);
    }
    
    /**
     * Mark welcome screen as shown
     */
    public void markWelcomeShown() {
        prefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply();
        Log.d(TAG, "Welcome screen marked as shown");
    }
    
    /**
     * Check if permissions have been explained
     */
    public boolean arePermissionsExplained() {
        return prefs.getBoolean(KEY_PERMISSIONS_EXPLAINED, false);
    }
    
    /**
     * Mark permissions as explained
     */
    public void markPermissionsExplained() {
        prefs.edit().putBoolean(KEY_PERMISSIONS_EXPLAINED, true).apply();
        Log.d(TAG, "Permissions explanation marked as shown");
    }
    
    /**
     * Check if contacts setup has been completed
     */
    public boolean isContactsSetupCompleted() {
        return prefs.getBoolean(KEY_CONTACTS_SETUP, false);
    }
    
    /**
     * Mark contacts setup as completed
     */
    public void markContactsSetupCompleted() {
        prefs.edit().putBoolean(KEY_CONTACTS_SETUP, true).apply();
        Log.d(TAG, "Contacts setup marked as completed");
    }
    
    /**
     * Check if AI features have been explained
     */
    public boolean isAIExplained() {
        return prefs.getBoolean(KEY_AI_EXPLAINED, false);
    }
    
    /**
     * Mark AI explanation as shown
     */
    public void markAIExplained() {
        prefs.edit().putBoolean(KEY_AI_EXPLAINED, true).apply();
        Log.d(TAG, "AI explanation marked as shown");
    }
    
    /**
     * Complete entire onboarding process
     */
    public void completeOnboarding() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putBoolean(KEY_WELCOME_SHOWN, true)
            .putBoolean(KEY_PERMISSIONS_EXPLAINED, true)
            .putBoolean(KEY_CONTACTS_SETUP, true)
            .putBoolean(KEY_AI_EXPLAINED, true)
            .apply();
        
        Log.d(TAG, "Onboarding completed");
    }
    
    /**
     * Reset onboarding for testing or re-onboarding
     */
    public void resetOnboarding() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .putBoolean(KEY_WELCOME_SHOWN, false)
            .putBoolean(KEY_PERMISSIONS_EXPLAINED, false)
            .putBoolean(KEY_CONTACTS_SETUP, false)
            .putBoolean(KEY_AI_EXPLAINED, false)
            .apply();
        
        Log.d(TAG, "Onboarding reset");
    }
    
    /**
     * Get onboarding progress percentage
     */
    public int getOnboardingProgress() {
        int totalSteps = 4; // welcome, permissions, contacts, ai
        int completedSteps = 0;
        
        if (isWelcomeShown()) completedSteps++;
        if (arePermissionsExplained()) completedSteps++;
        if (isContactsSetupCompleted()) completedSteps++;
        if (isAIExplained()) completedSteps++;
        
        return (completedSteps * 100) / totalSteps;
    }
    
    /**
     * Get next onboarding step
     */
    public OnboardingStep getNextStep() {
        if (!isWelcomeShown()) {
            return OnboardingStep.WELCOME;
        } else if (!arePermissionsExplained()) {
            return OnboardingStep.PERMISSIONS;
        } else if (!isContactsSetupCompleted()) {
            return OnboardingStep.CONTACTS;
        } else if (!isAIExplained()) {
            return OnboardingStep.AI_EXPLANATION;
        } else {
            return OnboardingStep.COMPLETED;
        }
    }
    
    /**
     * Check if app version has changed (for update notifications)
     */
    public boolean hasAppVersionChanged() {
        String currentVersion = getAppVersion();
        String storedVersion = prefs.getString(KEY_APP_VERSION, "");
        
        if (!storedVersion.equals(currentVersion)) {
            prefs.edit().putString(KEY_APP_VERSION, currentVersion).apply();
            return true;
        }
        return false;
    }
    
    /**
     * Get current app version
     */
    private String getAppVersion() {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0)
                .versionName;
        } catch (Exception e) {
            Log.e(TAG, "Error getting app version: " + e.getMessage());
            return "1.0.0";
        }
    }
    
    /**
     * Onboarding step enumeration
     */
    public enum OnboardingStep {
        WELCOME("Welcome to BilaWoga", "Your personal emergency protection system"),
        PERMISSIONS("Permissions", "BilaWoga needs certain permissions to protect you"),
        CONTACTS("Emergency Contacts", "Set up who to contact in emergencies"),
        AI_EXPLANATION("AI Protection", "Learn how AI keeps you safe"),
        COMPLETED("Setup Complete", "You're all set!");
        
        private String title;
        private String description;
        
        OnboardingStep(String title, String description) {
            this.title = title;
            this.description = description;
        }
        
        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
    
    /**
     * Get onboarding content for each step
     */
    public OnboardingContent getOnboardingContent(OnboardingStep step) {
        switch (step) {
            case WELCOME:
                return new OnboardingContent(
                    "Welcome to BilaWoga",
                    "Your Personal Emergency Protection System",
                    "BilaWoga uses advanced AI to detect emergencies and automatically send help when you need it most.",
                    "🛡️ 24/7 Protection\n🤖 AI-Powered Detection\n📱 Silent Emergency Alerts\n🌍 Works Worldwide"
                );
                
            case PERMISSIONS:
                return new OnboardingContent(
                    "Permissions Required",
                    "Why BilaWoga Needs These Permissions",
                    "To provide emergency protection, BilaWoga needs access to certain features on your device.",
                    "📍 Location - Share your location in emergencies\n📞 SMS - Send emergency messages\n🎤 Microphone - Detect distress sounds\n📱 Phone - Verify emergency contacts"
                );
                
            case CONTACTS:
                return new OnboardingContent(
                    "Emergency Contacts",
                    "Who Should We Contact?",
                    "Set up trusted contacts who will receive emergency alerts when BilaWoga detects a crisis.",
                    "👥 Family members\n👨‍⚕️ Healthcare providers\n👮‍♂️ Emergency services\n📞 Trusted friends"
                );
                
            case AI_EXPLANATION:
                return new OnboardingContent(
                    "AI Protection",
                    "How AI Keeps You Safe",
                    "BilaWoga's AI continuously monitors for signs of distress and emergency situations.",
                    "🎵 Audio Detection - Recognizes cries for help\n📱 Movement Analysis - Detects panic movements\n🤖 Pattern Learning - Learns your normal behavior\n⚡ Instant Response - Sends help immediately"
                );
                
            default:
                return new OnboardingContent("", "", "", "");
        }
    }
    
    /**
     * Onboarding content class
     */
    public static class OnboardingContent {
        private String title;
        private String subtitle;
        private String description;
        private String features;
        
        public OnboardingContent(String title, String subtitle, String description, String features) {
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.features = features;
        }
        
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getDescription() { return description; }
        public String getFeatures() { return features; }
    }
}
