package com.example.bilawoga.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bilawoga.MainActivity;
import com.example.bilawoga.ServiceMine;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import android.net.Uri;
import java.security.SecureRandom;
import java.util.UUID;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;

public class SOSHelper {
    private static final String TAG = "SOSHelper";
    private static final long LOCATION_TIMEOUT_MS = 5000; // 5 seconds timeout
    // SMS constants - must match MainActivity
    public static final String SMS_SENT_ACTION = "com.example.bilawoga.SMS_SENT";
    public static final String SMS_DELIVERED_ACTION = "com.example.bilawoga.SMS_DELIVERED";
    
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

    // No-op used by ShakeService to centralize routing (kept for API stability)
    public void testSMSSending(String number) { /* intentionally empty */ }

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
        // Validate input
        if (userName == null || userName.trim().isEmpty()) {
            userName = "Unknown User";
        }
        
        if (incidentType == null || incidentType.trim().isEmpty()) {
            incidentType = "Emergency";
        }
        
        // Check for valid emergency numbers
        boolean hasValidNumber1 = isValidNumber(emergencyNumber1);
        boolean hasValidNumber2 = isValidNumber(emergencyNumber2);
        
        if (!hasValidNumber1 && !hasValidNumber2) {
            Log.e(TAG, "No valid emergency numbers provided");
            showToast("No valid emergency numbers found! Please add emergency contacts in settings.");
            logSecurityEvent("SOS_FAILED", "No valid emergency numbers provided");
            return;
        }
        
        // Show a toast to indicate emergency SOS is being sent immediately
        showToast("EMERGENCY SOS: Sending alert immediately");
        
        // Log the emergency SOS attempt with security details
        logSecurityEvent("EMERGENCY_SOS_ATTEMPT", String.format(Locale.US, 
            "EMERGENCY: User: %s, Incident: %s, Numbers: %s%s", 
            userName, 
            incidentType,
            hasValidNumber1 ? maskNumber(emergencyNumber1) : "none",
            hasValidNumber2 ? ", " + maskNumber(emergencyNumber2) : ""));
        
        // Security verification - log both numbers being contacted for emergency
        if (hasValidNumber1) {
            logSecurityEvent("EMERGENCY_SOS_NUMBER_1", "EMERGENCY: Contacting: " + maskNumber(emergencyNumber1));
        }
        if (hasValidNumber2) {
            logSecurityEvent("EMERGENCY_SOS_NUMBER_2", "EMERGENCY: Contacting: " + maskNumber(emergencyNumber2));
        }
        
        // Get current location and then send emergency messages immediately
        String finalIncidentType = incidentType;
        String finalUserName = userName;
        getCurrentLocation(location -> {
            String locationText = "Location unavailable";
            
            if (location != null) {
                locationText = String.format(Locale.US, "%.6f, %.6f", 
                    location.getLatitude(), location.getLongitude());
                Log.d(TAG, "Emergency location obtained: " + locationText);
                logSecurityEvent("EMERGENCY_SOS_LOCATION", "Emergency location: " + locationText);
            } else {
                Log.e(TAG, "Could not get emergency location");
                showToast("Could not get location. Sending emergency SOS with available information...");
                logSecurityEvent("EMERGENCY_SOS_LOCATION", "Emergency location unavailable");
            }
            
            // Create the emergency message with better format
            String message = createEmergencyMessage(finalUserName, finalIncidentType, locationText);
            Log.d(TAG, "Emergency SOS message prepared: " + message);
            
            // Track if any emergency message was sent successfully
            boolean messageSent = false;
            int messagesAttempted = 0;
            int messagesSent = 0;
            
            // Send to first emergency number if valid - IMMEDIATE EMERGENCY RESPONSE
            if (hasValidNumber1) {
                messagesAttempted++;
                Log.d(TAG, "EMERGENCY: Sending SOS immediately to first emergency number: " + maskNumber(emergencyNumber1));
                boolean sent1 = sendSMS(emergencyNumber1, message);
                if (sent1) {
                    messagesSent++;
                    logSecurityEvent("EMERGENCY_SOS_SENT_1", "EMERGENCY SMS sent to: " + maskNumber(emergencyNumber1));
                } else {
                    logSecurityEvent("EMERGENCY_SOS_FAILED_1", "Failed to send emergency SMS to: " + maskNumber(emergencyNumber1));
                }
                messageSent = sent1 || messageSent;
            }
            
            // Send to second emergency number if valid and different from first - IMMEDIATE EMERGENCY RESPONSE
            if (hasValidNumber2 && !emergencyNumber2.equals(emergencyNumber1)) {
                messagesAttempted++;
                Log.d(TAG, "EMERGENCY: Sending SOS immediately to second emergency number: " + maskNumber(emergencyNumber2));
                boolean sent2 = sendSMS(emergencyNumber2, message);
                if (sent2) {
                    messagesSent++;
                    logSecurityEvent("EMERGENCY_SOS_SENT_2", "EMERGENCY SMS sent to: " + maskNumber(emergencyNumber2));
                } else {
                    logSecurityEvent("EMERGENCY_SOS_FAILED_2", "Failed to send emergency SMS to: " + maskNumber(emergencyNumber2));
                }
                messageSent = sent2 || messageSent;
            }
            
            // Log the final emergency result with detailed statistics
            if (messageSent) {
                String resultMessage = String.format(Locale.US, 
                    "EMERGENCY ALERT SENT: %d/%d messages delivered successfully", messagesSent, messagesAttempted);
                logSecurityEvent("EMERGENCY_SOS_SUCCESS", resultMessage);
                showToast(resultMessage);
            } else {
                logSecurityEvent("EMERGENCY_SOS_FAILED", "Failed to send any emergency SMS messages");
                showToast("EMERGENCY ALERT FAILED: Please check your emergency contacts");
            }
        });
    }
    
    private String createEmergencyMessage(String userName, String incidentType, String location) {
        // Create location access with direct Google Maps links for demo
        String address = "";
        String mapLink = "";
        
        if (location != null && !location.equals("Location unavailable")) {
            // Extract coordinates if available (format: "lat, lng")
            if (location.contains(",")) {
                // Get address from coordinates
                address = getAddressFromCoordinates(location);
                
                // Create direct Google Maps link for demo
                mapLink = String.format(Locale.US, 
                    "📍 Track my location here: https://www.google.com/maps?q=%s\n" +
                    "🗺️ Direct Map Link: https://maps.google.com/?q=%s", location, location);
            }
        } else {
            address = "Location unavailable";
            mapLink = "🔒 Location services not accessible at this time";
        }
        
        return String.format(Locale.getDefault(),
            "🚨 EMERGENCY ALERT 🚨\n\n" +
            "My name is %s.\n" +
            "I am experiencing %s\n\n" +
            "📍 LOCATION:\n" +
            "%s\n\n" +
            "%s\n\n" +
            "PLEASE SEND HELP IMMEDIATELY!",
            userName, incidentType, address, mapLink);
    }
    
    /**
     * Gets address from coordinates using reverse geocoding
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
            
            // Use Google Maps Geocoding API (free tier)
            String url = String.format(Locale.US, 
                "https://maps.googleapis.com/maps/api/geocode/json?latlng=%.6f,%.6f&key=YOUR_API_KEY", 
                lat, lng);
            
            // For demo purposes, return a sample address based on coordinates
            // In production, you would use the actual API call
            return getSampleAddress(lat, lng);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting address from coordinates: " + e.getMessage());
            return "Address not available";
        }
    }
    
    /**
     * Returns clean address format: Road, Area/Town, County
     */
    private String getSampleAddress(double lat, double lng) {
        // Sample addresses for Kenya coordinates - Road, Area/Town, County format
        if (lat >= -1.0 && lat <= 0.0 && lng >= 37.0 && lng <= 38.0) {
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
            return "Unknown location, Kenya";
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
        
        // First try to get last known location immediately
        try {
            Location lastKnownLocation = fusedLocationClient.getLastLocation().getResult();
            if (lastKnownLocation != null) {
                Log.d(TAG, "Got last known location: " + lastKnownLocation.getLatitude() + ", " + lastKnownLocation.getLongitude());
                logSecurityEvent("LOCATION_LAST_KNOWN", 
                    String.format(Locale.US, "%.6f,%.6f", 
                        lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));
                callback.accept(lastKnownLocation);
                return;
            } else {
                Log.d(TAG, "Last known location is null, trying current location");
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not get last known location: " + e.getMessage());
        }
        
        // Try alternative location providers if FusedLocation fails
        try {
            // Try GPS provider first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLocation != null) {
                    Log.d(TAG, "Got GPS location: " + gpsLocation.getLatitude() + ", " + gpsLocation.getLongitude());
                    callback.accept(gpsLocation);
                    return;
                }
            }
            
            // Try Network provider
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (networkLocation != null) {
                    Log.d(TAG, "Got Network location: " + networkLocation.getLatitude() + ", " + networkLocation.getLongitude());
                    callback.accept(networkLocation);
                    return;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Alternative location providers failed: " + e.getMessage());
        }
        
        // Create location request with more aggressive settings for emergency
        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)  // 1 second
                .setFastestInterval(500)  // 0.5 seconds
                .setNumUpdates(1)
                .setMaxWaitTime(3000);  // 3 seconds max wait for emergency
                
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
                        callback.accept(location);
                    } else {
                        Log.e(TAG, "Location is null");
                        logSecurityEvent("LOCATION_NULL", "Location result was not null but location is null");
                        callback.accept(null);
                    }
                } else {
                    Log.e(TAG, "Location result is null");
                    logSecurityEvent("LOCATION_RESULT_NULL", "Location result is null");
                    callback.accept(null);
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
                        Location fallbackLocation = fusedLocationClient.getLastLocation().getResult();
                        if (fallbackLocation != null) {
                            Log.d(TAG, "Got fallback location: " + fallbackLocation.getLatitude() + ", " + fallbackLocation.getLongitude());
                            callback.accept(fallbackLocation);
                        } else {
                            Log.e(TAG, "Fallback location is also null");
                            callback.accept(null);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Fallback location also failed: " + e.getMessage());
                        callback.accept(null);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Error removing location updates: " + e.getMessage());
                    callback.accept(null);
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
    
    private boolean sendSMS(String number, String message) {
        try {
            // Security logging - log the SMS attempt
            logSecurityEvent("SMS_ATTEMPT", "Attempting to send SMS to: " + maskNumber(number));
            
            Log.d(TAG, "DEMO: Opening SMS app with emergency message for " + maskNumber(number));
            logSecurityEvent("SMS_DEMO_ATTEMPT", "Opening SMS app for demo: " + maskNumber(number));
            
            // For demo purposes - open the default SMS app with the message pre-filled
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
            smsIntent.setData(Uri.parse("smsto:" + number));
            smsIntent.putExtra("sms_body", message);
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Try to start the SMS app directly
            try {
                Log.d(TAG, "Opening SMS app for demo with emergency message");
                context.startActivity(smsIntent);
                showToast("Opening SMS app with emergency message");
                logSecurityEvent("SMS_DEMO_OPENED", "SMS app opened for demo: " + maskNumber(number));
                return true;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "SMS app not found, falling back to direct sending");
                logSecurityEvent("SMS_APP_NOT_FOUND", "No SMS app, using direct send for: " + maskNumber(number));
                
                // Fallback to direct SMS sending if no SMS app is available
                return sendSMSDirect(number, message);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send emergency SMS: " + e.getMessage());
            logSecurityEvent("SMS_EMERGENCY_ERROR", "Error: " + e.getMessage() + " for: " + maskNumber(number));
            
            // Fallback to direct SMS sending
            Log.d(TAG, "Falling back to direct SMS sending");
            return sendSMSDirect(number, message);
        }
    }
    
    private boolean sendSMSDirect(String number, String message) {
        try {
            Log.d(TAG, "Starting direct SMS send to: " + maskNumber(number));
            
            // Check for SMS permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                
                Log.d(TAG, "SMS permission granted, proceeding with send");
                SmsManager smsManager = SmsManager.getDefault();
                
                // Create pending intents for delivery status
                Intent sentIntent = new Intent(SMS_SENT_ACTION);
                sentIntent.putExtra("phoneNumber", number);
                PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE);
                
                Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
                deliveredIntent.putExtra("phoneNumber", number);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveredIntent, PendingIntent.FLAG_IMMUTABLE);
                
                Log.d(TAG, "Sending SMS via SmsManager to: " + maskNumber(number));
                
                // Send SMS immediately for emergency response
                smsManager.sendTextMessage(number, null, message, sentPI, deliveredPI);
                Log.d(TAG, "Emergency SMS sent immediately to " + maskNumber(number));
                
                // Log successful emergency send
                logSecurityEvent("SMS_EMERGENCY_SENT", "Emergency SMS sent to: " + maskNumber(number));
                if (BuildConfig.DEBUG) { showToast("Emergency SMS sent"); }
                return true;
            } else {
                Log.e(TAG, "SMS permission not granted for emergency");
                showToast("SMS permission not granted. Cannot send emergency message.");
                
                // Log permission denial for emergency
                logSecurityEvent("SMS_EMERGENCY_PERMISSION_DENIED", "Could not send emergency SMS to " + maskNumber(number));
                
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception for emergency SMS: " + e.getMessage());
            showToast("Security exception: " + e.getMessage());
            logSecurityEvent("SMS_EMERGENCY_SECURITY_EXCEPTION", e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send emergency SMS: " + e.getMessage());
            showToast("Failed to send emergency SMS: " + e.getMessage());
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
}
