package com.example.bilawoga;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;

import android.Manifest;
import android.annotation.SuppressLint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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


import com.example.bilawoga.utils.AppAnalytics;
import com.example.bilawoga.utils.EmergencyContactVerifier;
import com.example.bilawoga.utils.OnboardingManager;
import com.example.bilawoga.utils.SecureStorageManager;
import com.example.bilawoga.utils.SilentEmergencyAI;
import com.example.bilawoga.utils.SmartNotificationManager;
import com.example.bilawoga.utils.CountdownDialog;
import com.example.bilawoga.utils.SOSHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.app.Dialog;

import java.util.Map;
// import com.example.bilawoga.utils.PredictiveAI; // REMOVED: AI only for sound detection, not movement
import com.example.bilawoga.utils.SecurityManager;
import com.example.bilawoga.utils.MultiChannelCommunicator;

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
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS
    };
    
    private static final String[] OPTIONAL_PERMISSIONS = {
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
    };
    
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            Manifest.permission.POST_NOTIFICATIONS
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
                        Toast.makeText(MainActivity.this, "All permissions granted! Starting SOS service...", Toast.LENGTH_LONG).show();
                        startServiceAutomatically();
                        
                        // Permissions granted; proceed as normal
                    } else {
                        Log.d(TAG, "Some permissions were denied, showing settings dialog...");
                        Toast.makeText(MainActivity.this, "Some permissions denied. Please grant them in Settings.", Toast.LENGTH_SHORT).show();
                        
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
        // Initialize SMS sent receiver
        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case RESULT_OK:
                        showToast("SMS sent successfully");
                        break;
                    default:
                        showToast("Failed to send SMS");
                        break;
                }
            }
        };
        
        // Initialize SMS delivered receiver
        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case RESULT_OK:
                        showToast("SMS delivered");
                        break;
                    default:
                        showToast("SMS not delivered");
                        break;
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
            Toast.makeText(this, "Test Mode is ON", Toast.LENGTH_SHORT).show();
        }
    }

    // Remove programmatic menu inflation; use Toolbar's app:menu in XML as requested.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true; // Toolbar handles the menu from XML
    }

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
                Toast.makeText(this, "Test Mode " + (!current ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_privacy_policy) {
            // Open a simple policy viewer screen if available; otherwise ignore
            Toast.makeText(this, "Privacy Policy", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_terms_of_use) {
            Toast.makeText(this, "Terms of Use", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_view_log) {
            Toast.makeText(this, "Activity Log", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_panic_wipe) {
            Toast.makeText(this, "Panic wipe requested", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Initialize views and set up click listeners
    public MainActivity() {
        super();
    }

    // Show policy content in a reusable dialog
    private void showLogoutDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Log out")
            .setMessage("Do you want to keep your emergency contacts and settings on this device?")
            .setNegativeButton("Erase now", (d, w) -> {
                // Firebase sign out if configured
                try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut(); } catch (Throwable ignore) {}
                // Wipe local encrypted data and any app prefs
                com.example.bilawoga.utils.SecureStorageManager.secureWipeAllData(this);
                if (sharedPrefs != null) { sharedPrefs.edit().clear().apply(); }
                Toast.makeText(this, "Data erased and logged out", Toast.LENGTH_LONG).show();
                // Go to onboarding
                Intent i = new Intent(this, OnboardingActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            })
            .setPositiveButton("Back up to cloud & log out", (d, w) -> {
                backupDataToFirebaseAndLogout();
            })
            .setNeutralButton("Cancel", null)
            .show();
    }

    private void backupDataToFirebaseAndLogout() {
        // Gather data from encrypted prefs
        SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
        if (prefs == null) {
            Toast.makeText(this, "Secure storage not available", Toast.LENGTH_SHORT).show();
            return;
        }
        String username = prefs.getString("USERNAME", "");
        String num1 = prefs.getString("ENUM_1", "");
        String num2 = prefs.getString("ENUM_2", "");
        String incident = prefs.getString("INCIDENT_TYPE", "");

        // Anonymous sign-in then write to Firestore
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        auth.signInAnonymously()
            .addOnSuccessListener(result -> {
                com.google.firebase.installations.FirebaseInstallations.getInstance().getId()
                    .addOnSuccessListener(fid -> {
                        String docId = (fid != null && !fid.isEmpty()) ? fid : (result.getUser() != null ? result.getUser().getUid() : java.util.UUID.randomUUID().toString());
                        java.util.Map<String, Object> data = com.example.bilawoga.utils.CloudBackupCrypto.buildEncryptedPayload(
                                username, num1, num2, incident, System.currentTimeMillis());

                        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                        db.collection("backups").document(docId)
                            .set(data)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Backup complete. Logging out...", Toast.LENGTH_LONG).show();
                                // Sign out and wipe local
                                try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut(); } catch (Throwable ignore) {}
                                com.example.bilawoga.utils.SecureStorageManager.secureWipeAllData(this);
                                if (sharedPrefs != null) { sharedPrefs.edit().clear().apply(); }
                                // Go to onboarding
                                Intent i = new Intent(this, OnboardingActivity.class);
                                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Backup failed (FID): " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Anonymous sign-in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Unable to show " + title, Toast.LENGTH_SHORT).show();
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
                .setTitle("Logout")
                .setMessage("If you log out, your saved data (including emergency contacts) will be erased from this device. Do you want to continue?")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Logout", (d, w) -> {
                    // Clear encrypted prefs
                    SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
                    if (prefs != null) {
                        prefs.edit().clear().apply();
                    }
                    Toast.makeText(this, "You have been logged out. Local data erased.", Toast.LENGTH_LONG).show();
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

        // Service button now shows Kenya emergency hotlines
        startButton.setOnClickListener(v -> showKenyaEmergencyHotlines());

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

    // stopService removed with button

    // XML onClick handler: toggles emergency numbers visibility
    public void toggleEmergencyNumbers(View v) {
        TextView numbersText = findViewById(R.id.emergencyNumbersText);
        areEmergencyNumbersVisible = !areEmergencyNumbersVisible;

        if (numbersText != null) {
            if (areEmergencyNumbersVisible) {
                // Load full contacts from encrypted storage
                SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
                String num1 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_1, "") : "";
                String num2 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_2, "") : "";

                StringBuilder sb = new StringBuilder();
                if (num1 != null && !num1.isEmpty()) {
                    sb.append("Contact 1: ").append(num1);
                }
                if (num2 != null && !num2.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("Contact 2: ").append(num2);
                }
                if (sb.length() == 0) {
                    sb.append("No emergency contacts saved. Go to Change Number to add them.");
                }
                numbersText.setText(sb.toString());
                numbersText.setVisibility(View.VISIBLE);
            } else {
                numbersText.setVisibility(View.GONE);
            }
        }
    }

    private void startServiceAutomatically() {
        showToast("Service started");
        // Optional: navigate to RegisterNumberActivity if contacts not set
        SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
        String num1 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_1, "") : "";
        String num2 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_2, "") : "";
        if ((num1 == null || num1.isEmpty()) && (num2 == null || num2.isEmpty())) {
            startActivity(new Intent(this, RegisterNumberActivity.class));
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String p : ESSENTIAL_PERMISSIONS) {
                if (checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Handles the send message/SOS button click
     * @param view The view that was clicked
     */
    public void sendMessage(View view) {
        // Load latest saved data
        SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
        String userName = (prefs != null) ? prefs.getString(KEY_USERNAME, "") : "";
        String incidentType = (prefs != null) ? prefs.getString(KEY_INCIDENT_TYPE, "Emergency") : "Emergency";
        String emergencyNumber1 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_1, "") : "";
        String emergencyNumber2 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_2, "") : "";

        // Validate numbers early with same rules as SOSHelper
        if (!isValidNumberForUi(emergencyNumber1) && !isValidNumberForUi(emergencyNumber2)) {
            new AlertDialog.Builder(this)
                    .setTitle("Invalid Emergency Contacts")
                    .setMessage("Please save at least one valid number in +2547XXXXXXXX or 2547XXXXXXXX format.")
                    .setPositiveButton("Update Now", (d, w) -> startActivity(new Intent(this, RegisterNumberActivity.class)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Check SEND_SMS permission before showing countdown (for direct send fallback)
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.SEND_SMS}, REQ_SEND_SMS);
            // Inform user we need permission to send after countdown
            Toast.makeText(this, "Grant SMS permission to send SOS", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show 7-second countdown; user can cancel or send immediately
        countdownDialog = new CountdownDialog(this, userName, incidentType, emergencyNumber1, emergencyNumber2,
                new CountdownDialog.CountdownListener() {
                    @Override
                    public void onCountdownFinished(String u, String inc, String n1, String n2) {
                        authenticateAndSendSOS(u, inc, n1, n2);
                    }
                    @Override
                    public void onCountdownCancelled() {
                        Toast.makeText(MainActivity.this, "SOS cancelled", Toast.LENGTH_SHORT).show();
                    }
                });
        countdownDialog.show();
    }

    // UI-side validation mirroring SOSHelper rules (without logs)
    private boolean isValidNumberForUi(String number) {
        if (number == null || number.trim().isEmpty() || number.equalsIgnoreCase("NONE")) return false;
        String clean = number.replaceAll("[^0-9+]", "");
        if (clean.length() < 8) return false;
        return clean.startsWith("+") || clean.startsWith("254");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SEND_SMS) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission granted. Tap Send Alert again.", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("SMS permission is needed to send emergency messages. You can grant it in Settings > Apps > BilaWoga > Permissions.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    /**
     * Sends SOS with the latest user data from SharedPreferences
     */
    private void sendSOS() {
        SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
        String userName = (prefs != null) ? prefs.getString(KEY_USERNAME, "") : "";
        String incidentType = (prefs != null) ? prefs.getString(KEY_INCIDENT_TYPE, "Emergency") : "Emergency";
        String emergencyNumber1 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_1, "") : "";
        String emergencyNumber2 = (prefs != null) ? prefs.getString(KEY_EMERGENCY_NUMBER_2, "") : "";

        authenticateAndSendSOS(userName, incidentType, emergencyNumber1, emergencyNumber2);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if user needs onboarding
        onboardingManager = new OnboardingManager(this);
        if (onboardingManager.isNewUser()) {
            // Start onboarding flow
            Intent onboardingIntent = new Intent(this, OnboardingActivity.class);
            startActivity(onboardingIntent);
            finish();
            return;
        }

        // Check and request permissions when activity is created
        checkAndRequestPermissions();

        // Check for root/jailbreak
        if (SecureStorageManager.isDeviceTampered()) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("Security Warning")
                    .setMessage("This device appears to be rooted, running in an emulator, or otherwise tampered. For your safety, BilaWoga cannot run on this device.")
                    .setPositiveButton("Exit", (dialog, which) -> {
                        finishAffinity();
                        System.exit(0);
                    })
                    .setCancelable(false)
                    .show();
            return;
        }
        // Check app integrity
        Context context = getApplicationContext();
        if (!SecureStorageManager.checkAppIntegrity(context)) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("App Integrity Error")
                    .setMessage("This app's integrity check failed. Please reinstall BilaWoga from the official source.")
                    .setPositiveButton("Exit", (dialog, which) -> {
                        finishAffinity();
                        System.exit(0);
                    })
                    .setCancelable(false)
                    .show();
            return;
        }

        // Setup toolbar with overflow menu
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle("BilaWoga Emergency");
            // Use Toolbar's app:menu from XML and handle clicks here
            toolbar.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_change_number) {
                    startActivity(new Intent(this, RegisterNumberActivity.class));
                    return true;
                } else if (id == R.id.action_logout) {
                    showLogoutConfirmDialog();
                    return true;
                } else if (id == R.id.action_privacy_policy) {
                    showPolicyDialog("Privacy Policy", getStringFromAsset("PRIVACY_POLICY.html"));
                    return true;
                } else if (id == R.id.action_terms_of_use) {
                    showPolicyDialog("Terms of Use", getStringFromAsset("TERMS_OF_USE.html"));
                    return true;
                } else if (id == R.id.action_view_log) {
                    Toast.makeText(this, "Activity Log", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.action_panic_wipe) {
                    Toast.makeText(this, "Panic wipe requested", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        loadSavedData();
        initializeSensors();
        // initializeAIShakeDetection(); // DISABLED - class not available

        // Initialize Silent Emergency AI (background monitoring)
        initializeSilentEmergencyAI();

        setupAccessibilityFab();
        setupTextToSpeech();

        // Add programmatic click listeners to ensure SOS buttons work
        setupSOSButtons();

        // Check emergency contact status
        checkEmergencyContacts();

        // Register SMS broadcast receivers
        registerSmsReceivers();

        // Initialize Advanced Features
        if (isAdvancedFeaturesEnabled) {
            initializeAdvancedFeatures();
        }

        // Initialize analytics and monitoring systems
        initializeAnalyticsAndMonitoring();

        // Block app if encrypted storage is unavailable
        context = getApplicationContext();
        SharedPreferences testPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
        if (testPrefs == null) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("Critical Security Error")
                    .setMessage("Encrypted storage is unavailable on this device. For your safety, BilaWoga cannot run without secure storage. Please check your device security settings or use a supported device.")
                    .setPositiveButton("Exit", (dialog, which) -> {
                        finishAffinity();
                        System.exit(0);
                    })
                    .setCancelable(false)
                    .show();
            return;
        }
        // Prompt user to review/update emergency info after 30 days or more
        long now = System.currentTimeMillis();
        long lastUpdate = testPrefs.getLong("LAST_UPDATE_TIME", 0);
        long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

        // Only show the popup if it's been at least 30 days since last update
        if (lastUpdate > 0 && (now - lastUpdate) >= THIRTY_DAYS_MS) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Update Emergency Info")
                    .setMessage("It's been a while since you updated your emergency contacts and info. Please review and update to keep your safety info current.")
                    .setPositiveButton("Update Now", (dialog, which) -> {
                        // Update the timestamp when user clicks Update Now
                        SharedPreferences.Editor editor = testPrefs.edit();
                        editor.putLong("LAST_UPDATE_TIME", now);
                        editor.apply();
                        startActivity(new Intent(this, RegisterNumberActivity.class));
                    })
                    .setNegativeButton("Remind Me Later", (dialog, which) -> {
                        // Update the timestamp but only by 29 days to ensure it shows again in 1 day
                        SharedPreferences.Editor editor = testPrefs.edit();
                        editor.putLong("LAST_UPDATE_TIME", now - (29L * 24 * 60 * 60 * 1000));
                        editor.apply();
                    })
                    .setCancelable(true)
                    .show();
        }

        // In onCreate, check for crash recovery
        context = getApplicationContext();
        SharedPreferences crashPrefs = SecureStorageManager.getEncryptedSharedPreferences(context);
        if (crashPrefs != null && crashPrefs.getBoolean("PENDING_SOS", false)) {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle("Resume Emergency Alert?")
                    .setMessage("It looks like an emergency alert was interrupted. Would you like to resume sending the SOS?")
                    .setPositiveButton("Resume", (dialog, which) -> {
                        // Retrieve last known user info and send SOS again
                        String userName = crashPrefs.getString(KEY_USERNAME, "Unknown User");
                        String incidentType = crashPrefs.getString(KEY_INCIDENT_TYPE, "Manual SOS");
                        String emergencyNumber1 = crashPrefs.getString(KEY_EMERGENCY_NUMBER_1, "NONE");
                        String emergencyNumber2 = crashPrefs.getString(KEY_EMERGENCY_NUMBER_2, "NONE");
                        authenticateAndSendSOS(userName, incidentType, emergencyNumber1, emergencyNumber2);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        crashPrefs.edit().putBoolean("PENDING_SOS", false).apply();
                    })
                    .setCancelable(false)
                    .show();
        }

        // Register SMS sent/delivered receivers with the correct action strings
        registerSmsReceivers() ;

        // Check and display setup mode status after UI is initialized
        checkAndDisplaySetupMode();

        // Initialize views
        MainActivity mainActivity = new MainActivity();
    }

    private void setupTextToSpeech() {
    }

    private void checkAndDisplaySetupMode() {

    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSmsReceivers() {
        initializeSmsReceivers();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use NOT_EXPORTED for app-internal broadcasts
            registerReceiver(smsSentReceiver, new IntentFilter(SMS_SENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(smsDeliveredReceiver, new IntentFilter(SMS_DELIVERED_ACTION), Context.RECEIVER_NOT_EXPORTED);
        } else {
            // Older APIs use the 2-arg overload
            registerReceiver(smsSentReceiver, new IntentFilter(SMS_SENT_ACTION));
            registerReceiver(smsDeliveredReceiver, new IntentFilter(SMS_DELIVERED_ACTION));
        }
    }

    private void initializeSilentEmergencyAI() {
    }

    private void checkEmergencyContacts() {
    }

    private void initializeAdvancedFeatures() {
    }

    private void initializeAnalyticsAndMonitoring() {
    }

    // Simple dialog with Kenya hotlines
    private void showKenyaEmergencyHotlines() {
        String[] items = new String[]{
                "999 / 112 — National Emergency",
                "1195 — GBV Toll-Free",
                "116 — Child Helpline",
                "1199 — Kenya Red Cross"
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Kenya Emergency Hotlines")
                .setItems(items, (d, which) -> {})
                .setPositiveButton("Close", null)
                .show();
    }

    private void setupAccessibilityFab() {
        FloatingActionButton fab = findViewById(R.id.accessibilityFab);
        if (fab != null) {
            fab.setOnClickListener(v -> Toast.makeText(this, "Accessibility options coming soon", Toast.LENGTH_SHORT).show());
        }
    }

    private void setupSOSButtons() {
        FloatingActionButton quickFab = findViewById(R.id.quickSOSFab);
        if (quickFab != null) quickFab.setOnClickListener(v -> sendSOS());
    }

    private void loadSavedData() {
    }

    private void authenticateAndSendSOS(String userName, String incidentType, String emergencyNumber1, String emergencyNumber2) {
        // Minimal: directly call SOS helper to send
        SOSHelper helper = new SOSHelper(this);
        helper.sendEmergencySOS(userName, incidentType, emergencyNumber1, emergencyNumber2);
    }

    private void createNotificationChannel() {
    }

    @Override
    protected void onDestroy() throws IllegalArgumentException {
        super.onDestroy();
        // Unregister sensor listener
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // Unregister broadcast receivers safely
        try {
            if (smsSentReceiver != null) {
                unregisterReceiver(smsSentReceiver);
                smsSentReceiver = null;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "smsSentReceiver not registered: " + e.getMessage());
        }
        try {
            if (smsDeliveredReceiver != null) {
                unregisterReceiver(smsDeliveredReceiver);
                smsDeliveredReceiver = null;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "smsDeliveredReceiver not registered: " + e.getMessage());
        }

        // NOTE: Showing dialogs or accessing UI from onDestroy is unsafe; removed invalid blocks
        // If you need a success popup, trigger it where SOS is confirmed, not here.

        // Also removed stray code blocks that caused syntax errors
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

// ... (rest of the code remains the same)

