package com.example.bilawoga.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * COVERT EMERGENCY DETECTION SYSTEM
 * Detects subtle distress signals for abduction/coercion scenarios
 */
public class CovertEmergencyDetector implements SensorEventListener {
    private static final String TAG = "CovertEmergencyDetector";
    
    // Detection Modes
    public enum EmergencyMode {
        NORMAL, COVERT_POCKET, SILENT_EMERGENCY, ABDUCTION_ALERT
    }
    
    // Covert Patterns
    private static final int[] COVERT_PATTERN = {1, 2, 1, 3, 1, 2, 1};
    private static final int[] ABDUCTION_PATTERN = {2, 1, 2, 1, 2, 1, 2};
    
    // Sensitivity
    private static final float POCKET_SENSITIVITY = 0.6f;
    private static final int PATTERN_TIMEOUT = 5000;
    
    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private EmergencyMode currentMode = EmergencyMode.NORMAL;
    
    private Queue<MovementData> movementHistory;
    private List<Float> accelerationBuffer;
    private boolean isPhoneInPocket = false;
    private boolean isSilentMode = false;
    private int suspiciousActivityCount = 0;
    
    public interface CovertEmergencyListener {
        void onCovertEmergencyDetected(EmergencyMode mode, float confidence, String pattern);
        void onAbductionAlert(float threatLevel, String indicators);
        void onStealthSOSActivated(String method);
    }
    
    private final CovertEmergencyListener listener;
    
    private static class MovementData {
        float acceleration;
        long timestamp;
        boolean isSignificant;
        
        MovementData(float acc, long time, boolean significant) {
            this.acceleration = acc;
            this.timestamp = time;
            this.isSignificant = significant;
        }
    }
    
    public CovertEmergencyDetector(Context context, CovertEmergencyListener listener) {
        this.context = context;
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.movementHistory = new ConcurrentLinkedQueue<>();
        this.accelerationBuffer = new ArrayList<>();
        
        detectContext();
    }
    
    private void detectContext() {
        // Check silent mode
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int ringerMode = audioManager.getRingerMode();
            isSilentMode = (ringerMode == AudioManager.RINGER_MODE_SILENT);
        }
        
        Log.d(TAG, "Context detected - Silent: " + isSilentMode);
    }
    
    public void startDetection() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }
    
    public void stopDetection() {
        sensorManager.unregisterListener(this);
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            processAccelerometerData(event);
        }
    }
    
    private void processAccelerometerData(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        
        float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
        long timestamp = System.currentTimeMillis();
        
        // Store data
        accelerationBuffer.add(acceleration);
        if (accelerationBuffer.size() > 50) {
            accelerationBuffer.remove(0);
        }
        
        // Detect pocket vs hand
        detectPhonePosition();
        
        // Check for significant movement
        boolean isSignificant = acceleration > POCKET_SENSITIVITY;
        MovementData data = new MovementData(acceleration, timestamp, isSignificant);
        movementHistory.offer(data);
        
        while (movementHistory.size() > 100) {
            movementHistory.poll();
        }
        
        // Analyze patterns
        analyzeCovertPatterns();
    }
    
    private void detectPhonePosition() {
        if (accelerationBuffer.size() > 10) {
            float avgMovement = calculateAverageMovement();
            float variance = calculateVariance();
            
            // Pocket: lower movement, higher variance
            if (avgMovement < 0.5f && variance > 0.3f) {
                isPhoneInPocket = true;
                currentMode = EmergencyMode.COVERT_POCKET;
            } else if (avgMovement > 1.0f && variance < 0.2f) {
                isPhoneInPocket = false;
                currentMode = EmergencyMode.NORMAL;
            }
        }
    }
    
    private void analyzeCovertPatterns() {
        List<MovementData> recent = getRecentSignificantMovements();
        
        if (recent.size() >= 7) {
            // Check covert pattern
            if (matchPattern(recent, COVERT_PATTERN)) {
                float confidence = calculateConfidence(recent);
                if (confidence > 0.7f) {
                    listener.onCovertEmergencyDetected(
                        EmergencyMode.COVERT_POCKET, 
                        confidence, 
                        "Covert SOS Pattern"
                    );
                    activateStealthSOS("Covert Pattern");
                }
            }
            
            // Check abduction pattern
            if (matchPattern(recent, ABDUCTION_PATTERN)) {
                float confidence = calculateConfidence(recent);
                if (confidence > 0.8f) {
                    currentMode = EmergencyMode.ABDUCTION_ALERT;
                    float threatLevel = calculateThreatLevel();
                    String indicators = getThreatIndicators();
                    
                    listener.onAbductionAlert(threatLevel, indicators);
                    activateStealthSOS("Abduction Alert");
                }
            }
        }
    }
    
    private List<MovementData> getRecentSignificantMovements() {
        List<MovementData> significant = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (MovementData data : movementHistory) {
            if (data.isSignificant && (currentTime - data.timestamp) < PATTERN_TIMEOUT) {
                significant.add(data);
            }
        }
        
        return significant;
    }
    
    private boolean matchPattern(List<MovementData> movements, int[] pattern) {
        if (movements.size() < pattern.length) return false;
        
        int matches = 0;
        for (int i = 0; i < pattern.length && i < movements.size(); i++) {
            float intensity = movements.get(i).acceleration;
            if (intensity >= pattern[i] * 0.5f && intensity <= pattern[i] * 1.5f) {
                matches++;
            }
        }
        
        return (float) matches / pattern.length > 0.7f;
    }
    
    private float calculateConfidence(List<MovementData> movements) {
        if (movements.isEmpty()) return 0.0f;
        
        float avgIntensity = 0;
        for (MovementData data : movements) {
            avgIntensity += data.acceleration;
        }
        avgIntensity /= movements.size();
        
        // Higher confidence for consistent movements
        float consistency = calculateConsistency(movements);
        return (avgIntensity * 0.6f + consistency * 0.4f);
    }
    
    private float calculateConsistency(List<MovementData> movements) {
        if (movements.size() < 2) return 0.0f;
        
        float mean = 0;
        for (MovementData data : movements) {
            mean += data.acceleration;
        }
        mean /= movements.size();
        
        float variance = 0;
        for (MovementData data : movements) {
            variance += Math.pow(data.acceleration - mean, 2);
        }
        variance /= movements.size();
        
        return Math.max(0, 1.0f - variance);
    }
    
    private float calculateThreatLevel() {
        float threat = 0.0f;
        if (isPhoneInPocket) threat += 0.3f;
        if (isSilentMode) threat += 0.3f;
        if (suspiciousActivityCount > 0) threat += 0.4f;
        return Math.min(1.0f, threat);
    }
    
    private String getThreatIndicators() {
        List<String> indicators = new ArrayList<>();
        if (isPhoneInPocket) indicators.add("Covert usage");
        if (isSilentMode) indicators.add("Silent mode");
        if (suspiciousActivityCount > 0) indicators.add("Suspicious activity");
        return String.join(", ", indicators);
    }
    
    private void activateStealthSOS(String method) {
        listener.onStealthSOSActivated(method);
        Log.i(TAG, "STEALTH SOS: " + method);
    }
    
    private float calculateAverageMovement() {
        if (accelerationBuffer.isEmpty()) return 0.0f;
        float sum = 0;
        for (float value : accelerationBuffer) sum += value;
        return sum / accelerationBuffer.size();
    }
    
    private float calculateVariance() {
        if (accelerationBuffer.size() < 2) return 0.0f;
        float mean = calculateAverageMovement();
        float variance = 0;
        for (float value : accelerationBuffer) {
            variance += Math.pow(value - mean, 2);
        }
        return variance / accelerationBuffer.size();
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }
    
    public void cleanup() {
        stopDetection();
        movementHistory.clear();
        accelerationBuffer.clear();
    }
} 