package com.example.bilawoga.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ENHANCED EMERGENCY DETECTION SYSTEM
 * 
 * Features:
 * - False alarm prevention for normal pocket touches
 * - Voice detection for help cries
 * - Context-aware movement analysis
 * - Multi-modal threat detection
 */
public class EnhancedEmergencyDetector implements SensorEventListener {
    private static final String TAG = "EnhancedEmergencyDetector";
    
    // Detection Modes
    public enum EmergencyMode {
        NORMAL, COVERT_POCKET, VOICE_EMERGENCY, ABDUCTION_ALERT, FALSE_ALARM_PREVENTION
    }
    
    // Enhanced Patterns (more specific to prevent false alarms)
    private static final int[] EMERGENCY_SOS_PATTERN = {2, 1, 3, 1, 2, 1, 3, 1, 2}; // 9-step pattern
    private static final int[] COVERT_PATTERN = {1, 2, 1, 3, 1, 2, 1}; // 7-step pattern
    private static final int[] ABDUCTION_PATTERN = {2, 1, 2, 1, 2, 1, 2}; // Alternating
    
    // False Alarm Prevention
    private static final int[] NORMAL_POCKET_TOUCH = {1, 1, 1, 1, 1}; // Consistent light touches
    private static final int[] WALKING_PATTERN = {1, 2, 1, 2, 1, 2, 1}; // Rhythmic walking
    
    // Voice Detection Keywords
    private static final String[] HELP_KEYWORDS = {
        "help", "help me", "emergency", "sos", "save me", "danger",
        "stop", "let me go", "police", "fire", "ambulance", "rescue",
        "attack", "assault", "abduction", "kidnap", "threat", "dangerous",
        "scared", "afraid", "terrified", "panic", "distress", "urgent"
    };
    
    private static final String[] DISTRESS_PHRASES = {
        "someone help", "call police", "call 911", "emergency help",
        "i need help", "please help", "save me", "dangerous situation",
        "being followed", "threatened", "attacked", "assaulted"
    };
    
    // Sensitivity Settings
    private static final float POCKET_SENSITIVITY = 0.6f;
    private static final float VOICE_SENSITIVITY = 0.7f;
    private static final int PATTERN_TIMEOUT = 8000; // 8 seconds for longer patterns
    private static final int VOICE_DETECTION_TIMEOUT = 10000; // 10 seconds
    
    // Context Detection
    private static final float WALKING_THRESHOLD = 0.8f;
    private static final float NORMAL_TOUCH_THRESHOLD = 0.4f;
    private static final int MIN_EMERGENCY_PATTERN_LENGTH = 7;
    private static final int MAX_FALSE_ALARM_PATTERN_LENGTH = 5;
    
    private final Context context;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private EmergencyMode currentMode = EmergencyMode.NORMAL;
    
    // Data Collection
    private Queue<MovementData> movementHistory;
    private List<Float> accelerationBuffer;
    private List<Float> walkingPatternBuffer;
    private List<String> voiceDetectionHistory;
    
    // State Tracking
    private boolean isPhoneInPocket = false;
    private boolean isSilentMode = false;
    private boolean isWalking = false;
    private boolean isNormalPocketActivity = false;
    private int suspiciousActivityCount = 0;
    private int falseAlarmCount = 0;
    private long lastNormalActivity = 0;
    
    // Voice Detection
    private SpeechRecognizer speechRecognizer;
    private boolean isVoiceDetectionActive = false;
    private long lastVoiceDetection = 0;
    private int voiceEmergencyCount = 0;
    
    public interface EnhancedEmergencyListener {
        void onEmergencyDetected(EmergencyMode mode, float confidence, String pattern);
        void onVoiceEmergencyDetected(String detectedWords, float confidence);
        void onFalseAlarmPrevented(String reason, String pattern);
        void onAbductionAlert(float threatLevel, String indicators);
        void onStealthSOSActivated(String method);
        void onContextChanged(String context);
    }
    
    private final EnhancedEmergencyListener listener;
    
    private static class MovementData {
        float acceleration;
        long timestamp;
        boolean isSignificant;
        String context; // "walking", "pocket_touch", "emergency", "normal"
        
        MovementData(float acc, long time, boolean significant, String ctx) {
            this.acceleration = acc;
            this.timestamp = time;
            this.isSignificant = significant;
            this.context = ctx;
        }
    }
    
    public EnhancedEmergencyDetector(Context context, EnhancedEmergencyListener listener) {
        this.context = context;
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.movementHistory = new ConcurrentLinkedQueue<>();
        this.accelerationBuffer = new ArrayList<>();
        this.walkingPatternBuffer = new ArrayList<>();
        this.voiceDetectionHistory = new ArrayList<>();
        
        initializeVoiceDetection();
        detectContext();
    }
    
    /**
     * Initialize voice detection for help cries
     */
    private void initializeVoiceDetection() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(android.os.Bundle bundle) {
                    Log.d(TAG, "Voice detection ready");
                }
                
                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "Voice detection started");
                }
                
                @Override
                public void onRmsChanged(float v) {
                    // Monitor voice intensity
                }
                
                @Override
                public void onBufferReceived(byte[] bytes) {
                    // Process audio buffer
                }
                
                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "Voice detection ended");
                }
                
                @Override
                public void onError(int error) {
                    Log.w(TAG, "Voice detection error: " + error);
                    // Restart voice detection after error
                    restartVoiceDetection();
                }
                
                @Override
                public void onResults(android.os.Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        processVoiceInput(matches.get(0));
                    }
                    restartVoiceDetection();
                }
                
                @Override
                public void onPartialResults(android.os.Bundle bundle) {
                    // Handle partial results
                }
                
                @Override
                public void onEvent(int i, android.os.Bundle bundle) {
                    // Handle events
                }
            });
            
            Log.d(TAG, "Voice detection initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing voice detection: " + e.getMessage());
        }
    }
    
    /**
     * Process voice input for emergency keywords
     */
    private void processVoiceInput(String speech) {
        if (speech == null || speech.trim().isEmpty()) {
            return;
        }
        
        String lowerSpeech = speech.toLowerCase().trim();
        voiceDetectionHistory.add(lowerSpeech);
        
        // Keep only recent voice history
        if (voiceDetectionHistory.size() > 10) {
            voiceDetectionHistory.remove(0);
        }
        
        // Check for emergency keywords
        float emergencyScore = calculateEmergencyVoiceScore(lowerSpeech);
        
        if (emergencyScore > VOICE_SENSITIVITY) {
            voiceEmergencyCount++;
            Log.w(TAG, "VOICE EMERGENCY DETECTED: " + lowerSpeech + " (score: " + emergencyScore + ")");
            
            listener.onVoiceEmergencyDetected(lowerSpeech, emergencyScore);
            
            // Activate emergency response
            if (voiceEmergencyCount >= 2) { // Require multiple detections
                activateVoiceEmergency(lowerSpeech, emergencyScore);
            }
        } else {
            // Reset counter if no emergency detected
            voiceEmergencyCount = Math.max(0, voiceEmergencyCount - 1);
        }
    }
    
    /**
     * Calculate emergency score for voice input
     */
    private float calculateEmergencyVoiceScore(String speech) {
        float score = 0.0f;
        
        // Check for help keywords
        for (String keyword : HELP_KEYWORDS) {
            if (speech.contains(keyword)) {
                score += 0.3f;
            }
        }
        
        // Check for distress phrases
        for (String phrase : DISTRESS_PHRASES) {
            if (speech.contains(phrase)) {
                score += 0.5f;
            }
        }
        
        // Check for urgency indicators
        if (speech.contains("now") || speech.contains("immediately") || speech.contains("urgent")) {
            score += 0.2f;
        }
        
        // Check for repetition (multiple help cries)
        int helpCount = 0;
        for (String history : voiceDetectionHistory) {
            for (String keyword : HELP_KEYWORDS) {
                if (history.contains(keyword)) {
                    helpCount++;
                }
            }
        }
        
        if (helpCount > 1) {
            score += 0.3f;
        }
        
        return Math.min(1.0f, score);
    }
    
    /**
     * Activate voice emergency response
     */
    private void activateVoiceEmergency(String speech, float score) {
        currentMode = EmergencyMode.VOICE_EMERGENCY;
        
        // Send emergency alert
        listener.onEmergencyDetected(
            EmergencyMode.VOICE_EMERGENCY,
            score,
            "Voice Emergency: " + speech
        );
        
        // Activate stealth SOS
        activateStealthSOS("Voice Emergency Detection");
        
        Log.w(TAG, "VOICE EMERGENCY ACTIVATED: " + speech);
    }
    
    /**
     * Restart voice detection
     */
    private void restartVoiceDetection() {
        if (speechRecognizer != null && !isVoiceDetectionActive) {
            try {
                android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                
                speechRecognizer.startListening(intent);
                isVoiceDetectionActive = true;
                lastVoiceDetection = System.currentTimeMillis();
                
            } catch (Exception e) {
                Log.e(TAG, "Error restarting voice detection: " + e.getMessage());
            }
        }
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
        
        // Start voice detection
        restartVoiceDetection();
        
        Log.d(TAG, "Enhanced emergency detection started");
    }
    
    public void stopDetection() {
        sensorManager.unregisterListener(this);
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
        }
        
        Log.d(TAG, "Enhanced emergency detection stopped");
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
        
        // Detect context
        detectPhonePosition();
        detectWalkingPattern();
        
        // Determine movement context
        String movementContext = determineMovementContext(acceleration);
        
        // Check for significant movement
        boolean isSignificant = acceleration > POCKET_SENSITIVITY;
        MovementData data = new MovementData(acceleration, timestamp, isSignificant, movementContext);
        movementHistory.offer(data);
        
        while (movementHistory.size() > 100) {
            movementHistory.poll();
        }
        
        // Analyze patterns with false alarm prevention
        analyzeEnhancedPatterns();
    }
    
    /**
     * Determine the context of the movement
     */
    private String determineMovementContext(float acceleration) {
        if (isWalking && acceleration > WALKING_THRESHOLD) {
            return "walking";
        } else if (isPhoneInPocket && acceleration < NORMAL_TOUCH_THRESHOLD) {
            return "normal_pocket_touch";
        } else if (acceleration > POCKET_SENSITIVITY) {
            return "significant_movement";
        } else {
            return "normal";
        }
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
    
    private void detectWalkingPattern() {
        if (accelerationBuffer.size() > 20) {
            // Analyze for rhythmic walking pattern
            float walkingScore = calculateWalkingScore();
            
            if (walkingScore > 0.7f) {
                isWalking = true;
                isNormalPocketActivity = true; // Walking often involves pocket touches
            } else {
                isWalking = false;
            }
        }
    }
    
    private float calculateWalkingScore() {
        if (accelerationBuffer.size() < 10) return 0.0f;
        
        int rhythmicCount = 0;
        for (int i = 1; i < accelerationBuffer.size() - 1; i++) {
            float prev = accelerationBuffer.get(i - 1);
            float curr = accelerationBuffer.get(i);
            float next = accelerationBuffer.get(i + 1);
            
            // Check for rhythmic pattern (up-down-up)
            if (curr > prev && curr > next && curr > 0.8f) {
                rhythmicCount++;
            }
        }
        
        return (float) rhythmicCount / accelerationBuffer.size();
    }
    
    private void analyzeEnhancedPatterns() {
        List<MovementData> recent = getRecentSignificantMovements();
        
        if (recent.size() >= MIN_EMERGENCY_PATTERN_LENGTH) {
            // First, check for false alarm patterns
            if (checkFalseAlarmPatterns(recent)) {
                return; // Prevent false alarm
            }
            
            // Then check for emergency patterns
            checkEmergencyPatterns(recent);
        }
    }
    
    /**
     * Check for false alarm patterns first
     */
    private boolean checkFalseAlarmPatterns(List<MovementData> movements) {
        // Check for normal pocket touch pattern
        if (matchPattern(movements, NORMAL_POCKET_TOUCH)) {
            String reason = "Normal pocket touch pattern detected";
            listener.onFalseAlarmPrevented(reason, "Normal Pocket Touch");
            falseAlarmCount++;
            Log.d(TAG, "False alarm prevented: " + reason);
            return true;
        }
        
        // Check for walking pattern
        if (matchPattern(movements, WALKING_PATTERN) && isWalking) {
            String reason = "Walking movement pattern detected";
            listener.onFalseAlarmPrevented(reason, "Walking Pattern");
            falseAlarmCount++;
            Log.d(TAG, "False alarm prevented: " + reason);
            return true;
        }
        
        // Check for consistent light touches (normal pocket activity)
        if (isConsistentLightTouches(movements)) {
            String reason = "Consistent light touches (normal pocket activity)";
            listener.onFalseAlarmPrevented(reason, "Light Touches");
            falseAlarmCount++;
            Log.d(TAG, "False alarm prevented: " + reason);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if movements are consistent light touches (normal pocket activity)
     */
    private boolean isConsistentLightTouches(List<MovementData> movements) {
        if (movements.size() < 5) return false;
        
        int lightTouchCount = 0;
        float totalIntensity = 0;
        
        for (MovementData data : movements) {
            totalIntensity += data.acceleration;
            if (data.acceleration < NORMAL_TOUCH_THRESHOLD) {
                lightTouchCount++;
            }
        }
        
        float avgIntensity = totalIntensity / movements.size();
        float lightTouchRatio = (float) lightTouchCount / movements.size();
        
        // Normal pocket activity: mostly light touches with low average intensity
        return lightTouchRatio > 0.7f && avgIntensity < 0.5f;
    }
    
    private void checkEmergencyPatterns(List<MovementData> movements) {
        // Check emergency SOS pattern (longer, more specific)
        if (matchPattern(movements, EMERGENCY_SOS_PATTERN)) {
            float confidence = calculateConfidence(movements);
            if (confidence > 0.8f) {
                listener.onEmergencyDetected(
                    EmergencyMode.COVERT_POCKET,
                    confidence,
                    "Emergency SOS Pattern"
                );
                activateStealthSOS("Emergency SOS Pattern");
            }
        }
        
        // Check covert pattern
        if (matchPattern(movements, COVERT_PATTERN)) {
            float confidence = calculateConfidence(movements);
            if (confidence > 0.7f) {
                listener.onEmergencyDetected(
                    EmergencyMode.COVERT_POCKET,
                    confidence,
                    "Covert SOS Pattern"
                );
                activateStealthSOS("Covert Pattern");
            }
        }
        
        // Check abduction pattern
        if (matchPattern(movements, ABDUCTION_PATTERN)) {
            float confidence = calculateConfidence(movements);
            if (confidence > 0.8f) {
                currentMode = EmergencyMode.ABDUCTION_ALERT;
                float threatLevel = calculateThreatLevel();
                String indicators = getThreatIndicators();
                
                listener.onAbductionAlert(threatLevel, indicators);
                activateStealthSOS("Abduction Alert");
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
        
        // Penalize if it looks like normal activity
        float normalActivityPenalty = 0.0f;
        if (isNormalPocketActivity || isWalking) {
            normalActivityPenalty = 0.2f;
        }
        
        return Math.max(0.0f, (avgIntensity * 0.6f + consistency * 0.4f) - normalActivityPenalty);
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
        if (voiceEmergencyCount > 0) threat += 0.5f; // Voice emergency is high threat
        return Math.min(1.0f, threat);
    }
    
    private String getThreatIndicators() {
        List<String> indicators = new ArrayList<>();
        if (isPhoneInPocket) indicators.add("Covert usage");
        if (isSilentMode) indicators.add("Silent mode");
        if (suspiciousActivityCount > 0) indicators.add("Suspicious activity");
        if (voiceEmergencyCount > 0) indicators.add("Voice emergency detected");
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
        walkingPatternBuffer.clear();
        voiceDetectionHistory.clear();
    }
} 