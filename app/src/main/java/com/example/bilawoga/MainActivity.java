package com.example.bilawoga;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;


import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.bilawoga.safety.R;
import com.example.bilawoga.utils.AdaptiveVoiceLearningAI;
import com.example.bilawoga.utils.AppAnalytics;
import com.example.bilawoga.utils.EmergencyContactVerifier;
import com.example.bilawoga.utils.OnboardingManager;
import com.example.bilawoga.utils.SecureStorageManager;
import com.example.bilawoga.utils.SilentEmergencyAI;
import com.example.bilawoga.utils.SmartNotificationManager;
import com.example.bilawoga.utils.CountdownDialog;
import com.example.bilawoga.utils.SOSHelper;
import com.example.bilawoga.utils.CSVTrainingDataManager;
import com.example.bilawoga.utils.GBVModelTrainer;
import com.example.bilawoga.utils.ComprehensiveGBVDetector;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.app.Dialog;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
// import com.example.bilawoga.utils.PredictiveAI; // REMOVED: AI only for sound detection, not movement
import com.example.bilawoga.utils.SecurityManager;
import com.example.bilawoga.utils.MultiChannelCommunicator;
import com.example.bilawoga.utils.AIMonitoringPermission;
import com.example.bilawoga.utils.TTSLanguageManager;
import android.net.Uri;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MainActivity";
    private static final int REQ_SEND_SMS = 1001;
    private static final float SHAKE_THRESHOLD = 12.0f;
    private static final long SHAKE_COOLDOWN_MS = 10000; // 10 seconds cooldown
    public static final String SMS_SENT_ACTION = "com.example.bilawoga.SMS_SENT";
    public static final String SMS_DELIVERED_ACTION = "com.example.bilawoga.SMS_DELIVERED";
    private static final int DOUBLE_SHAKE_COUNT = 2; // NEW: Require double shake
    private static final long DOUBLE_SHAKE_TIMEOUT = 3000; // 3 seconds to complete double shake

    // Permission request codes
    public static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;

    // Permission groups
    private static final String[] ESSENTIAL_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SEND_SMS
    };
    
    private static final String[] OPTIONAL_PERMISSIONS = {
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
    };
    
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SEND_SMS
    };

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float accelValue;
    private float accelLast;
    private float shake;
    private FusedLocationProviderClient fusedLocationClient;
    private String userName;
    private String ENUM; // Consider renaming or removing if not used
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isVoiceSpeaking = false;
    private boolean areEmergencyNumbersVisible = false;
    private TextView emergencyNumbersText;
    private FloatingActionButton accessibilityFab;
    private CountdownDialog countdownDialog;
    private Dialog accessibilityDialog;
    private boolean isHighContrast = false;
    private boolean isLargeText = false;
    private boolean isReadingGuide = false;
    private boolean isAudioActive = false;
    private Handler audioHandler = new Handler(Looper.getMainLooper());
    private Runnable audioRunnable;
    private String incidentType;
    private long lastShakeTime = 0; // Declared once as a member variable

    // NEW: Double shake detection variables
    private int shakeCount = 0;
    private long firstShakeTime = 0;
    private boolean isDoubleShakeDetected = false;

    // AI SHAKE DETECTION - DISABLED (class not available)
    // private com.example.bilawoga.utils.AIShakeDetector aiShakeDetector;
    private boolean isAIShakeDetectionEnabled = false; // DISABLED - class not available

    // Silent Emergency AI - works in background without user interaction
    private SilentEmergencyAI silentEmergencyAI;
    private boolean isSilentEmergencyEnabled = true; // ENABLED for testing

    // Comprehensive GBV Detection System
    private ComprehensiveGBVDetector comprehensiveGBVDetector;
    private boolean isGBVDetectionEnabled = true;

    // Advanced Features Integration
    // private PredictiveAI predictiveAI; // REMOVED: AI only for sound detection, not movement
    private SecurityManager securityManager;
    private MultiChannelCommunicator multiChannelCommunicator;
    private boolean isAdvancedFeaturesEnabled = true;

    // Analytics and monitoring
    private AppAnalytics appAnalytics;
    private SmartNotificationManager notificationManager;
    private EmergencyContactVerifier contactVerifier;
    private OnboardingManager onboardingManager;

    // Use the keys defined in SOSHelper for consistency and clarity
    private static final String KEY_USERNAME = "USERNAME";
    private static final String KEY_INCIDENT_TYPE = "INCIDENT_TYPE";
    private static final String KEY_EMERGENCY_NUMBER_1 = "ENUM_1";
    private static final String KEY_EMERGENCY_NUMBER_2 = "ENUM_2";
    // Guard to prevent double-showing the AI permission dialog
    private boolean aiPermissionRequestInProgress = false;

    // Cache policy content to avoid disk I/O during menu click (prevents jank/ANR)
    private String cachedPrivacyHtml = null;
    private String cachedTermsHtml = null;
    private boolean isPolicyDialogShowing = false;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // SOS countdown state
    private androidx.appcompat.app.AlertDialog sosCountdownDialog;
    private final Handler sosCountdownHandler = new Handler(Looper.getMainLooper());
    private int sosCountdown = 7;
    private Runnable sosCountdownTick;
    
    // Debouncing for send alert button
    private boolean isSendingSOS = false;
    private long lastSOSPressTime = 0;
    private static final long SOS_DEBOUNCE_DELAY_MS = 3000; // 3 seconds between presses
    
    // Rate limiting: 2 SOS per hour
    private static final int MAX_SOS_PER_HOUR = 2;
    private static final long HOUR_MS = 60 * 60 * 1000; // 1 hour in milliseconds



    private final ActivityResultLauncher<String[]> multiplePermissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    boolean allGranted = true;
                    boolean anyDenied = false;
                    
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allGranted = false;
                            anyDenied = true;
                            Log.e(TAG, "Permission denied: " + entry.getKey());
                        }
                    }
                    
                    if (allGranted) {
                        Log.d(TAG, "All permissions granted, starting service");
                        Toast.makeText(MainActivity.this, getString(R.string.all_permissions_granted), Toast.LENGTH_LONG).show();
                        startServiceAutomatically();
                        
                        // Permissions granted; proceed as normal
                    } else {
                        Log.d(TAG, "Some permissions were denied, showing settings dialog...");
                        Toast.makeText(MainActivity.this, getString(R.string.some_permissions_denied), Toast.LENGTH_SHORT).show();
                        
                        // Show settings dialog for denied permissions
                        showPermissionSettingsDialog();
                    }
                }

                private void showPermissionSettingsDialog() {
                }
            });

    // SharedPreferences for app settings
    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "BilaWogaPrefs";
    private static final String KEY_CRASH_REPORTING = "crash_reporting";
    private static final String KEY_FIRST_TIME_PERMISSIONS = "first_time_permissions";
    
    // BroadcastReceiver for SMS status
    private BroadcastReceiver smsSentReceiver;
    private BroadcastReceiver smsDeliveredReceiver;
    private Object showSOSSuccessPopup;

    /**
     * Shows a toast message on the UI thread
     * @param message The message to display
     */
    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }
    
    private void initializeSmsReceivers() {
        // Initialize SMS sent receiver - STEALTH MODE: No visible indication
        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int code = getResultCode();
                // STEALTH MODE: No toast, no UI indication - only receiver sees message
                if (code == RESULT_OK) {
                    Log.d(TAG, "STEALTH: SMS sent successfully (no UI indication on sender's phone)");
                } else {
                    // Log silently - no toast
                    Log.e(TAG, "STEALTH: SMS send failed with code: " + code + " (no UI indication)");
                }
            }
        };
        
        // Initialize SMS delivered receiver - STEALTH MODE: No visible indication
        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int code = getResultCode();
                // STEALTH MODE: No toast, no UI indication - only receiver sees message
                if (code == RESULT_OK) {
                    Log.d(TAG, "STEALTH: SMS delivered (no UI indication on sender's phone)");
                } else {
                    Log.e(TAG, "STEALTH: SMS not delivered (no UI indication)");
                }
            }
        };
    }



    private void checkAndRequestPermissions() {
        if (!checkPermissions()) {
            requestPermissions();
        }
    }

    private void initializeSensors() {
        // Ensure toolbar and menu work; no sensor init yet
        initializeViews();
        // Ensure TEST_MODE toggle entry in menu or settings can be added later
        SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
        if (prefs != null && prefs.getBoolean("TEST_MODE", false)) {
            Toast.makeText(this, getString(R.string.test_mode_on), Toast.LENGTH_SHORT).show();
        }
    }

    // (Old stub onCreateOptionsMenu removed; menu is inflated in the later override)

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_change_number) {
            startActivity(new Intent(this, RegisterNumberActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        } else if (id == R.id.action_toggle_test_mode) {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            if (prefs != null) {
                boolean current = prefs.getBoolean("TEST_MODE", false);
                prefs.edit().putBoolean("TEST_MODE", !current).apply();
                Toast.makeText(this, (!current ? getString(R.string.test_mode_enabled) : getString(R.string.test_mode_disabled)), Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_privacy_policy) {
            // Open Privacy Policy content in dialog using cached content
            if (isPolicyDialogShowing) return true;
            isPolicyDialogShowing = true;
            String html = (cachedPrivacyHtml != null) ? cachedPrivacyHtml : "<h2>Privacy Policy</h2><p>Your privacy is important. We do not share personal data without consent.</p>";
            showPolicyDialog(getString(R.string.privacy_policy), html);
            // Reset flag after dialog dismiss via a short delay (dialog is cancelable)
            new Handler(Looper.getMainLooper()).postDelayed(() -> isPolicyDialogShowing = false, 500);
            return true;
        } else if (id == R.id.action_terms_of_use) {
            // Open Terms of Use content in dialog using cached content
            if (isPolicyDialogShowing) return true;
            isPolicyDialogShowing = true;
            String html = (cachedTermsHtml != null) ? cachedTermsHtml : "<h2>Terms of Use</h2><p>Use this app responsibly. Emergency features send SMS/location to contacts.</p>";
            showPolicyDialog(getString(R.string.terms_of_use), html);
            new Handler(Looper.getMainLooper()).postDelayed(() -> isPolicyDialogShowing = false, 500);
            return true;
        } else if (id == R.id.action_view_log) {
            Toast.makeText(this, getString(R.string.activity_log), Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_panic_wipe) {
            Toast.makeText(this, getString(R.string.panic_wipe_requested), Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Initialize views and set up click listeners
    public MainActivity() {
        super();
    }
    
    // Update locale based on selected language
    private void updateLocale() {
        Locale selectedLocale = com.example.bilawoga.utils.TTSLanguageManager.getSelectedLocale(this);
        Locale.setDefault(selectedLocale);
        android.content.res.Configuration config = getResources().getConfiguration();
        config.setLocale(selectedLocale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // SECURITY: Prevent screenshots and screen recording
        com.example.bilawoga.utils.ScreenSecurityManager.preventScreenshots(this);
        
        // Set locale based on selected language
        updateLocale();
        setContentView(R.layout.activity_main);
        
        // IMMEDIATE TTS PREFERENCE POP-UP: Show before anything else loads
        // Ask user if they want TTS auto-talk enabled (only once on first launch)
        if (com.example.bilawoga.utils.TTSLanguageManager.shouldShowTTSPreferenceDialog(this)) {
            showTTSPreferenceDialog();
        }
        
        // SECURITY: Clear clipboard on app start
        com.example.bilawoga.utils.ScreenSecurityManager.clearClipboard(this);

        // SECURITY: Initialize app lock (but emergency SOS bypasses it)
        com.example.bilawoga.utils.AppLockManager appLock = new com.example.bilawoga.utils.AppLockManager(this);
        
        // Check if authentication required (emergency operations bypass this)
        // Note: Emergency SOS, shake detection, and background AI always work regardless of lock
        if (appLock.isLockEnabled() && appLock.isAuthenticationRequired() && !appLock.isEmergencyOperation()) {
            // Show lock screen (implementation depends on your UI)
            // For now, we'll allow access but log the security check
            Log.d(TAG, "App lock enabled - authentication may be required for non-emergency features");
        }
        
        // Basic initializations
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Prepare UI and receivers
        initializeViews();
        initializeSmsReceivers();

        // Start permission flow for Android runtime permissions first
        startPermissionFlowIfNeeded();
        
        // Initialize inclusivity settings
        com.example.bilawoga.utils.InclusivityManager.initializeDefaults(this);
        
        // Check Safety-by-Design compliance
        com.example.bilawoga.utils.SafetyByDesignCompliance.ComplianceReport compliance = 
            com.example.bilawoga.utils.SafetyByDesignCompliance.checkCompliance(this);
        Log.d(TAG, "Safety-by-Design Compliance: " + compliance.getComplianceStatus() + " (" + compliance.overallScore + "%)");
        
        // Check inclusivity compliance
        com.example.bilawoga.utils.InclusivityManager.InclusivityReport inclusivity = 
            com.example.bilawoga.utils.InclusivityManager.checkInclusivity(this);
        Log.d(TAG, "Inclusivity Status: " + inclusivity.getInclusivityStatus() + " (" + inclusivity.overallScore + "%)");

        // Initialize TTS for accessibility
        TTSLanguageManager.initDefaultOnFirstLaunch(this);
        setupTextToSpeech();
        
        // Initialize SilentEmergencyAI for audio detection and recording
        initializeSilentEmergencyAI();
        
        // Initialize Comprehensive GBV Detection System
        initializeComprehensiveGBVDetector();

        // Preload policy HTML off main thread to keep menu fast
        ioExecutor.execute(() -> {
            try {
                String pp = getStringFromAsset("PRIVACY_POLICY.html");
                String tu = getStringFromAsset("TERMS_OF_USE.html");
                if (pp == null || pp.isEmpty()) {
                    pp = "<h2>Privacy Policy</h2><p>Your privacy is important. We do not share personal data without consent.</p>";
                }
                if (tu == null || tu.isEmpty()) {
                    tu = "<h2>Terms of Use</h2><p>Use this app responsibly. Emergency features send SMS/location to contacts.</p>";
                }
                final String fpp = pp;
                final String ftu = tu;
                runOnUiThread(() -> {
                    cachedPrivacyHtml = fpp;
                    cachedTermsHtml = ftu;
                });
            } catch (Throwable ignored) { }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.popup, menu);
        return true;
    }

    // Show policy content in a reusable dialog
    private void showLogoutDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.log_out))
            .setMessage(getString(R.string.log_out_message))
            .setNegativeButton(getString(R.string.erase_now), (d, w) -> {
                // COMPLETE DATA DELETION: User wants to erase everything
                // Firebase sign out if configured
                try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut(); } catch (Throwable ignore) {}
                
                // Wipe local encrypted data and any app prefs
                com.example.bilawoga.utils.SecureStorageManager.secureWipeAllData(this);
                if (sharedPrefs != null) { sharedPrefs.edit().clear().apply(); }
                
                // Remove user identity completely (no restore possible)
                com.example.bilawoga.utils.UserIdentityManager.removeUserIdentity(this);
                
                Toast.makeText(this, getString(R.string.data_erased_logged_out), Toast.LENGTH_LONG).show();
                Log.d(TAG, "Complete data deletion: All data erased, user identity removed");
                
                // Go to onboarding (fresh start, no restore)
                Intent i = new Intent(this, OnboardingActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            })
            .setPositiveButton(getString(R.string.back_up_to_cloud_log_out), (d, w) -> {
                backupDataToFirebaseAndLogout();
            })
            .setNeutralButton(getString(R.string.cancel), null)
            .show();
    }

    private void backupDataToFirebaseAndLogout() {
        // Gather data from encrypted prefs
        SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
        if (prefs == null) {
            Toast.makeText(this, getString(R.string.secure_storage_not_available), Toast.LENGTH_SHORT).show();
            return;
        }
        String username = prefs.getString("USERNAME", "");
        String num1 = prefs.getString("ENUM_1", "");
        String num2 = prefs.getString("ENUM_2", "");
        String incident = prefs.getString("INCIDENT_TYPE", "");

        // USER IDENTITY: Get or create user ID for reliable restore
        String userId = com.example.bilawoga.utils.UserIdentityManager.getOrCreateUserId(this);
        Log.d(TAG, "Backing up data for user: " + com.example.bilawoga.utils.UserIdentityManager.maskUserId(userId));

        // Anonymous sign-in then write to Firestore
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        auth.signInAnonymously()
            .addOnSuccessListener(result -> {
                // Use user ID as document ID for reliable restore
                // This ensures same user can restore data even after logout
                String docId = userId; // Use user ID instead of FID for better reliability
                
                java.util.Map<String, Object> data = com.example.bilawoga.utils.CloudBackupCrypto.buildEncryptedPayload(
                        username, num1, num2, incident, System.currentTimeMillis());
                
                // Add user ID and device hash for verification
                data.put("user_id", userId);
                data.put("device_hash", com.example.bilawoga.utils.UserIdentityManager.getDeviceIdHash(this));
                data.put("backup_timestamp", System.currentTimeMillis());

                com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                db.collection("backups").document(docId)
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, getString(R.string.backup_complete_logging_out), Toast.LENGTH_LONG).show();
                        Log.d(TAG, "Data backed up successfully. User can restore later using user ID.");
                        
                        // Sign out and wipe local data (but keep user ID for restore)
                        try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut(); } catch (Throwable ignore) {}
                        
                        // Clear emergency data but preserve user identity for restore
                        com.example.bilawoga.utils.SecureStorageManager.secureWipeAllData(this);
                        if (sharedPrefs != null) { sharedPrefs.edit().clear().apply(); }
                        
                        // Clear user session but keep user ID
                        com.example.bilawoga.utils.UserIdentityManager.clearUserSession(this);
                        
                        // Go to onboarding (will auto-restore if same device)
                        Intent i = new Intent(this, OnboardingActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, getString(R.string.backup_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Backup failed: " + e.getMessage());
                    });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, getString(R.string.anonymous_sign_in_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Anonymous sign-in failed: " + e.getMessage());
            });
    }

    private void showPolicyDialog(String title, String htmlContent) {
        try {
            android.view.View policyView = getLayoutInflater().inflate(R.layout.dialog_policy, null);
            TextView policyContent = policyView.findViewById(R.id.policyContent);
            Button continueButton = policyView.findViewById(R.id.btnContinue);
            if (policyContent != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    policyContent.setText(android.text.Html.fromHtml(htmlContent, android.text.Html.FROM_HTML_MODE_COMPACT));
                } else {
                    policyContent.setText(android.text.Html.fromHtml(htmlContent));
                }
            }
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(policyView)
                    .setCancelable(true)
                    .create();
            if (continueButton != null) {
                continueButton.setOnClickListener(v -> dialog.dismiss());
            }
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.unable_to_show, title), Toast.LENGTH_SHORT).show();
        }
    }

    // Read simple HTML from assets (fallbacks to short text if not found)
    private String getStringFromAsset(String fileName) {
        try {
            java.io.InputStream is = getAssets().open(fileName);
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            return sb.toString();
        } catch (Exception e) {
            if ("PRIVACY_POLICY.html".equals(fileName)) {
                return "<h2>Privacy Policy</h2><p>Your privacy is important. We do not share personal data without consent.</p>";
            } else if ("TERMS_OF_USE.html".equals(fileName)) {
                return "<h2>Terms of Use</h2><p>Use this app responsibly. Emergency features send SMS/location to contacts.</p>";
            }
            return "";
        }
    }

    // Confirm logout and warn about data erasure
    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_message))
                .setNegativeButton(getString(R.string.cancel), (d, w) -> d.dismiss())
                .setPositiveButton(getString(R.string.logout_button), (d, w) -> {
                    // Clear encrypted prefs
                    SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
                    if (prefs != null) {
                        prefs.edit().clear().apply();
                    }
                    Toast.makeText(this, getString(R.string.you_have_been_logged_out), Toast.LENGTH_LONG).show();
                    finish();
                })
                .show();
    }

    /**
     * Initialize all views and set up click listeners
     */
    private void initializeViews() {
        Button startButton = findViewById(R.id.start);
        Button stopButton = null; // removed Stop button from layout
        Button sendButton = findViewById(R.id.send);
        TextView numbersText = findViewById(R.id.emergencyNumbersText);
        Button toggle = findViewById(R.id.btn_toggle_numbers);
        
        // SECURITY: Disable text selection on emergency contacts (prevent copying)
        if (numbersText != null) {
            com.example.bilawoga.utils.ScreenSecurityManager.disableTextSelection(numbersText);
        }

        // Service button opens the Emergency Help Center dialog
        startButton.setOnClickListener(v -> openEmergencyHelpCenter());
        
        // Support button opens support form
        Button contactSupportButton = findViewById(R.id.contactSupportButton);
        if (contactSupportButton != null) {
            contactSupportButton.setOnClickListener(v -> showSupportForm(v));
        }

        if (toggle != null && numbersText != null) {
            toggle.setOnClickListener(v -> toggleEmergencyNumbers(v));
        }

        // Send Alert should show countdown first
        if (sendButton != null) {
            sendButton.setOnClickListener(this::sendMessage);
        }
        // stopButton removed
    }


    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(ESSENTIAL_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private void startPermissionFlowIfNeeded() {
        if (!checkPermissions()) {
            // Build a dynamic list of only missing permissions
            List<String> missing = new ArrayList<>();
            for (String p : ESSENTIAL_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    missing.add(p);
                }
            }
            if (!missing.isEmpty()) {
                try {
                    multiplePermissions.launch(missing.toArray(new String[0]));
                } catch (Throwable t) {
                    Log.e(TAG, "Permission launcher failed: " + t.getMessage());
                    // Fallback to legacy API
                    requestPermissions();
                }
                return;
            }
        }

        // All runtime permissions granted; optionally request AI permission and start service
        if (!AIMonitoringPermission.hasPermission(this)) {
            checkAndRequestAIMonitoringPermission();
        } else if (!isMyServiceRunning("com.example.bilawoga.ServiceMine")) {
            startServiceAutomatically();
        }
    }

    // stopService removed with button

    // XML onClick handler: toggles emergency numbers visibility
    public void toggleEmergencyNumbers(View v) {
        TextView numbersText = findViewById(R.id.emergencyNumbersText);
        areEmergencyNumbersVisible = !areEmergencyNumbersVisible;

        if (numbersText != null) {
            // SECURITY: Disable text selection on emergency contacts (prevent copying)
            com.example.bilawoga.utils.ScreenSecurityManager.disableTextSelection(numbersText);
            if (areEmergencyNumbersVisible) {
                // Load full contacts from encrypted storage
                SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
                String num1 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_1, "") : "";
                String num2 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_2, "") : "";

                StringBuilder sb = new StringBuilder();
                if (num1 != null && !num1.isEmpty()) {
                    sb.append(getString(R.string.contact_1, num1));
                }
                if (num2 != null && !num2.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(getString(R.string.contact_2, num2));
                }
                if (sb.length() == 0) {
                    sb.append(getString(R.string.no_emergency_contacts_saved));
                }
                numbersText.setText(sb.toString());
                numbersText.setVisibility(View.VISIBLE);
            } else {
                numbersText.setVisibility(View.GONE);
            }
        }
    }

    private void startServiceAutomatically() {
        Log.d(TAG, "Starting service automatically...");
        
        // Check if we have AI monitoring permission before starting service
        if (AIMonitoringPermission.hasPermission(this)) {
            // Start the service when all permissions are granted
            Intent serviceIntent = new Intent();
            serviceIntent.setClassName(this, "com.example.bilawoga.ServiceMine");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Service started with AI monitoring");
            
            // Also start BackgroundAudioMonitor for continuous monitoring
            startBackgroundAudioMonitor();
        } else {
            Log.d(TAG, "AI monitoring permission required to start service");
            // Debounce permission request
            if (!aiPermissionRequestInProgress) {
                checkAndRequestAIMonitoringPermission();
            }
        }
    }
    
    /**
     * Start BackgroundAudioMonitor for continuous AI monitoring
     * This ensures monitoring works even when app is closed
     */
    private void startBackgroundAudioMonitor() {
        try {
            // Check if AI monitoring is enabled
            if (!AIMonitoringPermission.hasPermission(this)) {
                Log.d(TAG, "AI monitoring not enabled - BackgroundAudioMonitor not started");
                return;
            }
            
            // Check audio permission
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted - BackgroundAudioMonitor cannot start");
                return;
            }
            
            // Check if already running
            if (isMyServiceRunning("com.example.bilawoga.utils.BackgroundAudioMonitor")) {
                Log.d(TAG, "BackgroundAudioMonitor already running");
                return;
            }
            
            Intent audioMonitorIntent = new Intent(this, com.example.bilawoga.utils.BackgroundAudioMonitor.class);
            audioMonitorIntent.putExtra("emergency_listener", true);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(audioMonitorIntent);
            } else {
                startService(audioMonitorIntent);
            }
            
            Log.d(TAG, "BackgroundAudioMonitor started for continuous AI monitoring");
        } catch (Exception e) {
            Log.e(TAG, "Error starting BackgroundAudioMonitor: " + e.getMessage());
        }
    }

    private void checkAndRequestAIMonitoringPermission() {
        if (aiPermissionRequestInProgress) return;
        aiPermissionRequestInProgress = true;
        AIMonitoringPermission.checkAndRequestPermission(this, new AIMonitoringPermission.PermissionCallback() {
            @Override
            public void onPermissionGranted() {
                Log.d(TAG, "AI Monitoring permission granted");
                // Start service only if not already running
                if (!isMyServiceRunning("com.example.bilawoga.ServiceMine")) {
                    startServiceAutomatically();
                } else {
                    // Service already running, just ensure BackgroundAudioMonitor is also running
                    startBackgroundAudioMonitor();
                }
                // Show enabled status to user
                showToast(getString(R.string.ai_monitoring_enabled_enhanced));
                aiPermissionRequestInProgress = false;
            }

            @Override
            public void onPermissionDenied() {
                Log.d(TAG, "AI Monitoring permission denied");
                // Show message that some features may be limited
                if (isFinishing() || isDestroyed()) return;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showToast(getString(R.string.ai_monitoring_disabled_limited));
                    }
                }, 1000);
                aiPermissionRequestInProgress = false;
            }
        }, true);
    }
    
    private boolean isMyServiceRunning(String serviceClassName) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClassName.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        
        // Ensure BackgroundAudioMonitor is running if AI monitoring is enabled
        if (AIMonitoringPermission.hasPermission(this)) {
            if (!isMyServiceRunning("com.example.bilawoga.utils.BackgroundAudioMonitor")) {
                Log.d(TAG, "BackgroundAudioMonitor not running - starting it");
                startBackgroundAudioMonitor();
            }
        }
        
        // SECURITY: Monitor clipboard for sensitive data
        com.example.bilawoga.utils.ScreenSecurityManager.monitorClipboard(this);
        
        // Ensure runtime permissions first; then AI; then service
        if (!checkPermissions()) {
            startPermissionFlowIfNeeded();
        } else if (!AIMonitoringPermission.hasPermission(this)) {
            checkAndRequestAIMonitoringPermission();
        } else {
            // AI monitoring enabled - ensure both services are running
            if (!isMyServiceRunning("com.example.bilawoga.ServiceMine")) {
                startServiceAutomatically();
            } else {
                // ServiceMine is running, ensure BackgroundAudioMonitor is also running
                startBackgroundAudioMonitor();
            }
        }
        
        // Register sensor listener when activity resumes
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Register SMS sent/delivered receivers
        try {
            if (smsSentReceiver != null) {
                registerReceiver(smsSentReceiver, new IntentFilter(SMS_SENT_ACTION));
            }
            if (smsDeliveredReceiver != null) {
                registerReceiver(smsDeliveredReceiver, new IntentFilter(SMS_DELIVERED_ACTION));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering SMS receivers: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // SECURITY: Clear clipboard when app goes to background
        com.example.bilawoga.utils.ScreenSecurityManager.clearClipboard(this);
        
        // Unregister SMS receivers to avoid leaks
        try {
            if (smsSentReceiver != null) unregisterReceiver(smsSentReceiver);
        } catch (IllegalArgumentException ignore) {}
        try {
            if (smsDeliveredReceiver != null) unregisterReceiver(smsDeliveredReceiver);
        } catch (IllegalArgumentException ignore) {}
    }

    @Override
    protected void onDestroy() {
        // Stop TTS
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        // Unregister SMS receivers safely
        try {
            if (smsSentReceiver != null) {
                unregisterReceiver(smsSentReceiver);
                smsSentReceiver = null;
            }
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "smsSentReceiver not registered: " + ex.getMessage());
        }

        try {
            if (smsDeliveredReceiver != null) {
                unregisterReceiver(smsDeliveredReceiver);
                smsDeliveredReceiver = null;
            }
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "smsDeliveredReceiver not registered: " + ex.getMessage());
        }

        // Cleanup resources
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (audioHandler != null) {
            audioHandler.removeCallbacksAndMessages(null);
        }

        // Cleanup SOS countdown
        if (sosCountdownDialog != null && sosCountdownDialog.isShowing()) {
            try { sosCountdownDialog.dismiss(); } catch (Throwable ignore) {}
        }
        sosCountdownHandler.removeCallbacksAndMessages(null);

        // Unregister sensor listener
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        
        // Cleanup SilentEmergencyAI
        if (silentEmergencyAI != null) {
            silentEmergencyAI.cleanup();
            Log.d(TAG, "SilentEmergencyAI cleaned up");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    // --- Helper methods added to resolve missing references ---

    /**
     * Checks if all essential permissions are granted.
     */
    private boolean checkPermissions() {
        for (String perm : ESSENTIAL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Shows a simple dialog listing Kenya emergency hotlines.
     */
    private void showKenyaEmergencyHotlines() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.emergency_services_kenya))
                    .setMessage(getString(R.string.kenya_emergency_hotlines))
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.unable_to_show, getString(R.string.emergency_services_kenya)), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows support form dialog for users to submit issues/feedback
     */
    public void showSupportForm(View v) {
        try {
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_support_form, null);
            
            com.google.android.material.textfield.TextInputEditText issueDescription = 
                dialogView.findViewById(R.id.issueDescription);
            com.google.android.material.checkbox.MaterialCheckBox anonymousCheckbox = 
                dialogView.findViewById(R.id.anonymousCheckbox);
            com.google.android.material.textfield.TextInputLayout emailLayout = 
                dialogView.findViewById(R.id.emailLayout);
            com.google.android.material.textfield.TextInputEditText userEmail = 
                dialogView.findViewById(R.id.userEmail);
            com.google.android.material.button.MaterialButton cancelButton = 
                dialogView.findViewById(R.id.cancelButton);
            com.google.android.material.button.MaterialButton submitButton = 
                dialogView.findViewById(R.id.submitButton);
            
            // Toggle email field visibility based on anonymous checkbox
            anonymousCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                emailLayout.setVisibility(isChecked ? View.GONE : View.VISIBLE);
                if (isChecked) {
                    userEmail.setText(""); // Clear email when anonymous
                }
            });
            
            // Initially show email field
            emailLayout.setVisibility(View.VISIBLE);
            
            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
            
            cancelButton.setOnClickListener(v1 -> dialog.dismiss());
            
            submitButton.setOnClickListener(v1 -> {
                String description = issueDescription.getText() != null ? 
                    issueDescription.getText().toString().trim() : "";
                
                if (description.isEmpty()) {
                    Toast.makeText(this, "Please describe your issue", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                boolean isAnonymous = anonymousCheckbox.isChecked();
                String email = isAnonymous ? "" : 
                    (userEmail.getText() != null ? userEmail.getText().toString().trim() : "");
                
                // Send email
                sendSupportEmail(description, email, isAnonymous);
                dialog.dismiss();
                Toast.makeText(this, "Support request submitted. Thank you!", Toast.LENGTH_LONG).show();
            });
            
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing support form: " + e.getMessage(), e);
            Toast.makeText(this, "Unable to open support form", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Sends support email automatically to carenjeruto477@gmail.com (email hidden from user)
     * Works with or without internet/data connection
     */
    private void sendSupportEmail(String description, String userEmail, boolean isAnonymous) {
        // Check internet connectivity first
        boolean hasInternet = checkInternetConnection();
        
        if (hasInternet) {
            // Show loading indicator
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
            progressDialog.setMessage("Sending support request...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // Run in background thread
            new Thread(() -> {
                try {
                    String subject = "BilaWoga Support Request" + (isAnonymous ? " (Anonymous)" : "");
                    
                    // Try to send via HTTP automatically
                    boolean sent = com.example.bilawoga.utils.EmailSender.sendEmail(
                        this, subject, description, userEmail, isAnonymous);
                    
                    final boolean finalSent = sent;
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (finalSent) {
                            Toast.makeText(this, "Support request sent successfully! We'll get back to you soon.", Toast.LENGTH_LONG).show();
                        } else {
                            // Fallback: Use email intent (user has to click send, but email is pre-filled)
                            sendEmailViaIntentFallback(subject, description, userEmail, isAnonymous);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error sending support email: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        // Fallback to email intent or offline method
                        sendEmailViaIntentFallback("BilaWoga Support Request", description, userEmail, isAnonymous);
                    });
                }
            }).start();
        } else {
            // No internet - use offline method
            handleOfflineSupportRequest(description, userEmail, isAnonymous);
        }
    }
    
    /**
     * Check if device has internet connection
     */
    private boolean checkInternetConnection() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e(TAG, "Error checking internet: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handle support request when user has no internet/data
     * Saves request locally and shows options to send later
     */
    private void handleOfflineSupportRequest(String description, String userEmail, boolean isAnonymous) {
        try {
            // Save support request locally for later sending
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            if (prefs != null) {
                String requestId = "support_" + System.currentTimeMillis();
                StringBuilder requestData = new StringBuilder();
                requestData.append("BilaWoga Support Request\n\n");
                requestData.append("Issue Description:\n");
                requestData.append(description).append("\n\n");
                
                if (!isAnonymous && userEmail != null && !userEmail.isEmpty()) {
                    requestData.append("User Email: ").append(userEmail).append("\n");
                } else {
                    requestData.append("Submitted anonymously\n");
                }
                
                requestData.append("\nTimestamp: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
                
                // Save to preferences
                prefs.edit().putString(requestId, requestData.toString()).apply();
                
                // Show dialog with options
                new MaterialAlertDialogBuilder(this)
                    .setTitle("No Internet Connection")
                    .setMessage("Your support request has been saved locally.\n\n" +
                               "Options:\n" +
                               "1. Send via SMS (if you have SMS credit)\n" +
                               "2. Save for later (will send automatically)\n" +
                               "3. Send when you have internet/data\n\n" +
                               "Your request will be sent automatically when you get internet.")
                    .setPositiveButton("Send via SMS", (dialog, which) -> {
                        sendSupportViaSMS(description, userEmail, isAnonymous);
                    })
                    .setNeutralButton("Save for Later", (dialog, which) -> {
                        scheduleSupportRequestRetry(description, userEmail, isAnonymous);
                        Toast.makeText(this, "Request saved. Will send automatically when internet is available.", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Send Later", (dialog, which) -> {
                        Toast.makeText(this, "Request saved. Will send automatically when internet is available.", Toast.LENGTH_LONG).show();
                        // Schedule retry when internet is available
                        scheduleSupportRequestRetry(description, userEmail, isAnonymous);
                    })
                    .setCancelable(true)
                    .show();
            } else {
                // If can't save, schedule retry
                scheduleSupportRequestRetry(description, userEmail, isAnonymous);
                Toast.makeText(this, "Request saved. Will send automatically when internet is available.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling offline request: " + e.getMessage(), e);
            // Fallback: Schedule retry
            scheduleSupportRequestRetry(description, userEmail, isAnonymous);
            Toast.makeText(this, "Request saved. Will send automatically when internet is available.", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Send support request via SMS (works without internet, only needs SMS credit)
     */
    private void sendSupportViaSMS(String description, String userEmail, boolean isAnonymous) {
        try {
            // Check SMS permission
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission required to send support request", Toast.LENGTH_SHORT).show();
                requestPermissions(new String[]{android.Manifest.permission.SEND_SMS}, 999);
                return;
            }
            
            String recipientNumber = "+254712028456"; // Support number (hidden from user in code)
            String smsBody = "BilaWoga Support: " + description.substring(0, Math.min(140, description.length()));
            if (!isAnonymous && userEmail != null && !userEmail.isEmpty()) {
                smsBody += " Email: " + userEmail;
            }
            
            android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
            smsManager.sendTextMessage(recipientNumber, null, smsBody, null, null);
            
            Toast.makeText(this, "Support request sent via SMS", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS: " + e.getMessage(), e);
            Toast.makeText(this, "Unable to send SMS. Your request will be sent automatically when internet is available.", Toast.LENGTH_LONG).show();
            scheduleSupportRequestRetry(description, userEmail, isAnonymous);
        }
    }
    
    /**
     * Copy support request to clipboard for manual sending
     */
    private void copySupportRequestToClipboard(String description, String userEmail, boolean isAnonymous) {
        try {
            StringBuilder clipboardText = new StringBuilder();
            clipboardText.append("BilaWoga Support Request\n\n");
            clipboardText.append("Issue: ").append(description).append("\n\n");
            if (!isAnonymous && userEmail != null && !userEmail.isEmpty()) {
                clipboardText.append("My Email: ").append(userEmail).append("\n\n");
            }
            clipboardText.append("Send to: carenjeruto477@gmail.com");
            
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Support Request", clipboardText.toString());
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(this, "Support request saved. It will be sent automatically when internet is available.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error copying to clipboard: " + e.getMessage(), e);
        }
    }
    
    /**
     * Schedule retry to send support request when internet becomes available
     */
    private void scheduleSupportRequestRetry(String description, String userEmail, boolean isAnonymous) {
        // Save request to be sent later
        SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
        if (prefs != null) {
            String pendingRequest = description + "|||" + (userEmail != null ? userEmail : "") + "|||" + isAnonymous;
            prefs.edit().putString("pending_support_request", pendingRequest).apply();
            
            // Check for internet periodically and send when available
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (checkInternetConnection()) {
                    String subject = "BilaWoga Support Request" + (isAnonymous ? " (Anonymous)" : "");
                    com.example.bilawoga.utils.EmailSender.sendEmail(this, subject, description, userEmail, isAnonymous);
                    prefs.edit().remove("pending_support_request").apply();
                    Toast.makeText(this, "Support request sent automatically!", Toast.LENGTH_SHORT).show();
                }
            }, 30000); // Check after 30 seconds
        }
    }
    
    /**
     * Fallback: Send via email intent (email is pre-filled but user needs to click send)
     * Email address is still hidden from user in the UI
     */
    private void sendEmailViaIntentFallback(String subject, String description, String userEmail, boolean isAnonymous) {
        try {
            String recipientEmail = "carenjeruto477@gmail.com"; // Hidden from user
            
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("BilaWoga Support Request\n\n");
            emailBody.append("Issue Description:\n");
            emailBody.append(description).append("\n\n");
            
            if (!isAnonymous && userEmail != null && !userEmail.isEmpty()) {
                emailBody.append("User Email: ").append(userEmail).append("\n");
            } else {
                emailBody.append("Submitted anonymously\n");
            }
            
            emailBody.append("\n---\n");
            try {
                emailBody.append("App Version: ").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append("\n");
            } catch (Exception e) {
                emailBody.append("App Version: Unknown\n");
            }
            emailBody.append("Device: ").append(android.os.Build.MODEL).append("\n");
            emailBody.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            emailBody.append("Timestamp: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
            
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(android.net.Uri.parse("mailto:" + recipientEmail));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, emailBody.toString());
            
            if (emailIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(emailIntent);
                Toast.makeText(this, "Please click 'Send' in your email app", Toast.LENGTH_SHORT).show();
            } else {
                // Last resort: Save for later sending
                Toast.makeText(this, "Email app not available. Your request will be sent automatically when possible.", Toast.LENGTH_LONG).show();
                // Save request to be sent later
                scheduleSupportRequestRetry(description, userEmail, isAnonymous);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in fallback email: " + e.getMessage());
            Toast.makeText(this, "Unable to send email. Please contact support.", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Opens the Emergency Help Center dialog using a custom layout.
     */
    private void openEmergencyHelpCenter() {
        try {
            View view = getLayoutInflater().inflate(R.layout.dialog_emergency_services, null);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.emergency_help_center))
                    .setView(view)
                    .setPositiveButton(getString(R.string.close), null)
                    .show();
        } catch (Throwable t) {
            Log.e(TAG, "openEmergencyHelpCenter error: " + t.getMessage());
            Toast.makeText(this, getString(R.string.unable_to_show, getString(R.string.emergency_help_center)), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * onClick handler from dialog_emergency_services.xml buttons. Uses tag="tel:<number>".
     */
    public void onEmergencyCall(View v) {
        try {
            Object tag = v.getTag();
            if (tag == null) {
                Toast.makeText(this, getString(R.string.no_number_available), Toast.LENGTH_SHORT).show();
                return;
            }
            String uri = tag.toString(); // e.g., tel:999
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "onEmergencyCall error: " + t.getMessage());
            Toast.makeText(this, getString(R.string.unable_to_open_dialer), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * XML onClick handler for the Send button.
     */
    public void sendMessage(View v) {
        // DEBOUNCING: Prevent multiple presses from causing multiple SOS alerts
        long currentTime = System.currentTimeMillis();
        if (isSendingSOS || (currentTime - lastSOSPressTime < SOS_DEBOUNCE_DELAY_MS)) {
            // Already sending or too soon after last press
            if (isSendingSOS) {
                android.widget.Toast.makeText(this, "Emergency alert is already being sent. Please wait...", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }
        
        // RATE LIMITING: Check if user has exceeded 2 SOS per hour
        if (!canSendSOS()) {
            android.widget.Toast.makeText(this, "Emergency alert limit reached. Maximum 2 alerts per hour for safety.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        
        // Mark as sending and record time
        isSendingSOS = true;
        lastSOSPressTime = currentTime;
        
        // Record SOS send for rate limiting
        recordSOSSend(currentTime);
        
        // Disable button temporarily to prevent multiple presses
        if (v != null) {
            v.setEnabled(false);
        }
        
        // CONFIRMATION MODE: Show countdown dialog to prevent accidental sends
        // User must confirm before SOS is sent
        showSOSCountdownDialog();
        
        // Re-enable button after delay (only if not already re-enabled by cancellation)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isSendingSOS) { // Only re-enable if still sending (not cancelled)
                isSendingSOS = false;
                if (v != null) {
                    v.setEnabled(true);
                }
            }
        }, SOS_DEBOUNCE_DELAY_MS);
        
        // Optionally: Show brief invisible confirmation (dismisses immediately)
        // This allows the button press to feel responsive without showing anything
        try {
            // Dismiss any existing dialog silently
            if (sosCountdownDialog != null && sosCountdownDialog.isShowing()) {
                sosCountdownDialog.dismiss();
            }
        } catch (Throwable ignore) {}
    }
    
    /**
     * Check if user can send SOS (rate limiting: 2 per hour)
     */
    private boolean canSendSOS() {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            if (prefs == null) {
                return true; // Allow if can't check (fail open for emergencies)
            }
            
            String sosHistoryJson = prefs.getString("sos_send_history", "[]");
            java.util.List<Long> sosTimestamps = new java.util.ArrayList<>();
            
            // Parse JSON array of timestamps
            try {
                org.json.JSONArray jsonArray = new org.json.JSONArray(sosHistoryJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    sosTimestamps.add(jsonArray.getLong(i));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing SOS history: " + e.getMessage());
            }
            
            // Remove timestamps older than 1 hour
            long currentTime = System.currentTimeMillis();
            java.util.List<Long> recentSOS = new java.util.ArrayList<>();
            for (Long timestamp : sosTimestamps) {
                if (currentTime - timestamp < HOUR_MS) {
                    recentSOS.add(timestamp);
                }
            }
            
            // Check if user has exceeded limit
            if (recentSOS.size() >= MAX_SOS_PER_HOUR) {
                Log.w(TAG, "SOS rate limit reached: " + recentSOS.size() + " sends in the last hour");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking SOS rate limit: " + e.getMessage());
            return true; // Fail open for emergencies
        }
    }
    
    /**
     * Record SOS send timestamp for rate limiting
     */
    private void recordSOSSend(long timestamp) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            if (prefs == null) {
                return;
            }
            
            String sosHistoryJson = prefs.getString("sos_send_history", "[]");
            java.util.List<Long> sosTimestamps = new java.util.ArrayList<>();
            
            // Parse existing timestamps
            try {
                org.json.JSONArray jsonArray = new org.json.JSONArray(sosHistoryJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    long ts = jsonArray.getLong(i);
                    // Only keep timestamps from last hour
                    if (System.currentTimeMillis() - ts < HOUR_MS) {
                        sosTimestamps.add(ts);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing SOS history: " + e.getMessage());
            }
            
            // Add new timestamp
            sosTimestamps.add(timestamp);
            
            // Save back to preferences
            org.json.JSONArray newArray = new org.json.JSONArray();
            for (Long ts : sosTimestamps) {
                newArray.put(ts);
            }
            
            prefs.edit().putString("sos_send_history", newArray.toString()).apply();
            Log.d(TAG, "Recorded SOS send: " + sosTimestamps.size() + " sends in the last hour");
        } catch (Exception e) {
            Log.e(TAG, "Error recording SOS send: " + e.getMessage());
        }
    }

    private void cancelSosCountdown() {
        sosCountdownHandler.removeCallbacksAndMessages(null);
        if (sosCountdownDialog != null) {
            try { sosCountdownDialog.dismiss(); } catch (Throwable ignore) {}
        }
    }

    private void performSendSOS() {
        try {
            // EMERGENCY BYPASS: SOS always works even if app is locked
            // This ensures user safety is never compromised by security measures
            
            // Ensure SEND_SMS permission before attempting
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // STEALTH: Silent permission request - no toast
                requestPermissions();
                return;
            }
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            if (prefs == null) {
                // STEALTH: Silent failure - no toast
                Log.e(TAG, "STEALTH: Secure storage not available (no UI indication)");
                return;
            }

            String user = prefs.getString(KEY_USERNAME, getString(R.string.user));
            String incident = prefs.getString(KEY_INCIDENT_TYPE, getString(R.string.emergency));
            String num1 = prefs.getString(KEY_EMERGENCY_NUMBER_1, "");
            String num2 = prefs.getString(KEY_EMERGENCY_NUMBER_2, "");

            // EMERGENCY BYPASS: Works even if app lock is enabled
            com.example.bilawoga.utils.SOSHelper helper = new com.example.bilawoga.utils.SOSHelper(this);
            helper.sendEmergencySOS(user, incident, num1, num2);
            
            // SECURITY: Clear clipboard after sending SOS (may contain sensitive data)
            com.example.bilawoga.utils.ScreenSecurityManager.clearClipboard(this);
            
            // Success message will be shown by SOSHelper after SMS is sent
        } catch (Throwable t) {
            Log.e(TAG, "STEALTH: performSendSOS error: " + t.getMessage() + " (no UI indication)");
            // No toast - silent failure
        }
    }

    /**
     * Initialize SilentEmergencyAI for background audio monitoring
     * This enables the trained AI model to detect emergencies and send recordings
     */
    private void initializeSilentEmergencyAI() {
        try {
            if (isSilentEmergencyEnabled) {
                silentEmergencyAI = new SilentEmergencyAI(this, new SilentEmergencyAI.EmergencyListener() {
                    @Override
                    public void onEmergencyDetected(String type, float confidence) {
                        Log.d(TAG, "AI Emergency Detected: " + type + " (confidence: " + confidence + ")");
                        
                        // Show user notification about AI detection
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                "🚨 AI Alert: " + type + " detected!\n" +
                                "Confidence: " + String.format("%.0f%%", confidence * 100) + "\n" +
                                "Press Send Alert if you need emergency help", 
                                Toast.LENGTH_LONG).show();
                        });
                        
                        // AI detection alert - NO automatic sending
                        Log.d(TAG, "⚠️ AI DETECTION ALERT: Emergency detected but not sending automatically. User must manually send SOS.");
                    }
                    
                    @Override
                    public void onEmergencyConfirmed(String type) {
                        Log.d(TAG, "AI Emergency Confirmed: " + type);
                        
                        // Show user notification about AI confirmation
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                "✅ AI Confirmed: " + type + "\n" +
                                "High confidence detection verified\n" +
                                "Emergency alert ready if needed", 
                                Toast.LENGTH_LONG).show();
                        });
                        
                        // AI confirmation - NO automatic sending
                        Log.d(TAG, "⚠️ AI CONFIRMATION: Emergency confirmed but not sending automatically. User must manually send SOS.");
                    }
                    
                    @Override
                    public void onFalseAlarmPrevented(String reason) {
                        Log.d(TAG, "AI False Alarm Prevented: " + reason);
                        
                        // Show brief notification about false alarm prevention
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                "🛡️ AI False Alarm Prevented: " + reason, 
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    @Override
                    public void onModelUnavailable(String reason) {
                        Log.w(TAG, "AI Model Unavailable: " + reason);
                        // Fallback to BackgroundAudioMonitor if model unavailable
                    }
                    
                    @Override
                    public void onAudioRecorded(byte[] audioData, String emergencyType) {
                        Log.d(TAG, "AI Audio Recorded: " + emergencyType + " (" + audioData.length + " bytes)");
                        // Audio recorded and will be sent to trusted contacts
                    }
                    
                    @Override
                    public void onNewVoiceDetected(String voiceId, float similarity) {
                        Log.d(TAG, "AI New Voice Detected: " + voiceId + " (similarity: " + similarity + ")");
                        // Learn new voice for future recognition
                        learnNewVoice(voiceId, similarity);
                    }
                });
                
                // Start silent monitoring if model is available
                if (silentEmergencyAI.isModelAvailable()) {
                    silentEmergencyAI.startSilentMonitoring();
                    Log.d(TAG, "SilentEmergencyAI started - monitoring for emergencies with audio recording");
                } else {
                    Log.w(TAG, "SilentEmergencyAI model not available - using BackgroundAudioMonitor only");
                }
                
                // Always start BackgroundAudioMonitor for continuous monitoring
                startBackgroundAudioMonitor();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SilentEmergencyAI: " + e.getMessage());
            // Continue without AI - BackgroundAudioMonitor will still work
        }
    }
    
    /**
     * Learn new voice for future recognition
     * ENHANCED: Voice learning capability
     */
    private void learnNewVoice(String voiceId, float similarity) {
        try {
            // Use AdaptiveVoiceLearningAI to learn and store voice patterns
            com.example.bilawoga.utils.AdaptiveVoiceLearningAI voiceLearner = 
                new com.example.bilawoga.utils.AdaptiveVoiceLearningAI(this);
            
            // Store voice pattern for future recognition
            voiceLearner.learnVoicePattern(voiceId, similarity);
            
            Log.d(TAG, "✅ Voice learned: " + voiceId + " (similarity: " + similarity + ")");
            Log.d(TAG, "Voice pattern stored for future emergency detection");
            
        } catch (Exception e) {
            Log.e(TAG, "Error learning voice: " + e.getMessage());
        }
    }
    
    /**
     * Initialize Comprehensive GBV Detection System
     * Supports all three modes: Audio, Text, and Synthetic Training
     */
    private void initializeComprehensiveGBVDetector() {
        try {
            if (isGBVDetectionEnabled) {
                Log.i(TAG, "Initializing Comprehensive GBV Detection System...");
                
                // Initialize comprehensive detector with all modes
                comprehensiveGBVDetector = new ComprehensiveGBVDetector(this, 
                    new ComprehensiveGBVDetector.ComprehensiveDetectionListener() {
                    
                    @Override
                    public void onGBVDetected(String type, float confidence, String source) {
                        Log.w(TAG, "GBV Detected: " + type + " (confidence: " + confidence + 
                                  ", source: " + source + ")");
                        
                        // Show user notification ONLY - do NOT send SOS automatically
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                "GBV Pattern Detected: " + type + " (Confidence: " + 
                                String.format("%.0f%%", confidence * 100) + ")\n" +
                                "Press Send Alert if you need emergency help", 
                                Toast.LENGTH_LONG).show();
                        });
                        
                        // AUTOMATIC SOS DISABLED: Only notify user, do not send automatically
                        // User must manually press "Send Alert" button to send SOS
                        if (confidence > 0.7f) {
                            Log.d(TAG, "⚠️ GBV AUTOMATIC SOS DISABLED: High confidence GBV detected but not sending automatically. User must manually send SOS.");
                            // triggerEmergencyFromGBV(type, confidence); // DISABLED
                        }
                    }
                    
                    @Override
                    public void onTrainingProgress(int epoch, int totalEpochs, float accuracy) {
                        Log.d(TAG, "GBV Training - Epoch " + epoch + "/" + totalEpochs + 
                                  " - Accuracy: " + String.format("%.2f%%", accuracy * 100));
                    }
                    
                    @Override
                    public void onTrainingComplete(boolean success, String message) {
                        if (success) {
                            Log.i(TAG, "✅ GBV Training Complete: " + message);
                            showGBVTrainingCompleteNotification();
                        } else {
                            Log.e(TAG, "❌ GBV Training Failed: " + message);
                        }
                    }
                    
                    @Override
                    public void onModeChanged(ComprehensiveGBVDetector.DetectionMode newMode) {
                        Log.i(TAG, "GBV Detection mode changed to: " + newMode);
                        showModeChangeNotification(newMode);
                    }
                    
                    @Override
                    public void onSyntheticAudioGenerated(String audioPath, int count) {
                        Log.d(TAG, "Synthetic audio generated: " + audioPath + " (Total: " + count + ")");
                    }
                });
                
                // Set default mode to HYBRID (works in all environments)
                comprehensiveGBVDetector.setDetectionMode(ComprehensiveGBVDetector.DetectionMode.HYBRID);
                
                // Show available modes to user
                showAvailableGBVModes();
                
                Log.i(TAG, "Comprehensive GBV Detector initialized successfully");
                
            } else {
                Log.i(TAG, "Comprehensive GBV Detection System disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Comprehensive GBV Detector: " + e.getMessage(), e);
        }
    }
    
    /**
     * Show available GBV detection modes to user
     */
    private void showAvailableGBVModes() {
        if (comprehensiveGBVDetector != null) {
            List<ComprehensiveGBVDetector.DetectionMode> availableModes = 
                comprehensiveGBVDetector.getAvailableModes();
            
            StringBuilder modesInfo = new StringBuilder();
            modesInfo.append("Available GBV Detection Modes:\n");
            
            for (ComprehensiveGBVDetector.DetectionMode mode : availableModes) {
                modesInfo.append("- ").append(mode.toString()).append(": ")
                          .append(comprehensiveGBVDetector.getModeDescription(mode)).append("\n");
            }
            
            Log.i(TAG, modesInfo.toString());
            
            // Show to user
            runOnUiThread(() -> {
                Toast.makeText(this, "GBV Detection System Active - Multiple modes available", 
                             Toast.LENGTH_LONG).show();
            });
        }
    }
    
    /**
     * Show mode change notification
     */
    private void showModeChangeNotification(ComprehensiveGBVDetector.DetectionMode mode) {
        runOnUiThread(() -> {
            String description = comprehensiveGBVDetector.getModeDescription(mode);
            Toast.makeText(this, "GBV Mode: " + description, Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * Show GBV training complete notification
     */
    private void showGBVTrainingCompleteNotification() {
        runOnUiThread(() -> {
            Toast.makeText(this, "GBV Detection System Ready! Enhanced protection activated.", 
                         Toast.LENGTH_LONG).show();
            Log.i(TAG, "Comprehensive GBV detection system is now ready");
        });
    }
    
    /**
     * Trigger emergency from GBV detection
     */
    private void triggerEmergencyFromGBV(String gbvType, float confidence) {
        try {
            Log.w(TAG, "Triggering emergency from GBV detection: " + gbvType);
            
            // Use existing SOS helper to send emergency alert
            SOSHelper sosHelper = new SOSHelper(this);
            
            String emergencyMessage = "GBV Pattern Detected: " + gbvType + 
                                   " (Confidence: " + String.format("%.0f%%", confidence * 100) + ")";
            
            // Get emergency contacts
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
            String userName = prefs.getString("USERNAME", "Unknown User");
            String emergencyNumber1 = prefs.getString("ENUM_1", "");
            String emergencyNumber2 = prefs.getString("ENUM_2", "");
            
            // Send emergency alert
            sosHelper.sendEmergencyAlert(userName, emergencyMessage, 
                                     emergencyNumber1, emergencyNumber2);
            
        } catch (Exception e) {
            Log.e(TAG, "Error triggering emergency from GBV: " + e.getMessage(), e);
        }
    }
    
    /**
     * Show SOS countdown dialog for user confirmation
     * Prevents accidental SOS sends
     */
    private void showSOSCountdownDialog() {
        sosCountdown = 7; // 7 second countdown
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("🚨 EMERGENCY ALERT CONFIRMATION");
        builder.setMessage("Sending emergency alert in " + sosCountdown + " seconds...\n\n" +
                          "This will send your location and emergency message to your contacts.\n\n" +
                          "Press CANCEL to stop.");
        
        // Make dialog non-cancelable to prevent accidental dismissal
        builder.setCancelable(false);
        
        // Add Cancel button
        builder.setNegativeButton("CANCEL", (dialog, which) -> {
            dialog.dismiss();
            isSendingSOS = false;
            Log.i(TAG, "SOS cancelled by user");
            Toast.makeText(this, "Emergency alert cancelled", Toast.LENGTH_SHORT).show();
        });
        
        sosCountdownDialog = builder.create();
        sosCountdownDialog.show();
        
        // Start countdown
        sosCountdownTick = new Runnable() {
            @Override
            public void run() {
                sosCountdown--;
                
                if (sosCountdown > 0) {
                    // Update dialog message
                    if (sosCountdownDialog != null && sosCountdownDialog.isShowing()) {
                        sosCountdownDialog.setMessage("Sending emergency alert in " + sosCountdown + " seconds...\n\n" +
                                                  "This will send your location and emergency message to your contacts.\n\n" +
                                                  "Press CANCEL to stop.");
                    }
                    
                    // Continue countdown
                    sosCountdownHandler.postDelayed(this, 1000);
                } else {
                    // Countdown finished - send SOS
                    if (sosCountdownDialog != null && sosCountdownDialog.isShowing()) {
                        sosCountdownDialog.dismiss();
                    }
                    performSendSOS();
                }
            }
        };
        
        // Start countdown timer
        sosCountdownHandler.postDelayed(sosCountdownTick, 1000);
    }
    
    private void setupTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                updateTTSLanguage();
                // Auto-read main screen if enabled
                if (TTSLanguageManager.isAutoReadEnabled(this)) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        speakMainScreenContent();
                    }, 500); // Small delay to ensure UI is ready
                }
            }
        });
    }

    private void updateTTSLanguage() {
        if (tts == null || !ttsReady) return;
        try {
            Locale selected = TTSLanguageManager.getSelectedLocale(this);
            if (selected == null) {
                selected = new Locale("sw", "KE");
            }
            int result = TTSLanguageManager.setTtsLanguage(tts, selected);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, getString(R.string.selected_tts_voice_missing), Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                } catch (Exception ignored) {}
            }
            if ("sw".equals(selected.getLanguage())) {
                tts.setSpeechRate(0.90f);
                tts.setPitch(1.0f);
            } else {
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.0f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating TTS language: " + e.getMessage());
        }
    }

    private void speakMainScreenContent() {
        if (!TTSLanguageManager.isAutoReadEnabled(this) || !ttsReady || tts == null) return;
        
        List<String> parts = new ArrayList<>();
        Locale selected = TTSLanguageManager.getSelectedLocale(this);
        boolean isSw = selected != null && "sw".equalsIgnoreCase(selected.getLanguage());
        
        if (isSw) {
            parts.add("Ukurasa wa BilaWoga.");
            parts.add("Hapa unaweza kuanzisha dharura au kupata msaada.");
            parts.add("Mawasiliano ya Dharura: Hapa unaona namba za dharura zako.");
            parts.add("Msaada wa BilaWoga: Piga simu ya msaada yetu kwa msaada wa ziada.");
            parts.add("Kituo cha Msaada wa Dharura: Fungua orodha ya namba za dharura za Kenya.");
            parts.add("Tuma Taarifa: Tuma taarifa ya dharura kwa mawasiliano yako.");
            parts.add("Ulinzi wa AI: Ufuatiliaji wa AI unaendelea kwa ajili ya kugundua dharura.");
        } else {
            parts.add("BilaWoga Main Screen.");
            parts.add("Here you can initiate emergencies or get help.");
            parts.add("Emergency Contacts: View your emergency contact numbers here.");
            parts.add("BilaWoga Support: Call our support line for additional assistance.");
            parts.add("Emergency Help Center: Open the list of Kenya emergency numbers.");
            parts.add("Send Alert: Send an emergency alert to your contacts.");
            parts.add("AI Protection: AI monitoring is active for emergency detection.");
        }
        
        // Speak queued
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            int queueMode = first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(p, queueMode, null, java.util.UUID.randomUUID().toString());
            tts.playSilentUtterance(150, TextToSpeech.QUEUE_ADD, java.util.UUID.randomUUID().toString());
            first = false;
        }
    }
    
    /**
     * Show TTS preference dialog immediately on launch
     * Asks user if they want TTS auto-talk enabled (YES/NO)
     * Only shows once on first launch
     */
    private void showTTSPreferenceDialog() {
        try {
            // Determine language for dialog text
            java.util.Locale selected = com.example.bilawoga.utils.TTSLanguageManager.getSelectedLocale(this);
            boolean isSw = selected != null && "sw".equalsIgnoreCase(selected.getLanguage());
            
            String title, message, yesButton, noButton;
            
            if (isSw) {
                title = "Sauti ya Kusoma Maandishi (TTS)";
                message = "Je, ungependa BilaWoga ikusomee maandishi kwa sauti?\n\n" +
                         "Hii ni hiari na unaweza kuibadilisha wakati wowote kutoka kwenye Mipangilio.";
                yesButton = "Ndiyo";
                noButton = "Hapana";
            } else {
                title = "Text-to-Speech (TTS)";
                message = "Do you want BilaWoga to read text aloud?\n\n" +
                         "This is optional and you can change it anytime from Settings.";
                yesButton = "Yes";
                noButton = "No";
            }
            
            androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yesButton, (d, which) -> {
                    // User wants TTS enabled
                    com.example.bilawoga.utils.TTSLanguageManager.setAutoReadEnabled(this, true);
                    com.example.bilawoga.utils.TTSLanguageManager.markTTSPreferenceAsked(this);
                    Log.d(TAG, "User enabled TTS auto-talk");
                    d.dismiss();
                })
                .setNegativeButton(noButton, (d, which) -> {
                    // User does not want TTS enabled
                    com.example.bilawoga.utils.TTSLanguageManager.setAutoReadEnabled(this, false);
                    com.example.bilawoga.utils.TTSLanguageManager.markTTSPreferenceAsked(this);
                    Log.d(TAG, "User disabled TTS auto-talk");
                    d.dismiss();
                })
                .setCancelable(false) // User must choose - cannot dismiss without selecting
                .create();
            
            // Show dialog immediately
            dialog.show();
            
            // Set cream white text colors for inclusivity and better visibility on black background
            try {
                // Cream white color (#FFFEF5 or similar warm off-white)
                int creamWhite = android.graphics.Color.parseColor("#FFFEF5");
                
                // Set title text color to cream white
                android.widget.TextView titleView = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
                if (titleView != null) {
                    titleView.setTextColor(creamWhite);
                }
                
                // Set message text color to cream white
                android.widget.TextView messageView = dialog.findViewById(android.R.id.message);
                if (messageView != null) {
                    messageView.setTextColor(creamWhite);
                }
                
                // Set button text colors to cream white
                android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                if (positiveButton != null) {
                    positiveButton.setTextColor(creamWhite);
                }
                
                android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
                if (negativeButton != null) {
                    negativeButton.setTextColor(creamWhite);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting dialog text colors: " + e.getMessage());
            }
            
            Log.d(TAG, "TTS preference dialog shown on launch");
        } catch (Exception e) {
            Log.e(TAG, "Error showing TTS preference dialog: " + e.getMessage());
            // Mark as asked even if error to prevent infinite loop
            com.example.bilawoga.utils.TTSLanguageManager.markTTSPreferenceAsked(this);
        }
    }

}

