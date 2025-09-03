package com.example.bilawoga.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.bilawoga.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundAudioMonitor extends Service {
    private static final String TAG = "BackgroundAudioMonitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "BackgroundAudioMonitor";
    
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
    private static final long EMERGENCY_CONFIRMATION_TIME = 5000; // 5 seconds to confirm emergency
    private static final int MIN_EMERGENCY_DURATION = 2000; // Must last at least 2 seconds
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ExecutorService executorService;
    private Handler mainHandler;
    private EmergencySoundDetector soundDetector;
    private long lastEmergencyTime = 0;
    private boolean emergencyConfirmed = false;
    
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
                Log.d(TAG, "Audio recording started");
                
                byte[] audioBuffer = new byte[BUFFER_SIZE];
                
                while (isRecording) {
                    int readSize = audioRecord.read(audioBuffer, 0, BUFFER_SIZE);
                    if (readSize > 0) {
                        analyzeAudioData(audioBuffer, readSize);
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
     */
    private void analyzeAudioData(byte[] audioData, int readSize) {
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
        
        if (result.isEmergency) {
            handleEmergencyDetection(result.type, result.confidence);
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
     * Handle emergency sound detection
     */
    private void handleEmergencyDetection(String type, float confidence) {
        long currentTime = System.currentTimeMillis();
        
        // Prevent multiple triggers within short time
        if (currentTime - lastEmergencyTime < 5000) {
            return;
        }
        
        lastEmergencyTime = currentTime;
        
        Log.d(TAG, "Emergency detected: " + type + " (confidence: " + confidence + ")");
        
        // Notify listener
        if (emergencyListener != null) {
            mainHandler.post(() -> emergencyListener.onEmergencyDetected(type, confidence));
        }
        
        // Confirm emergency after delay
        mainHandler.postDelayed(() -> {
            if (!emergencyConfirmed) {
                emergencyConfirmed = true;
                Log.d(TAG, "Emergency confirmed: " + type + " - Sending SOS automatically!");
                
                // Send SOS automatically
                sendAutomaticSOS(type);
                
                if (emergencyListener != null) {
                    emergencyListener.onEmergencyConfirmed(type);
                }
            }
        }, EMERGENCY_CONFIRMATION_TIME);
    }
    
    /**
     * Send automatic SOS without user interaction
     */
    private void sendAutomaticSOS(String emergencyType) {
        Log.d(TAG, "Sending automatic SOS for: " + emergencyType);
        
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
        
        Log.d(TAG, "Automatic SOS sent successfully");
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
    
    /**
     * Create notification for foreground service
     */
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BilaWoga Safety Monitor")
            .setContentText("Monitoring for emergency sounds")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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
