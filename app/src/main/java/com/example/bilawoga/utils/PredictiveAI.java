package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PREDICTIVE AI THREAT DETECTION
 * Learns user patterns and predicts potential threats
 */
public class PredictiveAI {
    private static final String TAG = "PredictiveAI";
    
    private final Context context;
    private final Map<String, Float> userPatterns;
    private final Map<String, Float> threatIndicators;
    private float currentThreatLevel = 0.0f;
    
    public interface PredictiveAIListener {
        void onThreatPredicted(String threatType, float confidence, String reason);
        void onSafetyRecommendation(String recommendation, float priority);
        void onBehavioralLearning(String pattern, float confidence);
    }
    
    private final PredictiveAIListener listener;
    
    public PredictiveAI(Context context, PredictiveAIListener listener) {
        this.context = context;
        this.listener = listener;
        this.userPatterns = new HashMap<>();
        this.threatIndicators = new HashMap<>();
        
        initializePatterns();
    }
    
    private void initializePatterns() {
        // Initialize user behavior patterns
        userPatterns.put("movement_frequency", 0.5f);
        userPatterns.put("location_consistency", 0.7f);
        userPatterns.put("time_patterns", 0.6f);
        
        // Initialize threat indicators
        threatIndicators.put("unusual_movement", 0.0f);
        threatIndicators.put("dangerous_location", 0.0f);
        threatIndicators.put("abnormal_time", 0.0f);
        
        Log.d(TAG, "Predictive AI initialized");
    }
    
    public void analyzeMovement(float intensity, long timestamp) {
        // Learn normal movement patterns
        float avgIntensity = userPatterns.get("movement_frequency");
        float deviation = Math.abs(intensity - avgIntensity) / avgIntensity;
        
        if (deviation > 0.8f) {
            threatIndicators.put("unusual_movement", 
                Math.min(1.0f, threatIndicators.get("unusual_movement") + 0.2f));
            
            listener.onThreatPredicted("MOVEMENT_THREAT", deviation, 
                "Unusual movement pattern detected");
        }
        
        // Update learned patterns
        userPatterns.put("movement_frequency", (avgIntensity + intensity) / 2);
        listener.onBehavioralLearning("movement_pattern", 1.0f - deviation);
    }
    
    public void analyzeLocation(float latitude, float longitude, long timestamp) {
        // Simplified location analysis
        float locationRisk = calculateLocationRisk(latitude, longitude, timestamp);
        
        if (locationRisk > 0.7f) {
            threatIndicators.put("dangerous_location", locationRisk);
            
            listener.onThreatPredicted("LOCATION_THREAT", locationRisk,
                "Dangerous location detected");
            
            listener.onSafetyRecommendation("Move to a safer location immediately", 0.9f);
        }
    }
    
    public void analyzeTime(long timestamp) {
        long hour = (timestamp / (60 * 60 * 1000)) % 24;
        float timeRisk = calculateTimeRisk(hour);
        
        if (timeRisk > 0.6f) {
            threatIndicators.put("abnormal_time", timeRisk);
            
            listener.onThreatPredicted("TIME_THREAT", timeRisk,
                "Unusual time activity detected");
            
            listener.onSafetyRecommendation("Avoid traveling alone at this time", 0.7f);
        }
    }
    
    private float calculateLocationRisk(float latitude, float longitude, long timestamp) {
        // Simplified location risk calculation
        // In real implementation, this would check against known dangerous areas
        
        float risk = 0.3f; // Base risk
        
        // Higher risk at night
        long hour = (timestamp / (60 * 60 * 1000)) % 24;
        if (hour < 6 || hour > 22) {
            risk += 0.3f;
        }
        
        // Higher risk for unknown locations
        if (latitude == 0.0f && longitude == 0.0f) {
            risk += 0.2f;
        }
        
        return Math.min(1.0f, risk);
    }
    
    private float calculateTimeRisk(long hour) {
        float risk = 0.0f;
        
        // Higher risk at night
        if (hour < 6 || hour > 22) {
            risk += 0.4f;
        }
        
        // Higher risk in early morning
        if (hour >= 1 && hour <= 4) {
            risk += 0.3f;
        }
        
        return Math.min(1.0f, risk);
    }
    
    public float getCurrentThreatLevel() {
        float totalThreat = 0.0f;
        for (float threat : threatIndicators.values()) {
            totalThreat += threat;
        }
        return Math.min(1.0f, totalThreat / threatIndicators.size());
    }
    
    public void resetThreatIndicators() {
        for (String key : threatIndicators.keySet()) {
            threatIndicators.put(key, 0.0f);
        }
    }
} 