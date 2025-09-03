package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class AbusePreventionManager {
    private static final String TAG = "AbusePreventionManager";
    private static final String ABUSE_PREFS = "abuse_prevention_prefs";
    
    // Usage limits
    private static final int MAX_SOS_PER_HOUR = 3;
    private static final int MAX_SOS_PER_DAY = 10;
    private static final long HOUR_IN_MS = TimeUnit.HOURS.toMillis(1);
    private static final long DAY_IN_MS = TimeUnit.DAYS.toMillis(1);
    
    // Abuse detection
    private static final int SUSPICIOUS_ACTIVITY_THRESHOLD = 5;
    private static final long SUSPICIOUS_TIME_WINDOW = TimeUnit.MINUTES.toMillis(10);

    public static boolean canSendSOS(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Check hourly limit
            long lastHourSOS = prefs.getLong("last_hour_sos_time", 0);
            int hourlyCount = prefs.getInt("hourly_sos_count", 0);
            
            if (currentTime - lastHourSOS < HOUR_IN_MS) {
                if (hourlyCount >= MAX_SOS_PER_HOUR) {
                    Log.w(TAG, "Hourly SOS limit exceeded");
                    return false;
                }
            } else {
                // Reset hourly count if hour has passed
                hourlyCount = 0;
            }
            
            // Check daily limit
            long lastDaySOS = prefs.getLong("last_day_sos_time", 0);
            int dailyCount = prefs.getInt("daily_sos_count", 0);
            
            if (currentTime - lastDaySOS < DAY_IN_MS) {
                if (dailyCount >= MAX_SOS_PER_DAY) {
                    Log.w(TAG, "Daily SOS limit exceeded");
                    return false;
                }
            } else {
                // Reset daily count if day has passed
                dailyCount = 0;
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking SOS limits: " + e.getMessage());
            return true; // Allow SOS if check fails
        }
    }

    public static void recordSOSAttempt(Context context, boolean success) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Update hourly count
            long lastHourSOS = prefs.getLong("last_hour_sos_time", 0);
            int hourlyCount = prefs.getInt("hourly_sos_count", 0);
            
            if (currentTime - lastHourSOS < HOUR_IN_MS) {
                hourlyCount++;
            } else {
                hourlyCount = 1;
            }
            
            // Update daily count
            long lastDaySOS = prefs.getLong("last_day_sos_time", 0);
            int dailyCount = prefs.getInt("daily_sos_count", 0);
            
            if (currentTime - lastDaySOS < DAY_IN_MS) {
                dailyCount++;
            } else {
                dailyCount = 1;
            }
            
            // Save updated counts
            prefs.edit()
                    .putLong("last_hour_sos_time", currentTime)
                    .putInt("hourly_sos_count", hourlyCount)
                    .putLong("last_day_sos_time", currentTime)
                    .putInt("daily_sos_count", dailyCount)
                    .putLong("last_sos_attempt", currentTime)
                    .putBoolean("last_sos_success", success)
                    .apply();
            
            // Check for suspicious activity
            checkForSuspiciousActivity(context, currentTime);
            
        } catch (Exception e) {
            Log.e(TAG, "Error recording SOS attempt: " + e.getMessage());
        }
    }

    public static void reportAbuse(Context context, String reason) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Record abuse report
            int abuseReports = prefs.getInt("abuse_reports", 0) + 1;
            prefs.edit()
                    .putInt("abuse_reports", abuseReports)
                    .putLong("last_abuse_report", currentTime)
                    .putString("last_abuse_reason", reason)
                    .apply();
            
            Log.w(TAG, "Abuse reported: " + reason + " (Total reports: " + abuseReports + ")");
            
            // If multiple abuse reports, consider blocking
            if (abuseReports >= 3) {
                Log.w(TAG, "Multiple abuse reports detected - considering app restrictions");
                // Could implement app restrictions here
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting abuse: " + e.getMessage());
        }
    }

    private static void checkForSuspiciousActivity(Context context, long currentTime) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            
            // Check for rapid SOS attempts
            long lastSOS = prefs.getLong("last_sos_attempt", 0);
            if (currentTime - lastSOS < SUSPICIOUS_TIME_WINDOW) {
                int rapidAttempts = prefs.getInt("rapid_attempts", 0) + 1;
                prefs.edit().putInt("rapid_attempts", rapidAttempts).apply();
                
                if (rapidAttempts >= SUSPICIOUS_ACTIVITY_THRESHOLD) {
                    Log.w(TAG, "Suspicious activity detected: " + rapidAttempts + " rapid SOS attempts");
                    reportAbuse(context, "Rapid SOS attempts detected");
                }
            } else {
                // Reset rapid attempts counter
                prefs.edit().putInt("rapid_attempts", 0).apply();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking suspicious activity: " + e.getMessage());
        }
    }

    public static String getUsageStats(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            
            int hourlyCount = prefs.getInt("hourly_sos_count", 0);
            int dailyCount = prefs.getInt("daily_sos_count", 0);
            int abuseReports = prefs.getInt("abuse_reports", 0);
            
            return String.format("Hourly: %d/%d, Daily: %d/%d, Abuse Reports: %d", 
                    hourlyCount, MAX_SOS_PER_HOUR, dailyCount, MAX_SOS_PER_DAY, abuseReports);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting usage stats: " + e.getMessage());
            return "Stats unavailable";
        }
    }

    public static void resetUsageStats(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit().clear().apply();
            Log.d(TAG, "Usage stats reset");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting usage stats: " + e.getMessage());
        }
    }
} 