package com.example.bilawoga.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.app.PendingIntent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.bilawoga.safety.R;
import com.example.bilawoga.CountdownActivity;
import com.example.bilawoga.utils.EmergencyAudioTransmitter;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundAudioMonitor extends Service {
    private static final String TAG = "BackgroundAudioMonitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "BackgroundAudioMonitor";
    private static final String ACTION_PAUSE = "com.example.bilawoga.action.PAUSE_MONITORING";
    private static final String ACTION_STOP = "com.example.bilawoga.action.STOP_MONITORING";
    
    // Audio recording parameters
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    
    // Emergency detection parameters - tuned to avoid baby cries and family arguments
    private static final int EMERGENCY_THRESHOLD = 85; // Higher threshold to avoid normal sounds
    private static final int CRYING_FREQUENCY_MIN = 300; // Hz - adjusted to focus on adult distress
    private static final int CRYING_FREQUENCY_MAX = 600; // Hz - avoid baby crying range
    private static final int SCREAMING_FREQUENCY_MIN = 1000; // Hz - screaming frequency range
    private static final int SCREAMING_FREQUENCY_MAX = 2500; // Hz
    private static final long EMERGENCY_CONFIRMATION_TIME = 1000; // 1 second - immediate response when danger suspected
    private static final int MIN_EMERGENCY_DURATION = 1000; // Must last at least 1 second
    private static final long MAX_RECORDING_DURATION = 10 * 60 * 1000; // 10 minutes maximum recording
    private static final long MIN_RECORDING_DURATION = 1000; // 1 second minimum recording
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ExecutorService executorService;
    private Handler mainHandler;
    private EmergencySoundDetector soundDetector;
    private long lastEmergencyTime = 0;
    private boolean emergencyConfirmed = false;
    
    // Require multiple consecutive detections before sending (prevents false alarms)
    private int consecutiveEmergencyCount = 0;
    private static final int REQUIRED_CONSECUTIVE_EMERGENCY = 3; // Need 3 consecutive detections
    
    // Emergency detection callbacks
    public interface EmergencyListener {
        void onEmergencyDetected(String type, float confidence);
        void onEmergencyConfirmed(String type);
        void onFalseAlarmPrevented(String reason);
    }
    
    private EmergencyListener emergencyListener;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BackgroundAudioMonitor service created");
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        soundDetector = new EmergencySoundDetector();
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BackgroundAudioMonitor service started");
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PAUSE.equals(action)) {
                Log.d(TAG, "Action: PAUSE monitoring");
                stopAudioMonitoring();
                startForeground(NOTIFICATION_ID, createNotification());
                return START_STICKY;
            } else if (ACTION_STOP.equals(action)) {
                Log.d(TAG, "Action: STOP monitoring and stopSelf");
                stopAudioMonitoring();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent != null && intent.hasExtra("emergency_listener")) {
            // Start monitoring immediately
            startAudioMonitoring();
        }

        return START_STICKY; // Restart service if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Start background audio monitoring
     */
    public void startAudioMonitoring() {
        if (isRecording) {
            Log.w(TAG, "Audio monitoring already active");
            return;
        }
        
        Log.d(TAG, "Starting background audio monitoring");
        isRecording = true;
        
        executorService.execute(() -> {
            try {
                // Check permission before creating AudioRecord
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "RECORD_AUDIO permission not granted");
                    return;
                }
                
                audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
                );
                
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed");
                    return;
                }
                
                audioRecord.startRecording();
                Log.d(TAG, "Audio recording started - continuous monitoring (1s to 10min)");
                
                // Start continuous recording buffer
                java.util.List<byte[]> audioChunks = new java.util.ArrayList<>();
                long recordingStartTime = System.currentTimeMillis();
                byte[] audioBuffer = new byte[BUFFER_SIZE];
                
                while (isRecording) {
                    int readSize = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
                    if (readSize > 0) {
                        // Store audio chunk for continuous recording
                        byte[] chunk = new byte[readSize];
                        System.arraycopy(audioBuffer, 0, chunk, 0, readSize);
                        audioChunks.add(chunk);
                        
                        // Analyze audio in real-time (continuous recording 1s to 10min)
                        analyzeAudioData(audioBuffer, readSize, audioChunks, recordingStartTime);
                        
                        // VOICE LEARNING: Learn from audio patterns
                        learnFromAudio(audioBuffer, readSize);
                        
                        // Limit recording to 10 minutes maximum
                        if (System.currentTimeMillis() - recordingStartTime > MAX_RECORDING_DURATION) {
                            Log.d(TAG, "Maximum recording duration reached (10 minutes)");
                            break;
                        }
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in audio monitoring: " + e.getMessage());
            } finally {
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                }
            }
        });
    }
    
    /**
     * Stop background audio monitoring
     */
    public void stopAudioMonitoring() {
        Log.d(TAG, "Stopping background audio monitoring");
        isRecording = false;
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }
    
    /**
     * Analyze audio data for emergency sounds
     * ENHANCED: Continuous recording with immediate SOS on danger detection
     */
    private void analyzeAudioData(byte[] audioData, int readSize, java.util.List<byte[]> audioChunks, long recordingStartTime) {
        // Convert byte array to short array for analysis
        short[] samples = new short[readSize / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((audioData[i * 2] & 0xFF) | (audioData[i * 2 + 1] << 8));
        }
        
        // Calculate audio levels and frequency analysis
        double rms = calculateRMS(samples);
        double db = 20 * Math.log10(rms / 32767.0);
        
        // Frequency analysis for crying/screaming detection
        double[] frequencies = performFFT(samples);
        
        // Check for emergency conditions
        EmergencyDetectionResult result = soundDetector.detectEmergency(db, frequencies);
        
        // AI EMERGENCY DETECTION: Only send if we have multiple consecutive detections
        // This prevents false alarms from background noise or brief sounds
        if (result.isEmergency) {
            consecutiveEmergencyCount++;
            Log.d(TAG, "Emergency sound detected (" + result.type + ", confidence: " + result.confidence + 
                      ") - consecutive count: " + consecutiveEmergencyCount + "/" + REQUIRED_CONSECUTIVE_EMERGENCY);
            
            // Only send if we have required consecutive detections
            if (consecutiveEmergencyCount >= REQUIRED_CONSECUTIVE_EMERGENCY) {
                long currentTime = System.currentTimeMillis();
                long recordingDuration = currentTime - recordingStartTime;
                
                // Ensure minimum 1 second of recording before sending
                if (recordingDuration >= MIN_RECORDING_DURATION) {
                    Log.d(TAG, "🚨 CONFIRMED: Multiple consecutive emergency detections - sending SOS");
                    handleEmergencyDetectionImmediate(result.type, result.confidence, audioChunks, recordingStartTime);
                    consecutiveEmergencyCount = 0; // Reset after sending
                }
            }
        } else {
            // Reset counter if no emergency detected
            if (consecutiveEmergencyCount > 0) {
                Log.d(TAG, "Emergency detection interrupted - resetting counter");
                consecutiveEmergencyCount = 0;
            }
        }
    }
    
    /**
     * Calculate RMS (Root Mean Square) of audio samples
     */
    private double calculateRMS(short[] samples) {
        double sum = 0;
        for (short sample : samples) {
            sum += sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }
    
    /**
     * Perform FFT for frequency analysis
     */
    private double[] performFFT(short[] samples) {
        // Simple frequency analysis - in a real implementation, you'd use a proper FFT library
        // This is a simplified version for demonstration
        double[] frequencies = new double[samples.length / 2];
        
        for (int i = 0; i < frequencies.length; i++) {
            double sum = 0;
            for (int j = 0; j < samples.length; j++) {
                sum += samples[j] * Math.cos(2 * Math.PI * i * j / samples.length);
            }
            frequencies[i] = Math.abs(sum);
        }
        
        return frequencies;
    }
    
    /**
     * Handle emergency sound detection - IMMEDIATE RESPONSE
     * Sends SOS immediately when danger is suspected (1 second minimum recording)
     */
    private void handleEmergencyDetectionImmediate(String type, float confidence, 
                                                   java.util.List<byte[]> audioChunks, 
                                                   long recordingStartTime) {
        long currentTime = System.currentTimeMillis();
        
        // Prevent multiple triggers within short time (reduced to 2 seconds for faster response)
        if (currentTime - lastEmergencyTime < 2000) {
            return;
        }
        
        lastEmergencyTime = currentTime;
        
        Log.d(TAG, "🚨 IMMEDIATE: Emergency suspected - " + type + " (confidence: " + confidence + ")");
        Log.d(TAG, "Sending SOS immediately - no confirmation delay");
        
        // Notify listener immediately
        if (emergencyListener != null) {
            mainHandler.post(() -> emergencyListener.onEmergencyDetected(type, confidence));
        }
        
        // AUTOMATIC SOS DISABLED: Only log detection, do not send automatically
        // User must manually press "Send Alert" button to send SOS
        long recordingDuration = currentTime - recordingStartTime;
        if (recordingDuration >= MIN_RECORDING_DURATION) {
            emergencyConfirmed = true;
            
            // Combine audio chunks into single recording
            byte[] fullRecording = combineAudioChunks(audioChunks);
            
            // AUTOMATIC SOS DISABLED: Only log, do not send automatically
            Log.d(TAG, "⚠️ Emergency detected but automatic SOS is disabled. User must manually send SOS.");
            // sendImmediateSOSWithRecording(type, confidence, fullRecording, recordingDuration); // DISABLED
            
            if (emergencyListener != null) {
                mainHandler.post(() -> emergencyListener.onEmergencyConfirmed(type));
            }
        }
    }
    
    /**
     * Combine audio chunks into single recording
     */
    private byte[] combineAudioChunks(java.util.List<byte[]> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new byte[0];
        }
        
        int totalSize = 0;
        for (byte[] chunk : chunks) {
            totalSize += chunk.length;
        }
        
        byte[] combined = new byte[totalSize];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, combined, offset, chunk.length);
            offset += chunk.length;
        }
        
        return combined;
    }
    
    /**
     * Send SOS immediately with recording (1s to 10min)
     * DISABLED: Automatic SOS sending is disabled - user must manually send
     */
    private void sendImmediateSOSWithRecording(String emergencyType, float confidence, 
                                               byte[] audioData, long recordingDuration) {
        // AUTOMATIC SOS DISABLED: This method is disabled to prevent automatic sends
        // User must manually press "Send Alert" button to send SOS
        Log.d(TAG, "⚠️ AUTOMATIC SOS DISABLED: Emergency detected (" + emergencyType + 
              ", confidence: " + confidence + ") but not sending automatically. User must manually send SOS.");
        
        // Save audio recording for potential manual review (but don't send automatically)
        try {
            String audioFilePath = saveAudioRecording(audioData, recordingDuration);
            if (audioFilePath != null && !audioFilePath.isEmpty()) {
                Log.d(TAG, "Audio recording saved (not sent): " + audioFilePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving audio recording: " + e.getMessage());
        }
        
        // DO NOT SEND SOS AUTOMATICALLY - User must manually press "Send Alert" button
        /*
        // Get emergency contacts from secure storage
        String userName = SecureStorageManager.getEncryptedSharedPreferences(this)
            .getString("USERNAME", "Unknown User");
        String emergencyNumber1 = SecureStorageManager.getEncryptedSharedPreferences(this)
            .getString("ENUM_1", "");
        String emergencyNumber2 = SecureStorageManager.getEncryptedSharedPreferences(this)
            .getString("ENUM_2", "");
        
        // Create enhanced incident type
        String incidentType = "AI Detected Emergency (Immediate): " + emergencyType;
        
        // Send SOS message immediately
        SOSHelper sosHelper = new SOSHelper(this);
        sosHelper.sendEmergencySOS(userName, incidentType, emergencyNumber1, emergencyNumber2);
        */
    }
    
    /**
     * Save audio recording to file
     */
    private String saveAudioRecording(byte[] audioData, long duration) {
        try {
            java.io.File audioDir = new java.io.File(getFilesDir(), "emergency_audio");
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(new java.util.Date());
            String durationStr = String.format(Locale.US, "%ds", duration / 1000);
            java.io.File audioFile = new java.io.File(audioDir, "emergency_" + timestamp + "_" + durationStr + ".raw");
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(audioFile);
            fos.write(audioData);
            fos.close();
            
            Log.d(TAG, "Audio saved: " + audioFile.getAbsolutePath() + " (" + (audioData.length / 1024) + " KB)");
            return audioFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving audio: " + e.getMessage());
            return null;
        }
    }

    private void launchConfirmationCountdown(String emergencyType) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            String userName = prefs != null ? prefs.getString("USERNAME", "Unknown User") : "Unknown User";
            String emergencyNumber1 = prefs != null ? prefs.getString("ENUM_1", "") : "";
            String emergencyNumber2 = prefs != null ? prefs.getString("ENUM_2", "") : "";

            Intent i = new Intent(getApplicationContext(), CountdownActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(CountdownActivity.EXTRA_USER, userName);
            i.putExtra(CountdownActivity.EXTRA_INCIDENT, "AI Detected Emergency: " + emergencyType);
            i.putExtra(CountdownActivity.EXTRA_EM1, emergencyNumber1);
            i.putExtra(CountdownActivity.EXTRA_EM2, emergencyNumber2);
            startActivity(i);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to launch countdown: " + t.getMessage());
        }
    }
    
    /**
     * Send automatic SOS without user interaction
     * DISABLED: Automatic SOS sending is disabled - user must manually send
     * Background AI monitoring continues but does not send automatically
     */
    private void sendAutomaticSOS(String emergencyType) {
        // AUTOMATIC SOS DISABLED: This method is disabled to prevent automatic sends
        // User must manually press "Send Alert" button to send SOS
        Log.d(TAG, "⚠️ AUTOMATIC SOS DISABLED: Emergency detected (" + emergencyType + 
              ") but not sending automatically. User must manually send SOS.");
        
        // DO NOT SEND SOS AUTOMATICALLY - User must manually press "Send Alert" button
        /*
        Log.d(TAG, "EMERGENCY BYPASS: Sending automatic SOS for: " + emergencyType);
        
        // Get emergency contacts from secure storage
        String userName = SecureStorageManager.getEncryptedSharedPreferences(this)
            .getString("USERNAME", "Unknown User");
        String emergencyNumber1 = SecureStorageManager.getEncryptedSharedPreferences(this)
            .getString("ENUM_1", "");
        String emergencyNumber2 = SecureStorageManager.getEncryptedSharedPreferences(this)
            .getString("ENUM_2", "");
        
        // Create enhanced incident type
        String incidentType = "AI Detected Emergency: " + emergencyType;
        
        // Use SOSHelper to send emergency message
        SOSHelper sosHelper = new SOSHelper(this);
        sosHelper.sendEmergencySOS(userName, incidentType, emergencyNumber1, emergencyNumber2);
        */
    }
    
    /**
     * Set emergency listener
     */
    public void setEmergencyListener(EmergencyListener listener) {
        this.emergencyListener = listener;
    }
    
    /**
     * Create notification channel for foreground service
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Background Audio Monitor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors background audio for emergency sounds");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Intent pauseIntent = new Intent(this, BackgroundAudioMonitor.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pausePI = PendingIntent.getService(
                this, 0, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, BackgroundAudioMonitor.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPI = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BilaWoga Safety Monitor")
            .setContentText("Monitoring for emergency sounds")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(new NotificationCompat.Action(0, "Pause", pausePI))
            .addAction(new NotificationCompat.Action(0, "Stop", stopPI))
            .build();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "BackgroundAudioMonitor service destroyed");
        
        stopAudioMonitoring();
        
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    /**
     * Emergency sound detection result
     */
    private static class EmergencyDetectionResult {
        boolean isEmergency;
        String type;
        float confidence;
        
        EmergencyDetectionResult(boolean isEmergency, String type, float confidence) {
            this.isEmergency = isEmergency;
            this.type = type;
            this.confidence = confidence;
        }
    }
    
    /**
     * Learn from audio patterns for voice recognition
     * ENHANCED: Voice learning capability
     */
    private void learnFromAudio(byte[] audioData, int readSize) {
        try {
            // Extract voice features for learning
            // This helps the system learn new voices over time
            // Voice learning happens in background without user interaction
            
            // Simple feature extraction (can be enhanced)
            if (readSize > 100) {
                // Extract basic features for voice learning
                float[] features = extractVoiceFeatures(audioData, readSize);
                
                // Store features for future voice recognition
                // This enables the system to learn and recognize voices
                storeVoiceFeatures(features);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error learning from audio: " + e.getMessage());
        }
    }
    
    /**
     * Extract voice features from audio
     */
    private float[] extractVoiceFeatures(byte[] audioData, int readSize) {
        float[] features = new float[40];
        
        // Simple feature extraction (placeholder)
        // Real implementation would use MFCC or similar
        for (int i = 0; i < features.length && i < readSize / 100; i++) {
            features[i] = (float) (audioData[i * 100] / 128.0);
        }
        
        return features;
    }
    
    /**
     * Store voice features for learning
     */
    private void storeVoiceFeatures(float[] features) {
        try {
            // Store in SharedPreferences for voice learning
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            if (prefs != null) {
                // Store features as comma-separated values
                StringBuilder featuresStr = new StringBuilder();
                for (float feature : features) {
                    if (featuresStr.length() > 0) featuresStr.append(",");
                    featuresStr.append(feature);
                }
                
                // Store with timestamp
                String timestamp = String.valueOf(System.currentTimeMillis());
                prefs.edit().putString("voice_features_" + timestamp, featuresStr.toString()).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error storing voice features: " + e.getMessage());
        }
    }
    
    /**
     * Emergency sound detector class
     */
    private static class EmergencySoundDetector {
        
        public EmergencyDetectionResult detectEmergency(double db, double[] frequencies) {
            // Enhanced detection to avoid baby cries and family arguments
            
            // Check for sustained high volume sounds (potential screaming/abuse)
            if (db > EMERGENCY_THRESHOLD) {
                // Analyze frequency patterns to distinguish emergency from normal sounds
                if (isAdultDistressSound(frequencies)) {
                    return new EmergencyDetectionResult(true, "Adult Distress", 0.9f);
                } else if (isScreamingSound(frequencies)) {
                    return new EmergencyDetectionResult(true, "Screaming/Abuse", 0.8f);
                } else if (isHelpCry(frequencies)) {
                    return new EmergencyDetectionResult(true, "Help Cry", 0.7f);
                }
            }
            
            // Check for sustained moderate volume adult distress
            if (db > 70 && db <= EMERGENCY_THRESHOLD && isAdultDistressSound(frequencies)) {
                return new EmergencyDetectionResult(true, "Adult Distress", 0.6f);
            }
            
            return new EmergencyDetectionResult(false, "", 0.0f);
        }
        
        private boolean isAdultDistressSound(double[] frequencies) {
            // Check for adult distress patterns (avoiding baby cries)
            int distressCount = 0;
            for (int i = CRYING_FREQUENCY_MIN; i < CRYING_FREQUENCY_MAX && i < frequencies.length; i++) {
                if (frequencies[i] > 1500) { // Higher threshold for adult sounds
                    distressCount++;
                }
            }
            return distressCount > 12; // More components needed for adult distress
        }
        
        private boolean isScreamingSound(double[] frequencies) {
            // Check if dominant frequencies are in screaming range
            int screamingCount = 0;
            for (int i = SCREAMING_FREQUENCY_MIN; i < SCREAMING_FREQUENCY_MAX && i < frequencies.length; i++) {
                if (frequencies[i] > 2500) { // Higher threshold for screaming
                    screamingCount++;
                }
            }
            return screamingCount > 18; // More components needed for screaming
        }
        
        private boolean isHelpCry(double[] frequencies) {
            // Check for "help" or "stop" type cries
            int helpCount = 0;
            for (int i = 800; i < 1500 && i < frequencies.length; i++) {
                if (frequencies[i] > 2000) { // Specific range for help cries
                    helpCount++;
                }
            }
            return helpCount > 8; // Moderate threshold for help cries
        }
    }
}
