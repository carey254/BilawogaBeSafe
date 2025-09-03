package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Analytics and monitoring system for BilaWoga app
 * Tracks usage patterns, emergency activations, and performance metrics
 */
public class AppAnalytics {
    private static final String TAG = "AppAnalytics";
    private static final String PREF_NAME = "app_analytics";
    
    // Analytics Keys
    private static final String KEY_TOTAL_ACTIVATIONS = "total_activations";
    private static final String KEY_SUCCESSFUL_SOS = "successful_sos";
    private static final String KEY_FAILED_SOS = "failed_sos";
    private static final String KEY_AI_DETECTIONS = "ai_detections";
    private static final String KEY_FALSE_POSITIVES = "false_positives";
    private static final String KEY_APP_LAUNCHES = "app_launches";
    private static final String KEY_SERVICE_UPTIME = "service_uptime";
    private static final String KEY_LAST_ACTIVATION = "last_activation";
    private static final String KEY_RESPONSE_TIME = "avg_response_time";
    
    private static AppAnalytics instance;
    private SharedPreferences prefs;
    private Context context;
    
    private AppAnalytics(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
    }
    
    public static synchronized AppAnalytics getInstance(Context context) {
        if (instance == null) {
            instance = new AppAnalytics(context);
        }
        return instance;
    }
    
    /**
     * Track emergency activation
     */
    public void trackEmergencyActivation(String triggerType, boolean success, long responseTime) {
        incrementCounter(KEY_TOTAL_ACTIVATIONS);
        
        if (success) {
            incrementCounter(KEY_SUCCESSFUL_SOS);
        } else {
            incrementCounter(KEY_FAILED_SOS);
        }
        
        // Track response time
        updateAverageResponseTime(responseTime);
        
        // Log activation details
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Log.i(TAG, "Emergency Activation: " + triggerType + " | Success: " + success + 
              " | Response Time: " + responseTime + "ms | Time: " + timestamp);
        
        // Store last activation time
        prefs.edit().putString(KEY_LAST_ACTIVATION, timestamp).apply();
    }
    
    /**
     * Track AI detection events
     */
    public void trackAIDetection(String detectionType, float confidence, boolean wasEmergency) {
        incrementCounter(KEY_AI_DETECTIONS);
        
        if (!wasEmergency) {
            incrementCounter(KEY_FALSE_POSITIVES);
        }
        
        Log.d(TAG, "AI Detection: " + detectionType + " | Confidence: " + confidence + 
              "% | Emergency: " + wasEmergency);
    }
    
    /**
     * Track app launch
     */
    public void trackAppLaunch() {
        incrementCounter(KEY_APP_LAUNCHES);
        Log.d(TAG, "App launched - Total launches: " + getCounter(KEY_APP_LAUNCHES));
    }
    
    /**
     * Track service uptime
     */
    public void trackServiceUptime(long uptimeMinutes) {
        long currentUptime = prefs.getLong(KEY_SERVICE_UPTIME, 0);
        prefs.edit().putLong(KEY_SERVICE_UPTIME, currentUptime + uptimeMinutes).apply();
        
        Log.d(TAG, "Service uptime tracked: " + uptimeMinutes + " minutes | Total: " + 
              (currentUptime + uptimeMinutes) + " minutes");
    }
    
    /**
     * Get comprehensive analytics report
     */
    public Map<String, Object> getAnalyticsReport() {
        Map<String, Object> report = new HashMap<>();
        
        report.put("total_activations", getCounter(KEY_TOTAL_ACTIVATIONS));
        report.put("successful_sos", getCounter(KEY_SUCCESSFUL_SOS));
        report.put("failed_sos", getCounter(KEY_FAILED_SOS));
        report.put("ai_detections", getCounter(KEY_AI_DETECTIONS));
        report.put("false_positives", getCounter(KEY_FALSE_POSITIVES));
        report.put("app_launches", getCounter(KEY_APP_LAUNCHES));
        report.put("service_uptime_hours", prefs.getLong(KEY_SERVICE_UPTIME, 0) / 60.0);
        report.put("last_activation", prefs.getString(KEY_LAST_ACTIVATION, "Never"));
        report.put("avg_response_time_ms", prefs.getLong(KEY_RESPONSE_TIME, 0));
        
        // Calculate success rate
        long total = getCounter(KEY_TOTAL_ACTIVATIONS);
        long successful = getCounter(KEY_SUCCESSFUL_SOS);
        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        report.put("success_rate_percent", successRate);
        
        // Calculate false positive rate
        long aiDetections = getCounter(KEY_AI_DETECTIONS);
        long falsePositives = getCounter(KEY_FALSE_POSITIVES);
        double falsePositiveRate = aiDetections > 0 ? (double) falsePositives / aiDetections * 100 : 0;
        report.put("false_positive_rate_percent", falsePositiveRate);
        
        return report;
    }
    
    /**
     * Reset analytics data
     */
    public void resetAnalytics() {
        prefs.edit().clear().apply();
        Log.i(TAG, "Analytics data reset");
    }
    
    /**
     * Export analytics data for backup
     */
    public String exportAnalyticsData() {
        Map<String, Object> report = getAnalyticsReport();
        StringBuilder export = new StringBuilder();
        export.append("BilaWoga Analytics Report\n");
        export.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        
        for (Map.Entry<String, Object> entry : report.entrySet()) {
            export.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        return export.toString();
    }
    
    // Helper methods
    private void incrementCounter(String key) {
        long current = prefs.getLong(key, 0);
        prefs.edit().putLong(key, current + 1).apply();
    }
    
    private long getCounter(String key) {
        return prefs.getLong(key, 0);
    }
    
    private void updateAverageResponseTime(long newResponseTime) {
        long currentAvg = prefs.getLong(KEY_RESPONSE_TIME, 0);
        long totalActivations = getCounter(KEY_TOTAL_ACTIVATIONS);
        
        if (totalActivations > 0) {
            long newAvg = (currentAvg * (totalActivations - 1) + newResponseTime) / totalActivations;
            prefs.edit().putLong(KEY_RESPONSE_TIME, newAvg).apply();
        } else {
            prefs.edit().putLong(KEY_RESPONSE_TIME, newResponseTime).apply();
        }
    }
}
