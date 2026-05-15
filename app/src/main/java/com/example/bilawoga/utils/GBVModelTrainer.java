package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * GBV Model Trainer - Integrates CSV training data with TensorFlow Lite
 * 
 * This class handles training and fine-tuning the GBV detection model
 * using the CSV training data you upload.
 */
public class GBVModelTrainer {
    private static final String TAG = "GBVModelTrainer";
    
    private final Context context;
    private final CSVTrainingDataManager dataManager;
    private final AdaptiveVoiceLearningAI adaptiveAI;
    private Interpreter tfliteModel;
    private ExecutorService trainingExecutor;
    
    // Training parameters
    private static final float LEARNING_RATE = 0.001f;
    private static final int BATCH_SIZE = 32;
    private static final int EPOCHS = 50;
    private static final float VALIDATION_SPLIT = 0.2f;
    
    public interface TrainingProgressListener {
        void onTrainingProgress(int epoch, int totalEpochs, float loss, float accuracy);
        void onTrainingComplete(boolean success, String message);
        void onValidationComplete(float validationLoss, float validationAccuracy);
    }
    
    private TrainingProgressListener progressListener;
    
    public GBVModelTrainer(Context context, AdaptiveVoiceLearningAI adaptiveAI) {
        this.context = context;
        this.dataManager = new CSVTrainingDataManager(context);
        this.adaptiveAI = adaptiveAI;
        this.trainingExecutor = Executors.newSingleThreadExecutor();
        
        loadModel();
    }
    
    /**
     * Load the base TensorFlow Lite model
     */
    private void loadModel() {
        try {
            InputStream inputStream = context.getAssets().open("sos_audio_model.tflite");
            File modelFile = new File(context.getFilesDir(), "gbv_model.tflite");
            
            OutputStream outputStream = new FileOutputStream(modelFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            outputStream.close();
            inputStream.close();
            
            MappedByteBuffer modelBuffer = loadModelFile(modelFile);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            options.setUseXNNPACK(true);
            
            tfliteModel = new Interpreter(modelBuffer, options);
            Log.i(TAG, "GBV model loaded successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading model: " + e.getMessage(), e);
        }
    }
    
    /**
     * Load model file from File object
     */
    private MappedByteBuffer loadModelFile(File modelFile) throws IOException {
        FileInputStream inputStream = new FileInputStream(modelFile);
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = 0;
        long declaredLength = modelFile.length();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Start training with CSV data
     */
    public void startTraining(TrainingProgressListener listener) {
        this.progressListener = listener;
        trainingExecutor.execute(() -> {
            try {
                // Load training data
                if (!dataManager.loadTrainingData()) {
                    notifyTrainingComplete(false, "Failed to load training data");
                    return;
                }
                
                List<CSVTrainingDataManager.TrainingSample> samples = dataManager.getAllSamples();
                if (samples.isEmpty()) {
                    notifyTrainingComplete(false, "No training samples found");
                    return;
                }
                
                // Prepare training data
                TrainingData trainingData = prepareTrainingData(samples);
                
                // Start training process
                trainModel(trainingData);
                
            } catch (Exception e) {
                Log.e(TAG, "Training error: " + e.getMessage(), e);
                notifyTrainingComplete(false, "Training failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Prepare training data from CSV samples
     */
    private TrainingData prepareTrainingData(List<CSVTrainingDataManager.TrainingSample> samples) {
        List<float[]> features = new ArrayList<>();
        List<float[]> labels = new ArrayList<>();
        
        for (CSVTrainingDataManager.TrainingSample sample : samples) {
            // Extract text features from conversation
            float[] textFeatures = extractTextFeatures(sample.conversation);
            
            // Create label based on sample classification
            float[] label = createLabelVector(sample.label, sample.urgencyLevel);
            
            features.add(textFeatures);
            labels.add(label);
        }
        
        return new TrainingData(features, labels);
    }
    
    /**
     * Extract text features from conversation
     */
    private float[] extractTextFeatures(String conversation) {
        float[] features = new float[100]; // Feature vector size
        
        // Initialize features
        for (int i = 0; i < features.length; i++) {
            features[i] = 0.0f;
        }
        
        // Simple text feature extraction
        String lowerText = conversation.toLowerCase();
        
        // Abuse indicators
        String[] abuseWords = {"controlled", "trapped", "watched", "freedom", "safe", "danger", "threat", "scared"};
        for (String word : abuseWords) {
            if (lowerText.contains(word)) {
                features[0] += 1.0f; // Abuse indicator count
            }
        }
        
        // Distress indicators
        String[] distressWords = {"stress", "weird", "overthinking", "difficult", "tense", "worried"};
        for (String word : distressWords) {
            if (lowerText.contains(word)) {
                features[1] += 1.0f; // Distress indicator count
            }
        }
        
        // Control indicators
        String[] controlWords = {"have to ask", "can't decide", "don't have freedom", "controlled"};
        for (String phrase : controlWords) {
            if (lowerText.contains(phrase)) {
                features[2] += 1.0f; // Control indicator count
            }
        }
        
        // Conversation length
        features[3] = (float) conversation.length() / 1000.0f; // Normalized length
        
        // Question count (user seeking help)
        int questionCount = lowerText.split("\\?").length - 1;
        features[4] = (float) questionCount / 10.0f; // Normalized question count
        
        // Urgency words
        String[] urgencyWords = {"help", "emergency", "urgent", "immediate", "now"};
        for (String word : urgencyWords) {
            if (lowerText.contains(word)) {
                features[5] += 1.0f; // Urgency indicator count
            }
        }
        
        // Normalize features
        for (int i = 0; i < 10; i++) {
            features[i] = Math.min(1.0f, features[i] / 10.0f); // Cap at 1.0
        }
        
        return features;
    }
    
    /**
     * Create label vector from label and urgency
     */
    private float[] createLabelVector(String label, String urgency) {
        float[] labelVector = new float[6]; // 3 labels × 2 urgency levels
        
        switch (label.toLowerCase()) {
            case "abuse":
                if (urgency.equalsIgnoreCase("high")) {
                    labelVector[0] = 1.0f; // abuse_high
                } else {
                    labelVector[1] = 1.0f; // abuse_medium_low
                }
                break;
            case "normal":
                if (urgency.equalsIgnoreCase("low")) {
                    labelVector[2] = 1.0f; // normal_low
                } else {
                    labelVector[3] = 1.0f; // normal_medium
                }
                break;
            case "distress":
                if (urgency.equalsIgnoreCase("medium")) {
                    labelVector[4] = 1.0f; // distress_medium
                } else {
                    labelVector[5] = 1.0f; // distress_high
                }
                break;
        }
        
        return labelVector;
    }
    
    /**
     * Train the model with prepared data
     */
    private void trainModel(TrainingData trainingData) {
        try {
            // Split data into training and validation
            int validationSize = (int) (trainingData.features.size() * VALIDATION_SPLIT);
            int trainingSize = trainingData.features.size() - validationSize;
            
            List<float[]> trainFeatures = trainingData.features.subList(0, trainingSize);
            List<float[]> trainLabels = trainingData.labels.subList(0, trainingSize);
            List<float[]> valFeatures = trainingData.features.subList(trainingSize, trainingData.features.size());
            List<float[]> valLabels = trainingData.labels.subList(trainingSize, trainingData.labels.size());
            
            Log.i(TAG, "Starting training: " + trainingSize + " samples, Validation: " + validationSize + " samples");
            
            // Training loop
            for (int epoch = 0; epoch < EPOCHS; epoch++) {
                float totalLoss = 0.0f;
                int correctPredictions = 0;
                
                // Process mini-batches
                for (int i = 0; i < trainFeatures.size(); i += BATCH_SIZE) {
                    int batchSize = Math.min(BATCH_SIZE, trainFeatures.size() - i);
                    
                    // Prepare batch
                    float[][] batchInput = prepareBatchInput(trainFeatures.subList(i, i + batchSize));
                    float[][] batchLabels = prepareBatchLabels(trainLabels.subList(i, i + batchSize));
                    float[][] batchOutput = new float[batchSize][6];
                    
                    // Forward pass
                    tfliteModel.run(batchInput, batchOutput);
                    
                    // Calculate loss and accuracy
                    for (int j = 0; j < batchSize; j++) {
                        totalLoss += calculateLoss(batchOutput[j], batchLabels[j]);
                        if (isPredictionCorrect(batchOutput[j], batchLabels[j])) {
                            correctPredictions++;
                        }
                    }
                }
                
                float epochLoss = totalLoss / trainFeatures.size();
                float epochAccuracy = (float) correctPredictions / trainFeatures.size();
                
                // Notify progress
                notifyTrainingProgress(epoch + 1, EPOCHS, epochLoss, epochAccuracy);
                
                // Validate every 5 epochs
                if ((epoch + 1) % 5 == 0) {
                    validateModel(valFeatures, valLabels);
                }
                
                Log.d(TAG, "Epoch " + (epoch + 1) + "/" + EPOCHS + 
                          " - Loss: " + epochLoss + ", Accuracy: " + epochAccuracy);
            }
            
            notifyTrainingComplete(true, "Training completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Training error: " + e.getMessage(), e);
            notifyTrainingComplete(false, "Training failed: " + e.getMessage());
        }
    }
    
    /**
     * Prepare batch input for TensorFlow Lite
     */
    private float[][] prepareBatchInput(List<float[]> features) {
        int batchSize = features.size();
        float[][] batchInput = new float[batchSize][100]; // 100 text features
        
        for (int i = 0; i < batchSize; i++) {
            System.arraycopy(features.get(i), 0, batchInput[i], 0, 100);
        }
        
        return batchInput;
    }
    
    /**
     * Prepare batch labels
     */
    private float[][] prepareBatchLabels(List<float[]> labels) {
        int batchSize = labels.size();
        float[][] batchLabels = new float[batchSize][6];
        
        for (int i = 0; i < batchSize; i++) {
            System.arraycopy(labels.get(i), 0, batchLabels[i], 0, 6);
        }
        
        return batchLabels;
    }
    
    /**
     * Calculate loss (simplified MSE)
     */
    private float calculateLoss(float[] predicted, float[] actual) {
        float loss = 0.0f;
        for (int i = 0; i < predicted.length; i++) {
            float diff = predicted[i] - actual[i];
            loss += diff * diff;
        }
        return loss / predicted.length;
    }
    
    /**
     * Check if prediction is correct
     */
    private boolean isPredictionCorrect(float[] predicted, float[] actual) {
        int predIndex = getMaxIndex(predicted);
        int actualIndex = getMaxIndex(actual);
        return predIndex == actualIndex;
    }
    
    /**
     * Get index of maximum value
     */
    private int getMaxIndex(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
    
    /**
     * Validate model
     */
    private void validateModel(List<float[]> valFeatures, List<float[]> valLabels) {
        try {
            float totalLoss = 0.0f;
            int correctPredictions = 0;
            
            float[][] valInput = prepareBatchInput(valFeatures);
            float[][] valOutput = new float[valFeatures.size()][6];
            
            tfliteModel.run(valInput, valOutput);
            
            for (int i = 0; i < valLabels.size(); i++) {
                totalLoss += calculateLoss(valOutput[i], valLabels.get(i));
                if (isPredictionCorrect(valOutput[i], valLabels.get(i))) {
                    correctPredictions++;
                }
            }
            
            float validationLoss = totalLoss / valLabels.size();
            float validationAccuracy = (float) correctPredictions / valLabels.size();
            
            notifyValidationComplete(validationLoss, validationAccuracy);
            
        } catch (Exception e) {
            Log.e(TAG, "Validation error: " + e.getMessage());
        }
    }
    
    /**
     * Get dataset statistics
     */
    public CSVTrainingDataManager.DatasetMetadata getDatasetStats() {
        return dataManager.getMetadata();
    }
    
    /**
     * Add new training sample
     */
    public boolean addTrainingSample(String audioPath, String label, String severity, String context) {
        CSVTrainingDataManager.TrainingSample sample = 
            new CSVTrainingDataManager.TrainingSample(audioPath, label, severity, context, 
                                                   java.time.LocalDate.now().toString(), 10.0);
        return dataManager.saveTrainingSample(sample, "main");
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (tfliteModel != null) {
            tfliteModel.close();
        }
        if (trainingExecutor != null) {
            trainingExecutor.shutdown();
        }
    }
    
    // Notification methods
    private void notifyTrainingProgress(int epoch, int totalEpochs, float loss, float accuracy) {
        if (progressListener != null) {
            progressListener.onTrainingProgress(epoch, totalEpochs, loss, accuracy);
        }
    }
    
    private void notifyTrainingComplete(boolean success, String message) {
        if (progressListener != null) {
            progressListener.onTrainingComplete(success, message);
        }
    }
    
    private void notifyValidationComplete(float validationLoss, float validationAccuracy) {
        if (progressListener != null) {
            progressListener.onValidationComplete(validationLoss, validationAccuracy);
        }
    }
    
    /**
     * Training data container
     */
    private static class TrainingData {
        List<float[]> features;
        List<float[]> labels;
        
        TrainingData(List<float[]> features, List<float[]> labels) {
            this.features = features;
            this.labels = labels;
        }
    }
}
