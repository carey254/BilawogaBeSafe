package com.example.bilawoga.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SYNTHETIC AUDIO GENERATOR
 * 
 * Converts text conversations to synthetic audio for training
 * Generates realistic audio patterns from GBV text data
 */
public class SyntheticAudioGenerator {
    private static final String TAG = "SyntheticAudioGenerator";
    
    private final Context context;
    private ExecutorService executorService;
    private File outputDirectory;
    
    // Audio parameters
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = 1024;
    
    // Voice synthesis parameters
    private static final int EMERGENCY_PITCH = 200;    // Higher pitch for distress
    private static final int NORMAL_PITCH = 150;       // Normal pitch
    private static final int EMERGENCY_VOLUME = 80;     // Louder for emergency
    private static final int NORMAL_VOLUME = 60;        // Normal volume
    
    public interface GenerationListener {
        void onAudioGenerated(String filePath, String label);
        void onGenerationComplete(int totalFiles);
        void onGenerationError(String error);
    }
    
    private GenerationListener listener;
    
    public SyntheticAudioGenerator(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        
        // Create output directory
        outputDirectory = new File(context.getFilesDir(), "synthetic_audio");
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        Log.i(TAG, "Synthetic Audio Generator initialized");
    }
    
    /**
     * Generate synthetic audio from text conversation
     */
    public String generateAudioFromText(String conversation, String label, String sampleId) {
        try {
            Log.d(TAG, "Generating synthetic audio for: " + label + " - " + sampleId);
            
            // Parse conversation into segments
            String[] segments = parseConversation(conversation);
            
            // Generate audio file path
            String fileName = "synthetic_" + label + "_" + sampleId + ".wav";
            String filePath = new File(outputDirectory, fileName).getAbsolutePath();
            
            // Generate synthetic audio
            generateSyntheticWavFile(segments, label, filePath);
            
            Log.d(TAG, "Generated synthetic audio: " + filePath);
            
            if (listener != null) {
                listener.onAudioGenerated(filePath, label);
            }
            
            return filePath;
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating synthetic audio: " + e.getMessage(), e);
            if (listener != null) {
                listener.onGenerationError(e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Parse conversation into speaking segments
     */
    private String[] parseConversation(String conversation) {
        // Split by speaker markers (U: and A:)
        String[] parts = conversation.split("\\|");
        
        // Clean up each part
        String[] segments = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            segments[i] = parts[i].trim();
            // Remove speaker markers
            segments[i] = segments[i].replaceAll("^[UA]:\\s*", "");
        }
        
        return segments;
    }
    
    /**
     * Generate synthetic WAV file from text segments
     */
    private void generateSyntheticWavFile(String[] segments, String label, String outputPath) throws IOException {
        FileOutputStream fos = new FileOutputStream(outputPath);
        
        // Calculate total audio data size
        int totalSamples = 0;
        for (String segment : segments) {
            totalSamples += calculateAudioDuration(segment) * SAMPLE_RATE;
        }
        
        int dataSize = totalSamples * 2; // 16-bit samples
        int fileLength = 44 + dataSize; // WAV header + data
        
        // Write WAV header
        writeWavHeader(fos, fileLength, dataSize);
        
        // Generate and write audio data for each segment
        Random random = new Random();
        
        for (String segment : segments) {
            int segmentDuration = calculateAudioDuration(segment);
            int segmentSamples = segmentDuration * SAMPLE_RATE;
            
            // Determine audio characteristics based on label
            int pitch = getPitchForLabel(label);
            int volume = getVolumeForLabel(label);
            
            // Generate synthetic audio samples
            for (int i = 0; i < segmentSamples; i++) {
                short sample = generateSyntheticSample(i, segmentSamples, pitch, volume, random);
                
                // Write 16-bit little-endian sample
                fos.write(sample & 0xFF);
                fos.write((sample >> 8) & 0xFF);
            }
        }
        
        fos.close();
        Log.d(TAG, "Synthetic WAV file generated: " + outputPath + 
                  " (" + segments.length + " segments, " + totalSamples + " samples)");
    }
    
    /**
     * Write WAV file header
     */
    private void writeWavHeader(FileOutputStream fos, int fileLength, int dataSize) throws IOException {
        // RIFF header
        fos.write("RIFF".getBytes());
        fos.write(intToByteArray(fileLength - 8));
        fos.write("WAVE".getBytes());
        
        // fmt chunk
        fos.write("fmt ".getBytes());
        fos.write(intToByteArray(16)); // chunk size
        fos.write(shortToByteArray((short) 1)); // audio format (PCM)
        fos.write(shortToByteArray((short) 1)); // channels (mono)
        fos.write(intToByteArray(SAMPLE_RATE)); // sample rate
        fos.write(intToByteArray(SAMPLE_RATE * 2)); // byte rate
        fos.write(shortToByteArray((short) 2)); // block align
        fos.write(shortToByteArray((short) 16)); // bits per sample
        
        // data chunk
        fos.write("data".getBytes());
        fos.write(intToByteArray(dataSize));
    }
    
    /**
     * Generate synthetic audio sample
     */
    private short generateSyntheticSample(int sampleIndex, int totalSamples, int pitch, int volume, Random random) {
        // Generate synthetic speech-like waveform
        double time = (double) sampleIndex / SAMPLE_RATE;
        
        // Base frequency (pitch)
        double frequency = pitch;
        
        // Amplitude modulation for speech-like quality
        double amplitude = volume / 100.0;
        
        // Add formants for more realistic speech
        double sample = 0.0;
        
        // Fundamental frequency
        sample += amplitude * Math.sin(2 * Math.PI * frequency * time);
        
        // Add harmonics
        sample += amplitude * 0.3 * Math.sin(4 * Math.PI * frequency * time);
        sample += amplitude * 0.2 * Math.sin(6 * Math.PI * frequency * time);
        
        // Add amplitude envelope (attack/decay)
        double envelope = 1.0;
        if (sampleIndex < totalSamples * 0.1) {
            // Attack
            envelope = (double) sampleIndex / (totalSamples * 0.1);
        } else if (sampleIndex > totalSamples * 0.9) {
            // Decay
            envelope = (double) (totalSamples - sampleIndex) / (totalSamples * 0.1);
        }
        
        sample *= envelope;
        
        // Add small noise for realism
        sample += (random.nextGaussian() * 0.05);
        
        // Clamp to 16-bit range
        sample = Math.max(-1.0, Math.min(1.0, sample));
        
        return (short) (sample * Short.MAX_VALUE);
    }
    
    /**
     * Calculate audio duration for text segment (in seconds)
     */
    private int calculateAudioDuration(String text) {
        // Rough estimate: 150 words per minute, 2.5 characters per word
        int estimatedWords = Math.max(1, text.length() / 3);
        return Math.max(1, (estimatedWords * 60) / 150); // Minimum 1 second
    }
    
    /**
     * Get pitch frequency based on label
     */
    private int getPitchForLabel(String label) {
        switch (label.toLowerCase()) {
            case "abuse":
            case "distress":
                return EMERGENCY_PITCH + 50; // Higher pitch for distress
            case "normal":
                return NORMAL_PITCH;
            default:
                return NORMAL_PITCH + 25;
        }
    }
    
    /**
     * Get volume level based on label
     */
    private int getVolumeForLabel(String label) {
        switch (label.toLowerCase()) {
            case "abuse":
            case "distress":
                return EMERGENCY_VOLUME; // Louder for emergency
            case "normal":
                return NORMAL_VOLUME;
            default:
                return NORMAL_VOLUME + 10;
        }
    }
    
    /**
     * Generate synthetic audio dataset from all samples
     */
    public void generateDatasetFromCSV(CSVTrainingDataManager csvManager, GenerationListener listener) {
        this.listener = listener;
        
        executorService.execute(() -> {
            try {
                Log.i(TAG, "Starting synthetic audio dataset generation...");
                
                if (csvManager == null) {
                    Log.e(TAG, "CSV manager is null");
                    return;
                }
                
                List<CSVTrainingDataManager.TrainingSample> samples = csvManager.getAllSamples();
                int generatedCount = 0;
                
                for (int i = 0; i < samples.size(); i++) {
                    CSVTrainingDataManager.TrainingSample sample = samples.get(i);
                    String audioPath = generateAudioFromText(
                        sample.conversation, 
                        sample.label, 
                        sample.id
                    );
                    
                    if (audioPath != null) {
                        generatedCount++;
                    }
                    
                    // Log progress every 100 files
                    if (generatedCount % 100 == 0) {
                        Log.d(TAG, "Generated " + generatedCount + "/" + samples.size() + " synthetic audio files");
                    }
                }
                
                Log.i(TAG, "Synthetic audio generation complete: " + generatedCount + " files");
                
                if (listener != null) {
                    listener.onGenerationComplete(generatedCount);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating synthetic dataset: " + e.getMessage(), e);
                if (listener != null) {
                    listener.onGenerationError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * Get generated audio files count
     */
    public int getGeneratedAudioCount() {
        if (outputDirectory.exists()) {
            File[] files = outputDirectory.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".wav"));
            return files != null ? files.length : 0;
        }
        return 0;
    }
    
    /**
     * Clean up generated audio files
     */
    public void cleanupGeneratedFiles() {
        if (outputDirectory.exists()) {
            File[] files = outputDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted synthetic audio file: " + file.getName());
                    }
                }
            }
        }
    }
    
    /**
     * Utility methods for byte conversion
     */
    private byte[] intToByteArray(int value) {
        return new byte[] {
            (byte)(value & 0xFF),
            (byte)((value >> 8) & 0xFF),
            (byte)((value >> 16) & 0xFF),
            (byte)((value >> 24) & 0xFF)
        };
    }
    
    private byte[] shortToByteArray(short value) {
        return new byte[] {
            (byte)(value & 0xFF),
            (byte)((value >> 8) & 0xFF)
        };
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdown();
        }
        Log.i(TAG, "Synthetic Audio Generator cleaned up");
    }
}
