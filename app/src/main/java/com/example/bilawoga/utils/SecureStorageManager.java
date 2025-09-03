package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
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
                return null;
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
            Log.e(TAG, "CRITICAL SECURITY ERROR: Unable to create encrypted preferences. Sensitive data will NOT be stored. App cannot proceed securely. Error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error creating encrypted preferences: " + e.getMessage());
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
    // App integrity check: signature
    public static boolean checkAppIntegrity(Context context) {
        try {
            String expected = BuildConfig.SIGNATURE_SHA256; // Define per build type
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
            String hash = Base64.encodeToString(md.digest(certBytes), Base64.NO_WRAP);
            return hash.equals(expected);
        } catch (Exception e) {
            Log.e(TAG, "Integrity check failed: " + e.getMessage());
            return false;
        }
    }
} 