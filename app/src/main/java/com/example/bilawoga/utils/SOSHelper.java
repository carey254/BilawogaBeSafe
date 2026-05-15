package com.example.bilawoga.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bilawoga.MainActivity;
import com.example.bilawoga.ServiceMine;
import com.example.bilawoga.CountdownActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import android.net.Uri;
import java.security.SecureRandom;
import java.util.UUID;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;

public class SOSHelper extends Context {
    private static final String TAG = "SOSHelper";
    private static final long LOCATION_TIMEOUT_MS = 5000; // 5 seconds timeout
    // SMS constants - must match MainActivity
    public static final String SMS_SENT_ACTION = "com.bilawoga.safety.SMS_SENT";
    public static final String SMS_DELIVERED_ACTION = "com.bilawoga.safety.SMS_DELIVERED";
    
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService executorService;
    
    public SOSHelper(Context context) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
        this.executorService = Executors.newSingleThreadExecutor();
        // Initialize encrypted shared preferences
        SecureStorageManager.getEncryptedSharedPreferences(this.context);
        
        // Security verification - check SMS app availability
        verifySMSAppAvailability();
    }

    /**
     * Handle SMS send failures by retrying with alternate SIM and/or shorter message.
     */
    public void handleSmsSendFailure(Context context, Intent intent, int resultCode) {
        try {
            boolean isEmergency = intent.getBooleanExtra("isEmergency", true);
            if (!isEmergency) return;

            String number = intent.getStringExtra("phoneNumber");
            String body = intent.getStringExtra("messageBody");
            int attempt = intent.getIntExtra("attempt", 0);
            int usedSubId = intent.getIntExtra("subIdUsed", SubscriptionManager.INVALID_SUBSCRIPTION_ID);

            if (number == null || body == null) return;
            if (attempt >= 3) return;
            
            // Handle error codes 32 and 124 (network/carrier issues) - these are often false negatives
            if (resultCode == 32 || resultCode == 124) {
                Log.w(TAG, "SMS error code " + resultCode + " (network/carrier issue) - SMS may still be delivered");
                // Don't retry immediately for these codes as SMS might be delivered
                return;
            }

            // Build a shorter fallback body after first failure
            String fallbackBody = body;
            if (attempt >= 1) {
                // Keep only the essential lines to avoid multipart and content filters
                String[] lines = body.split("\\n");
                StringBuilder sb = new StringBuilder();
                int kept = 0;
                for (String l : lines) {
                    if (l.startsWith("Map:") || l.startsWith("Location:") || l.startsWith("Incident:") || l.startsWith("EMERGENCY")) {
                        sb.append(l).append('\n');
                        kept++;
                    }
                    if (kept >= 4) break;
                }
                if (sb.length() > 0) fallbackBody = sb.toString().trim();
            }

            // Choose alternate subscription if available
            SmsManager smsManager;
            int sendSubId = usedSubId;
            try {
                if (Build.VERSION.SDK_INT >= 22) {
                    SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                    if (sm != null) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        List<SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
                        if (list != null && list.size() > 1) {
                            for (SubscriptionInfo info : list) {
                                if (info.getSubscriptionId() != usedSubId) {
                                    sendSubId = info.getSubscriptionId();
                                    break;
                                }
                            }
                        }
                    }
                    if (sendSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        smsManager = SmsManager.getSmsManagerForSubscriptionId(sendSubId);
                    } else {
                        smsManager = SmsManager.getDefault();
                    }
                } else {
                    smsManager = SmsManager.getDefault();
                }
            } catch (Exception e) {
                smsManager = SmsManager.getDefault();
            }

            // Recreate sent/delivered PendingIntents with incremented attempt and subIdUsed
            Intent sent = new Intent(SMS_SENT_ACTION);
            sent.putExtra("phoneNumber", number);
            sent.putExtra("messageBody", fallbackBody);
            sent.putExtra("attempt", attempt + 1);
            sent.putExtra("isEmergency", true);
            if (Build.VERSION.SDK_INT >= 22) sent.putExtra("subIdUsed", sendSubId);

            Intent delivered = new Intent(SMS_DELIVERED_ACTION);
            delivered.putExtra("phoneNumber", number);
            delivered.putExtra("isEmergency", true);

            PendingIntent sentPI = PendingIntent.getBroadcast(context, 1000 + attempt, sent, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 2000 + attempt, delivered, PendingIntent.FLAG_IMMUTABLE);

            // If previous was multipart, still try to keep it short now
            ArrayList<String> parts = smsManager.divideMessage(fallbackBody);
            if (parts != null && parts.size() > 1) {
                ArrayList<PendingIntent> sIntents = new ArrayList<>();
                ArrayList<PendingIntent> dIntents = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    sIntents.add(PendingIntent.getBroadcast(context, 1000 + attempt * 10 + i, sent, PendingIntent.FLAG_IMMUTABLE));
                    dIntents.add(PendingIntent.getBroadcast(context, 2000 + attempt * 10 + i, delivered, PendingIntent.FLAG_IMMUTABLE));
                }
                smsManager.sendMultipartTextMessage(number, null, parts, sIntents, dIntents);
            } else {
                smsManager.sendTextMessage(number, null, fallbackBody, sentPI, deliveredPI);
            }

            Log.w(TAG, "Retrying emergency SMS (attempt " + (attempt + 1) + ") via subId=" + sendSubId + " to " + number);
        } catch (Exception e) {
            Log.e(TAG, "handleSmsSendFailure error: " + e.getMessage());
        }
    }
    
    /**
     * Verifies SMS app availability for security logging
     */
    private void verifySMSAppAvailability() {
        try {
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:"));
            
            if (smsIntent.resolveActivity(context.getPackageManager()) != null) {
                Log.d(TAG, "SMS app available for SOS");
                logSecurityEvent("SMS_APP_VERIFIED", "SMS app is available");
            } else {
                Log.w(TAG, "No SMS app available - will use direct SMS sending");
                logSecurityEvent("SMS_APP_MISSING", "No SMS app found - using direct SMS");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying SMS app: " + e.getMessage());
            logSecurityEvent("SMS_APP_VERIFY_ERROR", "Error: " + e.getMessage());
        }
    }





    public static void sendSOSFromService(ServiceMine serviceMine, FusedLocationProviderClient fusedLocationClient, String userName, String incidentType) {
        try {
            // read numbers
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(serviceMine);
            String em1 = prefs != null ? prefs.getString("ENUM_1", null) : null;
            String em2 = prefs != null ? prefs.getString("ENUM_2", null) : null;
            Intent i = new Intent(serviceMine, CountdownActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(CountdownActivity.EXTRA_USER, userName);
            i.putExtra(CountdownActivity.EXTRA_INCIDENT, incidentType);
            i.putExtra(CountdownActivity.EXTRA_EM1, em1);
            i.putExtra(CountdownActivity.EXTRA_EM2, em2);
            serviceMine.startActivity(i);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start countdown: " + t.getMessage());
        }
    }

    public void sendEmergencySOS(String userName, String incidentType, 
                               String emergencyNumber1, String emergencyNumber2) {
        // SECURITY: Verify emergency contacts are from encrypted storage (trusted contacts only)
        SharedPreferences securePrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
        if (securePrefs == null) {
            Log.e(TAG, "SECURITY ERROR: Encrypted storage not available - cannot verify trusted contacts");
            // STEALTH: No toast - silent failure
            logSecurityEvent("SOS_SECURITY_FAILED", "Encrypted storage unavailable");
            return;
        }
        
        // SECURITY: Verify numbers match stored trusted contacts (prevent spoofing)
        String storedNum1 = securePrefs.getString("ENUM_1", "");
        String storedNum2 = securePrefs.getString("ENUM_2", "");
        
        // Check if provided numbers are valid and match stored trusted contacts
        boolean hasValidNumber1 = isValidNumber(emergencyNumber1);
        boolean hasValidNumber2 = isValidNumber(emergencyNumber2);
        
        boolean isTrustedContact1 = hasValidNumber1 && emergencyNumber1 != null && 
                                   (emergencyNumber1.equals(storedNum1) || emergencyNumber1.equals(storedNum2));
        boolean isTrustedContact2 = hasValidNumber2 && emergencyNumber2 != null && 
                                   (emergencyNumber2.equals(storedNum1) || emergencyNumber2.equals(storedNum2));
        
        if (!isTrustedContact1 && !isTrustedContact2) {
            Log.e(TAG, "SECURITY: Numbers do not match trusted contacts - possible spoofing attempt");
            // STEALTH: No toast - silent failure
            logSecurityEvent("SOS_SPOOFING_DETECTED", "Attempted to send to non-trusted numbers");
            return;
        }
        
        // Use only verified trusted contacts
        String verifiedNum1 = "";
        String verifiedNum2 = "";
        
        // If provided numbers don't match stored, use stored numbers (most secure)
        if (verifiedNum1 == null && !storedNum1.isEmpty() && isValidNumber(storedNum1)) {
            verifiedNum1 = storedNum1;
            isTrustedContact1 = true;
        } else {
            verifiedNum1 = isTrustedContact1 ? emergencyNumber1 : null;
        }
        if (verifiedNum2 == null && !storedNum2.isEmpty() && isValidNumber(storedNum2)) {
            verifiedNum2 = storedNum2;
            isTrustedContact2 = true;
        } else {
            verifiedNum2 = isTrustedContact2 ? emergencyNumber2 : null;
        }

        // Validate input
        if (userName == null || userName.trim().isEmpty()) {
            userName = securePrefs.getString("USERNAME", "Unknown User");
        }
        
        if (incidentType == null || incidentType.trim().isEmpty()) {
            incidentType = securePrefs.getString("INCIDENT_TYPE", "Emergency");
        }
        
        // Final security check: must have at least one verified trusted contact
        if (!isTrustedContact1 && !isTrustedContact2) {
            Log.e(TAG, "SECURITY: No verified trusted contacts available");
            // STEALTH: No toast - silent failure
            logSecurityEvent("SOS_FAILED", "No verified trusted contacts provided");
            return;
        }
        
        // STEALTH MODE: No visible indication on sender's phone - silent send
        // Only receiver will see the message
        
        // SECURITY: Log the emergency SOS attempt with security verification
        logSecurityEvent("EMERGENCY_SOS_ATTEMPT", String.format(Locale.US, 
            "EMERGENCY: User: %s, Incident: %s, Verified Trusted Contacts: %s%s", 
            userName, 
            incidentType,
            isTrustedContact1 ? maskNumber(verifiedNum1) : "none",
            isTrustedContact2 ? ", " + maskNumber(verifiedNum2) : ""));
        
        // SECURITY: Log verification that only trusted contacts are being contacted
        if (isTrustedContact1) {
            logSecurityEvent("EMERGENCY_SOS_VERIFIED_1", "SECURE: Verified trusted contact: " + maskNumber(verifiedNum1));
        }
        if (isTrustedContact2) {
            logSecurityEvent("EMERGENCY_SOS_VERIFIED_2", "SECURE: Verified trusted contact: " + maskNumber(verifiedNum2));
        }
        
        // Get current location and then send emergency messages immediately
        String finalIncidentType = incidentType;
        String finalUserName = userName;
        boolean finalIsTrustedContact = isTrustedContact1;
        boolean finalIsTrustedContact1 = isTrustedContact2;
        String finalVerifiedNum = verifiedNum1;
        String finalVerifiedNum1 = verifiedNum2;
        getCurrentLocation(location -> {
            String locationText = "Location unavailable";
            android.location.Location locationObj = null;
            
            if (location != null) {
                locationText = String.format(Locale.US, "%.6f, %.6f", 
                    location.getLatitude(), location.getLongitude());
                locationObj = location; // Store for police station lookup
                Log.d(TAG, "Emergency location obtained: " + locationText);
                logSecurityEvent("EMERGENCY_SOS_LOCATION", "Emergency location: " + locationText);
            } else {
                Log.e(TAG, "Could not get emergency location");
                // STEALTH: No toast - silent send even without location
                logSecurityEvent("EMERGENCY_SOS_LOCATION", "Emergency location unavailable");
            }
            
            // Create the emergency message with better format (includes police helpline and map links)
            String message = createEmergencyMessage(finalUserName, finalIncidentType, locationText, locationObj);
            Log.d(TAG, "Emergency SOS message prepared: " + message);
            
            // STEALTH MODE: Record audio automatically when SOS is sent
            // Audio will be sent along with the message to trusted contacts
            com.example.bilawoga.utils.EmergencyAudioTransmitter audioTransmitter = 
                new com.example.bilawoga.utils.EmergencyAudioTransmitter(context);
            String audioFile = audioTransmitter.startRecording();
            
            // Track if any emergency message was sent successfully
            boolean messageSent = false;
            int messagesAttempted = 0;
            int messagesSent = 0;
            
            // SECURITY: Send only to verified trusted contacts - IMMEDIATE EMERGENCY RESPONSE
            if (finalIsTrustedContact && finalVerifiedNum != null) {
                messagesAttempted++;
                Log.d(TAG, "SECURE EMERGENCY: Sending SOS to verified trusted contact: " + maskNumber(finalVerifiedNum));
                boolean sent1 = sendSecureSMS(finalVerifiedNum, message);
                if (sent1) {
                    messagesSent++;
                    logSecurityEvent("EMERGENCY_SOS_SENT_SECURE_1", "SECURE SMS sent to verified trusted contact: " + maskNumber(finalVerifiedNum));
                } else {
                    logSecurityEvent("EMERGENCY_SOS_FAILED_1", "Failed to send secure SMS to: " + maskNumber(finalVerifiedNum));
                    // ROBUST RETRY: Automatically retry failed sends
                    retryEmergencySMS(finalVerifiedNum, message, 1);
                }
                messageSent = sent1 || messageSent;
            }
            
            // SECURITY: Send to second verified trusted contact if different from first
            if (finalIsTrustedContact1 && finalVerifiedNum1 != null && !finalVerifiedNum1.equals(finalVerifiedNum)) {
                messagesAttempted++;
                Log.d(TAG, "SECURE EMERGENCY: Sending SOS to verified trusted contact: " + maskNumber(finalVerifiedNum1));
                boolean sent2 = sendSecureSMS(finalVerifiedNum1, message);
                if (sent2) {
                    messagesSent++;
                    logSecurityEvent("EMERGENCY_SOS_SENT_SECURE_2", "SECURE SMS sent to verified trusted contact: " + maskNumber(finalVerifiedNum1));
                } else {
                    logSecurityEvent("EMERGENCY_SOS_FAILED_2", "Failed to send secure SMS to: " + maskNumber(finalVerifiedNum1));
                    // ROBUST RETRY: Automatically retry failed sends
                    retryEmergencySMS(finalVerifiedNum1, message, 1);
                }
                messageSent = sent2 || messageSent;
            }
            
            // STEALTH MODE: Send audio recording after 10 seconds (or when emergency confirmed)
            if (audioFile != null) {
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.postDelayed(() -> {
                    String recordedAudio = audioTransmitter.stopRecording();
                    if (recordedAudio != null && !recordedAudio.isEmpty()) {
                        // Send audio to trusted contacts automatically
                        audioTransmitter.sendEmergencyAudioWithMessage(
                            finalUserName,
                            finalIncidentType,
                            0.9f, // High confidence for manual SOS
                            recordedAudio,
                            finalIsTrustedContact ? finalVerifiedNum : null,
                            finalIsTrustedContact1 ? finalVerifiedNum1 : null
                        );
                        logSecurityEvent("EMERGENCY_AUDIO_SENT", "Audio recording sent to trusted contacts");
                        Log.d(TAG, "STEALTH: Audio recording sent to trusted contacts (no UI indication)");
                    }
                }, 10000); // Record for 10 seconds
            }
            
            // Show success/failure message to user
            if (messageSent) {
                String resultMessage = String.format(Locale.US, 
                    "EMERGENCY ALERT SENT: %d/%d messages delivered successfully", messagesSent, messagesAttempted);
                logSecurityEvent("EMERGENCY_SOS_SUCCESS", resultMessage);
                
                // Show success message to user
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        android.widget.Toast.makeText(context, 
                            "Emergency alert sent successfully!", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                logSecurityEvent("EMERGENCY_SOS_FAILED", "Failed to send any emergency SMS messages");
                
                // Show error message to user
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        android.widget.Toast.makeText(context, 
                            "Failed to send emergency alert. Please try again.", 
                            android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }
    
    private String createEmergencyMessage(String userName, String incidentType, String location, android.location.Location locationObj) {
        // Plain-text, detailed message: includes human-readable address and two map links
        String address = "";
        String links = "";

        if (location != null && !location.equals("Location unavailable")) {
            if (location.contains(",")) {
                address = getAddressFromCoordinates(location);
                String mapQ = String.format(Locale.US, "https://www.google.com/maps?q=%s", location);
                String mapDirect = String.format(Locale.US, "https://maps.google.com/?q=%s", location);
                links = String.format(Locale.US, "📍 Track my location: %s\n🗺️ Direct map: %s", mapQ, mapDirect);
                
                // Add police station map link if location is available
                if (locationObj != null) {
                    String policeMapUrl = com.example.bilawoga.utils.PoliceStationHelper.getPoliceStationsMapUrl(locationObj);
                    links += String.format(Locale.US, "\n🚔 Find police stations: %s", policeMapUrl);
                }
            }
        } else {
            address = "Location unavailable";
            links = "Location services not accessible at this time";
        }

        // Get police helpline information based on location
        String policeInfo = "";
        try {
            if (locationObj != null) {
                policeInfo = com.example.bilawoga.utils.PoliceStationHelper.getFormattedPoliceInfo(locationObj);
            } else if (location != null && !location.equals("Location unavailable") && location.contains(",")) {
                // Fallback: create location object from string
                String[] coords = location.split(",");
                if (coords.length == 2) {
                    double lat = Double.parseDouble(coords[0].trim());
                    double lng = Double.parseDouble(coords[1].trim());
                    android.location.Location loc = new android.location.Location("emergency");
                    loc.setLatitude(lat);
                    loc.setLongitude(lng);
                    policeInfo = com.example.bilawoga.utils.PoliceStationHelper.getFormattedPoliceInfo(loc);
                }
            } else {
                // Default police numbers if no location
                policeInfo = "\n🚨 POLICE EMERGENCY: 999 or 112\n";
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get police info: " + e.getMessage());
            // Add default police numbers
            policeInfo = "\n🚨 POLICE EMERGENCY: 999 or 112\n";
        }
        
        return String.format(Locale.getDefault(),
                "EMERGENCY ALERT\n\n" +
                "My name is %s.\n" +
                "I am experiencing: %s\n\n" +
                "LOCATION:\n" +
                "%s\n\n" +
                "%s\n\n" +
                "%s" +
                "PLEASE SEND HELP IMMEDIATELY.\n" +
                "-- Sent via BilaWoga Emergency",
                userName, incidentType, address, links, policeInfo);
    }
    
    /**
     * Gets address from coordinates using reverse geocoding with HIGH ACCURACY
     * Uses Android Geocoder API for precise location names
     */
    private String getAddressFromCoordinates(String coordinates) {
        try {
            // Parse coordinates
            String[] coords = coordinates.split(",");
            if (coords.length != 2) {
                return "Address not available";
            }
            
            double lat = Double.parseDouble(coords[0].trim());
            double lng = Double.parseDouble(coords[1].trim());
            
            // Use Android Geocoder API for accurate reverse geocoding
            android.location.Geocoder geocoder = new android.location.Geocoder(context, java.util.Locale.getDefault());
            
            try {
                java.util.List<android.location.Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                
                if (addresses != null && !addresses.isEmpty()) {
                    android.location.Address address = addresses.get(0);
                    
                    // Build detailed address with specific location name
                    StringBuilder addressBuilder = new StringBuilder();
                    
                    // Get specific location name (like "Kamakis") - usually in feature name or sub-locality
                    String featureName = address.getFeatureName(); // e.g., "Kamakis"
                    String subLocality = address.getSubLocality(); // e.g., "Kamakis"
                    String locality = address.getLocality(); // e.g., "Nairobi"
                    String thoroughfare = address.getThoroughfare(); // e.g., "Mombasa Road"
                    String subThoroughfare = address.getSubThoroughfare(); // Building number
                    
                    // Prioritize specific location names over generic road names
                    if (featureName != null && !featureName.isEmpty()) {
                        addressBuilder.append(featureName);
                    } else if (subLocality != null && !subLocality.isEmpty()) {
                        addressBuilder.append(subLocality);
                    }
                    
                    // Add road name if available and different from location name
                    if (thoroughfare != null && !thoroughfare.isEmpty()) {
                        if (addressBuilder.length() > 0) {
                            // Only add if it's different from the location name
                            if (!thoroughfare.equals(featureName) && !thoroughfare.equals(subLocality)) {
                                addressBuilder.append(", ").append(thoroughfare);
                            }
                        } else {
                            addressBuilder.append(thoroughfare);
                        }
                    }
                    
                    // Add area/locality
                    if (locality != null && !locality.isEmpty()) {
                        if (addressBuilder.length() > 0) {
                            addressBuilder.append(", ").append(locality);
                        } else {
                            addressBuilder.append(locality);
                        }
                    }
                    
                    // Add county if available
                    String adminArea = address.getAdminArea();
                    if (adminArea != null && !adminArea.isEmpty()) {
                        addressBuilder.append(", ").append(adminArea);
                    }
                    
                    String finalAddress = addressBuilder.toString();
                    if (!finalAddress.isEmpty()) {
                        Log.d(TAG, "Geocoded address: " + finalAddress + " (from " + lat + ", " + lng + ")");
                        return finalAddress;
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Geocoder service not available: " + e.getMessage());
            }
            
            // Fallback: Use Google Maps Geocoding API if Geocoder fails
            return getAddressFromGoogleMapsAPI(lat, lng);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting address from coordinates: " + e.getMessage());
            return "Address not available";
        }
    }
    
    /**
     * Fallback: Get address from Google Maps Geocoding API
     */
    private String getAddressFromGoogleMapsAPI(double lat, double lng) {
        try {
            // Try to use Google Maps Geocoding API if API key is available
            // For now, return coordinates with area name
            return String.format(Locale.US, "%.6f, %.6f", lat, lng) + " (Exact coordinates)";
        } catch (Exception e) {
            Log.e(TAG, "Error in Google Maps API fallback: " + e.getMessage());
            return String.format(Locale.US, "%.6f, %.6f", lat, lng);
        }
    }
    
    /**
     * Fallback: Returns approximate address if Geocoder fails
     * This is only used if Android Geocoder is unavailable
     */
    private String getSampleAddress(double lat, double lng) {
        // This is a fallback - Geocoder should provide accurate addresses
        // For Kamakis area (around -1.2, 36.8)
        if (lat >= -1.3 && lat <= -1.1 && lng >= 36.7 && lng <= 36.9) {
            return "Kamakis, Mombasa Road, Nairobi";
        }
        // Other areas as fallback
        else if (lat >= -1.0 && lat <= 0.0 && lng >= 37.0 && lng <= 38.0) {
            return "Embu-Mbuvori Rd, Kiriari, Embu County";
        } else if (lat >= -1.5 && lat <= -0.5 && lng >= 36.5 && lng <= 37.5) {
            return "Mombasa Rd, Nairobi, Nairobi County";
        } else if (lat >= -0.5 && lat <= 0.5 && lng >= 37.0 && lng <= 38.0) {
            return "Nyeri Rd, Karatina, Nyeri County";
        } else if (lat >= -0.8 && lat <= -0.3 && lng >= 36.8 && lng <= 37.2) {
            return "Thika Rd, Thika, Kiambu County";
        } else if (lat >= -1.3 && lat <= -0.8 && lng >= 36.8 && lng <= 37.2) {
            return "Mai Mahiu Rd, Naivasha, Nakuru County";
        } else {
            return String.format(Locale.US, "Coordinates: %.6f, %.6f", lat, lng);
        }
    }
    
    /**
     * Generates a secure, time-limited token for map access (12 hour expiry)
     */
    private String generateSecureMapToken() {
        try {
            // Create a secure random token
            SecureRandom secureRandom = new SecureRandom();
            StringBuilder token = new StringBuilder();
            
            // Generate random alphanumeric token (16 characters for map tokens)
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            for (int i = 0; i < 16; i++) {
                token.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            
            // Add timestamp component for 12-hour expiration
            long timestamp = System.currentTimeMillis();
            String timestampHex = Long.toHexString(timestamp).toUpperCase();
            
            // Combine token with timestamp (last 8 chars of timestamp for map tokens)
            String finalToken = "MAP" + token.toString() + timestampHex.substring(Math.max(0, timestampHex.length() - 8));
            
            Log.d(TAG, "Generated secure map token: " + finalToken.substring(0, 6) + "****");
            logSecurityEvent("SECURE_MAP_TOKEN_GENERATED", "Emergency map token created (12hr expiry)");
            
            return finalToken;
        } catch (Exception e) {
            Log.e(TAG, "Error generating secure map token: " + e.getMessage());
            // Fallback to UUID if secure random fails
            return "MAP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        }
    }
    
    /**
     * Generates a secure, time-limited token for location access
     */
    private String generateSecureLocationToken() {
        try {
            // Create a secure random token
            SecureRandom secureRandom = new SecureRandom();
            StringBuilder token = new StringBuilder();
            
            // Generate random alphanumeric token (12 characters)
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            for (int i = 0; i < 12; i++) {
                token.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            
            // Add timestamp component for expiration
            long timestamp = System.currentTimeMillis();
            String timestampHex = Long.toHexString(timestamp).toUpperCase();
            
            // Combine token with timestamp (last 6 chars of timestamp)
            String finalToken = token.toString() + timestampHex.substring(Math.max(0, timestampHex.length() - 6));
            
            Log.d(TAG, "Generated secure location token: " + finalToken.substring(0, 4) + "****");
            logSecurityEvent("SECURE_TOKEN_GENERATED", "Emergency location token created");
            
            return finalToken;
        } catch (Exception e) {
            Log.e(TAG, "Error generating secure token: " + e.getMessage());
            // Fallback to UUID if secure random fails
            return UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
        }
    }
    
    /**
     * Stores map data securely with 12-hour expiration
     */
    private void storeSecureMapData(String token, String location, String userName) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs != null) {
                SharedPreferences.Editor editor = prefs.edit();
                
                // Store map data with token as key (12-hour expiry)
                String mapData = String.format(Locale.US, "%s|%s|%d", 
                    location, userName, System.currentTimeMillis());
                
                editor.putString("emergency_map_" + token, mapData);
                editor.apply();
                
                Log.d(TAG, "Secure map data stored for token: " + token.substring(0, 6) + "****");
                logSecurityEvent("SECURE_MAP_STORED", "Emergency map data secured (12hr expiry)");
                
                // Clean up old map tokens (older than 12 hours)
                cleanupExpiredMapTokens(prefs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error storing secure map data: " + e.getMessage());
            logSecurityEvent("SECURE_MAP_ERROR", "Failed to store map data: " + e.getMessage());
        }
    }
    
    /**
     * Stores location data securely with expiration
     */
    private void storeSecureLocationData(String token, String location, String userName) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs != null) {
                SharedPreferences.Editor editor = prefs.edit();
                
                // Store location data with token as key
                String locationData = String.format(Locale.US, "%s|%s|%d", 
                    location, userName, System.currentTimeMillis());
                
                editor.putString("emergency_location_" + token, locationData);
                editor.apply();
                
                Log.d(TAG, "Secure location data stored for token: " + token.substring(0, 4) + "****");
                logSecurityEvent("SECURE_LOCATION_STORED", "Emergency location data secured");
                
                // Clean up old tokens (older than 24 hours)
                cleanupExpiredLocationTokens(prefs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error storing secure location data: " + e.getMessage());
            logSecurityEvent("SECURE_LOCATION_ERROR", "Failed to store location data: " + e.getMessage());
        }
    }
    
    /**
     * Cleans up expired map tokens for security (12-hour expiry)
     */
    private void cleanupExpiredMapTokens(SharedPreferences prefs) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            long currentTime = System.currentTimeMillis();
            long expirationTime = 12 * 60 * 60 * 1000; // 12 hours for map tokens
            
            // Get all keys and check for expired emergency map tokens
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith("emergency_map_")) {
                    String mapData = prefs.getString(key, "");
                    if (!mapData.isEmpty()) {
                        String[] parts = mapData.split("\\|");
                        if (parts.length >= 3) {
                            try {
                                long timestamp = Long.parseLong(parts[2]);
                                if (currentTime - timestamp > expirationTime) {
                                    editor.remove(key);
                                    Log.d(TAG, "Cleaned up expired map token (12hr expiry)");
                                }
                            } catch (NumberFormatException e) {
                                // Invalid timestamp, remove the key
                                editor.remove(key);
                            }
                        }
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error during map token cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Cleans up expired location tokens for security
     */
    private void cleanupExpiredLocationTokens(SharedPreferences prefs) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            long currentTime = System.currentTimeMillis();
            long expirationTime = 24 * 60 * 60 * 1000; // 24 hours
            
            // Get all keys and check for expired emergency location tokens
            for (String key : prefs.getAll().keySet()) {
                if (key.startsWith("emergency_location_")) {
                    String locationData = prefs.getString(key, "");
                    if (!locationData.isEmpty()) {
                        String[] parts = locationData.split("\\|");
                        if (parts.length >= 3) {
                            try {
                                long timestamp = Long.parseLong(parts[2]);
                                if (currentTime - timestamp > expirationTime) {
                                    editor.remove(key);
                                    Log.d(TAG, "Cleaned up expired location token");
                                }
                            } catch (NumberFormatException e) {
                                // Invalid timestamp, remove the key
                                editor.remove(key);
                            }
                        }
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error during token cleanup: " + e.getMessage());
        }
    }
    
    private void getCurrentLocation(Consumer<Location> callback) {
        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            Log.w(TAG, "Location permission not granted, continuing without location");
            logSecurityEvent("LOCATION_PERMISSION_DENIED", "Continuing without location");
            callback.accept(null);
            return;
        }
        
        // Check if location services are enabled
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        Log.d(TAG, "GPS enabled: " + isGpsEnabled + ", Network enabled: " + isNetworkEnabled);
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.w(TAG, "Location services are disabled, continuing without location");
            logSecurityEvent("LOCATION_SERVICES_DISABLED", "Continuing without location");
            callback.accept(null);
            return;
        }
        
        // Guard to ensure we call the callback only once
        final AtomicBoolean provided = new AtomicBoolean(false);

        // Try to get last known location asynchronously (but prefer fresh GPS location)
        // Don't use last known location immediately - wait for fresh GPS fix for accuracy
        try {
            // Only use last known location as absolute fallback after timeout
            Handler timeoutHandler = new Handler(Looper.getMainLooper());
            timeoutHandler.postDelayed(() -> {
                if (!provided.get()) {
                    // After 3 seconds, try last known location as fallback
                    fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(location -> {
                            if (location != null && provided.compareAndSet(false, true)) {
                                Log.d(TAG, "Using last known location (fallback): " + location.getLatitude() + ", " + location.getLongitude());
                                logSecurityEvent("LOCATION_LAST_KNOWN_FALLBACK", 
                                    String.format(Locale.US, "%.6f,%.6f", location.getLatitude(), location.getLongitude()));
                                callback.accept(location);
                            }
                        })
                        .addOnFailureListener(e -> Log.d(TAG, "getLastLocation failed: " + e.getMessage()));
                }
            }, 3000); // Wait 3 seconds before using last known location
        } catch (Exception e) {
            Log.d(TAG, "Could not request last known location: " + e.getMessage());
        }
        
        // Try alternative location providers if FusedLocation fails
        try {
            // Try GPS provider first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLocation != null) {
                    Log.d(TAG, "Got GPS location: " + gpsLocation.getLatitude() + ", " + gpsLocation.getLongitude());
                    if (provided.compareAndSet(false, true)) callback.accept(gpsLocation);
                    return;
                }
            }
            
            // Try Network provider
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (networkLocation != null) {
                    Log.d(TAG, "Got Network location: " + networkLocation.getLatitude() + ", " + networkLocation.getLongitude());
                    if (provided.compareAndSet(false, true)) callback.accept(networkLocation);
                    return;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Alternative location providers failed: " + e.getMessage());
        }
        
        // Create location request with MAXIMUM accuracy settings for emergency
        // MAXIMUM ACCURACY: Request fresh GPS location with longer wait time for precise fix
        // This ensures we get accurate location like "Kamakis" not just "Mombasa Road"
        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)  // Use GPS for best accuracy
                .setInterval(500)  // 0.5 seconds - very frequent updates
                .setFastestInterval(200)  // 0.2 seconds - fastest possible
                .setNumUpdates(1)
                .setMaxWaitTime(10000)  // 10 seconds max wait - allow time for GPS to get accurate fix
                .setSmallestDisplacement(0);  // Get location even if device hasn't moved
                
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                try {
                    fusedLocationClient.removeLocationUpdates(this);
                } catch (Exception e) {
                    Log.e(TAG, "Error removing location updates: " + e.getMessage());
                }
                
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        Log.d(TAG, "Got current location: " + location.getLatitude() + ", " + location.getLongitude());
                        logSecurityEvent("LOCATION_RETRIEVED", 
                            String.format(Locale.US, "%.6f,%.6f", 
                                location.getLatitude(), location.getLongitude()));
                        if (provided.compareAndSet(false, true)) callback.accept(location);
                    } else {
                        Log.e(TAG, "Location is null");
                        logSecurityEvent("LOCATION_NULL", "Location result was not null but location is null");
                        if (provided.compareAndSet(false, true)) callback.accept(null);
                    }
                } else {
                    Log.e(TAG, "Location result is null");
                    logSecurityEvent("LOCATION_RESULT_NULL", "Location result is null");
                    if (provided.compareAndSet(false, true)) callback.accept(null);
                }
            }
            
            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                if (!locationAvailability.isLocationAvailable()) {
                    Log.e(TAG, "Location not available");
                    logSecurityEvent("LOCATION_UNAVAILABLE", "Location services are not available");
                } else {
                    Log.d(TAG, "Location is available");
                }
            }
        };
        
        try {
            // Request location updates with shorter timeout
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Requested location updates");
            
            // Set up a shorter timeout (3 seconds for emergency)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    Log.e(TAG, "Location request timed out after 3 seconds");
                    logSecurityEvent("LOCATION_TIMEOUT", "Location request timed out after 3 seconds");
                    
                    // Try one more time with last known location
                    try {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(fallbackLocation -> {
                                    if (fallbackLocation != null && provided.compareAndSet(false, true)) {
                            Log.d(TAG, "Got fallback location: " + fallbackLocation.getLatitude() + ", " + fallbackLocation.getLongitude());
                            callback.accept(fallbackLocation);
                                    } else if (provided.compareAndSet(false, true)) {
                            Log.e(TAG, "Fallback location is also null");
                            callback.accept(null);
                        }
                                })
                                .addOnFailureListener(ex -> {
                                    if (provided.compareAndSet(false, true)) callback.accept(null);
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "Fallback location also failed: " + e.getMessage());
                        if (provided.compareAndSet(false, true)) callback.accept(null);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Error removing location updates: " + e.getMessage());
                    if (provided.compareAndSet(false, true)) callback.accept(null);
                }
            }, 3000); // 3 seconds timeout for emergency
            
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when requesting location: " + e.getMessage());
            showToast("Location permission error: " + e.getMessage());
            logSecurityEvent("LOCATION_SECURITY_EXCEPTION", e.getMessage());
            callback.accept(null);
        } catch (Exception e) {
            Log.e(TAG, "Error getting location: " + e.getMessage());
            showToast("Error getting location: " + e.getMessage());
            logSecurityEvent("LOCATION_ERROR", "Error: " + e.getMessage());
            callback.accept(null);
        }
    }
    
    /**
     * SECURE SMS sending - ensures only verified trusted contacts receive messages
     * Uses direct SMS API to prevent interception by third-party apps
     */
    private boolean sendSecureSMS(String number, String message) {
        try {
            // SECURITY: Verify this is a trusted contact before sending
            SharedPreferences securePrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (securePrefs == null) {
                Log.e(TAG, "SECURITY: Cannot verify trusted contact - encrypted storage unavailable");
                return false;
            }
            
            String storedNum1 = securePrefs.getString("ENUM_1", "");
            String storedNum2 = securePrefs.getString("ENUM_2", "");
            
            // Verify number matches stored trusted contact
            boolean isTrusted = number.equals(storedNum1) || number.equals(storedNum2);
            if (!isTrusted) {
                Log.e(TAG, "SECURITY BLOCKED: Attempted to send to non-trusted number: " + maskNumber(number));
                logSecurityEvent("SMS_BLOCKED_UNTRUSTED", "Blocked SMS to non-trusted number: " + maskNumber(number));
                return false;
            }
            
            // Security logging - log the secure SMS attempt
            logSecurityEvent("SMS_SECURE_ATTEMPT", "Sending secure SMS to verified trusted contact: " + maskNumber(number));
            
            // Send directly via Android SMS API (no third-party apps = no interception)
            return sendSMSDirect(number, message);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send secure emergency SMS: " + e.getMessage());
            logSecurityEvent("SMS_SECURE_ERROR", "Error: " + e.getMessage() + " for: " + maskNumber(number));
            return false;
        }
    }
    
    // Legacy method - redirects to secure method
    private boolean sendSMS(String number, String message) {
        return sendSecureSMS(number, message);
    }
    
    /**
     * SECURE DIRECT SMS sending - uses Android SMS API directly
     * This prevents interception by third-party SMS apps
     * Messages go directly to carrier network, only recipient receives
     */
    private boolean sendSMSDirect(String number, String message) {
        try {
            Log.d(TAG, "SECURE: Starting direct SMS send to verified trusted contact: " + maskNumber(number));
            
            // SECURITY: Check for SMS permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                
                Log.d(TAG, "SECURE: SMS permission granted, sending directly to carrier (no third-party apps)");
                
                // SECURITY: Use Android's native SmsManager (direct to carrier, no interception)
                SmsManager smsManager;
                try {
                    if (Build.VERSION.SDK_INT >= 22) {
                        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
                        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                            smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
                        } else {
                            smsManager = SmsManager.getDefault();
                        }
                    } else {
                        smsManager = SmsManager.getDefault();
                    }
                } catch (Exception e) {
                    smsManager = SmsManager.getDefault();
                }

                // SECURITY: Create secure pending intents with minimal data exposure
                Intent sentIntent = new Intent(SMS_SENT_ACTION);
                sentIntent.putExtra("phoneNumber", maskNumber(number)); // Only masked number in intent
                sentIntent.putExtra("attempt", 0);
                try {
                    if (Build.VERSION.SDK_INT >= 22) {
                        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
                        sentIntent.putExtra("subIdUsed", subId);
                    }
                } catch (Exception ignore) {}
                
                Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
                deliveredIntent.putExtra("phoneNumber", maskNumber(number)); // Only masked number
                deliveredIntent.putExtra("isEmergency", true); // Mark as emergency SMS

                // SECURITY: Send message directly via carrier (no app chooser, no interception)
                // Handle long messages: split and send multipart
                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts != null && parts.size() > 1) {
                    Log.d(TAG, "SECURE: Sending multipart SMS (" + parts.size() + ") directly to carrier: " + maskNumber(number));
                    ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                    ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
                    for (int i = 0; i < parts.size(); i++) {
                        sentIntent.putExtra("partIndex", i);
                        deliveredIntent.putExtra("partIndex", i);
                        sentIntents.add(PendingIntent.getBroadcast(context, i, sentIntent, PendingIntent.FLAG_IMMUTABLE));
                        deliveredIntents.add(PendingIntent.getBroadcast(context, i, deliveredIntent, PendingIntent.FLAG_IMMUTABLE));
                    }
                    // SECURITY: Direct send - bypasses all SMS apps, goes straight to carrier
                    smsManager.sendMultipartTextMessage(number, null, parts, sentIntents, deliveredIntents);
                } else {
                    Log.d(TAG, "SECURE: Sending single-part SMS directly to carrier: " + maskNumber(number));
                    PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE);
                    PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveredIntent, PendingIntent.FLAG_IMMUTABLE);
                    // SECURITY: Direct send - bypasses all SMS apps, goes straight to carrier
                    smsManager.sendTextMessage(number, null, message, sentPI, deliveredPI);
                }
                Log.d(TAG, "SECURE STEALTH: Emergency SMS sent directly to carrier (only recipient receives): " + maskNumber(number));
                
                // Log successful secure emergency send (silent - no UI indication)
                logSecurityEvent("SMS_SECURE_SENT", "Secure SMS sent directly to carrier for trusted contact: " + maskNumber(number));
                
                // STEALTH MODE: No toast or visible indication on sender's phone
                // Only the receiver will see the SMS message
                
                // DO NOT schedule retry on successful send - retry only happens if send fails
                // The SMS sent/delivered broadcast receivers will handle error cases
                return true;
            } else {
                Log.e(TAG, "SMS permission not granted for emergency");
                // STEALTH: No toast - silent failure
                logSecurityEvent("SMS_EMERGENCY_PERMISSION_DENIED", "Could not send emergency SMS to " + maskNumber(number));
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception for emergency SMS: " + e.getMessage());
            // STEALTH: No toast - silent failure
            logSecurityEvent("SMS_EMERGENCY_SECURITY_EXCEPTION", e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send emergency SMS: " + e.getMessage());
            // STEALTH: No toast - silent failure
            logSecurityEvent("SMS_EMERGENCY_FAILED", "Error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Test method to verify SMS sending is working
     */
    public void testSMSSending(String testNumber) {
        Log.d(TAG, "Testing SMS sending to: " + maskNumber(testNumber));
        String testMessage = "Test SMS from BilaWoga Emergency App - " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        boolean result = sendSMSDirect(testNumber, testMessage);
        Log.d(TAG, "Test SMS result: " + result);
    }
    
    private boolean isValidNumber(String number) {
        if (number == null || number.trim().isEmpty() || number.equalsIgnoreCase("NONE")) {
            return false;
        }
        
        // Clean the number - remove all non-digit characters except +
        String cleanNumber = number.replaceAll("[^0-9+]", "");
        
        // Check if it has at least 8 digits
        if (cleanNumber.length() < 8) {
            Log.w(TAG, "Number too short: " + maskNumber(number) + " (length: " + cleanNumber.length() + ")");
            return false;
        }
        
        // Ensure it starts with + or has country code
        if (!cleanNumber.startsWith("+") && !cleanNumber.startsWith("254")) {
            Log.w(TAG, "Number format issue: " + maskNumber(number) + " (should start with + or 254)");
            return false;
        }
        
        Log.d(TAG, "Valid number: " + maskNumber(number) + " -> " + cleanNumber);
        return true;
    }
    
    private String maskNumber(String number) {
        return number != null && number.length() > 4 ? 
               number.substring(0, 2) + "****" + number.substring(number.length() - 2) : "****";
    }
    
    private void logSecurityEvent(String event, String details) {
        String log = String.format(Locale.US, "[%s] %s: %s\n",
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()),
            event, details);
            
        try {
            // Get the encrypted shared preferences
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs != null) {
                // Append to the security log
                String existingLogs = prefs.getString("security_log", "");
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("security_log", existingLogs + log);
                editor.apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing to security log: " + e.getMessage());
        }
    }
    
    private void showToast(String message) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> 
                Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }
    }
    
    /**
     * Schedule SMS retry for error codes 32 and 124 (network issues)
     */
    private void scheduleSMSRetry(String number, String message, int attempt) {
        if (attempt > 3) {
            Log.w(TAG, "Max retry attempts reached for SMS to " + maskNumber(number));
            logSecurityEvent("SMS_MAX_RETRIES_REACHED", "Failed to send after 3 retries to: " + maskNumber(number));
            return;
        }
        
        // Schedule retry after 5 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "STEALTH RETRY: Retrying SMS send (attempt " + attempt + ") to " + maskNumber(number));
            logSecurityEvent("SMS_RETRY_ATTEMPT", "Retry attempt " + attempt + " for: " + maskNumber(number));
            try {
                boolean sent = sendSecureSMS(number, message);
                if (sent) {
                    Log.d(TAG, "STEALTH: SMS retry successful to " + maskNumber(number));
                    logSecurityEvent("SMS_RETRY_SUCCESS", "Retry successful for: " + maskNumber(number));
                } else {
                    Log.w(TAG, "STEALTH: SMS retry failed, scheduling next attempt");
                    scheduleSMSRetry(number, message, attempt + 1);
                }
            } catch (Exception e) {
                Log.e(TAG, "STEALTH: SMS retry error: " + e.getMessage());
                logSecurityEvent("SMS_RETRY_ERROR", "Retry error: " + e.getMessage());
                // Schedule another retry
                scheduleSMSRetry(number, message, attempt + 1);
            }
        }, 5000); // 5 seconds delay
    }
    
    /**
     * ROBUST RETRY: Retry emergency SMS with automatic retry mechanism
     * Ensures no error prevents message from being sent
     */
    private void retryEmergencySMS(String number, String message, int attempt) {
        if (attempt > 5) {
            Log.e(TAG, "CRITICAL: Max emergency retry attempts (5) reached for: " + maskNumber(number));
            logSecurityEvent("EMERGENCY_SOS_CRITICAL_FAILURE", "Failed to send emergency SMS after 5 retries to: " + maskNumber(number));
            return;
        }
        
        // STEALTH: Log silently - no UI indication
        Log.w(TAG, "STEALTH RETRY: Emergency SMS retry attempt " + attempt + " for: " + maskNumber(number));
        logSecurityEvent("EMERGENCY_SOS_RETRY", "Emergency retry attempt " + attempt + " for: " + maskNumber(number));
        
        // Retry with exponential backoff: 2s, 4s, 8s, 16s, 32s
        long delayMs = (long) Math.pow(2, attempt) * 1000;
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                boolean sent = sendSecureSMS(number, message);
                if (sent) {
                    Log.d(TAG, "STEALTH: Emergency SMS retry successful to " + maskNumber(number));
                    logSecurityEvent("EMERGENCY_SOS_RETRY_SUCCESS", "Emergency retry successful for: " + maskNumber(number));
                } else {
                    Log.w(TAG, "STEALTH: Emergency SMS retry failed, will retry again");
                    retryEmergencySMS(number, message, attempt + 1);
                }
            } catch (Exception e) {
                Log.e(TAG, "STEALTH: Emergency SMS retry error: " + e.getMessage());
                retryEmergencySMS(number, message, attempt + 1);
            }
        }, delayMs);
    }

    @Override
    public AssetManager getAssets() {
        return null;
    }

    @Override
    public Resources getResources() {
        return null;
    }

    @Override
    public PackageManager getPackageManager() {
        return null;
    }

    @Override
    public ContentResolver getContentResolver() {
        return null;
    }

    @Override
    public Looper getMainLooper() {
        return null;
    }

    @Override
    public Context getApplicationContext() {
        return null;
    }

    @Override
    public void setTheme(int resid) {

    }

    @Override
    public Resources.Theme getTheme() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public String getPackageName() {
        return "";
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return null;
    }

    @Override
    public String getPackageResourcePath() {
        return "";
    }

    @Override
    public String getPackageCodePath() {
        return "";
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return null;
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        return false;
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        return false;
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return null;
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return null;
    }

    @Override
    public boolean deleteFile(String name) {
        return false;
    }

    @Override
    public File getFileStreamPath(String name) {
        return null;
    }

    @Override
    public File getDataDir() {
        return null;
    }

    @Override
    public File getFilesDir() {
        return null;
    }

    @Override
    public File getNoBackupFilesDir() {
        return null;
    }

    @Nullable
    @Override
    public File getExternalFilesDir(@Nullable String type) {
        return null;
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        return new File[0];
    }

    @Override
    public File getObbDir() {
        return null;
    }

    @Override
    public File[] getObbDirs() {
        return new File[0];
    }

    @Override
    public File getCacheDir() {
        return null;
    }

    @Override
    public File getCodeCacheDir() {
        return null;
    }

    @Nullable
    @Override
    public File getExternalCacheDir() {
        return null;
    }

    @Override
    public File[] getExternalCacheDirs() {
        return new File[0];
    }

    @Override
    public File[] getExternalMediaDirs() {
        return new File[0];
    }

    @Override
    public String[] fileList() {
        return new String[0];
    }

    @Override
    public File getDir(String name, int mode) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, @Nullable DatabaseErrorHandler errorHandler) {
        return null;
    }

    @Override
    public boolean moveDatabaseFrom(Context sourceContext, String name) {
        return false;
    }

    @Override
    public boolean deleteDatabase(String name) {
        return false;
    }

    @Override
    public File getDatabasePath(String name) {
        return null;
    }

    @Override
    public String[] databaseList() {
        return new String[0];
    }

    @Override
    public Drawable getWallpaper() {
        return null;
    }

    @Override
    public Drawable peekWallpaper() {
        return null;
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        return 0;
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        return 0;
    }

    @Override
    public void setWallpaper(Bitmap bitmap) throws IOException {

    }

    @Override
    public void setWallpaper(InputStream data) throws IOException {

    }

    @Override
    public void clearWallpaper() throws IOException {

    }

    @Override
    public void startActivity(Intent intent) {

    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {

    }

    @Override
    public void startActivities(Intent[] intents) {

    }

    @Override
    public void startActivities(Intent[] intents, Bundle options) {

    }

    @Override
    public void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) throws IntentSender.SendIntentException {

    }

    @Override
    public void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, @Nullable Bundle options) throws IntentSender.SendIntentException {

    }

    @Override
    public void sendBroadcast(Intent intent) {

    }

    @Override
    public void sendBroadcast(Intent intent, @Nullable String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, @Nullable String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(@NonNull Intent intent, @Nullable String receiverPermission, @Nullable BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, @Nullable String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, @Nullable String receiverPermission, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void sendStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle user, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Nullable
    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, @Nullable String broadcastPermission, @Nullable Handler scheduler) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, @Nullable String broadcastPermission, @Nullable Handler scheduler, int flags) {
        return null;
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {

    }

    @Nullable
    @Override
    public ComponentName startService(Intent service) {
        return null;
    }

    @Nullable
    @Override
    public ComponentName startForegroundService(Intent service) {
        return null;
    }

    @Override
    public boolean stopService(Intent service) {
        return false;
    }

    @Override
    public boolean bindService(@NonNull Intent service, @NonNull ServiceConnection conn, int flags) {
        return false;
    }

    @Override
    public void unbindService(@NonNull ServiceConnection conn) {

    }

    @Override
    public boolean startInstrumentation(@NonNull ComponentName className, @Nullable String profileFile, @Nullable Bundle arguments) {
        return false;
    }

    @Override
    public Object getSystemService(@NonNull String name) {
        return null;
    }

    @Nullable
    @Override
    public String getSystemServiceName(@NonNull Class<?> serviceClass) {
        return "";
    }

    @Override
    public int checkPermission(@NonNull String permission, int pid, int uid) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkSelfPermission(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforcePermission(@NonNull String permission, int pid, int uid, @Nullable String message) {

    }

    @Override
    public void enforceCallingPermission(@NonNull String permission, @Nullable String message) {

    }

    @Override
    public void enforceCallingOrSelfPermission(@NonNull String permission, @Nullable String message) {

    }

    @Override
    public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkUriPermission(@Nullable Uri uri, @Nullable String readPermission, @Nullable String writePermission, int pid, int uid, int modeFlags) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceUriPermission(@Nullable Uri uri, @Nullable String readPermission, @Nullable String writePermission, int pid, int uid, int modeFlags, @Nullable String message) {

    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createContextForSplit(String splitName) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createConfigurationContext(@NonNull Configuration overrideConfiguration) {
        return null;
    }

    @Override
    public Context createDisplayContext(@NonNull Display display) {
        return null;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        return null;
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        return false;
    }

    public void sendEmergencyAlert(String userName, String emergencyMessage, String emergencyNumber1, String emergencyNumber2) {
    }
}

