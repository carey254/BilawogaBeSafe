package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * COMPREHENSIVE GBV DETECTOR - Works in All Environments
 * 
 * This class provides three GBV detection approaches:
 * 1. Audio-based detection (existing system)
 * 2. Text-based detection (new - conversation analysis)
 * 3. Synthetic audio training (convert text to audio)
 */
public class ComprehensiveGBVDetector {
    private static final String TAG = "ComprehensiveGBVDetector";
    
    // Detection modes
    public enum DetectionMode {
        AUDIO_ONLY,           // Use existing audio detection
        TEXT_ONLY,            // Use text-based detection
        HYBRID,              // Combine audio + text
        SYNTHETIC_TRAINING   // Convert text to audio for training
    }
    
    private final Context context;
    private DetectionMode currentMode;
    private CSVTrainingDataManager csvDataManager;
    private GBVModelTrainer gbvTrainer;
    private SilentEmergencyAI audioDetector;
    private TextGBVDetector textDetector;
    private SyntheticAudioGenerator syntheticGenerator;
    
    // Training status
    private boolean isTrainingComplete = false;
    private float trainingAccuracy = 0.0f;
    private int samplesProcessed = 0;
    
    public interface ComprehensiveDetectionListener {
        void onGBVDetected(String type, float confidence, String source);
        void onTrainingProgress(int epoch, int totalEpochs, float accuracy);
        void onTrainingComplete(boolean success, String message);
        void onModeChanged(DetectionMode newMode);
        void onSyntheticAudioGenerated(String audioPath, int count);
    }
    
    private ComprehensiveDetectionListener listener;
    private ExecutorService executorService;
    
    public ComprehensiveGBVDetector(Context context, ComprehensiveDetectionListener listener) {
        this.context = context;
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor();
        this.currentMode = DetectionMode.HYBRID; // Default to hybrid
        
        initializeComponents();
    }
    
    /**
     * Initialize all detection components
     */
    private void initializeComponents() {
        try {
            Log.i(TAG, "Initializing Comprehensive GBV Detector...");
            
            // Initialize CSV data manager
            csvDataManager = new CSVTrainingDataManager(context);
            boolean dataLoaded = csvDataManager.loadTrainingData();
            
            if (dataLoaded) {
                CSVTrainingDataManager.DatasetMetadata stats = csvDataManager.getMetadata();
                Log.i(TAG, "GBV Dataset loaded: " + stats.totalSamples + " samples");
                
                // Initialize text-based detector
                textDetector = new TextGBVDetector(context, csvDataManager);
                
                // Initialize synthetic audio generator
                syntheticGenerator = new SyntheticAudioGenerator(context);
                
                // Initialize GBV trainer
                gbvTrainer = new GBVModelTrainer(context, null);
                
                Log.i(TAG, "All GBV detection components initialized");
                
            } else {
                Log.e(TAG, "Failed to load GBV dataset - some features may not work");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing GBV detector: " + e.getMessage(), e);
        }
    }
    
    /**
     * Set detection mode
     */
    public void setDetectionMode(DetectionMode mode) {
        this.currentMode = mode;
        Log.i(TAG, "GBV Detection mode changed to: " + mode);
        
        if (listener != null) {
            listener.onModeChanged(mode);
        }
        
        // Initialize mode-specific components
        initializeModeSpecificComponents(mode);
    }
    
    /**
     * Initialize components specific to detection mode
     */
    private void initializeModeSpecificComponents(DetectionMode mode) {
        executorService.execute(() -> {
            try {
                switch (mode) {
                    case AUDIO_ONLY:
                        // Use existing audio detection only
                        Log.i(TAG, "Audio-only mode activated");
                        break;
                        
                    case TEXT_ONLY:
                        // Use text-based detection only
                        if (textDetector != null) {
                            textDetector.activate();
                            Log.i(TAG, "Text-only mode activated");
                        }
                        break;
                        
                    case HYBRID:
                        // Combine both audio and text
                        if (textDetector != null) {
                            textDetector.activate();
                        }
                        Log.i(TAG, "Hybrid mode activated - using both audio and text");
                        break;
                        
                    case SYNTHETIC_TRAINING:
                        // Generate synthetic audio from text for training
                        startSyntheticAudioTraining();
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing mode " + mode + ": " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Start synthetic audio training process
     */
    private void startSyntheticAudioTraining() {
        executorService.execute(() -> {
            try {
                Log.i(TAG, "Starting synthetic audio generation from text dataset...");
                
                if (syntheticGenerator != null && csvDataManager != null) {
                    // Get all conversation samples
                    List<CSVTrainingDataManager.TrainingSample> samples = csvDataManager.getAllSamples();
                    
                    // Generate synthetic audio for each sample
                    int generatedCount = 0;
                    for (CSVTrainingDataManager.TrainingSample sample : samples) {
                        String audioPath = syntheticGenerator.generateAudioFromText(
                            sample.conversation, 
                            sample.label,
                            sample.id
                        );
                        
                        if (audioPath != null) {
                            generatedCount++;
                            
                            // Notify progress
                            if (listener != null && generatedCount % 100 == 0) {
                                listener.onSyntheticAudioGenerated(audioPath, generatedCount);
                            }
                        }
                    }
                    
                    Log.i(TAG, "Generated " + generatedCount + " synthetic audio files");
                    
                    if (listener != null) {
                        listener.onSyntheticAudioGenerated("Complete", generatedCount);
                    }
                    
                    // Start training with generated audio
                    startTrainingWithSyntheticAudio();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in synthetic audio training: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Start training with synthetic audio data
     */
    private void startTrainingWithSyntheticAudio() {
        try {
            Log.i(TAG, "Starting GBV training with synthetic audio data...");
            
            if (gbvTrainer != null) {
                gbvTrainer.startTraining(new GBVModelTrainer.TrainingProgressListener() {
                    @Override
                    public void onTrainingProgress(int epoch, int totalEpochs, float loss, float accuracy) {
                        trainingAccuracy = accuracy;
                        samplesProcessed = epoch;
                        
                        if (listener != null) {
                            listener.onTrainingProgress(epoch, totalEpochs, accuracy);
                        }
                        
                        Log.d(TAG, "Synthetic Audio Training - Epoch " + epoch + "/" + totalEpochs + 
                                  " - Accuracy: " + String.format("%.2f%%", accuracy * 100));
                    }
                    
                    @Override
                    public void onTrainingComplete(boolean success, String message) {
                        isTrainingComplete = success;
                        
                        if (listener != null) {
                            listener.onTrainingComplete(success, message);
                        }
                        
                        if (success) {
                            Log.i(TAG, "✅ Synthetic Audio Training Complete: " + message);
                        } else {
                            Log.e(TAG, "❌ Synthetic Audio Training Failed: " + message);
                        }
                    }
                    
                    @Override
                    public void onValidationComplete(float validationLoss, float validationAccuracy) {
                        Log.d(TAG, "Synthetic Audio Validation - Accuracy: " + 
                                  String.format("%.2f%%", validationAccuracy * 100));
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting synthetic audio training: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process audio input (for audio-based detection)
     */
    public void processAudioInput(byte[] audioData, int readSize) {
        if (currentMode == DetectionMode.AUDIO_ONLY || currentMode == DetectionMode.HYBRID) {
            // Use existing audio detection
            if (audioDetector != null) {
                // This would integrate with existing SilentEmergencyAI
                Log.d(TAG, "Processing audio input for GBV detection");
            }
        }
    }
    
    /**
     * Process text input (for text-based detection)
     */
    public void processTextInput(String text) {
        if (currentMode == DetectionMode.TEXT_ONLY || currentMode == DetectionMode.HYBRID) {
            if (textDetector != null) {
                TextGBVDetector.DetectionResult result = textDetector.analyzeText(text);
                
                if (result.isGBVDetected) {
                    Log.w(TAG, "GBV detected in text: " + result.type + " (confidence: " + result.confidence + ")");
                    
                    if (listener != null) {
                        listener.onGBVDetected(result.type, result.confidence, "text_analysis");
                    }
                }
            }
        }
    }
    
    /**
     * Get current detection mode
     */
    public DetectionMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Get training status
     */
    public boolean isTrainingComplete() {
        return isTrainingComplete;
    }
    
    /**
     * Get training accuracy
     */
    public float getTrainingAccuracy() {
        return trainingAccuracy;
    }
    
    /**
     * Get dataset statistics
     */
    public CSVTrainingDataManager.DatasetMetadata getDatasetStatistics() {
        if (csvDataManager != null) {
            return csvDataManager.getMetadata();
        }
        return null;
    }
    
    /**
     * Get available modes for this device
     */
    public List<DetectionMode> getAvailableModes() {
        List<DetectionMode> modes = new ArrayList<>();
        
        // Audio mode is always available (existing system)
        modes.add(DetectionMode.AUDIO_ONLY);
        
        // Text mode is available if we have dataset
        if (csvDataManager != null && csvDataManager.getAllSamples().size() > 0) {
            modes.add(DetectionMode.TEXT_ONLY);
        }
        
        // Hybrid mode if both are available
        if (modes.size() > 1) {
            modes.add(DetectionMode.HYBRID);
        }
        
        // Synthetic training if we have text data
        if (csvDataManager != null && csvDataManager.getAllSamples().size() > 0) {
            modes.add(DetectionMode.SYNTHETIC_TRAINING);
        }
        
        return modes;
    }
    
    /**
     * Get mode description
     */
    public String getModeDescription(DetectionMode mode) {
        switch (mode) {
            case AUDIO_ONLY:
                return "Audio Detection Only - Uses existing emergency sound detection";
            case TEXT_ONLY:
                return "Text Analysis Only - Analyzes conversations for GBV patterns";
            case HYBRID:
                return "Hybrid Mode - Combines audio and text detection";
            case SYNTHETIC_TRAINING:
                return "Synthetic Training - Converts text to audio for model training";
            default:
                return "Unknown Mode";
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        try {
            if (executorService != null) {
                executorService.shutdown();
            }
            
            if (textDetector != null) {
                textDetector.cleanup();
            }
            
            if (syntheticGenerator != null) {
                syntheticGenerator.cleanup();
            }
            
            if (gbvTrainer != null) {
                gbvTrainer.cleanup();
            }
            
            Log.i(TAG, "Comprehensive GBV Detector cleaned up");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage(), e);
        }
    }
}
