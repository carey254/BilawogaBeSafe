package com.example.bilawoga.utils;

import android.content.Context;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * VOICE EMERGENCY DETECTION SYSTEM
 * Listens for help cries and distress calls
 */
public class VoiceEmergencyDetector {
    private static final String TAG = "VoiceEmergencyDetector";
    
    // Emergency keywords and phrases
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
    
    private static final float VOICE_SENSITIVITY = 0.7f;
    private static final int VOICE_DETECTION_TIMEOUT = 10000; // 10 seconds
    
    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private boolean isVoiceDetectionActive = false;
    private long lastVoiceDetection = 0;
    private int voiceEmergencyCount = 0;
    private List<String> voiceDetectionHistory;
    
    public interface VoiceEmergencyListener {
        void onVoiceEmergencyDetected(String detectedWords, float confidence);
        void onVoiceDetectionError(String error);
        void onVoiceDetectionReady();
    }
    
    private final VoiceEmergencyListener listener;
    
    public VoiceEmergencyDetector(Context context, VoiceEmergencyListener listener) {
        this.context = context;
        this.listener = listener;
        this.voiceDetectionHistory = new ArrayList<>();
        
        initializeVoiceDetection();
    }
    
    private void initializeVoiceDetection() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(android.os.Bundle bundle) {
                    Log.d(TAG, "Voice detection ready");
                    listener.onVoiceDetectionReady();
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
            listener.onVoiceDetectionError("Voice detection initialization failed");
        }
    }
    
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
    
    private void activateVoiceEmergency(String speech, float score) {
        Log.w(TAG, "VOICE EMERGENCY ACTIVATED: " + speech + " (confidence: " + score + ")");
    }
    
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
                listener.onVoiceDetectionError("Voice detection restart failed");
            }
        }
    }
    
    public void startDetection() {
        restartVoiceDetection();
        Log.d(TAG, "Voice emergency detection started");
    }
    
    public void stopDetection() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
        }
        Log.d(TAG, "Voice emergency detection stopped");
    }
    
    public void cleanup() {
        stopDetection();
        voiceDetectionHistory.clear();
    }
} 