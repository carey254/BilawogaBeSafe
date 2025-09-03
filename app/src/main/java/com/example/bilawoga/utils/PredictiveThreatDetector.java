package com.example.bilawoga.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADVANCED AI PREDICTIVE THREAT DETECTION SYSTEM
 * 
 * Features:
 * - Learns user's normal daily patterns
 * - Predicts potential threats before they happen
 * - Behavioral analysis and anomaly detection
 * - Proactive safety recommendations
 * - Multi-dimensional threat assessment
 */
public class PredictiveThreatDetector implements SensorEventListener {
    private static final String TAG = "PredictiveThreatDetector";
    
    // Learning Parameters
    private static final int LEARNING_PERIOD_DAYS = 7;
    private static final int PATTERN_MEMORY_SIZE = 1000;
    private static final float ANOMALY_THRESHOLD = 0.8f;
    private static final float THREAT_PREDICTION_THRESHOLD = 0.7f;
    
    // Behavioral Analysis
    private static final int BEHAVIORAL_WINDOW = 24 * 60 * 60 * 1000; // 24 hours
    private static final int LOCATION_ANALYSIS_RADIUS = 100; // meters
    private static final int TIME_ANALYSIS_WINDOW = 60 * 60 * 1000; // 1 hour
    
    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor gyroscope;
    private final Sensor magnetometer;
    
    // Data Collection
    private Map<String, List<BehavioralData>> behavioralHistory;
    private Map<String, List<LocationData>> locationHistory;
    private Map<String, List<TimePatternData>> timePatterns;
    private List<SensorData> sensorBuffer;
    
    // AI Learning Models
    private Map<String, Float> normalPatterns;
    private Map<String, Float> threatIndicators;
    private Map<String, Float> locationRiskScores;
    private Map<String, Float> timeRiskScores;
    
    // Threat Prediction
    private float currentThreatLevel = 0.0f;
    private String currentThreatType = "NONE";
    private List<String> activePredictions;
    private long lastPredictionUpdate = 0;
    
    public interface PredictiveThreatListener {
        void onThreatPredicted(String threatType, float confidence, String reason);
        void onAnomalyDetected(String anomalyType, float severity, String details);
        void onSafetyRecommendation(String recommendation, float priority);
        void onBehavioralLearning(String pattern, float confidence);
        void onThreatLevelChanged(float newLevel, String factors);
    }
    
    private final PredictiveThreatListener listener;
    
    // Data Structures
    private static class BehavioralData {
        long timestamp;
        float movementIntensity;
        float locationLatitude;
        float locationLongitude;
        String activityType; // "walking", "driving", "stationary", "unknown"
        float confidence;
        
        BehavioralData(long time, float movement, float lat, float lon, String activity, float conf) {
            this.timestamp = time;
            this.movementIntensity = movement;
            this.locationLatitude = lat;
            this.locationLongitude = lon;
            this.activityType = activity;
            this.confidence = conf;
        }
    }
    
    private static class LocationData {
        long timestamp;
        float latitude;
        float longitude;
        float accuracy;
        String locationType; // "home", "work", "shopping", "unknown", "dangerous"
        float riskScore;
        
        LocationData(long time, float lat, float lon, float acc, String type, float risk) {
            this.timestamp = time;
            this.latitude = lat;
            this.longitude = lon;
            this.accuracy = acc;
            this.locationType = type;
            this.riskScore = risk;
        }
    }
    
    private static class TimePatternData {
        long timestamp;
        int hourOfDay;
        int dayOfWeek;
        String activityType;
        float frequency;
        
        TimePatternData(long time, int hour, int day, String activity, float freq) {
            this.timestamp = time;
            this.hourOfDay = hour;
            this.dayOfWeek = day;
            this.activityType = activity;
            this.frequency = freq;
        }
    }
    
    private static class SensorData {
        long timestamp;
        float accelerometerX, accelerometerY, accelerometerZ;
        float gyroscopeX, gyroscopeY, gyroscopeZ;
        float magnetometerX, magnetometerY, magnetometerZ;
        
        SensorData(long time, float ax, float ay, float az, float gx, float gy, float gz, float mx, float my, float mz) {
            this.timestamp = time;
            this.accelerometerX = ax;
            this.accelerometerY = ay;
            this.accelerometerZ = az;
            this.gyroscopeX = gx;
            this.gyroscopeY = gy;
            this.gyroscopeZ = gz;
            this.magnetometerX = mx;
            this.magnetometerY = my;
            this.magnetometerZ = mz;
        }
    }
    
    public PredictiveThreatDetector(Context context, PredictiveThreatListener listener) {
        this.context = context;
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        this.magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        
        // Initialize data structures
        this.behavioralHistory = new ConcurrentHashMap<>();
        this.locationHistory = new ConcurrentHashMap<>();
        this.timePatterns = new ConcurrentHashMap<>();
        this.sensorBuffer = new ArrayList<>();
        
        // Initialize AI models
        this.normalPatterns = new ConcurrentHashMap<>();
        this.threatIndicators = new ConcurrentHashMap<>();
        this.locationRiskScores = new ConcurrentHashMap<>();
        this.timeRiskScores = new ConcurrentHashMap<>();
        this.activePredictions = new ArrayList<>();
        
        initializeLearningModels();
        Log.d(TAG, "Predictive threat detector initialized");
    }
    
    private void initializeLearningModels() {
        // Initialize with default patterns
        normalPatterns.put("movement_frequency", 0.5f);
        normalPatterns.put("location_consistency", 0.7f);
        normalPatterns.put("time_patterns", 0.6f);
        normalPatterns.put("activity_regularity", 0.8f);
        
        // Initialize threat indicators
        threatIndicators.put("unusual_movement", 0.0f);
        threatIndicators.put("dangerous_location", 0.0f);
        threatIndicators.put("abnormal_time", 0.0f);
        threatIndicators.put("behavioral_anomaly", 0.0f);
        
        Log.d(TAG, "AI learning models initialized");
    }
    
    public void startDetection() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
        
        Log.d(TAG, "Predictive threat detection started");
    }
    
    public void stopDetection() {
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Predictive threat detection stopped");
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestamp = System.currentTimeMillis();
        
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                processAccelerometerData(event, timestamp);
                break;
            case Sensor.TYPE_GYROSCOPE:
                processGyroscopeData(event, timestamp);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                processMagnetometerData(event, timestamp);
                break;
        }
        
        // Analyze patterns periodically
        if (timestamp - lastPredictionUpdate > 5000) { // Every 5 seconds
            analyzeBehavioralPatterns();
            predictThreats();
            updateThreatLevel();
            lastPredictionUpdate = timestamp;
        }
    }
    
    private void processAccelerometerData(SensorEvent event, long timestamp) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
        
        // Store in sensor buffer
        if (sensorBuffer.size() > 50) {
            sensorBuffer.remove(0);
        }
        
        // Create behavioral data
        BehavioralData data = new BehavioralData(
            timestamp,
            acceleration,
            0.0f, // Will be updated with location
            0.0f, // Will be updated with location
            classifyActivity(acceleration),
            calculateConfidence(acceleration)
        );
        
        storeBehavioralData("movement", data);
    }
    
    private void processGyroscopeData(SensorEvent event, long timestamp) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        // Analyze rotation patterns for threat detection
        float rotationMagnitude = (float) Math.sqrt(x * x + y * y + z * z);
        
        if (rotationMagnitude > 2.0f) {
            // Unusual rotation detected
            threatIndicators.put("unusual_movement", 
                Math.min(1.0f, threatIndicators.get("unusual_movement") + 0.1f));
        }
    }
    
    private void processMagnetometerData(SensorEvent event, long timestamp) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        // Analyze magnetic field changes for context
        float magneticMagnitude = (float) Math.sqrt(x * x + y * y + z * z);
        
        // Detect if phone is being moved in unusual ways
        if (magneticMagnitude > 100.0f) {
            // Strong magnetic field change - possible threat
            threatIndicators.put("unusual_movement", 
                Math.min(1.0f, threatIndicators.get("unusual_movement") + 0.05f));
        }
    }
    
    private String classifyActivity(float acceleration) {
        if (acceleration < 0.5f) return "stationary";
        else if (acceleration < 1.5f) return "walking";
        else if (acceleration < 3.0f) return "running";
        else return "unknown";
    }
    
    private float calculateConfidence(float acceleration) {
        // Higher confidence for more consistent movements
        return Math.min(1.0f, acceleration / 5.0f);
    }
    
    private void storeBehavioralData(String category, BehavioralData data) {
        if (!behavioralHistory.containsKey(category)) {
            behavioralHistory.put(category, new ArrayList<>());
        }
        
        List<BehavioralData> history = behavioralHistory.get(category);
        history.add(data);
        
        // Keep only recent data
        while (history.size() > PATTERN_MEMORY_SIZE) {
            history.remove(0);
        }
    }
    
    public void updateLocation(float latitude, float longitude, float accuracy) {
        long timestamp = System.currentTimeMillis();
        
        // Classify location type
        String locationType = classifyLocation(latitude, longitude);
        float riskScore = calculateLocationRisk(latitude, longitude, locationType);
        
        LocationData locationData = new LocationData(
            timestamp, latitude, longitude, accuracy, locationType, riskScore
        );
        
        // Store location data
        if (!locationHistory.containsKey(locationType)) {
            locationHistory.put(locationType, new ArrayList<>());
        }
        
        List<LocationData> history = locationHistory.get(locationType);
        history.add(locationData);
        
        // Keep only recent location data
        while (history.size() > 100) {
            history.remove(0);
        }
        
        // Update location risk scores
        locationRiskScores.put(locationType, riskScore);
        
        Log.d(TAG, "Location updated: " + locationType + " (risk: " + riskScore + ")");
    }
    
    private String classifyLocation(float latitude, float longitude) {
        // This would integrate with a location service to classify areas
        // For now, using simplified classification
        
        // Example classification logic
        if (latitude == 0.0f && longitude == 0.0f) {
            return "unknown";
        }
        
        // Check if this is a known safe location (home, work, etc.)
        if (isKnownSafeLocation(latitude, longitude)) {
            return "safe";
        }
        
        // Check if this is a known dangerous area
        if (isKnownDangerousLocation(latitude, longitude)) {
            return "dangerous";
        }
        
        return "unknown";
    }
    
    private boolean isKnownSafeLocation(float latitude, float longitude) {
        // This would check against stored safe locations
        // For now, return false
        return false;
    }
    
    private boolean isKnownDangerousLocation(float latitude, float longitude) {
        // This would check against known dangerous areas
        // For now, return false
        return false;
    }
    
    private float calculateLocationRisk(float latitude, float longitude, String locationType) {
        float risk = 0.0f;
        
        switch (locationType) {
            case "safe":
                risk = 0.1f;
                break;
            case "dangerous":
                risk = 0.9f;
                break;
            case "unknown":
                risk = 0.5f;
                break;
            default:
                risk = 0.3f;
        }
        
        // Add time-based risk factors
        long currentHour = System.currentTimeMillis() / (60 * 60 * 1000) % 24;
        if (currentHour < 6 || currentHour > 22) {
            risk += 0.2f; // Higher risk at night
        }
        
        return Math.min(1.0f, risk);
    }
    
    private void analyzeBehavioralPatterns() {
        // Analyze movement patterns
        analyzeMovementPatterns();
        
        // Analyze location patterns
        analyzeLocationPatterns();
        
        // Analyze time patterns
        analyzeTimePatterns();
        
        // Update normal patterns based on learning
        updateNormalPatterns();
    }
    
    private void analyzeMovementPatterns() {
        if (!behavioralHistory.containsKey("movement")) return;
        
        List<BehavioralData> movements = behavioralHistory.get("movement");
        if (movements.size() < 10) return;
        
        // Calculate average movement intensity
        float totalIntensity = 0;
        for (BehavioralData data : movements) {
            totalIntensity += data.movementIntensity;
        }
        float avgIntensity = totalIntensity / movements.size();
        
        // Check for anomalies
        float currentIntensity = movements.get(movements.size() - 1).movementIntensity;
        float intensityDeviation = Math.abs(currentIntensity - avgIntensity) / avgIntensity;
        
        if (intensityDeviation > ANOMALY_THRESHOLD) {
            listener.onAnomalyDetected("unusual_movement", intensityDeviation, 
                "Movement intensity deviates " + (intensityDeviation * 100) + "% from normal");
        }
        
        // Learn normal patterns
        normalPatterns.put("movement_frequency", avgIntensity);
        listener.onBehavioralLearning("movement_pattern", 1.0f - intensityDeviation);
    }
    
    private void analyzeLocationPatterns() {
        // Analyze location consistency
        float locationConsistency = calculateLocationConsistency();
        normalPatterns.put("location_consistency", locationConsistency);
        
        // Check for unusual locations
        if (locationConsistency < 0.3f) {
            listener.onAnomalyDetected("unusual_location", 1.0f - locationConsistency,
                "User is in an unusual location");
        }
    }
    
    private float calculateLocationConsistency() {
        // Simplified location consistency calculation
        // In a real implementation, this would analyze location patterns over time
        return 0.7f; // Default value
    }
    
    private void analyzeTimePatterns() {
        long currentTime = System.currentTimeMillis();
        int currentHour = (int) (currentTime / (60 * 60 * 1000)) % 24;
        int currentDay = (int) (currentTime / (24 * 60 * 60 * 1000)) % 7;
        
        // Check if current time is unusual for user's patterns
        float timeRisk = calculateTimeRisk(currentHour, currentDay);
        timeRiskScores.put("current_time", timeRisk);
        
        if (timeRisk > 0.7f) {
            listener.onAnomalyDetected("unusual_time", timeRisk,
                "User activity at unusual time: " + currentHour + ":00");
        }
    }
    
    private float calculateTimeRisk(int hour, int day) {
        float risk = 0.0f;
        
        // Higher risk at night
        if (hour < 6 || hour > 22) {
            risk += 0.3f;
        }
        
        // Higher risk on weekends (if user normally works)
        if (day == 0 || day == 6) { // Saturday or Sunday
            risk += 0.2f;
        }
        
        return Math.min(1.0f, risk);
    }
    
    private void updateNormalPatterns() {
        // Update patterns based on recent behavior
        // This is a simplified version - real implementation would use ML algorithms
        
        for (Map.Entry<String, Float> entry : normalPatterns.entrySet()) {
            String pattern = entry.getKey();
            float currentValue = entry.getValue();
            
            // Gradually adjust patterns based on recent observations
            // This simulates learning
            float adjustment = 0.01f; // Small learning rate
            normalPatterns.put(pattern, currentValue + adjustment);
        }
    }
    
    private void predictThreats() {
        float threatScore = 0.0f;
        List<String> threatFactors = new ArrayList<>();
        
        // Movement-based threats
        float movementThreat = threatIndicators.get("unusual_movement");
        if (movementThreat > 0.5f) {
            threatScore += movementThreat * 0.3f;
            threatFactors.add("Unusual movement patterns");
        }
        
        // Location-based threats
        float locationThreat = 0.0f;
        for (float risk : locationRiskScores.values()) {
            locationThreat = Math.max(locationThreat, risk);
        }
        if (locationThreat > 0.7f) {
            threatScore += locationThreat * 0.4f;
            threatFactors.add("Dangerous location detected");
        }
        
        // Time-based threats
        float timeThreat = timeRiskScores.getOrDefault("current_time", 0.0f);
        if (timeThreat > 0.6f) {
            threatScore += timeThreat * 0.2f;
            threatFactors.add("Unusual time activity");
        }
        
        // Behavioral anomalies
        float behavioralThreat = calculateBehavioralAnomalyScore();
        if (behavioralThreat > 0.5f) {
            threatScore += behavioralThreat * 0.3f;
            threatFactors.add("Behavioral anomalies detected");
        }
        
        // Update current threat level
        currentThreatLevel = Math.min(1.0f, threatScore);
        
        // Predict specific threats
        if (currentThreatLevel > THREAT_PREDICTION_THRESHOLD) {
            String threatType = determineThreatType(threatFactors);
            currentThreatType = threatType;
            
            listener.onThreatPredicted(threatType, currentThreatLevel, 
                String.join(", ", threatFactors));
            
            // Provide safety recommendations
            provideSafetyRecommendations(threatType, currentThreatLevel);
        }
    }
    
    private float calculateBehavioralAnomalyScore() {
        float anomalyScore = 0.0f;
        
        // Check for deviations from normal patterns
        for (Map.Entry<String, Float> entry : normalPatterns.entrySet()) {
            String pattern = entry.getKey();
            float normalValue = entry.getValue();
            
            // Calculate current deviation (simplified)
            float currentDeviation = Math.abs(normalValue - 0.5f); // Simplified
            anomalyScore += currentDeviation;
        }
        
        return Math.min(1.0f, anomalyScore / normalPatterns.size());
    }
    
    private String determineThreatType(List<String> factors) {
        if (factors.contains("Dangerous location detected")) {
            return "LOCATION_THREAT";
        } else if (factors.contains("Unusual movement patterns")) {
            return "MOVEMENT_THREAT";
        } else if (factors.contains("Unusual time activity")) {
            return "TIME_THREAT";
        } else if (factors.contains("Behavioral anomalies detected")) {
            return "BEHAVIORAL_THREAT";
        } else {
            return "GENERAL_THREAT";
        }
    }
    
    private void provideSafetyRecommendations(String threatType, float threatLevel) {
        List<String> recommendations = new ArrayList<>();
        
        switch (threatType) {
            case "LOCATION_THREAT":
                recommendations.add("Move to a safer location immediately");
                recommendations.add("Share your location with trusted contacts");
                recommendations.add("Stay in well-lit, populated areas");
                break;
            case "MOVEMENT_THREAT":
                recommendations.add("Check your surroundings");
                recommendations.add("Move to a secure location");
                recommendations.add("Consider activating emergency contacts");
                break;
            case "TIME_THREAT":
                recommendations.add("Avoid traveling alone at this time");
                recommendations.add("Stay in familiar, safe areas");
                recommendations.add("Keep emergency contacts on speed dial");
                break;
            case "BEHAVIORAL_THREAT":
                recommendations.add("Review your recent activities");
                recommendations.add("Consider if you're being followed");
                recommendations.add("Stay alert and aware of surroundings");
                break;
        }
        
        for (String recommendation : recommendations) {
            float priority = threatLevel * 0.8f + 0.2f; // Priority based on threat level
            listener.onSafetyRecommendation(recommendation, priority);
        }
    }
    
    private void updateThreatLevel() {
        float previousLevel = currentThreatLevel;
        
        // Threat level is already calculated in predictThreats()
        // Just notify listener if it changed significantly
        if (Math.abs(currentThreatLevel - previousLevel) > 0.1f) {
            String factors = String.join(", ", activePredictions);
            listener.onThreatLevelChanged(currentThreatLevel, factors);
        }
    }
    
    public float getCurrentThreatLevel() {
        return currentThreatLevel;
    }
    
    public String getCurrentThreatType() {
        return currentThreatType;
    }
    
    public List<String> getActivePredictions() {
        return new ArrayList<>(activePredictions);
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
    
    public void cleanup() {
        stopDetection();
        behavioralHistory.clear();
        locationHistory.clear();
        timePatterns.clear();
        sensorBuffer.clear();
        normalPatterns.clear();
        threatIndicators.clear();
        locationRiskScores.clear();
        timeRiskScores.clear();
        activePredictions.clear();
    }
} 