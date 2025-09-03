package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class CrashRecoveryManager {
    private static final String TAG = "CrashRecoveryManager";
    private static final String CRASH_PREFS = "crash_recovery_prefs";
    private static final String KEY_LAST_SOS_TIME = "last_sos_time";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";

    public static void initializeCrashReporting(Context context) {
        try {
            // Enable Crashlytics collection
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
            
            // Set user identifier if available
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs != null) {
                String username = prefs.getString("USERNAME", "Unknown");
                FirebaseCrashlytics.getInstance().setUserId(username);
            }
            
            Log.d(TAG, "Crash reporting initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize crash reporting: " + e.getMessage());
        }
    }

    public static void logEmergencyEvent(Context context, String eventType) {
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.log("Emergency Event: " + eventType);
            crashlytics.setCustomKey("emergency_event_type", eventType);
            crashlytics.setCustomKey("emergency_timestamp", System.currentTimeMillis());
            
            // Log to local storage for recovery
            logToLocalStorage(context, eventType);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to log emergency event: " + e.getMessage());
        }
    }

    public static void logSOSAttempt(Context context, boolean success, String reason) {
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.log("SOS Attempt - Success: " + success + ", Reason: " + reason);
            crashlytics.setCustomKey("sos_success", success);
            crashlytics.setCustomKey("sos_reason", reason);
            
            if (success) {
                updateLastSOSTime(context);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to log SOS attempt: " + e.getMessage());
        }
    }

    public static void handleAppCrash(Context context) {
        try {
            // Increment crash count
            SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            int crashCount = crashPrefs.getInt(KEY_CRASH_COUNT, 0) + 1;
            long currentTime = System.currentTimeMillis();
            
            crashPrefs.edit()
                    .putInt(KEY_CRASH_COUNT, crashCount)
                    .putLong(KEY_LAST_CRASH_TIME, currentTime)
                    .apply();
            
            // Log crash to Firebase
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("crash_count", crashCount);
            crashlytics.setCustomKey("last_crash_time", currentTime);
            
            // Check if this is a critical crash (during emergency)
            long lastSOSTime = getLastSOSTime(context);
            if (currentTime - lastSOSTime < 60000) { // Within 1 minute of SOS
                crashlytics.log("CRITICAL: App crashed during emergency situation");
                crashlytics.setCustomKey("critical_crash", true);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle app crash: " + e.getMessage());
        }
    }

    public static boolean shouldShowRecoveryDialog(Context context) {
        try {
            SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            int crashCount = crashPrefs.getInt(KEY_CRASH_COUNT, 0);
            long lastCrashTime = crashPrefs.getLong(KEY_LAST_CRASH_TIME, 0);
            long currentTime = System.currentTimeMillis();
            
            // Show recovery dialog if multiple crashes in short time
            return crashCount >= 2 && (currentTime - lastCrashTime) < 300000; // 5 minutes
        } catch (Exception e) {
            Log.e(TAG, "Failed to check recovery dialog: " + e.getMessage());
            return false;
        }
    }

    public static void resetCrashCount(Context context) {
        try {
            SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            crashPrefs.edit()
                    .putInt(KEY_CRASH_COUNT, 0)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset crash count: " + e.getMessage());
        }
    }

    private static void logToLocalStorage(Context context, String eventType) {
        try {
            SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            crashPrefs.edit()
                    .putString("last_event_type", eventType)
                    .putLong("last_event_time", currentTime)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to log to local storage: " + e.getMessage());
        }
    }

    private static void updateLastSOSTime(Context context) {
        try {
            SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            crashPrefs.edit()
                    .putLong(KEY_LAST_SOS_TIME, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to update last SOS time: " + e.getMessage());
        }
    }

    private static long getLastSOSTime(Context context) {
        try {
            SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            return crashPrefs.getLong(KEY_LAST_SOS_TIME, 0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get last SOS time: " + e.getMessage());
            return 0;
        }
    }
} 