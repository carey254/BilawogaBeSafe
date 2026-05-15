package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV Training Data Manager for GBV Detection
 * 
 * Handles loading, parsing, and managing CSV training datasets
 * for gender-based violence detection model training.
 */
public class CSVTrainingDataManager {
    private static final String TAG = "CSVTrainingDataManager";
    
    // Dataset file paths in assets
    private static final String MAIN_DATASET = "datasets/gbv_dataset.csv";
    private static final String EMERGENCY_DATASET = "datasets/emergency_samples.csv";
    private static final String NORMAL_DATASET = "datasets/non_emergency_samples.csv";
    private static final String METADATA_FILE = "datasets/dataset_metadata.json";
    
    private final Context context;
    private List<TrainingSample> trainingSamples;
    private DatasetMetadata metadata;
    
    public CSVTrainingDataManager(Context context) {
        this.context = context;
        this.trainingSamples = new ArrayList<>();
        this.metadata = new DatasetMetadata();
    }
    
    /**
     * Training sample data structure
     */
    public static class TrainingSample {
        public String id;
        public String conversation;     // conversation text
        public String label;           // abuse, distress, normal
        public String urgencyLevel;     // low, medium, high
        public double riskScore;       // 0.0 - 1.0
        public String contextType;     // public, relationship, family, etc.
        public int escalationLevel;     // 1-10 scale
        public String interventionRequired; // yes, no, maybe
        public double confidenceScore;  // 0.0 - 1.0
        public Map<String, String> additionalFeatures;
        
        public TrainingSample(String id, String conversation, String label, String urgencyLevel,
                            double riskScore, String contextType, int escalationLevel,
                            String interventionRequired, double confidenceScore) {
            this.id = id;
            this.conversation = conversation;
            this.label = label;
            this.urgencyLevel = urgencyLevel;
            this.riskScore = riskScore;
            this.contextType = contextType;
            this.escalationLevel = escalationLevel;
            this.interventionRequired = interventionRequired;
            this.confidenceScore = confidenceScore;
            this.additionalFeatures = new HashMap<>();
        }
    }
    
    /**
     * Dataset metadata
     */
    public static class DatasetMetadata {
        public int totalSamples = 0;
        public int abuseSamples = 0;
        public int distressSamples = 0;
        public int normalSamples = 0;
        public Map<String, Integer> urgencyDistribution = new HashMap<>();
        public Map<String, Integer> contextDistribution = new HashMap<>();
        public Map<String, Integer> interventionDistribution = new HashMap<>();
        public double averageRiskScore = 0.0;
        public double averageConfidenceScore = 0.0;
        public String lastUpdated;
        
        public void updateStatistics(List<TrainingSample> samples) {
            totalSamples = samples.size();
            abuseSamples = 0;
            distressSamples = 0;
            normalSamples = 0;
            urgencyDistribution.clear();
            contextDistribution.clear();
            interventionDistribution.clear();
            
            double totalRiskScore = 0.0;
            double totalConfidenceScore = 0.0;
            
            for (TrainingSample sample : samples) {
                // Count by label
                switch (sample.label.toLowerCase()) {
                    case "abuse":
                        abuseSamples++;
                        break;
                    case "distress":
                        distressSamples++;
                        break;
                    case "normal":
                        normalSamples++;
                        break;
                }
                
                // Count by urgency level
                urgencyDistribution.put(sample.urgencyLevel, 
                    urgencyDistribution.getOrDefault(sample.urgencyLevel, 0) + 1);
                
                // Count by context type
                contextDistribution.put(sample.contextType, 
                    contextDistribution.getOrDefault(sample.contextType, 0) + 1);
                
                // Count by intervention required
                interventionDistribution.put(sample.interventionRequired, 
                    interventionDistribution.getOrDefault(sample.interventionRequired, 0) + 1);
                
                // Accumulate scores
                totalRiskScore += sample.riskScore;
                totalConfidenceScore += sample.confidenceScore;
            }
            
            averageRiskScore = totalRiskScore / samples.size();
            averageConfidenceScore = totalConfidenceScore / samples.size();
            lastUpdated = java.time.LocalDate.now().toString();
        }
    }
    
    /**
     * Load training data from CSV files
     */
    public boolean loadTrainingData() {
        try {
            trainingSamples.clear();
            
            // Load main dataset
            loadCSVFromAssets(MAIN_DATASET);
            
            // Load emergency samples
            loadCSVFromAssets(EMERGENCY_DATASET);
            
            // Load normal samples
            loadCSVFromAssets(NORMAL_DATASET);
            
            // Update metadata
            metadata.updateStatistics(trainingSamples);
            
            Log.i(TAG, "Loaded " + trainingSamples.size() + " training samples");
            Log.i(TAG, "Emergency: " + metadata.emergencySamples + 
                      ", Normal: " + metadata.normalSamples + 
                      ", Uncertain: " + metadata.uncertainSamples);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading training data: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Load CSV file from assets
     */
    private void loadCSVFromAssets(String filename) {
        try {
            InputStream inputStream = context.getAssets().open(filename);
            CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
            
            String[] headers = reader.readNext(); // Skip header row
            if (headers == null) return;
            
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length >= 9) {
                    TrainingSample sample = new TrainingSample(
                        line[0],  // id
                        line[1],  // conversation
                        line[2],  // label
                        line[3],  // urgency_level
                        Double.parseDouble(line[4]),  // risk_score
                        line[5],  // context_type
                        Integer.parseInt(line[6]),  // escalation_level
                        line[7],  // intervention_required
                        Double.parseDouble(line[8])   // confidence_score
                    );
                    
                    // Add additional features if present
                    for (int i = 9; i < line.length; i += 2) {
                        if (i + 1 < line.length) {
                            sample.additionalFeatures.put(line[i], line[i + 1]);
                        }
                    }
                    
                    trainingSamples.add(sample);
                }
            }
            
            reader.close();
            Log.d(TAG, "Loaded " + trainingSamples.size() + " samples from " + filename);
            
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "Error loading CSV " + filename + ": " + e.getMessage());
        }
    }
    
    /**
     * Get training samples by label
     */
    public List<TrainingSample> getSamplesByLabel(String label) {
        List<TrainingSample> filtered = new ArrayList<>();
        for (TrainingSample sample : trainingSamples) {
            if (sample.label.equalsIgnoreCase(label)) {
                filtered.add(sample);
            }
        }
        return filtered;
    }
    
    /**
     * Get training samples by urgency level
     */
    public List<TrainingSample> getSamplesByUrgency(String urgency) {
        List<TrainingSample> filtered = new ArrayList<>();
        for (TrainingSample sample : trainingSamples) {
            if (sample.urgencyLevel.equalsIgnoreCase(urgency)) {
                filtered.add(sample);
            }
        }
        return filtered;
    }
    
    /**
     * Get high-risk samples (risk score > 0.7)
     */
    public List<TrainingSample> getHighRiskSamples() {
        List<TrainingSample> filtered = new ArrayList<>();
        for (TrainingSample sample : trainingSamples) {
            if (sample.riskScore > 0.7) {
                filtered.add(sample);
            }
        }
        return filtered;
    }
    
    /**
     * Get samples requiring intervention
     */
    public List<TrainingSample> getInterventionRequiredSamples() {
        List<TrainingSample> filtered = new ArrayList<>();
        for (TrainingSample sample : trainingSamples) {
            if (sample.interventionRequired.equalsIgnoreCase("yes")) {
                filtered.add(sample);
            }
        }
        return filtered;
    }
    
    /**
     * Get balanced dataset for training
     */
    public List<TrainingSample> getBalancedDataset(int maxSizePerCategory) {
        List<TrainingSample> balanced = new ArrayList<>();
        
        // Get samples by category
        List<TrainingSample> abuse = getSamplesByLabel("abuse");
        List<TrainingSample> distress = getSamplesByLabel("distress");
        List<TrainingSample> normal = getSamplesByLabel("normal");
        
        // Limit samples per category
        balanced.addAll(abuse.subList(0, Math.min(abuse.size(), maxSizePerCategory)));
        balanced.addAll(distress.subList(0, Math.min(distress.size(), maxSizePerCategory)));
        balanced.addAll(normal.subList(0, Math.min(normal.size(), maxSizePerCategory)));
        
        return balanced;
    }
    
    /**
     * Save new training sample to CSV
     */
    public boolean saveTrainingSample(TrainingSample sample, String datasetType) {
        try {
            // For now, just add to memory
            // In production, you'd write to external storage or database
            trainingSamples.add(sample);
            metadata.updateStatistics(trainingSamples);
            
            Log.d(TAG, "Saved training sample: " + sample.id + " (" + sample.label + ")");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving training sample: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get dataset statistics
     */
    public DatasetMetadata getMetadata() {
        return metadata;
    }
    
    /**
     * Get all training samples
     */
    public List<TrainingSample> getAllSamples() {
        return new ArrayList<>(trainingSamples);
    }
    
    /**
     * Validate CSV format
     */
    public boolean validateCSVFormat(String filePath) {
        try {
            InputStream inputStream = context.getAssets().open(filePath);
            CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
            
            String[] headers = reader.readNext();
            if (headers == null) return false;
            
            // Check required columns for GBV dataset
            String[] requiredColumns = {"id", "conversation", "label", "urgency_level", "risk_score", 
                                     "context_type", "escalation_level", "intervention_required", "confidence_score"};
            for (String required : requiredColumns) {
                boolean found = false;
                for (String header : headers) {
                    if (header.equalsIgnoreCase(required)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Log.e(TAG, "Missing required column: " + required);
                    return false;
                }
            }
            
            reader.close();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error validating CSV format: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export training data to CSV (for backup/analysis)
     */
    public boolean exportToCSV(String outputPath) {
        try {
            CSVWriter writer = new CSVWriter(new FileWriter(outputPath));
            
            // Write header
            String[] header = {"audio_file_path", "label", "severity", "context", "timestamp", "duration_seconds"};
            writer.writeNext(header);
            
            // Write data
            for (TrainingSample sample : trainingSamples) {
                String[] line = {
                    sample.audioFilePath,
                    sample.label,
                    sample.severity,
                    sample.context,
                    sample.timestamp,
                    String.valueOf(sample.durationSeconds)
                };
                writer.writeNext(line);
            }
            
            writer.close();
            Log.i(TAG, "Exported " + trainingSamples.size() + " samples to " + outputPath);
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error exporting to CSV: " + e.getMessage());
            return false;
        }
    }
}
