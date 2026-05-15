package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * EMERGENCY AUDIO TRANSMITTER
 * 
 * Features:
 * - Records audio when emergency detected
 * - Converts audio to compressed format (M4A/MP3)
 * - Sends audio to emergency contacts via MMS/WhatsApp
 * - Includes AI detection message
 */
public class EmergencyAudioTransmitter {
    private static final String TAG = "EmergencyAudioTransmitter";
    private static final String AUDIO_DIR = "emergency_audio";
    private static final int MAX_AUDIO_DURATION_MS = 10000; // 10 seconds
    
    private final Context context;
    private MediaRecorder mediaRecorder;
    private String currentAudioFile;
    private boolean isRecording = false;
    private FusedLocationProviderClient fusedLocationClient;
    
    public EmergencyAudioTransmitter(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        createAudioDirectory();
    }
    
    /**
     * Record emergency audio
     */
    public String startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return currentAudioFile;
        }
        
        try {
            // Create audio file
            File audioDir = new File(context.getFilesDir(), AUDIO_DIR);
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            currentAudioFile = new File(audioDir, "emergency_" + timestamp + ".m4a").getAbsolutePath();
            
            // Setup MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentAudioFile);
            mediaRecorder.setMaxDuration(MAX_AUDIO_DURATION_MS);
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            isRecording = true;
            Log.d(TAG, "Emergency audio recording started: " + currentAudioFile);
            
            return currentAudioFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Stop recording and return audio file path
     */
    public String stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            return null;
        }
        
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            
            Log.d(TAG, "Emergency audio recording stopped: " + currentAudioFile);
            return currentAudioFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio recording: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Send emergency audio to contacts with AI detection message
     * AUTOMATICALLY gets location and includes it in the message
     */
    public void sendEmergencyAudioWithMessage(String userName, String emergencyType, 
                                             float confidence, String audioFilePath,
                                             String emergencyNumber1, String emergencyNumber2) {
        try {
            // Get location automatically
            getCurrentLocation(location -> {
                String locationText = "Location unavailable";
                String locationAddress = "";
                String locationLinks = "";
                
                if (location != null) {
                    locationText = String.format(Locale.US, "%.6f, %.6f", 
                        location.getLatitude(), location.getLongitude());
                    
                    // Get address from coordinates
                    locationAddress = getAddressFromCoordinates(locationText);
                    
                    // Create map links
                    String mapQ = String.format(Locale.US, "https://www.google.com/maps?q=%s", locationText);
                    String mapDirect = String.format(Locale.US, "https://maps.google.com/?q=%s", locationText);
                    locationLinks = String.format(Locale.US, "Track location: %s\nDirect map: %s", mapQ, mapDirect);
                    
                    Log.d(TAG, "Emergency location obtained: " + locationText);
                } else {
                    Log.w(TAG, "Could not get emergency location");
                }
                
                // Build emergency message with AI detection info and location
                String message = buildEmergencyMessageWithAI(userName, emergencyType, confidence, 
                    locationText, locationAddress, locationLinks);
                
                // Send to contact 1
                if (emergencyNumber1 != null && !emergencyNumber1.trim().isEmpty() && 
                    !emergencyNumber1.equals("NONE")) {
                    sendAudioToContact(emergencyNumber1, message, audioFilePath);
                }
                
                // Send to contact 2
                if (emergencyNumber2 != null && !emergencyNumber2.trim().isEmpty() && 
                    !emergencyNumber2.equals("NONE")) {
                    sendAudioToContact(emergencyNumber2, message, audioFilePath);
                }
                
                Log.i(TAG, "Emergency audio with location sent to contacts automatically");
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending emergency audio: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build emergency message with AI detection information and location
     */
    private String buildEmergencyMessageWithAI(String userName, String emergencyType, float confidence,
                                               String locationText, String locationAddress, String locationLinks) {
        StringBuilder message = new StringBuilder();
        message.append("🚨 AI DETECTED EMERGENCY 🚨\n\n");
        message.append("⚠️ THIS IS AN AUTOMATED AI DETECTION\n");
        message.append("The BilaWoga Safety App has detected an emergency situation using AI.\n\n");
        message.append("Name: ").append(userName != null ? userName : "Unknown").append("\n");
        message.append("Emergency Type: ").append(emergencyType).append("\n");
        message.append("AI Confidence: ").append(String.format(Locale.US, "%.1f%%", confidence * 100)).append("\n");
        message.append("Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n\n");
        
        // Add location information
        message.append("📍 LOCATION:\n");
        if (locationText != null && !locationText.equals("Location unavailable")) {
            message.append("Coordinates: ").append(locationText).append("\n");
            if (locationAddress != null && !locationAddress.isEmpty()) {
                message.append("Address: ").append(locationAddress).append("\n");
            }
            if (locationLinks != null && !locationLinks.isEmpty()) {
                message.append(locationLinks).append("\n");
            }
        } else {
            message.append("Location unavailable at this time\n");
        }
        message.append("\n");
        
        message.append("🎤 Audio Recording: [Attached]\n");
        message.append("This audio was recorded when the emergency was detected.\n\n");
        message.append("⚠️ ACTION REQUIRED:\n");
        message.append("This person needs help immediately. Please respond as soon as possible.\n\n");
        message.append("This is an automated alert from BilaWoga Safety App.");
        
        return message.toString();
    }
    
    /**
     * Get current location automatically
     */
    private void getCurrentLocation(Consumer<Location> callback) {
        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted, continuing without location");
            callback.accept(null);
            return;
        }
        
        // Check if location services are enabled
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.w(TAG, "Location services are disabled, continuing without location");
            callback.accept(null);
            return;
        }
        
        // Guard to ensure we call the callback only once
        final AtomicBoolean provided = new AtomicBoolean(false);
        
        // Try to get last known location first (fastest)
        try {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null && provided.compareAndSet(false, true)) {
                        Log.d(TAG, "Got last known location: " + location.getLatitude() + ", " + location.getLongitude());
                        callback.accept(location);
                    } else if (provided.compareAndSet(false, true)) {
                        // If no last known location, try alternative providers
                        tryAlternativeLocationProviders(locationManager, callback, provided);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to get last known location: " + e.getMessage());
                    if (provided.compareAndSet(false, true)) {
                        tryAlternativeLocationProviders(locationManager, callback, provided);
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error getting location: " + e.getMessage());
            if (provided.compareAndSet(false, true)) {
                callback.accept(null);
            }
        }
    }
    
    /**
     * Try alternative location providers
     */
    private void tryAlternativeLocationProviders(LocationManager locationManager, Consumer<Location> callback, AtomicBoolean provided) {
        try {
            // Try GPS provider
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLocation != null && provided.compareAndSet(false, true)) {
                    Log.d(TAG, "Got GPS location: " + gpsLocation.getLatitude() + ", " + gpsLocation.getLongitude());
                    callback.accept(gpsLocation);
                    return;
                }
            }
            
            // Try Network provider
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (networkLocation != null && provided.compareAndSet(false, true)) {
                    Log.d(TAG, "Got Network location: " + networkLocation.getLatitude() + ", " + networkLocation.getLongitude());
                    callback.accept(networkLocation);
                    return;
                }
            }
            
            // If no location available, return null
            if (provided.compareAndSet(false, true)) {
                callback.accept(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying alternative location providers: " + e.getMessage());
            if (provided.compareAndSet(false, true)) {
                callback.accept(null);
            }
        }
    }
    
    /**
     * Get address from coordinates using reverse geocoding
     */
    private String getAddressFromCoordinates(String coordinates) {
        try {
            // Simplified reverse geocoding - in production, use Geocoder API
            // For now, return coordinates as address
            if (coordinates != null && coordinates.contains(",")) {
                String[] parts = coordinates.split(",");
                if (parts.length == 2) {
                    return String.format(Locale.US, "Lat: %s, Lon: %s", parts[0].trim(), parts[1].trim());
                }
            }
            return coordinates;
        } catch (Exception e) {
            Log.e(TAG, "Error getting address from coordinates: " + e.getMessage());
            return coordinates;
        }
    }
    
    /**
     * Send audio to contact via MMS
     * AUTOMATICALLY sends message and audio without user interaction
     */
    private void sendAudioToContact(String phoneNumber, String message, String audioFilePath) {
        try {
            // AUTOMATIC: Send text message with emergency info immediately
            SmsManager smsManager = SmsManager.getDefault();
            
            // Handle long messages: split and send multipart
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts != null && parts.size() > 1) {
                Log.d(TAG, "Sending multipart SMS (" + parts.size() + " parts) to: " + phoneNumber);
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            } else {
                Log.d(TAG, "Sending single-part SMS to: " + phoneNumber);
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
            
            Log.d(TAG, "✅ AUTOMATIC: Emergency text message sent to: " + phoneNumber);
            
            // AUTOMATIC: Then, send audio via MMS (if supported)
            sendAudioViaMMS(phoneNumber, message, audioFilePath);
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio to contact: " + e.getMessage(), e);
            
            // Fallback: Send WhatsApp message with audio
            sendAudioViaWhatsApp(phoneNumber, message, audioFilePath);
        }
    }
    
    /**
     * Send audio via MMS
     */
    private void sendAudioViaMMS(String phoneNumber, String message, String audioFilePath) {
        try {
            // Create MMS intent
            android.content.Intent mmsIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            mmsIntent.putExtra("address", phoneNumber);
            mmsIntent.putExtra("sms_body", message);
            mmsIntent.putExtra(android.content.Intent.EXTRA_STREAM, Uri.fromFile(new File(audioFilePath)));
            mmsIntent.setType("audio/mp4");
            mmsIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Try to find MMS app
            android.content.pm.PackageManager pm = context.getPackageManager();
            java.util.List<android.content.pm.ResolveInfo> activities = pm.queryIntentActivities(mmsIntent, 0);
            
            if (!activities.isEmpty()) {
                context.startActivity(mmsIntent);
                Log.d(TAG, "MMS intent sent for audio transmission");
            } else {
                Log.w(TAG, "No MMS app found, falling back to WhatsApp");
                sendAudioViaWhatsApp(phoneNumber, message, audioFilePath);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending MMS: " + e.getMessage(), e);
            sendAudioViaWhatsApp(phoneNumber, message, audioFilePath);
        }
    }
    
    /**
     * Send audio via WhatsApp
     */
    private void sendAudioViaWhatsApp(String phoneNumber, String message, String audioFilePath) {
        try {
            // Create WhatsApp intent
            android.content.Intent whatsappIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            whatsappIntent.setType("audio/*");
            whatsappIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);
            whatsappIntent.putExtra(android.content.Intent.EXTRA_STREAM, Uri.fromFile(new File(audioFilePath)));
            whatsappIntent.setPackage("com.whatsapp");
            whatsappIntent.putExtra("jid", phoneNumber + "@s.whatsapp.net");
            whatsappIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(whatsappIntent);
            Log.d(TAG, "WhatsApp intent sent for audio transmission");
            
        } catch (android.content.ActivityNotFoundException e) {
            Log.w(TAG, "WhatsApp not installed, audio cannot be sent");
            // Fallback: Send message with instructions to request audio
            sendFallbackMessage(phoneNumber, message);
        } catch (Exception e) {
            Log.e(TAG, "Error sending WhatsApp: " + e.getMessage(), e);
            sendFallbackMessage(phoneNumber, message);
        }
    }
    
    /**
     * Send fallback message if audio cannot be sent
     */
    private void sendFallbackMessage(String phoneNumber, String message) {
        try {
            String fallbackMessage = message + "\n\n[Note: Audio recording was captured but cannot be sent automatically. Please contact the person directly.]";
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, fallbackMessage, null, null);
            Log.d(TAG, "Fallback message sent to: " + phoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "Error sending fallback message: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create audio directory
     */
    private void createAudioDirectory() {
        File audioDir = new File(context.getFilesDir(), AUDIO_DIR);
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
    }
    
    /**
     * Cleanup old audio files (keep only last 10)
     */
    public void cleanupOldAudioFiles() {
        try {
            File audioDir = new File(context.getFilesDir(), AUDIO_DIR);
            if (!audioDir.exists()) {
                return;
            }
            
            File[] files = audioDir.listFiles();
            if (files == null || files.length <= 10) {
                return;
            }
            
            // Sort by modification time (oldest first)
            java.util.Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            
            // Delete oldest files
            int filesToDelete = files.length - 10;
            for (int i = 0; i < filesToDelete; i++) {
                files[i].delete();
                Log.d(TAG, "Deleted old audio file: " + files[i].getName());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up audio files: " + e.getMessage(), e);
        }
    }
}

