package com.example.bilawoga;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import com.bilawoga.safety.R;
import com.example.bilawoga.utils.OnboardingManager;

public class SplashScreen extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (Exception e) {
            android.util.Log.e("SplashScreen", "Error in super.onCreate: " + e.getMessage(), e);
            // Try to recover by going directly to MainActivity
            try {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            } catch (Exception e2) {
                android.util.Log.e("SplashScreen", "Cannot recover: " + e2.getMessage(), e2);
                finish();
                return;
            }
        }
        
        try {
            // SECURITY: Prevent screenshots and screen recording (wrap in try-catch)
            try {
                com.example.bilawoga.utils.ScreenSecurityManager.preventScreenshots(this);
            } catch (Exception e) {
                android.util.Log.w("SplashScreen", "Screenshot prevention failed: " + e.getMessage());
                // Continue anyway
            }

            // Early integrity check: if fails, abort (but don't crash - just log)
            try {
                boolean ok = com.example.bilawoga.utils.SecureStorageManager.checkAppIntegrity(this);
                if (!ok) {
                    android.util.Log.w("SplashScreen", "App integrity verification failed, but continuing anyway");
                }
            } catch (Throwable e) {
                android.util.Log.e("SplashScreen", "Integrity check error: " + e.getMessage());
                // Continue anyway - don't crash on integrity check
            }

            // Load layout - this is critical, wrap in try-catch
            try {
                setContentView(R.layout.activity_splash_screen);
            } catch (Exception e) {
                android.util.Log.e("SplashScreen", "Error loading layout: " + e.getMessage(), e);
                // Try to proceed to MainActivity without showing splash
                try {
                    OnboardingManager onboardingManager = new OnboardingManager(this);
                    proceed(onboardingManager);
                    return;
                } catch (Exception e2) {
                    android.util.Log.e("SplashScreen", "Cannot proceed: " + e2.getMessage(), e2);
                    try {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                        return;
                    } catch (Exception e3) {
                        android.util.Log.e("SplashScreen", "Complete failure: " + e3.getMessage(), e3);
                        finish();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SplashScreen", "Error in initialization: " + e.getMessage(), e);
            // Try to proceed anyway
            try {
                OnboardingManager onboardingManager = new OnboardingManager(this);
                proceed(onboardingManager);
                return;
            } catch (Exception e2) {
                android.util.Log.e("SplashScreen", "Cannot proceed: " + e2.getMessage(), e2);
                try {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                    return;
                } catch (Exception e3) {
                    android.util.Log.e("SplashScreen", "Complete failure: " + e3.getMessage(), e3);
                    finish();
                    return;
                }
            }
        }
        
        // Start delayed handler for app initialization
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                OnboardingManager onboardingManager = new OnboardingManager(this);

                // Attempt automatic restore if no local data but a cloud backup exists
                android.content.SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
                if (prefs == null) {
                    // If encrypted prefs can't be created, proceed without restore
                    Log.w("SplashScreen", "Encrypted preferences unavailable - proceeding without restore");
                    proceed(onboardingManager);
                    return;
                }
                
                boolean hasLocalData = (
                    (prefs.getString("ENUM_1", null) != null && !"NONE".equals(prefs.getString("ENUM_1", null))) ||
                    (prefs.getString("ENUM_2", null) != null && !"NONE".equals(prefs.getString("ENUM_2", null)))
                );

                if (!hasLocalData) {
                // AUTO-RESTORE: Try to restore data using user ID
                try {
                    // Ensure Firebase is initialized before using it
                    try {
                        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                            com.google.firebase.FirebaseApp.initializeApp(this);
                        }
                    } catch (Exception fe) {
                        Log.w("SplashScreen", "Firebase initialization warning: " + fe.getMessage());
                    }
                    
                    // Get user ID (persists across logout/login)
                    String userId = com.example.bilawoga.utils.UserIdentityManager.getOrCreateUserId(this);
                    Log.d("SplashScreen", "Attempting auto-restore for user: " + com.example.bilawoga.utils.UserIdentityManager.maskUserId(userId));
                    
                    // Try restore using user ID first (more reliable)
                    com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                    db.collection("backups").document(userId).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists() && prefs != null) {
                                // Verify device hash matches (security check)
                                String storedDeviceHash = (String) doc.get("device_hash");
                                String currentDeviceHash = com.example.bilawoga.utils.UserIdentityManager.getDeviceIdHash(this);
                                
                                // Restore data if device hash matches or if no device hash (legacy backup)
                                if (storedDeviceHash == null || storedDeviceHash.equals(currentDeviceHash)) {
                                    android.content.SharedPreferences.Editor ed = prefs.edit();
                                    String u = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("username"));
                                    String e1 = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("enum1"));
                                    String e2 = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("enum2"));
                                    String it = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("incident_type"));
                                    if (u != null) ed.putString("USERNAME", u);
                                    if (e1 != null) ed.putString("ENUM_1", e1);
                                    if (e2 != null) ed.putString("ENUM_2", e2);
                                    if (it != null) ed.putString("INCIDENT_TYPE", it);
                                    ed.apply();
                                    Log.d("SplashScreen", "Auto-restore successful: Data restored from backup");
                                } else {
                                    Log.w("SplashScreen", "Device hash mismatch - backup from different device, not restoring");
                                }
                            }
                            proceed(onboardingManager);
                        })
                        .addOnFailureListener(e -> {
                            // Fallback: Try FID-based restore (legacy)
                            Log.d("SplashScreen", "User ID restore failed: " + e.getMessage() + ", trying FID-based restore");
                            try {
                                // Ensure Firebase is initialized
                                if (com.google.firebase.FirebaseApp.getApps(SplashScreen.this).isEmpty()) {
                                    com.google.firebase.FirebaseApp.initializeApp(SplashScreen.this);
                                }
                                com.google.firebase.installations.FirebaseInstallations.getInstance().getId()
                                    .addOnSuccessListener(fid -> {
                                        if (fid != null && !fid.isEmpty()) {
                                            db.collection("backups").document(fid).get()
                                                .addOnSuccessListener(doc -> {
                                                    if (doc.exists() && prefs != null) {
                                                        android.content.SharedPreferences.Editor ed = prefs.edit();
                                                        String u = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("username"));
                                                        String e1 = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("enum1"));
                                                        String e2 = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("enum2"));
                                                        String it = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc.get("incident_type"));
                                                        if (u != null) ed.putString("USERNAME", u);
                                                        if (e1 != null) ed.putString("ENUM_1", e1);
                                                        if (e2 != null) ed.putString("ENUM_2", e2);
                                                        if (it != null) ed.putString("INCIDENT_TYPE", it);
                                                        ed.apply();
                                                        Log.d("SplashScreen", "FID-based restore successful");
                                                    }
                                                    proceed(onboardingManager);
                                                })
                                                .addOnFailureListener(ex -> proceed(onboardingManager));
                                        } else {
                                            proceed(onboardingManager);
                                        }
                                    })
                                    .addOnFailureListener(ex -> proceed(onboardingManager));
                            } catch (Throwable t2) {
                                proceed(onboardingManager);
                            }
                        });
                } catch (Throwable t) {
                    proceed(onboardingManager);
                }
            } else {
                proceed(onboardingManager);
            }
            } catch (Exception e) {
                Log.e("SplashScreen", "Error in splash screen handler: " + e.getMessage(), e);
                // Try to proceed anyway - don't crash
                try {
                    OnboardingManager onboardingManager = new OnboardingManager(this);
                    proceed(onboardingManager);
                } catch (Exception e2) {
                    Log.e("SplashScreen", "Critical error - cannot proceed: " + e2.getMessage(), e2);
                    // Last resort - try to go to MainActivity directly
                    try {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } catch (Exception e3) {
                        Log.e("SplashScreen", "Fatal error - app cannot start: " + e3.getMessage(), e3);
                        finish();
                    }
                }
            }
        }, 2000); // 2 seconds
    }

    private void proceed(OnboardingManager onboardingManager) {
        try {
            if (onboardingManager != null && onboardingManager.isNewUser()) {
                startActivity(new Intent(this, OnboardingActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        } catch (Exception e) {
            Log.e("SplashScreen", "Error in proceed: " + e.getMessage(), e);
            // Last resort - try MainActivity directly
            try {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } catch (Exception e2) {
                Log.e("SplashScreen", "Fatal error - cannot start any activity: " + e2.getMessage(), e2);
                finish();
            }
        }
    }
}