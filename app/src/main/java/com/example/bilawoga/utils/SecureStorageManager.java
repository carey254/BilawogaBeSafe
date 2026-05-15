package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class SecureStorageManager {

    private static final String TAG = "SecureStorageManager";
    private static final String PREFS_FILE_NAME = "secure_prefs";
    private static SharedPreferences encryptedSharedPreferences;

    public static synchronized SharedPreferences getEncryptedSharedPreferences(Context context) {
        try {
            if (context == null) {
                Log.e(TAG, "Context is null, cannot create encrypted preferences");
                return getFallbackPreferences(context);
            }

            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Log.d(TAG, "Encrypted SharedPreferences created successfully");
            return encryptedPrefs;
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "CRITICAL SECURITY ERROR: Unable to create encrypted preferences. Using fallback. Error: " + e.getMessage());
            return getFallbackPreferences(context);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error creating encrypted preferences: " + e.getMessage());
            return getFallbackPreferences(context);
        }
    }
    
    /**
     * Fallback to regular SharedPreferences if encrypted preferences fail
     * This ensures the app doesn't crash if encryption fails
     */
    private static SharedPreferences getFallbackPreferences(Context context) {
        try {
            if (context == null) return null;
            // Use regular SharedPreferences as fallback
            return context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE);
        } catch (Exception e) {
            Log.e(TAG, "Even fallback preferences failed: " + e.getMessage());
            return null;
        }
    }

    // NEW METHOD: Securely wipe all stored data
    public static void secureWipeAllData(Context context) {
        try {
            SharedPreferences prefs = getEncryptedSharedPreferences(context);
            if (prefs != null) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();
                Log.d(TAG, "All encrypted data securely wiped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error wiping data: " + e.getMessage());
        }
    }

    // NEW METHOD: Encrypt sensitive log messages
    public static String encryptLogMessage(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(message.getBytes());
            return Arrays.toString(hash).substring(0, 16) + "...";
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error encrypting log message: " + e.getMessage());
            return "[ENCRYPTED]";
        }
    }

    // NEW METHOD: Validate data integrity
    public static boolean validateDataIntegrity(Context context) {
        try {
            SharedPreferences prefs = getEncryptedSharedPreferences(context);
            if (prefs != null) {
                // Check if critical data exists and is not corrupted
                String username = prefs.getString("USERNAME", null);
                String emergency1 = prefs.getString("ENUM_1", null);
                String emergency2 = prefs.getString("ENUM_2", null);
                
                // Basic validation - at least one emergency number should be set
                return emergency1 != null && !emergency1.equals("NONE") || 
                       emergency2 != null && !emergency2.equals("NONE");
            }
        } catch (Exception e) {
            Log.e(TAG, "Data integrity check failed: " + e.getMessage());
        }
        return false;
    }

    // Tamper detection: root/emulator
    public static boolean isDeviceTampered() {
        try {
            // Root check: su binary
            String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su"};
            for (String path : paths) {
                if (new java.io.File(path).exists()) return true;
            }
            // Emulator check
            String fingerprint = android.os.Build.FINGERPRINT;
            String model = android.os.Build.MODEL;
            String product = android.os.Build.PRODUCT;
            if (fingerprint != null && (fingerprint.contains("generic") || fingerprint.contains("unknown"))) return true;
            if (model != null && (model.contains("Emulator") || model.contains("Android SDK built for x86"))) return true;
            if (product != null && product.contains("sdk")) return true;
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Tamper check failed: " + e.getMessage());
            return false;
        }
    }
    // App integrity check: signature (supports Base64 or hex with/without colons)
    public static boolean checkAppIntegrity(Context context) {
        try {
            // Get SIGNATURE_SHA256 from BuildConfig via reflection to avoid direct dependency/import
            String expected = "";
            try {
                Class<?> bc = Class.forName(context.getPackageName() + ".BuildConfig");
                Object val = bc.getField("SIGNATURE_SHA256").get(null);
                if (val instanceof String) {
                    expected = (String) val;
                }
            } catch (Throwable ignored) {
                // If not available, skip integrity check (treat as not configured)
            }
            if (expected == null || expected.isEmpty()) return true; // Skip if not set

            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            byte[] certBytes;
            if (Build.VERSION.SDK_INT >= 28) {
                SigningInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                Signature cert = info.getApkContentsSigners()[0];
                certBytes = cert.toByteArray();
            } else {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                certBytes = pi.signatures[0].toByteArray();
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(certBytes);

            String base64 = Base64.encodeToString(digest, Base64.NO_WRAP);
            String hex = bytesToHex(digest);
            String hexWithColons = bytesToHexWithColons(digest);

            String exp = expected.trim();
            return exp.equals(base64) || exp.equalsIgnoreCase(hex) || exp.equalsIgnoreCase(hexWithColons);
        } catch (Exception e) {
            Log.e(TAG, "Integrity check failed: " + e.getMessage());
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String bytesToHexWithColons(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) sb.append(":");
        }
        return sb.toString();
    }
} 