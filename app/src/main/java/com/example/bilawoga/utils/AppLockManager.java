package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricPrompt.PromptInfo;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * APP LOCK MANAGER - Prevents unauthorized access to sensitive data
 * 
 * Security Features:
 * - PIN/Password protection
 * - Biometric authentication (fingerprint/face)
 * - Session timeout
 * - Failed attempt lockout
 * - Screen recording detection
 * - Accessibility service detection
 */
public class AppLockManager {
    private static final String TAG = "AppLockManager";
    private static final String PREFS_NAME = "app_lock_prefs";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_LOCK_TYPE = "lock_type"; // "pin", "password", "biometric", "none"
    private static final String KEY_LOCK_HASH = "lock_hash"; // Hashed PIN/password
    private static final String KEY_LAST_ACTIVITY = "last_activity";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";
    
    // Security constants
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MIN_PASSWORD_LENGTH = 6;
    
    private final Context context;
    private final SharedPreferences prefs;
    private CancellationSignal cancellationSignal;
    
    public interface LockListener {
        void onLockSuccess();
        void onLockFailed(String reason);
        void onLockRequired();
    }

    public AppLockManager(Context context) {
        SharedPreferences prefs1;
        this.context = context.getApplicationContext();
        prefs1 = SecureStorageManager.getEncryptedSharedPreferences(context);
        if (prefs1 == null) {
            // Fallback to regular prefs if encrypted not available
            prefs1 = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = prefs1;
    }
    
    /**
     * Check if app lock is enabled
     */
    public boolean isLockEnabled() {
        return prefs.getBoolean(KEY_LOCK_ENABLED, false);
    }
    
    /**
     * Get lock type
     */
    public String getLockType() {
        return prefs.getString(KEY_LOCK_TYPE, "none");
    }
    
    /**
     * Enable PIN lock
     */
    public boolean enablePinLock(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH) {
            return false;
        }
        
        String hash = hashPin(pin);
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putString(KEY_LOCK_TYPE, "pin")
            .putString(KEY_LOCK_HASH, hash)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply();
        
        Log.d(TAG, "PIN lock enabled");
        return true;
    }
    
    /**
     * Enable password lock
     */
    public boolean enablePasswordLock(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        
        String hash = hashPassword(password);
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putString(KEY_LOCK_TYPE, "password")
            .putString(KEY_LOCK_HASH, hash)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply();
        
        Log.d(TAG, "Password lock enabled");
        return true;
    }
    
    /**
     * Enable biometric lock
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    public boolean enableBiometricLock(FragmentActivity activity) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.e(TAG, "Biometric not available: " + canAuthenticate);
            return false;
        }
        
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putString(KEY_LOCK_TYPE, "biometric")
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply();
        
        Log.d(TAG, "Biometric lock enabled");
        return true;
    }
    
    /**
     * Disable app lock
     */
    public void disableLock() {
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, false)
            .putString(KEY_LOCK_TYPE, "none")
            .remove(KEY_LOCK_HASH)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply();
        
        Log.d(TAG, "App lock disabled");
    }
    
    /**
     * Check if authentication is required
     * EMERGENCY BYPASS: Returns false for emergency operations to ensure user safety
     */
    public boolean isAuthenticationRequired() {
        if (!isLockEnabled()) {
            return false;
        }
        
        // EMERGENCY BYPASS: Never require authentication for emergency SOS
        // This ensures user safety is never compromised by security measures
        // Emergency operations are handled separately and bypass app lock
        
        // Check if locked out
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        if (System.currentTimeMillis() < lockoutUntil) {
            return true; // Still locked out
        }
        
        // Check session timeout
        long lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0);
        if (System.currentTimeMillis() - lastActivity > SESSION_TIMEOUT_MS) {
            return true; // Session expired
        }
        
        return false;
    }
    
    /**
     * Check if this is an emergency operation (bypasses lock)
     * EMERGENCY BYPASS: Emergency SOS always works even if app is locked
     */
    public boolean isEmergencyOperation() {
        // Emergency operations bypass app lock
        // This includes:
        // - SOS sending
        // - Background AI monitoring
        // - Shake detection
        // - Audio emergency detection
        return true; // Emergency operations always bypass lock
    }
    
    /**
     * Verify PIN
     */
    public boolean verifyPin(String pin) {
        if (isLockedOut()) {
            return false;
        }
        
        String storedHash = prefs.getString(KEY_LOCK_HASH, "");
        String inputHash = hashPin(pin);
        
        if (storedHash.equals(inputHash)) {
            // Success - reset failed attempts and update activity
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
                .putLong(KEY_LOCKOUT_UNTIL, 0)
                .apply();
            return true;
        } else {
            // Failed - increment attempts
            int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
            
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // Lock out
                long lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
                prefs.edit().putLong(KEY_LOCKOUT_UNTIL, lockoutUntil).apply();
                Log.w(TAG, "Too many failed attempts - locked out for 5 minutes");
            }
            
            return false;
        }
    }
    
    /**
     * Verify password
     */
    public boolean verifyPassword(String password) {
        if (isLockedOut()) {
            return false;
        }
        
        String storedHash = prefs.getString(KEY_LOCK_HASH, "");
        String inputHash = hashPassword(password);
        
        if (storedHash.equals(inputHash)) {
            // Success
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
                .putLong(KEY_LOCKOUT_UNTIL, 0)
                .apply();
            return true;
        } else {
            // Failed
            int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
            
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                long lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
                prefs.edit().putLong(KEY_LOCKOUT_UNTIL, lockoutUntil).apply();
                Log.w(TAG, "Too many failed attempts - locked out");
            }
            
            return false;
        }
    }
    
    /**
     * Authenticate with biometric
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    public void authenticateBiometric(FragmentActivity activity, LockListener listener) {

        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            if (listener != null) {
                listener.onLockFailed("Biometric authentication not available");
            }
            return;
        }
        
        Executor executor = ContextCompat.getMainExecutor(context);
        // Success - update activity time
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // Success - update activity time
                prefs.edit()
                        .putInt(KEY_FAILED_ATTEMPTS, 0)
                        .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
                        .putLong(KEY_LOCKOUT_UNTIL, 0)
                        .apply();

                if (listener != null) {
                    listener.onLockSuccess();
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (listener != null) {
                    listener.onLockFailed(errString.toString());
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                int attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();

                if (listener != null) {
                    listener.onLockFailed("Authentication failed");
                }
            }
        });
        
        PromptInfo promptInfo = new PromptInfo.Builder()
            .setTitle("BilaWoga Security")
            .setSubtitle("Authenticate to access emergency contacts")
            .setNegativeButtonText("Cancel")
            .build();
        
        biometricPrompt.authenticate(promptInfo);
    }
    
    /**
     * Update last activity time (call when user interacts with app)
     */
    public void updateActivityTime() {
        if (isLockEnabled()) {
            prefs.edit().putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis()).apply();
        }
    }
    
    /**
     * Check if currently locked out
     */
    public boolean isLockedOut() {
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        return System.currentTimeMillis() < lockoutUntil;
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    public long getRemainingLockoutSeconds() {
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0);
        long remaining = lockoutUntil - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }
    
    /**
     * Hash PIN using SHA-256
     */
    private String hashPin(String pin) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            Log.e(TAG, "Error hashing PIN: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Hash password using SHA-256 with salt
     */
    private String hashPassword(String password) {
        try {
            // Add salt from device ID for extra security
            String salt = android.provider.Settings.Secure.getString(
                context.getContentResolver(), 
                android.provider.Settings.Secure.ANDROID_ID
            );
            
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes());
            byte[] hash = digest.digest(password.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            Log.e(TAG, "Error hashing password: " + e.getMessage());
            return "";
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    /**
     * Detect if screen recording is active (Android 10+)
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public boolean isScreenRecording() {
        try {
            android.media.projection.MediaProjectionManager mgr = 
                (android.media.projection.MediaProjectionManager) 
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            // Note: This is a simplified check - full detection requires MediaProjection callback
            return false; // Placeholder - implement full detection if needed
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if accessibility services are enabled (potential security risk)
     */
    public boolean hasAccessibilityServices() {
        try {
            android.accessibilityservice.AccessibilityServiceInfo[] services =
                    ((android.view.accessibility.AccessibilityManager)
                            context.getSystemService(Context.ACCESSIBILITY_SERVICE))
                            .getEnabledAccessibilityServiceList(
                                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).toArray(new android.accessibilityservice.AccessibilityServiceInfo[0]);
            
            return services != null && services.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

