package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TEXT-BASED GBV DETECTOR
 * 
 * Analyzes text conversations for gender-based violence indicators
 * Works with your conversation dataset
 */
public class TextGBVDetector {
    private static final String TAG = "TextGBVDetector";
    
    private final Context context;
    private final CSVTrainingDataManager csvDataManager;
    private boolean isActive = false;
    
    // GBV indicator patterns
    private static final String[] CONTROL_PHRASES = {
        "i have to ask", "can't decide", "don't have freedom", "controlled",
        "need permission", "not allowed", "must get approval", "watched"
    };
    
    private static final String[] ABUSE_INDICATORS = {
        "trapped", "scared", "threatened", "unsafe", "danger",
        "afraid", "worried", "anxious", "fear", "help"
    };
    
    private static final String[] ISOLATION_PHRASES = {
        "can't talk to", "not allowed to see", "cut off", "isolated",
        "alone", "no contact", "forbidden", "restricted"
    };
    
    private static final String[] MANIPULATION_INDICATORS = {
        "it's my fault", "i deserved it", "i'm overthinking",
        "maybe i'm wrong", "it's not that bad", "i'm being dramatic"
    };
    
    // Risk scoring weights
    private static final float CONTROL_WEIGHT = 0.3f;
    private static final float ABUSE_WEIGHT = 0.4f;
    private static final float ISOLATION_WEIGHT = 0.2f;
    private static final float MANIPULATION_WEIGHT = 0.1f;
    
    public static class DetectionResult {
        public boolean isGBVDetected;
        public String type;           // "control", "abuse", "isolation", "manipulation"
        public float confidence;        // 0.0 to 1.0
        public float riskScore;        // 0.0 to 1.0
        public Map<String, Integer> indicators;
        public String explanation;
        
        public DetectionResult() {
            this.indicators = new HashMap<>();
            this.confidence = 0.0f;
            this.riskScore = 0.0f;
        }
    }
    
    public TextGBVDetector(Context context, CSVTrainingDataManager csvDataManager) {
        this.context = context;
        this.csvDataManager = csvDataManager;
    }
    
    /**
     * Activate text-based detection
     */
    public void activate() {
        isActive = true;
        Log.i(TAG, "Text-based GBV detection activated");
    }
    
    /**
     * Deactivate text-based detection
     */
    public void deactivate() {
        isActive = false;
        Log.i(TAG, "Text-based GBV detection deactivated");
    }
    
    /**
     * Analyze text for GBV indicators
     */
    public DetectionResult analyzeText(String text) {
        if (!isActive) {
            DetectionResult result = new DetectionResult();
            result.isGBVDetected = false;
            result.explanation = "Text detector is not active";
            return result;
        }
        
        DetectionResult result = new DetectionResult();
        String lowerText = text.toLowerCase();
        
        // Count indicators
        int controlCount = countPhrases(lowerText, CONTROL_PHRASES);
        int abuseCount = countPhrases(lowerText, ABUSE_INDICATORS);
        int isolationCount = countPhrases(lowerText, ISOLATION_PHRASES);
        int manipulationCount = countPhrases(lowerText, MANIPULATION_INDICATORS);
        
        // Store in result
        result.indicators.put("control", controlCount);
        result.indicators.put("abuse", abuseCount);
        result.indicators.put("isolation", isolationCount);
        result.indicators.put("manipulation", manipulationCount);
        
        // Calculate weighted risk score
        float controlScore = Math.min(1.0f, (controlCount * CONTROL_WEIGHT));
        float abuseScore = Math.min(1.0f, (abuseCount * ABUSE_WEIGHT));
        float isolationScore = Math.min(1.0f, (isolationCount * ISOLATION_WEIGHT));
        float manipulationScore = Math.min(1.0f, (manipulationCount * MANIPULATION_WEIGHT));
        
        result.riskScore = controlScore + abuseScore + isolationScore + manipulationScore;
        result.riskScore = Math.min(1.0f, result.riskScore);
        
        // Determine if GBV is detected
        result.isGBVDetected = result.riskScore > 0.3f; // 30% threshold
        
        // Determine type and confidence
        if (result.isGBVDetected) {
            if (controlCount >= 2) {
                result.type = "control";
                result.confidence = Math.min(1.0f, controlCount / 3.0f);
            } else if (abuseCount >= 2) {
                result.type = "abuse";
                result.confidence = Math.min(1.0f, abuseCount / 3.0f);
            } else if (isolationCount >= 1) {
                result.type = "isolation";
                result.confidence = Math.min(1.0f, isolationCount / 2.0f);
            } else if (manipulationCount >= 1) {
                result.type = "manipulation";
                result.confidence = Math.min(1.0f, manipulationCount / 2.0f);
            } else {
                result.type = "general_gbv_risk";
                result.confidence = result.riskScore;
            }
            
            result.explanation = generateExplanation(result);
        } else {
            result.type = "normal";
            result.confidence = 0.0f;
            result.explanation = "No significant GBV indicators detected";
        }
        
        Log.d(TAG, "Text analysis - Risk: " + String.format("%.2f", result.riskScore) + 
                  ", Type: " + result.type + ", Confidence: " + String.format("%.2f", result.confidence));
        
        return result;
    }
    
    /**
     * Count occurrences of phrases in text
     */
    private int countPhrases(String text, String[] phrases) {
        int count = 0;
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Generate explanation for detection result
     */
    private String generateExplanation(DetectionResult result) {
        StringBuilder explanation = new StringBuilder();
        
        if (result.indicators.get("control") > 0) {
            explanation.append("Control indicators detected (").append(result.indicators.get("control"))
                     .append(" instances). ");
        }
        
        if (result.indicators.get("abuse") > 0) {
            explanation.append("Abuse indicators detected (").append(result.indicators.get("abuse"))
                     .append(" instances). ");
        }
        
        if (result.indicators.get("isolation") > 0) {
            explanation.append("Isolation indicators detected (").append(result.indicators.get("isolation"))
                     .append(" instances). ");
        }
        
        if (result.indicators.get("manipulation") > 0) {
            explanation.append("Manipulation indicators detected (").append(result.indicators.get("manipulation"))
                     .append(" instances). ");
        }
        
        explanation.append("Overall risk score: ").append(String.format("%.1f%%", result.riskScore * 100));
        
        return explanation.toString();
    }
    
    /**
     * Analyze conversation from dataset
     */
    public DetectionResult analyzeConversationFromDataset(String conversationId) {
        if (csvDataManager == null) {
            DetectionResult result = new DetectionResult();
            result.isGBVDetected = false;
            result.explanation = "Dataset not available";
            return result;
        }
        
        // Find sample by ID (simplified - in production would use proper lookup)
        for (CSVTrainingDataManager.TrainingSample sample : csvDataManager.getAllSamples()) {
            if (sample.id.equals(conversationId)) {
                return analyzeText(sample.conversation);
            }
        }
        
        DetectionResult result = new DetectionResult();
        result.isGBVDetected = false;
        result.explanation = "Conversation not found in dataset";
        return result;
    }
    
    /**
     * Get detection statistics
     */
    public Map<String, Object> getDetectionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("isActive", isActive);
        stats.put("totalPhrases", CONTROL_PHRASES.length + ABUSE_INDICATORS.length + 
                     ISOLATION_PHRASES.length + MANIPULATION_INDICATORS.length);
        stats.put("controlPhrases", CONTROL_PHRASES.length);
        stats.put("abusePhrases", ABUSE_INDICATORS.length);
        stats.put("isolationPhrases", ISOLATION_PHRASES.length);
        stats.put("manipulationPhrases", MANIPULATION_INDICATORS.length);
        return stats;
    }
    
    /**
     * Test with sample conversations
     */
    public void runSelfTest() {
        Log.i(TAG, "Running Text GBV Detector self-test...");
        
        String[] testConversations = {
            "U: I feel controlled | A: Do you feel safe? | U: I don't have freedom",
            "U: we had a disagreement | U: things are fine now | U: it wasn't serious",
            "U: I'm scared | U: they threaten me | U: I feel trapped",
            "U: I can't talk to my friends | U: not allowed to see family | U: isolated"
        };
        
        for (int i = 0; i < testConversations.length; i++) {
            DetectionResult result = analyzeText(testConversations[i]);
            Log.i(TAG, "Test " + (i + 1) + ": " + 
                      (result.isGBVDetected ? "GBV DETECTED" : "Normal") + 
                      " - " + result.type + " (confidence: " + String.format("%.2f", result.confidence) + ")");
        }
        
        Log.i(TAG, "Text GBV Detector self-test complete");
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        isActive = false;
        Log.i(TAG, "Text GBV Detector cleaned up");
    }
}
