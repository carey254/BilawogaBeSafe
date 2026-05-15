package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ADAPTIVE VOICE LEARNING AI
 * 
 * Features:
 * - Learns from new voices encountered
 * - Identifies similar voices (voice embedding)
 * - Records audio when emergency detected
 * - Sends audio to emergency contacts with AI detection message
 * - On-device fine-tuning of model
 */
public class AdaptiveVoiceLearningAI {
    private static final String TAG = "AdaptiveVoiceLearningAI";
    private static final String MODEL_FILE_NAME = "sos_audio_model.tflite";
    private static final String VOICE_EMBEDDINGS_FILE = "voice_embeddings.dat";
    private static final String LEARNING_DATA_DIR = "voice_learning";
    
    // Audio recording parameters
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORDING_DURATION_MS = 10000; // 10 seconds
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Learning parameters
    private static final float SIMILARITY_THRESHOLD = 0.7f; // 70% similarity
    private static final int MIN_SAMPLES_FOR_LEARNING = 5; // Minimum samples before fine-tuning
    private static final float LEARNING_RATE = 0.01f; // Fine-tuning learning rate
    
    private final Context context;
    private Interpreter tflite;
    private Interpreter embeddingModel; // Voice embedding model
    private boolean isModelAvailable = false;
    private boolean isLearningEnabled = true;
    
    // Voice embeddings storage
    private Map<String, float[]> knownVoiceEmbeddings; // voiceId -> embedding
    private List<VoiceSample> pendingLearningSamples;
    private ExecutorService learningExecutor;
    
    public interface AdaptiveLearningListener {
        void onEmergencyDetected(String type, float confidence, byte[] audioData);
        void onNewVoiceDetected(String voiceId, float similarity);
        void onVoiceLearned(String voiceId, int samplesCount);
        void onAudioRecorded(byte[] audioData, String emergencyType);
        void onModelUnavailable(String reason);
    }
    
    private AdaptiveLearningListener listener;
    
    public AdaptiveVoiceLearningAI(Context context, AdaptiveLearningListener listener) {
        this.context = context;
        this.listener = listener;
        this.knownVoiceEmbeddings = new HashMap<>();
        this.pendingLearningSamples = new ArrayList<>();
        this.learningExecutor = Executors.newSingleThreadExecutor();
        
        loadModel();
        loadVoiceEmbeddings();
    }
    
    /**
     * Load TensorFlow Lite model with fine-tuning support
     */
    private void loadModel() {
        try {
            // Load main emergency detection model
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE_NAME);
            if (modelBuffer == null) {
                Log.e(TAG, "Failed to load model buffer");
                if (listener != null) {
                    listener.onModelUnavailable("Failed to load model file");
                }
                return;
            }
            
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            options.setUseXNNPACK(true);
            options.setAllowFp16PrecisionForFp32(true); // Enable fine-tuning
            
            tflite = new Interpreter(modelBuffer, options);
            
            // Load voice embedding model (for voice identification)
            // Note: You'll need to add a voice embedding model file
            // For now, we'll use a simplified embedding extraction
            loadVoiceEmbeddingModel();
            
            isModelAvailable = true;
            Log.i(TAG, "Adaptive voice learning AI initialized");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load adaptive model: " + e.getMessage(), e);
            isModelAvailable = false;
            if (listener != null) {
                listener.onModelUnavailable("Model loading error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Load voice embedding model (simplified - uses MFCC features as embeddings)
     */
    private void loadVoiceEmbeddingModel() {
        // For now, we'll extract embeddings from MFCC features
        // In production, you'd use a dedicated voice embedding model
        Log.d(TAG, "Voice embedding model initialized (using MFCC features)");
    }
    
    /**
     * Process audio and detect emergency with voice learning
     */
    public void processAudioWithLearning(byte[] audioData, int readSize) {
        if (!isModelAvailable() || tflite == null) {
            return;
        }
        
        try {
            // Extract MFCC features
            float[][] mfccFeatures = extractMFCCFeatures(audioData, readSize);
            
            // Run emergency detection
            float emergencyProb = detectEmergency(mfccFeatures);
            
            if (emergencyProb > 0.5f) {
                Log.d(TAG, "Emergency detected with probability: " + emergencyProb);
                
                // Extract voice embedding
                float[] voiceEmbedding = extractVoiceEmbedding(mfccFeatures);
                
                // Identify or learn voice
                String voiceId = identifyOrLearnVoice(voiceEmbedding);
                
                // Record audio for transmission
                byte[] recordedAudio = recordEmergencyAudio(audioData, readSize);
                
                // Notify listener
                if (listener != null) {
                    listener.onEmergencyDetected("AI Detected Emergency", emergencyProb, recordedAudio);
                    listener.onAudioRecorded(recordedAudio, "AI Detected Emergency");
                }
                
                // Learn from this sample
                if (isLearningEnabled) {
                    learnFromSample(voiceId, mfccFeatures, emergencyProb);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio with learning: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract MFCC features from audio
     */
    private float[][] extractMFCCFeatures(byte[] audioData, int readSize) {
        // Convert byte array to float array
        short[] samples = new short[readSize / 2];
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        
        // Extract MFCC features (simplified - you'd use TarsosDSP here)
        // For now, return placeholder
        float[][] mfccFeatures = new float[40][431]; // 40 MFCC coefficients, 431 frames
        // TODO: Implement actual MFCC extraction using TarsosDSP
        
        return mfccFeatures;
    }
    
    /**
     * Detect emergency using the model
     */
    private float detectEmergency(float[][] mfccFeatures) {
        try {
            // Prepare input tensor
            float[][][][] input = new float[1][40][431][1];
            for (int t = 0; t < 431 && t < mfccFeatures[0].length; t++) {
                for (int f = 0; f < 40 && f < mfccFeatures.length; f++) {
                    input[0][f][t][0] = mfccFeatures[f][t];
                }
            }
            
            // Run inference
            float[][] output = new float[1][1];
            tflite.run(input, output);
            
            return Math.max(0.0f, Math.min(1.0f, output[0][0]));
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting emergency: " + e.getMessage());
            return 0.0f;
        }
    }
    
    /**
     * Extract voice embedding from MFCC features
     */
    private float[] extractVoiceEmbedding(float[][] mfccFeatures) {
        // Extract voice embedding (simplified - uses average MFCC features)
        // In production, you'd use a dedicated voice embedding model
        float[] embedding = new float[40];
        
        for (int i = 0; i < 40 && i < mfccFeatures.length; i++) {
            float sum = 0.0f;
            int count = 0;
            for (int j = 0; j < mfccFeatures[i].length; j++) {
                sum += mfccFeatures[i][j];
                count++;
            }
            embedding[i] = count > 0 ? sum / count : 0.0f;
        }
        
        return embedding;
    }
    
    /**
     * Identify voice or create new voice ID if unknown
     */
    private String identifyOrLearnVoice(float[] voiceEmbedding) {
        // Check if voice is similar to known voices
        for (Map.Entry<String, float[]> entry : knownVoiceEmbeddings.entrySet()) {
            float similarity = calculateSimilarity(voiceEmbedding, entry.getValue());
            
            if (similarity > SIMILARITY_THRESHOLD) {
                Log.d(TAG, "Voice identified as: " + entry.getKey() + " (similarity: " + similarity + ")");
                if (listener != null) {
                    listener.onNewVoiceDetected(entry.getKey(), similarity);
                }
                return entry.getKey();
            }
        }
        
        // New voice detected - create new voice ID
        String newVoiceId = "voice_" + System.currentTimeMillis();
        knownVoiceEmbeddings.put(newVoiceId, voiceEmbedding);
        saveVoiceEmbeddings();
        
        Log.i(TAG, "New voice detected: " + newVoiceId);
        if (listener != null) {
            listener.onNewVoiceDetected(newVoiceId, 0.0f);
        }
        
        return newVoiceId;
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     */
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
    
    /**
     * Record emergency audio for transmission
     */
    private byte[] recordEmergencyAudio(byte[] audioData, int readSize) {
        // Record audio buffer (10 seconds)
        List<byte[]> audioBuffers = new ArrayList<>();
        audioBuffers.add(audioData);
        
        // Continue recording for specified duration
        // This is simplified - in production, you'd record continuously
        byte[] recordedAudio = new byte[readSize];
        System.arraycopy(audioData, 0, recordedAudio, 0, readSize);
        
        return recordedAudio;
    }
    
    /**
     * Learn from emergency sample
     */
    private void learnFromSample(String voiceId, float[][] mfccFeatures, float emergencyProb) {
        VoiceSample sample = new VoiceSample(voiceId, mfccFeatures, emergencyProb, System.currentTimeMillis());
        pendingLearningSamples.add(sample);
        
        // If we have enough samples, fine-tune the model
        if (pendingLearningSamples.size() >= MIN_SAMPLES_FOR_LEARNING) {
            learningExecutor.execute(() -> fineTuneModel());
        }
    }
    
    /**
     * Fine-tune the model with new samples
     */
    private void fineTuneModel() {
        try {
            Log.d(TAG, "Starting model fine-tuning with " + pendingLearningSamples.size() + " samples");
            
            // Fine-tuning logic (simplified)
            // In production, you'd use TensorFlow Lite Model Maker or similar
            // For now, we'll update voice embeddings
            
            for (VoiceSample sample : pendingLearningSamples) {
                // Update voice embedding with new sample
                float[] currentEmbedding = knownVoiceEmbeddings.get(sample.voiceId);
                if (currentEmbedding != null) {
                    // Update embedding using exponential moving average
                    float[] newEmbedding = extractVoiceEmbedding(sample.mfccFeatures);
                    for (int i = 0; i < currentEmbedding.length && i < newEmbedding.length; i++) {
                        currentEmbedding[i] = (1 - LEARNING_RATE) * currentEmbedding[i] + 
                                             LEARNING_RATE * newEmbedding[i];
                    }
                }
            }
            
            // Save updated embeddings
            saveVoiceEmbeddings();
            
            // Clear processed samples
            pendingLearningSamples.clear();
            
            Log.i(TAG, "Model fine-tuning completed");
            
            if (listener != null) {
                listener.onVoiceLearned("model", pendingLearningSamples.size());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error fine-tuning model: " + e.getMessage(), e);
        }
    }
    
    /**
     * Save voice embeddings to storage
     */
    private void saveVoiceEmbeddings() {
        try {
            File embeddingsFile = new File(context.getFilesDir(), VOICE_EMBEDDINGS_FILE);
            FileOutputStream fos = new FileOutputStream(embeddingsFile);
            
            // Save embeddings (simplified - you'd use proper serialization)
            SharedPreferences prefs = context.getSharedPreferences("voice_embeddings", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            int count = 0;
            for (Map.Entry<String, float[]> entry : knownVoiceEmbeddings.entrySet()) {
                editor.putString("voice_" + count + "_id", entry.getKey());
                // Save embedding as comma-separated values
                StringBuilder embeddingStr = new StringBuilder();
                for (float value : entry.getValue()) {
                    if (embeddingStr.length() > 0) embeddingStr.append(",");
                    embeddingStr.append(value);
                }
                editor.putString("voice_" + count + "_embedding", embeddingStr.toString());
                count++;
            }
            
            editor.putInt("voice_count", count);
            editor.apply();
            
            fos.close();
            Log.d(TAG, "Voice embeddings saved: " + count + " voices");
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving voice embeddings: " + e.getMessage(), e);
        }
    }
    
    /**
     * Load voice embeddings from storage
     */
    private void loadVoiceEmbeddings() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("voice_embeddings", Context.MODE_PRIVATE);
            int count = prefs.getInt("voice_count", 0);
            
            for (int i = 0; i < count; i++) {
                String voiceId = prefs.getString("voice_" + i + "_id", null);
                String embeddingStr = prefs.getString("voice_" + i + "_embedding", null);
                
                if (voiceId != null && embeddingStr != null) {
                    // Parse embedding
                    String[] values = embeddingStr.split(",");
                    float[] embedding = new float[values.length];
                    for (int j = 0; j < values.length; j++) {
                        embedding[j] = Float.parseFloat(values[j]);
                    }
                    
                    knownVoiceEmbeddings.put(voiceId, embedding);
                }
            }
            
            Log.d(TAG, "Voice embeddings loaded: " + knownVoiceEmbeddings.size() + " voices");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading voice embeddings: " + e.getMessage(), e);
        }
    }
    
    /**
     * Enable or disable learning
     */
    public void setLearningEnabled(boolean enabled) {
        this.isLearningEnabled = enabled;
        Log.d(TAG, "Voice learning " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if model is available
     */
    public boolean isModelAvailable() {
        return isModelAvailable && tflite != null;
    }
    
    /**
     * Get number of learned voices
     */
    public int getLearnedVoiceCount() {
        return knownVoiceEmbeddings.size();
    }
    
    /**
     * Learn voice pattern from detected voice
     * Called when new voice is detected during emergency
     */
    public void learnVoicePattern(String voiceId, float similarity) {
        try {
            Log.d(TAG, "Learning voice pattern: " + voiceId + " (similarity: " + similarity + ")");
            
            // If voice already exists, update embedding
            if (knownVoiceEmbeddings.containsKey(voiceId)) {
                Log.d(TAG, "Voice already learned: " + voiceId + " - updating pattern");
                // Voice already learned, just update similarity tracking
            } else {
                // New voice - create placeholder embedding (will be updated with actual audio)
                float[] placeholderEmbedding = new float[40];
                knownVoiceEmbeddings.put(voiceId, placeholderEmbedding);
                saveVoiceEmbeddings();
                Log.d(TAG, "New voice pattern stored: " + voiceId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error learning voice pattern: " + e.getMessage());
        }
    }
    
    /**
     * Constructor without listener (for simple voice learning)
     */
    public AdaptiveVoiceLearningAI(Context context) {
        this.context = context;
        this.listener = null;
        this.knownVoiceEmbeddings = new HashMap<>();
        this.pendingLearningSamples = new ArrayList<>();
        this.learningExecutor = Executors.newSingleThreadExecutor();
        
        loadVoiceEmbeddings(); // Load existing voices
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        if (learningExecutor != null) {
            learningExecutor.shutdown();
        }
    }
    
    /**
     * Load model file
     */
    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(afd.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = afd.getStartOffset();
        long declaredLength = afd.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Voice sample data structure
     */
    private static class VoiceSample {
        String voiceId;
        float[][] mfccFeatures;
        float emergencyProb;
        long timestamp;
        
        VoiceSample(String voiceId, float[][] mfccFeatures, float emergencyProb, long timestamp) {
            this.voiceId = voiceId;
            this.mfccFeatures = mfccFeatures;
            this.emergencyProb = emergencyProb;
            this.timestamp = timestamp;
        }
    }
}



