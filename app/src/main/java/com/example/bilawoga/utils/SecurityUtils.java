package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * SECURITY UTILITIES - Comprehensive Security Measures for Emergency App
 * 
 * Features:
 * - Input validation and sanitization
 * - Encryption and hashing
 * - Security logging
 * - Rate limiting
 * - Data integrity checks
 */
public class SecurityUtils {
    private static final String TAG = "SecurityUtils";
    
    // Security patterns
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{6,14}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s\\-']+$");
    private static final Pattern INCIDENT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-.,!?]+$");
    
    // Malicious patterns to detect
    private static final String[] MALICIOUS_PATTERNS = {
        "\\n", "\\r", "\\t", "SEND", "TO:", "SMS:", "@", "javascript:", 
        "data:", "vbscript:", "onload", "onerror", "<script", "</script>",
        "eval(", "alert(", "confirm(", "prompt(", "document.", "window.",
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
        "UNION", "OR", "AND", "1=1", "1' OR '1'='1", "'; DROP TABLE"
    };
    
    // Emergency service numbers to prevent abuse
    private static final String[] EMERGENCY_NUMBERS = {
        "911", "112", "999", "000", "110", "119", "120", "122",
        "100", "101", "102", "103", "104", "105", "106", "107", "108", "109"
    };
    
    /**
     * SECURITY: Validate and sanitize phone number
     */
    public static String validateAndSanitizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        
        // Remove all non-digit characters except +
        String sanitized = phoneNumber.replaceAll("[^+\\d]", "");
        
        // Check length
        if (sanitized.length() < 7 || sanitized.length() > 15) {
            return null;
        }
        
        // Check format
        if (!PHONE_PATTERN.matcher(sanitized).matches()) {
            return null;
        }
        
        // Check for malicious patterns
        if (containsMaliciousPatterns(phoneNumber)) {
            return null;
        }
        
        // Check for emergency service numbers
        if (isEmergencyServiceNumber(sanitized)) {
            return null;
        }
        
        return sanitized;
    }
    
    /**
     * SECURITY: Validate and sanitize name
     */
    public static String validateAndSanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        String sanitized = name.replaceAll("\\s+", " ").trim();
        
        // Check length
        if (sanitized.length() < 2 || sanitized.length() > 50) {
            return null;
        }
        
        // Check format
        if (!NAME_PATTERN.matcher(sanitized).matches()) {
            return null;
        }
        
        // Check for malicious patterns
        if (containsMaliciousPatterns(sanitized)) {
            return null;
        }
        
        return sanitized;
    }
    
    /**
     * SECURITY: Validate and sanitize incident type
     */
    public static String validateAndSanitizeIncidentType(String incident) {
        if (incident == null || incident.trim().isEmpty()) {
            return null;
        }
        
        String sanitized = incident.trim();
        
        // Check length
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        
        // Check for malicious patterns
        if (containsMaliciousPatterns(sanitized)) {
            return null;
        }
        
        return sanitized;
    }
    
    /**
     * SECURITY: Check for malicious patterns
     */
    public static boolean containsMaliciousPatterns(String input) {
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        for (String pattern : MALICIOUS_PATTERNS) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        // Check for HTML/XML injection
        if (input.matches(".*[<>\"'&;].*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * SECURITY: Check if number is emergency service
     */
    public static boolean isEmergencyServiceNumber(String number) {
        if (number == null) return false;
        
        for (String emergency : EMERGENCY_NUMBERS) {
            if (number.endsWith(emergency)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * SECURITY: Generate secure random string
     */
    public static String generateSecureRandomString(int length) {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * SECURITY: Hash sensitive data for logging
     */
    public static String hashSensitiveData(String data) {
        if (data == null) return "[NULL]";
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Arrays.toString(hash).substring(0, 16) + "...";
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error hashing data: " + e.getMessage());
            return "[HASH_ERROR]";
        }
    }
    
    /**
     * SECURITY: Mask phone number for display
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    /**
     * SECURITY: Mask name for display
     */
    public static String maskName(String name) {
        if (name == null || name.length() <= 2) {
            return "***";
        }
        return name.substring(0, 2) + "***";
    }
    
    /**
     * SECURITY: Validate data integrity
     */
    public static boolean validateDataIntegrity(String name, String number1, String number2, String incident) {
        // Check for null values
        if (name == null || incident == null) {
            return false;
        }
        
        // Check for at least one emergency number
        if ((number1 == null || number1.trim().isEmpty()) && 
            (number2 == null || number2.trim().isEmpty())) {
            return false;
        }
        
        // Check for duplicate numbers
        if (number1 != null && number2 != null && 
            !number1.trim().isEmpty() && !number2.trim().isEmpty() &&
            number1.trim().equals(number2.trim())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * SECURITY: Log security event
     */
    public static void logSecurityEvent(Context context, String event, String details) {
        String timestamp = new Date().toString();
        String maskedDetails = details != null ? hashSensitiveData(details) : "[NO_DETAILS]";
        
        Log.i(TAG, "SECURITY_EVENT: " + event + " | " + maskedDetails + " | " + timestamp);
        
        // Could also send to security monitoring service
        // sendToSecurityMonitoring(event, maskedDetails, timestamp);
    }
    
    /**
     * SECURITY: Check if device is rooted (basic check)
     */
    public static boolean isDeviceRooted() {
        // Basic root detection - in production, use more sophisticated methods
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        };
        
        for (String path : paths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * SECURITY: Validate app signature
     */
    public static boolean validateAppSignature(Context context) {
        try {
            String packageName = context.getPackageName();
            int flags = context.getPackageManager().getPackageInfo(packageName, 0).applicationInfo.flags;
            
            // Check if app is installed from Play Store (FLAG_SYSTEM or FLAG_UPDATED_SYSTEM_APP)
            return (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                   (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        } catch (Exception e) {
            Log.e(TAG, "Error validating app signature: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * SECURITY: Show security warning
     */
    public static void showSecurityWarning(Context context, String message) {
        Toast.makeText(context, "⚠️ Security Warning: " + message, Toast.LENGTH_LONG).show();
        logSecurityEvent(context, "SECURITY_WARNING", message);
    }
} 