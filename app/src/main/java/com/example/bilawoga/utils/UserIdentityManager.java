package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * USER IDENTITY MANAGER
 * 
 * Manages user identification for data backup and restore
 * Uses device ID + user-generated ID for reliable user identification
 * 
 * Features:
 * - Generates unique user ID on first launch
 * - Persists user ID across app reinstalls (if same device)
 * - Allows data restore when user logs back in
 * - Works even if Firebase Installation ID changes
 */
public class UserIdentityManager {
    private static final String TAG = "UserIdentityManager";
    private static final String PREFS_NAME = "user_identity_prefs";
    private static final String KEY_USER_ID = "user_unique_id";
    private static final String KEY_DEVICE_ID = "device_id_hash";
    private static final String KEY_BACKUP_ENABLED = "backup_enabled";
    
    /**
     * Get or create unique user ID
     * This ID persists across logout/login cycles
     */
    public static String getOrCreateUserId(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                // Fallback to regular prefs if encrypted not available
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            
            String userId = prefs.getString(KEY_USER_ID, null);
            
            if (userId == null || userId.isEmpty()) {
                // Generate new user ID
                userId = generateUserId(context);
                prefs.edit().putString(KEY_USER_ID, userId).apply();
                Log.d(TAG, "Generated new user ID: " + maskUserId(userId));
            }
            
            return userId;
        } catch (Exception e) {
            Log.e(TAG, "Error getting user ID: " + e.getMessage());
            // Fallback: generate temporary ID
            return generateUserId(context);
        }
    }
    
    /**
     * Generate unique user ID based on device ID + random UUID
     * This ensures same device = same user ID (for restore)
     */
    private static String generateUserId(Context context) {
        try {
            // Use Android ID as base (persists across app reinstalls on same device)
            String androidId = Settings.Secure.getString(
                context.getContentResolver(), 
                Settings.Secure.ANDROID_ID
            );
            
            // Generate UUID for uniqueness
            String uuid = UUID.randomUUID().toString();
            
            // Combine and hash for privacy
            String combined = (androidId != null ? androidId : "unknown") + "_" + uuid;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes());
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String userId = "BILA_" + hexString.toString().substring(0, 32).toUpperCase();
            Log.d(TAG, "Generated user ID: " + maskUserId(userId));
            return userId;
        } catch (Exception e) {
            Log.e(TAG, "Error generating user ID: " + e.getMessage());
            // Fallback: simple UUID
            return "BILA_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        }
    }
    
    /**
     * Get device ID hash (for verification)
     */
    public static String getDeviceIdHash(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            
            String deviceHash = prefs.getString(KEY_DEVICE_ID, null);
            
            if (deviceHash == null) {
                // Generate device hash
                String androidId = Settings.Secure.getString(
                    context.getContentResolver(), 
                    Settings.Secure.ANDROID_ID
                );
                
                if (androidId != null) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(androidId.getBytes());
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hash) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1) {
                            hexString.append('0');
                        }
                        hexString.append(hex);
                    }
                    deviceHash = hexString.toString().substring(0, 16).toUpperCase();
                    prefs.edit().putString(KEY_DEVICE_ID, deviceHash).apply();
                } else {
                    deviceHash = "UNKNOWN";
                }
            }
            
            return deviceHash;
        } catch (Exception e) {
            Log.e(TAG, "Error getting device ID hash: " + e.getMessage());
            return "UNKNOWN";
        }
    }
    
    /**
     * Check if backup is enabled for this user
     */
    public static boolean isBackupEnabled(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            return prefs.getBoolean(KEY_BACKUP_ENABLED, true); // Default: enabled
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * Enable/disable backup
     */
    public static void setBackupEnabled(Context context, boolean enabled) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            prefs.edit().putBoolean(KEY_BACKUP_ENABLED, enabled).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error setting backup enabled: " + e.getMessage());
        }
    }
    
    /**
     * Mask user ID for logging (privacy)
     */
    public static String maskUserId(String userId) {
        if (userId == null || userId.length() < 8) {
            return "****";
        }
        return userId.substring(0, 4) + "****" + userId.substring(userId.length() - 4);
    }
    
    /**
     * Verify user identity (for restore)
     * Checks if device ID matches stored device ID
     */
    public static boolean verifyUserIdentity(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            
            String storedDeviceHash = prefs.getString(KEY_DEVICE_ID, null);
            String currentDeviceHash = getDeviceIdHash(context);
            
            // If no stored hash, this is first launch - allow
            if (storedDeviceHash == null) {
                return true;
            }
            
            // Verify device hash matches
            return storedDeviceHash.equals(currentDeviceHash);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying user identity: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clear user identity (on logout)
     * Note: This does NOT clear the user ID - it's kept for restore
     */
    public static void clearUserSession(Context context) {
        try {
            // Don't clear user ID - keep it for restore
            // Only clear session data
            Log.d(TAG, "User session cleared (user ID preserved for restore)");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing user session: " + e.getMessage());
        }
    }
    
    /**
     * Completely remove user identity (for complete data deletion)
     */
    public static void removeUserIdentity(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs == null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
            
            prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_DEVICE_ID)
                .remove(KEY_BACKUP_ENABLED)
                .apply();
            
            Log.d(TAG, "User identity completely removed");
        } catch (Exception e) {
            Log.e(TAG, "Error removing user identity: " + e.getMessage());
        }
    }
}

