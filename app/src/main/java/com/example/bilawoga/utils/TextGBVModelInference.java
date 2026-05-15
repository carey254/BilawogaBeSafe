package com.example.bilawoga.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ML-based Text GBV Detection using trained TFLite model.
 *
 * Uses the trained gbv_text_model.tflite to classify conversations into
 * 4 categories: abuse, distress, emergency, normal.
 *
 * Input: 100-dimensional feature vector extracted from conversation text
 * Output: 4-class softmax probabilities [abuse, distress, emergency, normal]
 *
 * Feature extraction matches the Python training pipeline exactly.
 */
public class TextGBVModelInference {
    private static final String TAG = "TextGBVModelInference";
    private static final String MODEL_FILE = "gbv_text_model.tflite";

    private static final String[] CLASS_NAMES = {"abuse", "distress", "emergency", "normal"};

    // Keyword lists matching the Python training script
    private static final String[] ABUSE_WORDS = {
        "controlled", "trapped", "watched", "freedom", "safe", "danger",
        "threat", "scared", "afraid", "unsafe", "threatened"
    };
    private static final String[] DISTRESS_WORDS = {
        "stress", "weird", "overthinking", "difficult", "tense",
        "worried", "uneasy", "anxious", "not myself"
    };
    private static final String[] CONTROL_WORDS = {
        "have to ask", "can't decide", "don't have freedom",
        "controlled", "need permission", "not allowed", "must get approval"
    };
    private static final String[] URGENCY_WORDS = {
        "help", "emergency", "urgent", "immediate", "now",
        "please help", "save me", "danger", "sos"
    };
    private static final String[] ISOLATION_WORDS = {
        "can't talk to", "not allowed to see", "cut off", "isolated",
        "alone", "no contact", "forbidden", "restricted"
    };
    private static final String[] MANIPULATION_WORDS = {
        "it's my fault", "i deserved it", "i'm overthinking",
        "maybe i'm wrong", "it's not that bad", "i'm being dramatic"
    };
    private static final String[] VIOLENCE_WORDS = {
        "hit", "beat", "punch", "slap", "kick", "hurt", "pain",
        "bruise", "bleed", "weapon", "knife", "gun"
    };
    private static final String[] EMERGENCY_PHRASES = {
        "not safe", "in danger", "being followed", "someone is here",
        "help me", "call police", "hear something", "break in",
        "attacked", "assaulted"
    };
    private static final String[] NEGATIVE_WORDS = {
        "not", "no", "don't", "can't", "won't", "never", "nothing", "nowhere"
    };
    private static final String[] POSITIVE_WORDS = {
        "fine", "okay", "good", "normal", "talked", "resolved", "better"
    };
    private static final String[] FEAR_WORDS = {
        "scared", "afraid", "terrified", "panic", "fear", "frightened"
    };
    private static final String[] ANGER_WORDS = {
        "angry", "furious", "mad", "rage", "violent", "aggressive"
    };
    private static final String[] BIGRAMS = {
        "i feel", "feel safe", "not safe", "let me", "can't go",
        "have to", "told me", "makes me", "won't let", "doesn't let",
        "not normal", "something wrong", "feels wrong", "doesn't feel",
        "need help", "get out", "run away", "locked in", "no one",
        "all alone"
    };
    private static final String[] INTENSITY_WORDS = {
        "very", "really", "extremely", "so much", "terrible",
        "horrible", "worst", "unbearable", "desperate", "critical"
    };

    private final Context context;
    private Interpreter interpreter;
    private boolean isModelLoaded = false;

    public static class PredictionResult {
        public String predictedClass;
        public float confidence;
        public float[] probabilities;
        public boolean isGBVDetected;

        public PredictionResult() {
            this.probabilities = new float[4];
        }
    }

    public TextGBVModelInference(Context context) {
        this.context = context;
        loadModel();
    }

    private void loadModel() {
        try {
            MappedByteBuffer modelBuffer = loadModelFile();
            if (modelBuffer != null) {
                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(2);
                interpreter = new Interpreter(modelBuffer, options);

                int[] inputShape = interpreter.getInputTensor(0).shape();
                int[] outputShape = interpreter.getOutputTensor(0).shape();

                if (inputShape.length == 2 && inputShape[1] == 100 &&
                    outputShape.length == 2 && outputShape[1] == 4) {
                    isModelLoaded = true;
                    Log.i(TAG, "Text GBV model loaded. Input: " + Arrays.toString(inputShape) +
                              ", Output: " + Arrays.toString(outputShape));
                } else {
                    Log.e(TAG, "Unexpected model shape. Input: " + Arrays.toString(inputShape) +
                              ", Output: " + Arrays.toString(outputShape));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load text GBV model: " + e.getMessage(), e);
        }
    }

    private MappedByteBuffer loadModelFile() {
        try {
            AssetFileDescriptor afd = context.getAssets().openFd(MODEL_FILE);
            FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
            FileChannel fc = fis.getChannel();
            long startOffset = afd.getStartOffset();
            long declaredLength = afd.getDeclaredLength();
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            fis.close();
            afd.close();
            return buffer;
        } catch (IOException e) {
            Log.e(TAG, "Error loading model file: " + e.getMessage(), e);
            return null;
        }
    }

    public boolean isReady() {
        return isModelLoaded;
    }

    /**
     * Classify a conversation text.
     */
    public PredictionResult predict(String conversation) {
        PredictionResult result = new PredictionResult();

        if (!isModelLoaded || interpreter == null) {
            result.predictedClass = "unknown";
            result.confidence = 0.0f;
            result.isGBVDetected = false;
            return result;
        }

        try {
            float[] features = extractFeatures(conversation);
            float[][] input = new float[1][100];
            System.arraycopy(features, 0, input[0], 0, 100);

            float[][] output = new float[1][4];
            interpreter.run(input, output);

            result.probabilities = output[0];

            int maxIdx = 0;
            float maxProb = output[0][0];
            for (int i = 1; i < 4; i++) {
                if (output[0][i] > maxProb) {
                    maxProb = output[0][i];
                    maxIdx = i;
                }
            }

            result.predictedClass = CLASS_NAMES[maxIdx];
            result.confidence = maxProb;
            result.isGBVDetected = !result.predictedClass.equals("normal");

            Log.d(TAG, "Prediction: " + result.predictedClass +
                       " (conf=" + String.format("%.3f", result.confidence) +
                       ") [abuse=" + String.format("%.3f", output[0][0]) +
                       " distress=" + String.format("%.3f", output[0][1]) +
                       " emergency=" + String.format("%.3f", output[0][2]) +
                       " normal=" + String.format("%.3f", output[0][3]) + "]");

        } catch (Exception e) {
            Log.e(TAG, "Prediction error: " + e.getMessage(), e);
            result.predictedClass = "unknown";
            result.confidence = 0.0f;
        }

        return result;
    }

    /**
     * Extract 100-dimensional feature vector matching the Python training pipeline.
     */
    private float[] extractFeatures(String conversation) {
        float[] features = new float[100];
        String lower = conversation.toLowerCase();

        // Word category counts (features 0-7)
        features[0] = countOccurrences(lower, ABUSE_WORDS);
        features[1] = countOccurrences(lower, DISTRESS_WORDS);
        features[2] = countOccurrences(lower, CONTROL_WORDS);
        features[3] = countOccurrences(lower, URGENCY_WORDS);
        features[4] = countOccurrences(lower, ISOLATION_WORDS);
        features[5] = countOccurrences(lower, MANIPULATION_WORDS);
        features[6] = countOccurrences(lower, VIOLENCE_WORDS);
        features[7] = countOccurrences(lower, EMERGENCY_PHRASES);

        // Text statistics (features 8-14)
        features[8] = conversation.length() / 1000.0f;
        String[] words = lower.split("\\s+");
        features[9] = words.length / 200.0f;
        features[10] = countChar(lower, '?') / 10.0f;
        features[11] = countChar(lower, '!') / 10.0f;
        features[12] = countSubstring(lower, "...") / 5.0f;
        features[13] = countChar(lower, '|') / 20.0f;
        features[14] = countSubstring(lower, "U:") / 10.0f;

        // Sentiment indicators (features 15-18)
        features[15] = countOccurrences(lower, NEGATIVE_WORDS);
        features[16] = countOccurrences(lower, POSITIVE_WORDS);
        features[17] = countOccurrences(lower, FEAR_WORDS);
        features[18] = countOccurrences(lower, ANGER_WORDS);

        // Relationship context (features 19-23)
        features[19] = (lower.contains("partner") || lower.contains("relationship")) ? 1.0f : 0.0f;
        features[20] = (lower.contains("family") || lower.contains("home") || lower.contains("house")) ? 1.0f : 0.0f;
        features[21] = (lower.contains("public") || lower.contains("outside") || lower.contains("street")) ? 1.0f : 0.0f;
        features[22] = (lower.contains("work") || lower.contains("office") || lower.contains("boss")) ? 1.0f : 0.0f;
        features[23] = (lower.contains("school") || lower.contains("teacher")) ? 1.0f : 0.0f;

        // Escalation indicators (features 24-29)
        features[24] = (lower.contains("please help") || lower.contains("help me")) ? 1.0f : 0.0f;
        features[25] = (lower.contains("not safe") || lower.contains("unsafe")) ? 1.0f : 0.0f;
        features[26] = (lower.contains("call police") || lower.contains("call 911")) ? 1.0f : 0.0f;
        features[27] = lower.contains("being followed") ? 1.0f : 0.0f;
        features[28] = (lower.contains("someone is") || lower.contains("hear something")) ? 1.0f : 0.0f;
        features[29] = (lower.contains("break") || lower.contains("weapon")) ? 1.0f : 0.0f;

        // Bigram features (features 30-49)
        for (int i = 0; i < BIGRAMS.length && i < 20; i++) {
            features[30 + i] = countSubstring(lower, BIGRAMS[i]);
        }

        // Conversation dynamics (features 50-54)
        String[] turns = conversation.split("\\|");
        int userTurns = 0;
        int assistantTurns = 0;
        int totalUserLen = 0;
        Set<String> uniqueUserTexts = new HashSet<>();
        for (String turn : turns) {
            String trimmed = turn.trim();
            if (trimmed.startsWith("U:")) {
                userTurns++;
                totalUserLen += trimmed.length();
                uniqueUserTexts.add(trimmed.replace("U:", "").trim().toLowerCase());
            } else if (trimmed.startsWith("A:")) {
                assistantTurns++;
            }
        }
        features[50] = userTurns / 10.0f;
        features[51] = assistantTurns / 10.0f;
        features[52] = assistantTurns > 0 ? (float) userTurns / assistantTurns : 0.0f;
        features[53] = userTurns > 0 ? (totalUserLen / (float) userTurns) / 100.0f : 0.0f;
        features[54] = userTurns > 0 ? 1.0f - (float) uniqueUserTexts.size() / userTurns : 0.0f;

        // Emotional intensity (features 60-69)
        for (int i = 0; i < INTENSITY_WORDS.length && i < 10; i++) {
            features[60 + i] = lower.contains(INTENSITY_WORDS[i]) ? 1.0f : 0.0f;
        }

        // Normalize first 8 features (category counts)
        for (int i = 0; i < 8; i++) {
            features[i] = Math.min(1.0f, features[i] / 10.0f);
        }

        // Normalize sentiment counts
        for (int i = 15; i < 19; i++) {
            features[i] = Math.min(1.0f, features[i] / 10.0f);
        }

        return features;
    }

    private float countOccurrences(String text, String[] words) {
        float count = 0;
        for (String word : words) {
            int idx = 0;
            while ((idx = text.indexOf(word, idx)) != -1) {
                count++;
                idx += word.length();
            }
        }
        return count;
    }

    private float countChar(String text, char c) {
        float count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) count++;
        }
        return count;
    }

    private float countSubstring(String text, String sub) {
        float count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            isModelLoaded = false;
        }
    }
}
