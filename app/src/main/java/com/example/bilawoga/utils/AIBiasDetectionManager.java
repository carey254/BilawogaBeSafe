package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI BIAS DETECTION AND FAIRNESS MANAGER
 * 
 * Implements Security by Design (SbD) features to mitigate AI bias risks:
 * - Bias audits and fairness testing
 * - Demographic impact analysis
 * - Appeals process for AI decisions
 * - Human-in-the-loop review
 * - Input/output validation
 * - Feedback mechanisms for AI errors
 * - Ethical AI guidelines adherence
 */
public class AIBiasDetectionManager {
    private static final String TAG = "AIBiasDetectionManager";
    private static final String PREFS_NAME = "ai_bias_detection";
    
    // Bias detection thresholds
    private static final float BIAS_THRESHOLD = 0.15f; // 15% difference indicates potential bias
    private static final float FAIRNESS_THRESHOLD = 0.10f; // 10% difference for fairness
    
    // Demographic categories for analysis
    private static final String[] DEMOGRAPHIC_CATEGORIES = {
        "age_group", "gender", "location", "language", "device_type"
    };
    
    private final Context context;
    private final Map<String, BiasMetrics> demographicMetrics;
    private final List<AIAppeal> appeals;
    
    public interface BiasDetectionListener {
        void onBiasDetected(String category, float biasLevel, String details);
        void onFairnessViolation(String category, float violationLevel);
        void onAppealSubmitted(String appealId, String reason);
        void onHumanReviewRequired(String decisionId, String reason);
    }
    
    private BiasDetectionListener listener;
    
    public AIBiasDetectionManager(Context context) {
        this.context = context;
        this.demographicMetrics = new HashMap<>();
        this.appeals = new ArrayList<>();
        initializeMetrics();
    }
    
    public void setBiasDetectionListener(BiasDetectionListener listener) {
        this.listener = listener;
    }
    
    private void initializeMetrics() {
        for (String category : DEMOGRAPHIC_CATEGORIES) {
            demographicMetrics.put(category, new BiasMetrics(category));
        }
    }
    
    /**
     * Analyze AI decision for potential bias
     */
    public BiasAnalysisResult analyzeDecision(String decisionId, String decisionType, 
                                               Map<String, String> userContext) {
        try {
            BiasAnalysisResult result = new BiasAnalysisResult(decisionId, decisionType);
            
            // Check for demographic bias
            for (String category : DEMOGRAPHIC_CATEGORIES) {
                String value = userContext.get(category);
                if (value != null) {
                    BiasMetrics metrics = demographicMetrics.get(category);
                    float biasLevel = calculateBiasLevel(category, value, decisionType);
                    
                    if (biasLevel > BIAS_THRESHOLD) {
                        result.addBiasIssue(category, biasLevel, 
                            "Potential bias detected in " + category + " category");
                        
                        if (listener != null) {
                            listener.onBiasDetected(category, biasLevel, 
                                "Decision type: " + decisionType);
                        }
                    }
                    
                    // Check fairness
                    float fairnessScore = calculateFairnessScore(category, value, decisionType);
                    if (fairnessScore < (1.0f - FAIRNESS_THRESHOLD)) {
                        result.addFairnessIssue(category, fairnessScore);
                        
                        if (listener != null) {
                            listener.onFairnessViolation(category, 1.0f - fairnessScore);
                        }
                    }
                }
            }
            
            // Log analysis
            logBiasAnalysis(decisionId, result);
            
            // Store metrics
            updateDemographicMetrics(userContext, decisionType, result);
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing AI decision for bias: " + e.getMessage());
            return new BiasAnalysisResult(decisionId, decisionType);
        }
    }
    
    /**
     * Submit an appeal for an AI decision
     */
    public String submitAppeal(String decisionId, String reason, String userFeedback) {
        try {
            String appealId = "APPEAL_" + System.currentTimeMillis();
            AIAppeal appeal = new AIAppeal(appealId, decisionId, reason, userFeedback);
            appeals.add(appeal);
            
            // Store appeal
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString("appeal_" + appealId, appeal.toJson())
                .putLong("appeal_count", prefs.getLong("appeal_count", 0) + 1)
                .apply();
            
            Log.i(TAG, "Appeal submitted: " + appealId + " for decision: " + decisionId);
            
            if (listener != null) {
                listener.onAppealSubmitted(appealId, reason);
            }
            
            // Flag for human review if multiple appeals
            if (appeals.size() >= 3) {
                requestHumanReview(decisionId, "Multiple appeals received");
            }
            
            return appealId;
            
        } catch (Exception e) {
            Log.e(TAG, "Error submitting appeal: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Request human review for an AI decision
     */
    public void requestHumanReview(String decisionId, String reason) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString("human_review_" + decisionId, reason)
                .putLong("human_review_count", prefs.getLong("human_review_count", 0) + 1)
                .apply();
            
            Log.i(TAG, "Human review requested for decision: " + decisionId + " - " + reason);
            
            if (listener != null) {
                listener.onHumanReviewRequired(decisionId, reason);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error requesting human review: " + e.getMessage());
        }
    }
    
    /**
     * Validate AI input to prevent adversarial prompts
     */
    public InputValidationResult validateInput(String input, String inputType) {
        InputValidationResult result = new InputValidationResult();
        
        // Check for malicious patterns
        String[] maliciousPatterns = {
            "javascript:", "data:", "vbscript:", "<script", "</script>",
            "eval(", "alert(", "confirm(", "prompt(",
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE"
        };
        
        String lowerInput = input.toLowerCase();
        for (String pattern : maliciousPatterns) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                result.addIssue("malicious_pattern", 
                    "Potentially malicious pattern detected: " + pattern);
                result.setValid(false);
            }
        }
        
        // Check input length
        if (input.length() > 10000) {
            result.addIssue("input_length", 
                "Input exceeds maximum length (10000 characters)");
            result.setValid(false);
        }
        
        // Check for inappropriate content
        String[] inappropriateTerms = {
            "hack", "exploit", "bypass", "override", "disable"
        };
        
        for (String term : inappropriateTerms) {
            if (lowerInput.contains(term) && inputType.equals("command")) {
                result.addIssue("inappropriate_content", 
                    "Potentially inappropriate content detected");
                result.setValid(false);
                break;
            }
        }
        
        return result;
    }
    
    /**
     * Validate AI output before displaying
     */
    public OutputValidationResult validateOutput(String output, String outputType) {
        OutputValidationResult result = new OutputValidationResult();
        
        // Check for inappropriate suggestions
        String[] inappropriateSuggestions = {
            "ignore", "disable", "bypass", "skip", "remove"
        };
        
        String lowerOutput = output.toLowerCase();
        for (String suggestion : inappropriateSuggestions) {
            if (lowerOutput.contains(suggestion) && 
                (outputType.equals("safety_decision") || outputType.equals("emergency_response"))) {
                result.addIssue("inappropriate_suggestion", 
                    "Potentially inappropriate suggestion: " + suggestion);
                result.setValid(false);
            }
        }
        
        // Check output length
        if (output.length() > 5000) {
            result.addIssue("output_length", 
                "Output exceeds maximum length (5000 characters)");
            result.setValid(false);
        }
        
        return result;
    }
    
    /**
     * Record feedback about AI errors or biases
     */
    public void recordFeedback(String decisionId, String feedbackType, 
                               String feedback, float severity) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long feedbackCount = prefs.getLong("feedback_count", 0) + 1;
            
            prefs.edit()
                .putString("feedback_" + feedbackCount, 
                    decisionId + "|" + feedbackType + "|" + feedback + "|" + severity)
                .putLong("feedback_count", feedbackCount)
                .apply();
            
            Log.i(TAG, "Feedback recorded: " + feedbackType + " for decision: " + decisionId);
            
            // If high severity, request human review
            if (severity > 0.7f) {
                requestHumanReview(decisionId, "High severity feedback: " + feedbackType);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error recording feedback: " + e.getMessage());
        }
    }
    
    /**
     * Perform bias audit across all demographic categories
     */
    public BiasAuditResult performBiasAudit() {
        BiasAuditResult audit = new BiasAuditResult();
        
        for (String category : DEMOGRAPHIC_CATEGORIES) {
            BiasMetrics metrics = demographicMetrics.get(category);
            float overallBias = metrics.calculateOverallBias();
            
            if (overallBias > BIAS_THRESHOLD) {
                audit.addBiasFinding(category, overallBias, 
                    "Bias detected in " + category + " category");
            }
        }
        
        Log.i(TAG, "Bias audit completed. Findings: " + audit.getFindingCount());
        return audit;
    }
    
    /**
     * Perform fairness testing
     */
    public FairnessTestResult performFairnessTest() {
        FairnessTestResult result = new FairnessTestResult();
        
        for (String category : DEMOGRAPHIC_CATEGORIES) {
            BiasMetrics metrics = demographicMetrics.get(category);
            float fairnessScore = metrics.calculateFairnessScore();
            
            if (fairnessScore < (1.0f - FAIRNESS_THRESHOLD)) {
                result.addFairnessIssue(category, fairnessScore, 
                    "Fairness violation in " + category + " category");
            }
        }
        
        Log.i(TAG, "Fairness test completed. Issues: " + result.getIssueCount());
        return result;
    }
    
    private float calculateBiasLevel(String category, String value, String decisionType) {
        // Simplified bias calculation
        // In production, this would use statistical analysis of historical decisions
        BiasMetrics metrics = demographicMetrics.get(category);
        return metrics.getBiasLevel(value, decisionType);
    }
    
    private float calculateFairnessScore(String category, String value, String decisionType) {
        BiasMetrics metrics = demographicMetrics.get(category);
        return metrics.getFairnessScore(value, decisionType);
    }
    
    private void updateDemographicMetrics(Map<String, String> userContext, 
                                         String decisionType, 
                                         BiasAnalysisResult result) {
        for (String category : DEMOGRAPHIC_CATEGORIES) {
            String value = userContext.get(category);
            if (value != null) {
                BiasMetrics metrics = demographicMetrics.get(category);
                metrics.recordDecision(value, decisionType, result.hasBias());
            }
        }
    }
    
    private void logBiasAnalysis(String decisionId, BiasAnalysisResult result) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long analysisCount = prefs.getLong("analysis_count", 0) + 1;
        
        prefs.edit()
            .putString("analysis_" + analysisCount, decisionId + "|" + result.toJson())
            .putLong("analysis_count", analysisCount)
            .apply();
    }
    
    // Inner classes for data structures
    public static class BiasMetrics {
        private final String category;
        private final Map<String, Map<String, Integer>> decisionCounts;
        private final Map<String, Integer> totalCounts;
        
        public BiasMetrics(String category) {
            this.category = category;
            this.decisionCounts = new HashMap<>();
            this.totalCounts = new HashMap<>();
        }
        
        public void recordDecision(String value, String decisionType, boolean hasBias) {
            decisionCounts.putIfAbsent(value, new HashMap<>());
            decisionCounts.get(value).put(decisionType, 
                decisionCounts.get(value).getOrDefault(decisionType, 0) + 1);
            totalCounts.put(value, totalCounts.getOrDefault(value, 0) + 1);
        }
        
        public float getBiasLevel(String value, String decisionType) {
            // Simplified calculation - in production would use statistical methods
            int count = decisionCounts.getOrDefault(value, new HashMap<>())
                .getOrDefault(decisionType, 0);
            int total = totalCounts.getOrDefault(value, 0);
            
            if (total == 0) return 0.0f;
            return Math.abs((float) count / total - 0.5f); // Deviation from expected 50%
        }
        
        public float getFairnessScore(String value, String decisionType) {
            // Calculate fairness as 1 - bias
            return 1.0f - getBiasLevel(value, decisionType);
        }
        
        public float calculateOverallBias() {
            float totalBias = 0.0f;
            int count = 0;
            for (Map<String, Integer> counts : decisionCounts.values()) {
                for (int decisionCount : counts.values()) {
                    totalBias += decisionCount;
                    count++;
                }
            }
            return count > 0 ? totalBias / count : 0.0f;
        }
        
        public float calculateFairnessScore() {
            return 1.0f - calculateOverallBias();
        }
    }
    
    public static class BiasAnalysisResult {
        private final String decisionId;
        private final String decisionType;
        private final List<BiasIssue> biasIssues;
        private final List<FairnessIssue> fairnessIssues;
        
        public BiasAnalysisResult(String decisionId, String decisionType) {
            this.decisionId = decisionId;
            this.decisionType = decisionType;
            this.biasIssues = new ArrayList<>();
            this.fairnessIssues = new ArrayList<>();
        }
        
        public void addBiasIssue(String category, float level, String details) {
            biasIssues.add(new BiasIssue(category, level, details));
        }
        
        public void addFairnessIssue(String category, float level) {
            fairnessIssues.add(new FairnessIssue(category, level));
        }
        
        public boolean hasBias() {
            return !biasIssues.isEmpty();
        }
        
        public String toJson() {
            return "{\"decisionId\":\"" + decisionId + "\",\"biasIssues\":" + 
                biasIssues.size() + ",\"fairnessIssues\":" + fairnessIssues.size() + "}";
        }
    }
    
    public static class BiasIssue {
        public final String category;
        public final float level;
        public final String details;
        
        public BiasIssue(String category, float level, String details) {
            this.category = category;
            this.level = level;
            this.details = details;
        }
    }
    
    public static class FairnessIssue {
        public final String category;
        public final float level;
        
        public FairnessIssue(String category, float level) {
            this.category = category;
            this.level = level;
        }
    }
    
    public static class AIAppeal {
        public final String appealId;
        public final String decisionId;
        public final String reason;
        public final String userFeedback;
        public final long timestamp;
        
        public AIAppeal(String appealId, String decisionId, String reason, String userFeedback) {
            this.appealId = appealId;
            this.decisionId = decisionId;
            this.reason = reason;
            this.userFeedback = userFeedback;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String toJson() {
            return "{\"appealId\":\"" + appealId + "\",\"decisionId\":\"" + decisionId + 
                "\",\"reason\":\"" + reason + "\",\"timestamp\":" + timestamp + "}";
        }
    }
    
    public static class InputValidationResult {
        private boolean valid = true;
        private final List<ValidationIssue> issues;
        
        public InputValidationResult() {
            this.issues = new ArrayList<>();
        }
        
        public void addIssue(String type, String message) {
            issues.add(new ValidationIssue(type, message));
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public boolean isValid() {
            return valid && issues.isEmpty();
        }
        
        public List<ValidationIssue> getIssues() {
            return issues;
        }
    }
    
    public static class OutputValidationResult {
        private boolean valid = true;
        private final List<ValidationIssue> issues;
        
        public OutputValidationResult() {
            this.issues = new ArrayList<>();
        }
        
        public void addIssue(String type, String message) {
            issues.add(new ValidationIssue(type, message));
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public boolean isValid() {
            return valid && issues.isEmpty();
        }
        
        public List<ValidationIssue> getIssues() {
            return issues;
        }
    }
    
    public static class ValidationIssue {
        public final String type;
        public final String message;
        
        public ValidationIssue(String type, String message) {
            this.type = type;
            this.message = message;
        }
    }
    
    public static class BiasAuditResult {
        private final List<BiasFinding> findings;
        
        public BiasAuditResult() {
            this.findings = new ArrayList<>();
        }
        
        public void addBiasFinding(String category, float level, String details) {
            findings.add(new BiasFinding(category, level, details));
        }
        
        public int getFindingCount() {
            return findings.size();
        }
        
        public List<BiasFinding> getFindings() {
            return findings;
        }
    }
    
    public static class BiasFinding {
        public final String category;
        public final float level;
        public final String details;
        
        public BiasFinding(String category, float level, String details) {
            this.category = category;
            this.level = level;
            this.details = details;
        }
    }
    
    public static class FairnessTestResult {
        private final List<FairnessIssue> issues;
        
        public FairnessTestResult() {
            this.issues = new ArrayList<>();
        }
        
        public void addFairnessIssue(String category, float score, String details) {
            issues.add(new FairnessIssue(category, score));
        }
        
        public int getIssueCount() {
            return issues.size();
        }
        
        public List<FairnessIssue> getIssues() {
            return issues;
        }
    }
}




