package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import com.example.bilawoga.utils.AdaptiveVoiceLearningAI;

public class SilentEmergencyAI {
    private static final String TAG = "SilentEmergencyAI";
    private static final String MODEL_FILE_NAME = "sos_audio_model.tflite";
    private final Context context;
    private Interpreter tflite;
    private AudioDispatcher dispatcher;
    private boolean isMonitoring = false;
    private EmergencyListener listener;
    private static final float THRESHOLD = 0.5f; // Emergency probability threshold
    private boolean isModelAvailable = false;
    
    // High confidence detection counter - requires multiple consecutive detections
    private int highConfidenceCount = 0;
    private static final int REQUIRED_CONSECUTIVE_DETECTIONS = 3; // Need 3 consecutive high-confidence detections
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.7f; // 70% minimum confidence

    public interface EmergencyListener {
        void onEmergencyDetected(String type, float confidence);
        void onEmergencyConfirmed(String type);
        void onFalseAlarmPrevented(String reason);
        void onModelUnavailable(String reason); // New callback for model errors
        void onAudioRecorded(byte[] audioData, String emergencyType); // New callback for audio recording
        void onNewVoiceDetected(String voiceId, float similarity); // New callback for voice learning
    }
    
    public SilentEmergencyAI(Context context, EmergencyListener listener) {
        this.context = context;
        this.listener = listener;
        loadModel();
    }
    
    /**
     * Load TensorFlow Lite model with proper error handling
     */
    private void loadModel() {
        try {
            // Check if model file exists
            if (!isModelFileAvailable()) {
                Log.e(TAG, "Model file not found: " + MODEL_FILE_NAME);
                if (listener != null) {
                    listener.onModelUnavailable("Model file not found");
                }
                return;
            }
            
            // Load model file
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE_NAME);
            if (modelBuffer == null) {
                Log.e(TAG, "Failed to load model buffer");
                if (listener != null) {
                    listener.onModelUnavailable("Failed to load model file");
                }
                return;
            }
            
            // Use modern TensorFlow Lite API with options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2); // Use 2 threads for better performance
            options.setUseXNNPACK(true); // Enable XNNPACK for better performance (Android 8.0+)
            
            // Create interpreter with options
            tflite = new Interpreter(modelBuffer, options);
            
            // Validate model input/output shapes
            if (!validateModelShapes()) {
                Log.e(TAG, "Model shape validation failed");
                if (tflite != null) {
                    tflite.close();
                    tflite = null;
                }
                if (listener != null) {
                    listener.onModelUnavailable("Model shape validation failed");
                }
                return;
            }
            
            isModelAvailable = true;
            Log.i(TAG, "TensorFlow Lite model loaded successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load TFLite model: " + e.getMessage(), e);
            isModelAvailable = false;
            if (listener != null) {
                listener.onModelUnavailable("Model loading error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if model file exists in assets
     */
    private boolean isModelFileAvailable() {
        try {
            context.getAssets().openFd(MODEL_FILE_NAME);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Model file not found: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate model input/output shapes
     */
    private boolean validateModelShapes() {
        if (tflite == null) return false;
        
        try {
            // Expected input shape: [1, 40, 431, 1] (batch, height, width, channels)
            // Expected output shape: [1, 1] (batch, probability)
            
            // Get input tensor shape
            int inputTensorCount = tflite.getInputTensorCount();
            if (inputTensorCount != 1) {
                Log.e(TAG, "Expected 1 input tensor, got: " + inputTensorCount);
                return false;
            }
            
            // Get output tensor shape
            int outputTensorCount = tflite.getOutputTensorCount();
            if (outputTensorCount != 1) {
                Log.e(TAG, "Expected 1 output tensor, got: " + outputTensorCount);
                return false;
            }
            
            // Validate input shape
            int[] inputShape = tflite.getInputTensor(0).shape();
            if (inputShape.length != 4 || inputShape[0] != 1 || inputShape[1] != 40 || 
                inputShape[2] != 431 || inputShape[3] != 1) {
                Log.e(TAG, "Invalid input shape. Expected [1, 40, 431, 1], got: " + 
                    java.util.Arrays.toString(inputShape));
                return false;
            }
            
            // Validate output shape
            int[] outputShape = tflite.getOutputTensor(0).shape();
            if (outputShape.length != 2 || outputShape[0] != 1 || outputShape[1] != 1) {
                Log.e(TAG, "Invalid output shape. Expected [1, 1], got: " + 
                    java.util.Arrays.toString(outputShape));
                return false;
            }
            
            Log.d(TAG, "Model shape validation passed");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error validating model shapes: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if model is available and ready
     */
    public boolean isModelAvailable() {
        return isModelAvailable && tflite != null;
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        FileDescriptor fd = context.getAssets().openFd(modelName).getFileDescriptor();
        FileInputStream inputStream = new FileInputStream(fd);
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = context.getAssets().openFd(modelName).getStartOffset();
        long declaredLength = context.getAssets().openFd(modelName).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void startSilentMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Monitoring already active");
            return;
        }
        
        if (!isModelAvailable()) {
            Log.e(TAG, "Cannot start monitoring: Model not available");
            if (listener != null) {
                listener.onModelUnavailable("AI model not available. Please restart the app.");
            }
            return;
        }
        
        if (tflite == null) {
            Log.e(TAG, "Cannot start monitoring: TFLite interpreter is null");
            if (listener != null) {
                listener.onModelUnavailable("AI model interpreter not initialized");
            }
            return;
        }
        
        isMonitoring = true;
        // Remove incorrect Object declaration and use the static method directly
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(16000, 1024, 512);
        MFCC mfcc = new MFCC(1024, 16000, 40, 50, 300, 8000);
        List<float[]> mfccFrames = new ArrayList<>();
        dispatcher.addAudioProcessor(mfcc);
        dispatcher.addAudioProcessor(new be.tarsos.dsp.AudioProcessor() {
            @Override
            public boolean process(be.tarsos.dsp.AudioEvent audioEvent) {
                float[] mfccs = mfcc.getMFCC();
                mfccFrames.add(mfccs.clone());
                if (mfccFrames.size() >= 431) {
                    float[][][][] input = new float[1][40][431][1];
                    for (int t = 0; t < 431; t++) {
                        for (int f = 0; f < 40; f++) {
                            input[0][f][t][0] = mfccFrames.get(t)[f];
                        }
                    }
                    float[][] output = new float[1][1];
                    try {
                        // Run inference with error handling
                    tflite.run(input, output);
                    float emergencyProb = output[0][0];
                        
                        // Clamp probability to [0, 1] range
                        emergencyProb = Math.max(0.0f, Math.min(1.0f, emergencyProb));
                        
                    // AI EMERGENCY DETECTION: Only send if HIGH confidence (70%+) and multiple consecutive detections
                    // This prevents false alarms from background noise or silence
                    float highConfidenceThreshold = 0.7f; // Require 70% confidence minimum
                    
                    if (emergencyProb > highConfidenceThreshold) {
                        // Require multiple consecutive high-confidence detections before sending
                        // This ensures we're detecting actual distress, not just noise
                        incrementHighConfidenceCount();
                        
                        // Only send if we have required consecutive high-confidence detections
                        if (getHighConfidenceCount() >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                            Log.d(TAG, "🚨 AI CONFIRMED: High confidence GBV/distress detected - probability: " + emergencyProb + 
                                      " (consecutive detections: " + getHighConfidenceCount() + "/" + REQUIRED_CONSECUTIVE_DETECTIONS + ")");
                            
                            // Record audio for transmission (continuous recording 1s to 10min)
                            byte[] audioData = getAudioDataFromEvent(audioEvent);
                            
                            // Validate audio actually contains sound (not silence)
                            if (isValidDistressAudio(audioData)) {
                                // Extract voice embedding for learning
                                float[] voiceEmbedding = extractVoiceEmbeddingForLearning(audioData);
                                String voiceId = identifyOrLearnVoice(voiceEmbedding);
                                
                                if (listener != null) {
                                    listener.onEmergencyDetected("AI Detected GBV/Distress", emergencyProb);
                                    listener.onEmergencyConfirmed("AI Detected GBV/Distress");
                                    listener.onAudioRecorded(audioData, "AI Detected GBV/Distress");
                                    listener.onNewVoiceDetected(voiceId, emergencyProb);
                                }
                                
                                // AUTOMATIC SOS DISABLED: Only log detection, do not send automatically
                                // User must manually press "Send Alert" button to send SOS
                                Log.d(TAG, "⚠️ AUTOMATIC SOS DISABLED: Emergency detected but not sending automatically. User must manually send SOS.");
                                // sendEmergencyAlertWithAudio(emergencyProb, audioData); // DISABLED
                                
                                // Reset counter after detection (but don't send)
                                resetHighConfidenceCount();
                            } else {
                                Log.w(TAG, "Audio validation failed - likely silence or noise, not sending SOS");
                                resetHighConfidenceCount();
                                if (listener != null) {
                                    listener.onFalseAlarmPrevented("Audio validation failed - no distress sounds detected");
                                }
                            }
                        } else {
                            Log.d(TAG, "High confidence detection (" + emergencyProb + ") but need more consecutive detections: " + 
                                      getHighConfidenceCount() + "/" + REQUIRED_CONSECUTIVE_DETECTIONS);
                        }
                    } else if (emergencyProb > THRESHOLD) {
                        // Medium confidence - log but don't send yet (below 70% threshold)
                        Log.d(TAG, "Medium confidence detection: " + emergencyProb + " (below 70% threshold, not sending)");
                        resetHighConfidenceCount(); // Reset counter on lower confidence
                    } else {
                        // Low confidence - reset counter and log for debugging
                        resetHighConfidenceCount();
                        if (emergencyProb > 0.3f) {
                            Log.d(TAG, "Low confidence detection: " + emergencyProb + " (threshold: " + THRESHOLD + ") - not sending");
                        }
                    }
                    } catch (Exception e) {
                        Log.e(TAG, "Error during model inference: " + e.getMessage(), e);
                        // Prevent false alarms on inference errors
                        if (listener != null) {
                            listener.onFalseAlarmPrevented("Model inference error: " + e.getMessage());
                        }
                    }
                    mfccFrames.clear();
                }
                return true;
            }
            @Override
            public void processingFinished() {
                // No action needed
            }
        });
        new Thread(dispatcher, "Audio Dispatcher").start();
    }

    public void stopSilentMonitoring() {
        isMonitoring = false;
        if (dispatcher != null) dispatcher.stop();
    }
    
    public void cleanup() {
        stopSilentMonitoring();
        if (tflite != null) {
            try {
                tflite.close();
                tflite = null;
                isModelAvailable = false;
                Log.d(TAG, "TFLite interpreter closed");
            } catch (Exception e) {
                Log.e(TAG, "Error closing TFLite interpreter: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get model file size for diagnostics
     */
    public long getModelFileSize() {
        try {
            android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(MODEL_FILE_NAME);
            long size = afd.getLength();
            afd.close();
            return size;
        } catch (IOException e) {
            Log.e(TAG, "Error getting model file size: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Extract audio data from AudioEvent ByteBuffer
     * Handles both array-backed and direct buffers
     */
    private byte[] getAudioDataFromEvent(be.tarsos.dsp.AudioEvent audioEvent) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(audioEvent.getByteBuffer());
            if (buffer == null) {
                Log.w(TAG, "AudioEvent ByteBuffer is null");
                return new byte[0];
            }
            
            // Check if buffer has an array backing it
            if (buffer.hasArray()) {
                return buffer.array();
            } else {
                // For direct buffers, copy the data
                buffer.rewind(); // Reset position to start
                byte[] audioData = new byte[buffer.remaining()];
                buffer.get(audioData);
                return audioData;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting audio data from event: " + e.getMessage(), e);
            return new byte[0];
        }
    }
    
    /**
     * Send emergency alert with audio recording
     * DISABLED: Automatic SOS sending is disabled - user must manually send
     */
    private void sendEmergencyAlertWithAudio(float confidence, byte[] audioData) {
        // AUTOMATIC SOS DISABLED: This method is disabled to prevent automatic sends
        // User must manually press "Send Alert" button to send SOS
        Log.d(TAG, "⚠️ AUTOMATIC SOS DISABLED: Emergency detected (confidence: " + confidence + 
              ") but not sending automatically. User must manually send SOS.");
        
        // Save audio for potential manual review (but don't send automatically)
        try {
            String audioFilePath = saveAudioToFile(audioData);
            if (audioFilePath != null && !audioFilePath.isEmpty()) {
                Log.d(TAG, "Audio recording saved (not sent): " + audioFilePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving audio recording: " + e.getMessage());
        }
        
        // DO NOT SEND SOS AUTOMATICALLY - User must manually press "Send Alert" button
        /*
        try {
            // Get emergency contacts
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                Log.e(TAG, "Cannot access secure storage for emergency contacts");
                return;
            }
            
            String userName = prefs.getString("USERNAME", "Unknown User");
            String emergencyNumber1 = prefs.getString("ENUM_1", "");
            String emergencyNumber2 = prefs.getString("ENUM_2", "");
            
            // Use EmergencyAudioTransmitter to send audio
            EmergencyAudioTransmitter transmitter = new EmergencyAudioTransmitter(context);
            transmitter.sendEmergencyAudioWithMessage(
                userName,
                "AI Detected Emergency",
                confidence,
                saveAudioToFile(audioData),
                emergencyNumber1,
                emergencyNumber2
            );
            
            Log.i(TAG, "Emergency alert with audio sent to contacts");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending emergency alert with audio: " + e.getMessage(), e);
        }
        */
    }
    
    /**
     * Save audio data to file for transmission
     */
    private String saveAudioToFile(byte[] audioData) {
        try {
            File audioDir = new File(context.getFilesDir(), "emergency_audio");
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
            File audioFile = new File(audioDir, "emergency_" + timestamp + ".raw");
            
            FileOutputStream fos = new FileOutputStream(audioFile);
            fos.write(audioData);
            fos.close();
            
            Log.d(TAG, "Audio saved to file: " + audioFile.getAbsolutePath());
            return audioFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving audio to file: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extract voice embedding for learning
     * ENHANCED: Voice learning capability
     */
    private float[] extractVoiceEmbeddingForLearning(byte[] audioData) {
        try {
            // Convert audio to MFCC features (simplified)
            // In production, use proper MFCC extraction
            float[] embedding = new float[40];
            
            // Simple feature extraction (placeholder)
            // Real implementation would use TarsosDSP MFCC
            for (int i = 0; i < embedding.length && i < audioData.length / 100; i++) {
                embedding[i] = (float) (audioData[i * 100] / 128.0);
            }
            
            return embedding;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting voice embedding: " + e.getMessage());
            return new float[40];
        }
    }
    
    /**
     * Identify or learn voice
     */
    private String identifyOrLearnVoice(float[] voiceEmbedding) {
        try {
            // Use AdaptiveVoiceLearningAI to identify or learn voice
            AdaptiveVoiceLearningAI voiceLearner = new AdaptiveVoiceLearningAI(context);
            
            // Check similarity with known voices
            java.util.Map<String, float[]> knownVoices = getKnownVoiceEmbeddings();
            float maxSimilarity = 0.0f;
            String matchedVoiceId = null;
            
            for (java.util.Map.Entry<String, float[]> entry : knownVoices.entrySet()) {
                float similarity = calculateSimilarity(voiceEmbedding, entry.getValue());
                if (similarity > maxSimilarity && similarity > 0.7f) {
                    maxSimilarity = similarity;
                    matchedVoiceId = entry.getKey();
                }
            }
            
            if (matchedVoiceId != null) {
                Log.d(TAG, "Voice identified: " + matchedVoiceId + " (similarity: " + maxSimilarity + ")");
                return matchedVoiceId;
            } else {
                // New voice - learn it
                String newVoiceId = "voice_" + System.currentTimeMillis();
                voiceLearner.learnVoicePattern(newVoiceId, 0.0f);
                Log.d(TAG, "New voice learned: " + newVoiceId);
                return newVoiceId;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error identifying/learning voice: " + e.getMessage());
            return "unknown_voice";
        }
    }
    
    /**
     * Get known voice embeddings (helper method)
     */
    private java.util.Map<String, float[]> getKnownVoiceEmbeddings() {
        // Load from storage
        try {
            SharedPreferences prefs = context.getSharedPreferences("voice_embeddings", Context.MODE_PRIVATE);
            int count = prefs.getInt("voice_count", 0);
            java.util.Map<String, float[]> voices = new java.util.HashMap<>();
            
            for (int i = 0; i < count; i++) {
                String voiceId = prefs.getString("voice_" + i + "_id", null);
                String embeddingStr = prefs.getString("voice_" + i + "_embedding", null);
                
                if (voiceId != null && embeddingStr != null) {
                    String[] values = embeddingStr.split(",");
                    float[] embedding = new float[values.length];
                    for (int j = 0; j < values.length; j++) {
                        embedding[j] = Float.parseFloat(values[j]);
                    }
                    voices.put(voiceId, embedding);
                }
            }
            
            return voices;
        } catch (Exception e) {
            Log.e(TAG, "Error loading voice embeddings: " + e.getMessage());
            return new java.util.HashMap<>();
        }
    }
    
    /**
     * Calculate cosine similarity
     */
    /**
     * Increment high confidence detection counter
     */
    private void incrementHighConfidenceCount() {
        highConfidenceCount++;
    }
    
    /**
     * Reset high confidence detection counter
     */
    private void resetHighConfidenceCount() {
        highConfidenceCount = 0;
    }
    
    /**
     * Get current high confidence detection count
     */
    private int getHighConfidenceCount() {
        return highConfidenceCount;
    }
    
    /**
     * Validate that audio actually contains distress sounds (not silence or noise)
     */
    private boolean isValidDistressAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "Audio data is null or empty");
            return false;
        }
        
        // Check audio level - must have sufficient volume
        double rms = 0.0;
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i] & 0xFF) | (audioData[i + 1] << 8));
            rms += sample * sample;
        }
        rms = Math.sqrt(rms / (audioData.length / 2));
        
        // Convert to decibels
        double db = 20 * Math.log10(rms / 32767.0);
        
        // Must have minimum volume (not silence) but not too loud (not noise)
        // Distress sounds typically range from 40-80 dB
        if (db < 30 || db > 90) {
            Log.w(TAG, "Audio validation failed - volume out of range: " + db + " dB");
            return false;
        }
        
        Log.d(TAG, "Audio validation passed - volume: " + db + " dB");
        return true;
    }
    
    private float calculateSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            return 0.0f;
        }
        
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        float denominator = (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
        return denominator > 0 ? dotProduct / denominator : 0.0f;
    }
}
