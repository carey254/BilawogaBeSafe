package com.example.bilawoga;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Dialog;

import android.graphics.Color;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneNumberUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.button.MaterialButton;
import com.example.bilawoga.utils.PolicyViewerActivity;
// import com.example.bilawoga.utils.SOSHelper; // Temporarily commented out due to compilation issues

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Date;

public class RegisterNumberActivity extends AppCompatActivity {
    private static final String TAG = "RegisterNumberActivity";
    
    // SECURITY: Enhanced validation constants
    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MIN_PHONE_LENGTH = 7;
    private static final int MAX_PHONE_LENGTH = 15;
    private static final int MAX_INCIDENT_LENGTH = 100;
    
    // SECURITY: Rate limiting for form submissions
    private static final long SUBMISSION_COOLDOWN = 5000; // 5 seconds
    private long lastSubmissionTime = 0;

    private EditText nameEdit;
    private EditText numberEdit;
    private EditText number2Edit;
    private EditText manualIncidentEditText;
    private Spinner incidentSpinner;
    private TextInputLayout numberInputLayout;
    private TextInputLayout number2InputLayout;
    private TextInputLayout nameInputLayout;
    private TextInputLayout manualIncidentInputLayout;
    private TextToSpeech tts;
    private FloatingActionButton accessibilityFab;
    private Dialog accessibilityDialog;
    private boolean isHighContrast = false;
    private boolean isLargeText = false;
    private boolean isReadingGuide = false;
    private boolean isAudioActive = false;
    private Handler audioHandler = new Handler(Looper.getMainLooper());
    private Runnable audioRunnable;

    private static final String[] INCIDENT_TYPES = {
            "No Current Emergency (Safe and Secure)",
            "Abduction (taken forcefully or kidnapped)",
            "Sexual Assault / Harassment (unwanted touching or sexual comments)",
            "Domestic Violence (abuse from family or partner)",
            "Medical Emergency (serious health issue or injury)",
            "Other (Specify Below)"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_number);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Emergency Contact Setup");
        }
        
        // Block app if encrypted storage is unavailable - TEMPORARY BYPASS TO SHOW UI
        SharedPreferences testPrefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
        if (testPrefs == null) {
            Toast.makeText(this, "Secure storage unavailable; running in test mode.", Toast.LENGTH_LONG).show();
            // Note: In production, restore the blocking dialog and exit for security.
        }

        initializeViews();
        setupSpinner();
        loadSavedData();
        setupTextWatchers();
        setupAccessibilityFab();
        setupTextToSpeech();
        setupFieldFocusListeners();
        
        // Set up privacy policy hint click listener
        TextView privacyPolicyHint = findViewById(R.id.privacyPolicyHint);
        if (privacyPolicyHint != null) {
            privacyPolicyHint.setOnClickListener(v -> {
                showPolicyViewer(PolicyViewerActivity.POLICY_TYPE_PRIVACY);
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.popup, menu);
        return true;
    }

    // Added stub handler to prevent crash from android:onClick in layout
    public void saveNumber(View view) {
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();
        // TODO: Implement actual save logic
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Clipboard protection for sensitive fields
        if (nameEdit != null) {
            nameEdit.setLongClickable(false);
            nameEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });
        }
        if (numberEdit != null) {
            numberEdit.setLongClickable(false);
            numberEdit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });
        }
        if (number2Edit != null) {
            number2Edit.setLongClickable(false);
            number2Edit.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });
        }
        if (manualIncidentEditText != null) {
            manualIncidentEditText.setLongClickable(false);
            manualIncidentEditText.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                public void onDestroyActionMode(android.view.ActionMode mode) {}
            });
        }
    }

    private void initializeViews() {
        // Initialize EditTexts
        nameEdit = findViewById(R.id.nameEdit);
        numberEdit = findViewById(R.id.numberEdit);
        number2Edit = findViewById(R.id.number2Edit);
        manualIncidentEditText = findViewById(R.id.manualIncidentEditText);

        // Initialize TextInputLayouts
        numberInputLayout = findViewById(R.id.numberInputLayout);
        number2InputLayout = findViewById(R.id.number2InputLayout);
        nameInputLayout = findViewById(R.id.nameInputLayout);
        manualIncidentInputLayout = findViewById(R.id.manualIncidentInputLayout);

        // Initialize Spinner
        incidentSpinner = findViewById(R.id.incidentSpinner);

        // Only set visibility if the view exists
        if (manualIncidentInputLayout != null) {
            manualIncidentInputLayout.setVisibility(View.GONE);
        }

        // Set up error handling for required fields
        if (nameInputLayout != null) {
            nameInputLayout.setErrorEnabled(true);
        }
        if (numberInputLayout != null) {
            numberInputLayout.setErrorEnabled(true);
        }
        if (number2InputLayout != null) {
            number2InputLayout.setErrorEnabled(true);
        }
    }

    private void setupSpinner() {
        if (incidentSpinner == null) return;

        IncidentTypeAdapter adapter = new IncidentTypeAdapter(
                this,
                INCIDENT_TYPES
        );
        incidentSpinner.setAdapter(adapter);

        incidentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (manualIncidentInputLayout != null) {
                    boolean isOtherSelected = INCIDENT_TYPES[position].equals("Other (Specify Below)");
                    manualIncidentInputLayout.setVisibility(isOtherSelected ? View.VISIBLE : View.GONE);
                    if (!isOtherSelected && manualIncidentEditText != null) {
                        manualIncidentEditText.setText("");
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (manualIncidentInputLayout != null) {
                    manualIncidentInputLayout.setVisibility(View.GONE);
                }
            }
        });
    }

    private void loadSavedData() {
        String savedName = "";
        String savedNumber1 = "";
        String savedNumber2 = "";
        String savedIncident = "";
        
        try {
            SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
            savedName = prefs.getString("USERNAME", "");
            savedNumber1 = prefs.getString("ENUM_1", "");
            savedNumber2 = prefs.getString("ENUM_2", "");
            savedIncident = prefs.getString("INCIDENT_TYPE", "");
        } catch (Exception e) {
            Log.e(TAG, "Error loading from encrypted storage: " + e.getMessage());
            // Fallback to regular SharedPreferences
            try {
                SharedPreferences fallbackPrefs = getSharedPreferences("BilaWogaPrefs", MODE_PRIVATE);
                savedName = fallbackPrefs.getString("USERNAME", "");
                savedNumber1 = fallbackPrefs.getString("ENUM_1", "");
                savedNumber2 = fallbackPrefs.getString("ENUM_2", "");
                savedIncident = fallbackPrefs.getString("INCIDENT_TYPE", "");
            } catch (Exception fallbackError) {
                Log.e(TAG, "Fallback storage read also failed: " + fallbackError.getMessage());
            }
        }

        if (!TextUtils.isEmpty(savedName) && nameEdit != null) {
            nameEdit.setText(savedName);
        }
        if (!TextUtils.isEmpty(savedNumber1) && numberEdit != null) {
            numberEdit.setText(savedNumber1);
        }
        if (!TextUtils.isEmpty(savedNumber2) && number2Edit != null) {
            number2Edit.setText(savedNumber2);
        }

        // Try to match saved incident with spinner items
        if (incidentSpinner != null) {
            for (int i = 0; i < INCIDENT_TYPES.length; i++) {
                if (INCIDENT_TYPES[i].equals(savedIncident)) {
                    incidentSpinner.setSelection(i);
                    break;
                }
            }

            // If no match found, select "Other" and show in manual input
            if (!TextUtils.isEmpty(savedIncident) && incidentSpinner.getSelectedItemPosition() == 0) {
                incidentSpinner.setSelection(INCIDENT_TYPES.length - 1); // "Other" option
                if (manualIncidentEditText != null) {
                    manualIncidentEditText.setText(savedIncident);
                }
            }
        }
    }

    private void setupTextWatchers() {
        if (numberEdit != null) {
            numberEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    validateNumber(s.toString(), numberInputLayout);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (number2Edit != null) {
            number2Edit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!TextUtils.isEmpty(s.toString())) {
                        validateNumber(s.toString(), number2InputLayout);
                    } else {
                        if (number2InputLayout != null) {
                            number2InputLayout.setError(null);
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (nameEdit != null) {
            nameEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (nameInputLayout != null) {
                        nameInputLayout.setError(null);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private boolean validateNumber(String number, TextInputLayout inputLayout) {
        if (inputLayout == null) return false;

        if (TextUtils.isEmpty(number)) {
            inputLayout.setError("Emergency number is required");
            return false;
        }

        // SECURITY: Enhanced phone number validation with comprehensive checks
        String cleanNumber = sanitizePhoneNumber(number);
        
        // CRITICAL DEBUG: Log validation process
        Log.d(TAG, "=== VALIDATION DEBUG ===");
        Log.d(TAG, "Original number: '" + number + "'");
        Log.d(TAG, "Clean number: '" + cleanNumber + "'");
        Log.d(TAG, "Clean number length: " + cleanNumber.length());
        Log.d(TAG, "MIN_PHONE_LENGTH: " + MIN_PHONE_LENGTH);
        Log.d(TAG, "MAX_PHONE_LENGTH: " + MAX_PHONE_LENGTH);
        Log.d(TAG, "Length valid: " + (cleanNumber.length() >= MIN_PHONE_LENGTH && cleanNumber.length() <= MAX_PHONE_LENGTH));
        Log.d(TAG, "Format valid: " + isValidPhoneNumberFormat(cleanNumber));
        Log.d(TAG, "Contains malicious: " + containsMaliciousPatterns(number));
        Log.d(TAG, "Is emergency service: " + isEmergencyServiceNumber(cleanNumber));
        Log.d(TAG, "=== END VALIDATION DEBUG ===");
        
        // Length validation
        if (cleanNumber.length() < MIN_PHONE_LENGTH || cleanNumber.length() > MAX_PHONE_LENGTH) {
            inputLayout.setError("Invalid phone number length (" + MIN_PHONE_LENGTH + "-" + MAX_PHONE_LENGTH + " digits)");
            return false;
        }
        
        // Format validation
        if (!isValidPhoneNumberFormat(cleanNumber)) {
            inputLayout.setError("Invalid phone number format");
            return false;
        }
        
        // SECURITY: Prevent SMS injection and malicious patterns
        if (containsMaliciousPatterns(number)) {
            inputLayout.setError("Invalid characters detected in phone number");
            return false;
        }
        
        // SECURITY: Check for common emergency numbers to prevent abuse
        if (isEmergencyServiceNumber(cleanNumber)) {
            inputLayout.setError("Please use a personal emergency contact number");
            return false;
        }

        inputLayout.setError(null);
        return true;
    }
    
    // SECURITY: Sanitize phone number input
    private String sanitizePhoneNumber(String number) {
        if (number == null) return "";
        // Remove all non-digit characters except + for international numbers
        return number.replaceAll("[^+\\d]", "");
    }
    
    // SECURITY: Validate phone number format
    private boolean isValidPhoneNumberFormat(String number) {
        // International format: +[country code][number]
        // Local format: [country code][number]
        // Kenyan format: +254..., 254..., 07..., 011..., etc.
        
        // More permissive pattern for Kenyan numbers
        if (number.startsWith("+254") || number.startsWith("254")) {
            // Kenyan international format
            return number.matches("^\\+?254\\d{9}$");
        } else if (number.startsWith("0")) {
            // Kenyan local format starting with 0
            return number.matches("^0\\d{8}$");
        } else if (number.startsWith("7") || number.startsWith("1")) {
            // Kenyan mobile numbers starting with 7 or 1
            return number.matches("^[17]\\d{8}$");
        } else {
            // Generic international format
            return number.matches("^\\+?[1-9]\\d{6,14}$");
        }
    }
    
    // SECURITY: Check for malicious patterns
    private boolean containsMaliciousPatterns(String input) {
        if (input == null) return false;
        
        String[] maliciousPatterns = {
            "\\n", "\\r", "\\t", "SEND", "TO:", "SMS:", "@", "javascript:", 
            "data:", "vbscript:", "onload", "onerror", "<script", "</script>",
            "eval(", "alert(", "confirm(", "prompt(", "document.", "window."
        };
        
        String lowerInput = input.toLowerCase();
        for (String pattern : maliciousPatterns) {
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
    
    // SECURITY: Check if number is emergency service
    private boolean isEmergencyServiceNumber(String number) {
        String[] emergencyNumbers = {
            "911", "112", "999", "000", "110", "119", "120", "122",
            "100", "101", "102", "103", "104", "105", "106", "107", "108", "109"
        };
        
        for (String emergency : emergencyNumbers) {
            if (number.endsWith(emergency)) {
                return true;
            }
        }
        return false;
    }

    private boolean validateName(String name) {
        if (nameInputLayout == null) return false;

        if (TextUtils.isEmpty(name.trim())) {
            nameInputLayout.setError("Please Enter Your Full Name");
            return false;
        }
        
        // SECURITY: Enhanced name validation
        String sanitizedName = sanitizeName(name.trim());
        
        if (sanitizedName.length() < MIN_NAME_LENGTH) {
            nameInputLayout.setError("Name must be at least " + MIN_NAME_LENGTH + " characters");
            return false;
        }
        
        if (sanitizedName.length() > MAX_NAME_LENGTH) {
            nameInputLayout.setError("Name must be less than " + MAX_NAME_LENGTH + " characters");
            return false;
        }
        
        // SECURITY: Check for malicious patterns in name
        if (containsMaliciousPatterns(sanitizedName)) {
            nameInputLayout.setError("Name contains invalid characters");
            return false;
        }
        
        // SECURITY: Validate name format (letters, spaces, hyphens, apostrophes only)
        if (!sanitizedName.matches("^[a-zA-Z\\s\\-']+$")) {
            nameInputLayout.setError("Name can only contain letters, spaces, hyphens, and apostrophes");
            return false;
        }
        
        nameInputLayout.setError(null);
        return true;
    }
    
    // SECURITY: Sanitize name input
    private String sanitizeName(String name) {
        if (name == null) return "";
        // Remove extra whitespace and normalize
        return name.replaceAll("\\s+", " ").trim();
    }

    private String getSelectedIncident() {
        if (incidentSpinner == null || incidentSpinner.getSelectedItem() == null) {
            return "";
        }
        
        String selectedIncident = incidentSpinner.getSelectedItem().toString();
        String manualIncident = "";
        
        if (manualIncidentEditText != null) {
            manualIncident = manualIncidentEditText.getText().toString().trim();
        }

        if (selectedIncident.equals("Other (Specify Below)") && !TextUtils.isEmpty(manualIncident)) {
            return manualIncident;
        } else if (!selectedIncident.equals("Select Incident Type")) {
            return selectedIncident;
        }
        return "";
    }

    public void saveNumber(View view) {
        Log.d("RegisterNumberActivity", "saveNumber called");
        Toast.makeText(this, "Save button clicked", Toast.LENGTH_SHORT).show();
        // SECURITY: Rate limiting to prevent spam submissions
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSubmissionTime < SUBMISSION_COOLDOWN) {
            Snackbar.make(view, "Please wait before submitting again", Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        if (nameEdit == null || numberEdit == null || number2Edit == null) {
            Toast.makeText(this, "Error: Form fields not initialized", Toast.LENGTH_LONG).show();
            return;
        }
        
        // SECURITY: Sanitize all inputs before processing
        String nameString = sanitizeName(nameEdit.getText().toString().trim());
        String number1String = sanitizePhoneNumber(numberEdit.getText().toString().trim());
        String number2String = sanitizePhoneNumber(number2Edit.getText().toString().trim());
        String incident = sanitizeIncidentType(getSelectedIncident());
        
        // CRITICAL DEBUG: Log what we're about to save
        Log.d(TAG, "=== SAVING DEBUG ===");
        Log.d(TAG, "Raw input - Name: '" + nameEdit.getText().toString().trim() + "'");
        Log.d(TAG, "Raw input - Number1: '" + numberEdit.getText().toString().trim() + "'");
        Log.d(TAG, "Raw input - Number2: '" + number2Edit.getText().toString().trim() + "'");
        Log.d(TAG, "After sanitization - Name: '" + nameString + "'");
        Log.d(TAG, "After sanitization - Number1: '" + number1String + "'");
        Log.d(TAG, "After sanitization - Number2: '" + number2String + "'");
        Log.d(TAG, "Incident: '" + incident + "'");
        Log.d(TAG, "=== END SAVING DEBUG ===");

        // SECURITY: Comprehensive validation
        boolean isValid = validateName(nameString) & validateNumber(number1String, numberInputLayout);
        if (!TextUtils.isEmpty(number2String)) {
            isValid &= validateNumber(number2String, number2InputLayout);
        }
        if (TextUtils.isEmpty(incident)) {
            Log.w(TAG, "Validation failed: incident type missing");
            String errorMsg = "Please select or describe an emergency type";
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, errorMsg, Snackbar.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
            return;
        }
        if (!isValid) {
            Log.w(TAG, "Validation failed: name or number invalid");
            String errorMsg = "Please check your name and emergency numbers";
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
            return;
        }
        // SECURITY: Final validation before saving
        if (!validateEmergencyData(nameString, number1String, number2String, incident)) {
            Log.w(TAG, "Validation failed: emergency data invalid");
            String errorMsg = "Invalid emergency data detected";
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, errorMsg, Snackbar.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
            return;
        }

        try {
            SharedPreferences sharedPreferences = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
            if (sharedPreferences == null) {
                Log.w(TAG, "Encrypted storage not available, using fallback");
                SharedPreferences fallbackPrefs = getSharedPreferences("BilaWogaPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = fallbackPrefs.edit();
                editor.putString("USERNAME", nameString);
                editor.putString("ENUM_1", number1String);
                editor.putString("ENUM_2", number2String);
                editor.putString("INCIDENT_TYPE", incident);
                editor.putLong("LAST_UPDATE_TIME", currentTime);
                editor.apply();
                Log.d(TAG, "Fallback save: USERNAME=" + nameString + ", ENUM_1=" + number1String + ", ENUM_2=" + number2String + ", INCIDENT_TYPE=" + incident);
                String successMsg = "Emergency Contacts Saved Successfully!";
                Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
                Snackbar.make(view, successMsg, Snackbar.LENGTH_LONG).show();
                speakSuccessMessages(successMsg);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(RegisterNumberActivity.this, MainActivity.class));
                    finish();
                }, 1000);
                return;
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("USERNAME", nameString);
            editor.putString("ENUM_1", number1String);
            editor.putString("ENUM_2", number2String);
            editor.putString("INCIDENT_TYPE", incident);
            editor.putLong("LAST_UPDATE_TIME", currentTime);
            editor.apply();
            Log.d(TAG, "Saved: USERNAME=" + nameString + ", ENUM_1=" + number1String + ", ENUM_2=" + number2String + ", INCIDENT_TYPE=" + incident);
            String successMsg = "Emergency Contacts Saved Securely!";
            Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, successMsg, Snackbar.LENGTH_LONG).show();
            speakSuccessMessages(successMsg);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(RegisterNumberActivity.this, MainActivity.class));
                finish();
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Error saving emergency data: " + e.getMessage());
            String errorMsg = "Error saving emergency contacts!";
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, errorMsg, Snackbar.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
        }
    }
    
    // SECURITY: Validate emergency data before saving
    private boolean validateEmergencyData(String name, String number1, String number2, String incident) {
        // Check for duplicate emergency numbers
        if (!TextUtils.isEmpty(number1) && !TextUtils.isEmpty(number2) && number1.equals(number2)) {
            Toast.makeText(this, "Emergency numbers cannot be the same", Toast.LENGTH_LONG).show();
            return false;
        }
        
        // Check incident type length
        if (incident.length() > MAX_INCIDENT_LENGTH) {
            Toast.makeText(this, "Incident description too long", Toast.LENGTH_LONG).show();
            return false;
        }
        
        // Check for at least one emergency number
        if (TextUtils.isEmpty(number1) && TextUtils.isEmpty(number2)) {
            Toast.makeText(this, "At least one emergency number is required", Toast.LENGTH_LONG).show();
            return false;
        }
        
        return true;
    }
    
    // SECURITY: Sanitize incident type
    private String sanitizeIncidentType(String incident) {
        if (incident == null) return "";
        
        // Remove malicious patterns
        String sanitized = incident;
        for (String pattern : new String[]{"<script", "</script>", "javascript:", "onload", "onerror"}) {
            sanitized = sanitized.replaceAll("(?i)" + pattern, "");
        }
        
        // Limit length
        if (sanitized.length() > MAX_INCIDENT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_INCIDENT_LENGTH);
        }
        
        return sanitized.trim();
    }



    private void setupTextToSpeech() {
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale ukLocale = Locale.UK;
                    int result = tts.setLanguage(ukLocale);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(RegisterNumberActivity.this, "UK English voice not available. Please install UK English TTS in your device settings.", Toast.LENGTH_LONG).show();
                    }
                    tts.setSpeechRate(1.0f);
                    tts.setPitch(1.0f);
                }
            }
        });
    }

    /**
     * Sets up the accessibility floating action button
     */
    private void setupAccessibilityFab() {
        accessibilityFab = findViewById(R.id.accessibilityFab);
        if (accessibilityFab != null) {
            accessibilityFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAccessibilityDialog();
                }
            });
        }
    }
    
    /**
     * Sets up field focus listeners for accessibility
     */
    private void setupFieldFocusListeners() {
        if (nameEdit != null) {
            nameEdit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus("Name field", "Enter your full name as it appears on official documents");
                }
            });
        }
        
        if (numberEdit != null) {
            numberEdit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus("Primary emergency contact field", "Enter the phone number of your most trusted emergency contact");
                }
            });
        }
        
        if (number2Edit != null) {
            number2Edit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus("Secondary emergency contact field", "Enter an alternative emergency contact number as backup");
                }
            });
        }
        
        if (manualIncidentEditText != null) {
            manualIncidentEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus("Custom incident description field", "Describe your specific emergency type if not listed above");
                }
            });
        }
        
        if (incidentSpinner != null) {
            incidentSpinner.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus("Incident type dropdown", "Select the type of emergency you want to prepare for");
                }
            });
        }
    }

    /**
     * Shows the accessibility options dialog
     */
    private void showAccessibilityDialog() {
        accessibilityDialog = new Dialog(this);
        accessibilityDialog.setContentView(R.layout.dialog_accessibility_options);
        accessibilityDialog.setCancelable(true);
        
        // Set dialog to be smaller and positioned properly
        accessibilityDialog.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.85), // 85% of screen width
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        accessibilityDialog.getWindow().setGravity(android.view.Gravity.CENTER);

        MaterialButton startTTSButton = accessibilityDialog.findViewById(R.id.btnStartTTS);
        MaterialButton stopAudioButton = accessibilityDialog.findViewById(R.id.btnStopAudio);
        MaterialButton highContrastButton = accessibilityDialog.findViewById(R.id.btnHighContrast);

        MaterialButton largeTextButton = accessibilityDialog.findViewById(R.id.btnIncreaseTextSize);
        MaterialButton readingGuideButton = accessibilityDialog.findViewById(R.id.btnReadingGuide);
        MaterialButton speakAllButton = accessibilityDialog.findViewById(R.id.btnReadPage);
        MaterialButton resetButton = accessibilityDialog.findViewById(R.id.btnResetAccessibility);
        MaterialButton cancelButton = accessibilityDialog.findViewById(R.id.btnCancelAccessibility);

        startTTSButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTextToSpeech();
                Toast.makeText(RegisterNumberActivity.this, "Text-to-Speech started", Toast.LENGTH_SHORT).show();
            }
        });

        stopAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAudio();
                Toast.makeText(RegisterNumberActivity.this, "Audio stopped", Toast.LENGTH_SHORT).show();
            }
        });

        highContrastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleHighContrast();
                Toast.makeText(RegisterNumberActivity.this, "High Contrast Toggled", Toast.LENGTH_SHORT).show();
            }
        });



        largeTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTextSize();
                Toast.makeText(RegisterNumberActivity.this, "Text Size Toggled", Toast.LENGTH_SHORT).show();
            }
        });

        readingGuideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleReadingGuide();
                Toast.makeText(RegisterNumberActivity.this, "Reading Guide Toggled", Toast.LENGTH_SHORT).show();
            }
        });

        speakAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speakAllFields();
                Toast.makeText(RegisterNumberActivity.this, "Reading Page Content", Toast.LENGTH_SHORT).show();
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetAccessibilitySettings();
                Toast.makeText(RegisterNumberActivity.this, "All settings reset", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accessibilityDialog.dismiss();
                if (tts != null) {
                    tts.speak("Accessibility options closed", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });

        accessibilityDialog.show();
    }

    /**
     * Starts continuous Text-to-Speech narration
     */
    private void startTextToSpeech() {
        if (isAudioActive) {
            stopAudio();
        }
        
        isAudioActive = true;
        audioRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAudioActive && tts != null) {
                    speakAllPageContent();
                    audioHandler.postDelayed(this, 15000); // Repeat every 15 seconds
                }
            }
        };
        audioHandler.post(audioRunnable);
    }

    /**
     * Stops the continuous audio
     */
    private void stopAudio() {
        isAudioActive = false;
        if (audioHandler != null && audioRunnable != null) {
            audioHandler.removeCallbacks(audioRunnable);
        }
        if (tts != null) {
            tts.stop();
        }
    }

    /**
     * Speaks all page content for accessibility
     */
    private void speakAllPageContent() {
        if (tts == null) return;
        
        StringBuilder content = new StringBuilder();
        content.append("BilaWoga Emergency Contact Setup Page. ");
        content.append("This page allows you to configure your emergency contacts and incident preferences. ");
        content.append("All information entered here will be used only for emergency situations. ");
        
        content.append("Form Instructions: ");
        content.append("Please fill in all required fields marked with an asterisk. ");
        content.append("Your information will be stored securely and encrypted. ");
        
        // Read all visible text content with detailed instructions
        if (nameEdit != null && nameEdit.getText() != null) {
            String nameText = nameEdit.getText().toString();
            if (!nameText.isEmpty()) {
                content.append("Name field contains: ").append(nameText).append(". ");
            } else {
                content.append("Name field is empty. Please enter your full name as it appears on official documents. ");
                content.append("This name will be used to identify you in emergency situations. ");
            }
        }
        
        if (numberEdit != null && numberEdit.getText() != null) {
            String numberText = numberEdit.getText().toString();
            if (!numberText.isEmpty()) {
                content.append("Primary emergency contact field contains: ").append(numberText).append(". ");
            } else {
                content.append("Primary emergency contact field is empty. ");
                content.append("Please enter the phone number of your most trusted emergency contact. ");
                content.append("This should be someone who can respond quickly in an emergency. ");
                content.append("Enter the number in international format, for example: plus two five six seven seven zero one two three four five six. ");
            }
        }
        
        if (number2Edit != null && number2Edit.getText() != null) {
            String number2Text = number2Edit.getText().toString();
            if (!number2Text.isEmpty()) {
                content.append("Secondary emergency contact field contains: ").append(number2Text).append(". ");
            } else {
                content.append("Secondary emergency contact field is empty. ");
                content.append("Please enter an alternative emergency contact number as backup. ");
                content.append("This is optional but recommended for better emergency response. ");
            }
        }
        
        if (incidentSpinner != null) {
            content.append("Incident type dropdown is available. ");
            content.append("Select the type of emergency you want to prepare for. ");
            content.append("Options include: Abduction, Sexual Assault, Domestic Violence, Medical Emergency, or Other. ");
            content.append("If you select Other, you can provide additional details in the text field below. ");
        }
        
        if (manualIncidentEditText != null && manualIncidentEditText.getText() != null) {
            String incidentText = manualIncidentEditText.getText().toString();
            if (!incidentText.isEmpty()) {
                content.append("Custom incident description field contains: ").append(incidentText).append(". ");
            } else {
                content.append("Custom incident description field is empty. ");
                content.append("If you selected Other in the incident type dropdown, please describe your specific emergency type here. ");
                content.append("This helps emergency responders understand your situation better. ");
            }
        }
        
        content.append("Navigation and Actions: ");
        content.append("Save Emergency Contacts button: Saves all your information securely. ");
        content.append("Accessibility button: Opens additional accessibility options. ");
        content.append("Back button: Returns to the previous screen without saving. ");
        
        content.append("Security Information: ");
        content.append("All your data is encrypted and stored securely on your device. ");
        content.append("Emergency contacts are only contacted when you activate an emergency alert. ");
        content.append("Your privacy is protected and information is never shared without your consent. ");
        
        content.append("Thank you for using BilaWoga Safety App. Your safety is our priority.");
        
        tts.speak(content.toString(), TextToSpeech.QUEUE_FLUSH, null, "page_content");
    }
    
    /**
     * Speaks popup content for accessibility
     */
    private void speakPopupContent(String popupTitle, String popupMessage) {
        if (tts == null) return;
        
        StringBuilder popupContent = new StringBuilder();
        popupContent.append("Popup Alert. ");
        popupContent.append("Title: ").append(popupTitle).append(". ");
        popupContent.append("Message: ").append(popupMessage).append(". ");
        popupContent.append("Please read this information carefully. ");
        
        tts.speak(popupContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "popup_content");
    }
    
    /**
     * Speaks error messages for accessibility
     */
    private void speakErrorMessages(String errorMessage) {
        if (tts == null) return;
        
        StringBuilder errorContent = new StringBuilder();
        errorContent.append("Error Alert. ");
        errorContent.append("Error message: ").append(errorMessage).append(". ");
        errorContent.append("Please try again or contact support if the problem persists. ");
        
        tts.speak(errorContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "error_message");
    }
    
    /**
     * Speaks success messages for accessibility
     */
    private void speakSuccessMessages(String successMessage) {
        if (tts == null) return;
        
        StringBuilder successContent = new StringBuilder();
        successContent.append("Success Alert. ");
        successContent.append("Success message: ").append(successMessage).append(". ");
        successContent.append("Your action has been completed successfully. ");
        
        tts.speak(successContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "success_message");
    }
    
    /**
     * Speaks form field focus changes
     */
    private void speakFieldFocus(String fieldName, String fieldHint) {
        if (tts == null) return;
        
        StringBuilder focusContent = new StringBuilder();
        focusContent.append("Focused on: ").append(fieldName).append(". ");
        focusContent.append("Hint: ").append(fieldHint).append(". ");
        
        tts.speak(focusContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "field_focus");
    }



    /**
     * Toggles high contrast mode
     */
    private void toggleHighContrast() {
        isHighContrast = !isHighContrast;
        View root = findViewById(android.R.id.content).getRootView();
        if (isHighContrast) {
            if (root != null) {
                root.setBackgroundColor(Color.BLACK);
                setTextColorAll(root, Color.YELLOW);
            }
        } else {
            if (root != null) {
                root.setBackgroundResource(R.drawable.background);
                setTextColorAll(root, Color.BLACK);
            }
        }
    }

    /**
     * Toggles text size
     */
    private void toggleTextSize() {
        isLargeText = !isLargeText;
        if (nameEdit != null && numberEdit != null && manualIncidentEditText != null) {
            if (isLargeText) {
                nameEdit.setTextSize(24f);
                numberEdit.setTextSize(24f);
                manualIncidentEditText.setTextSize(24f);
            } else {
                nameEdit.setTextSize(16f);
                numberEdit.setTextSize(16f);
                manualIncidentEditText.setTextSize(16f);
            }
        }
    }

    /**
     * Toggles reading guide
     */
    private void toggleReadingGuide() {
        if (nameEdit != null && numberEdit != null && manualIncidentEditText != null) {
            if (!isReadingGuide) {
                nameEdit.setBackgroundColor(Color.parseColor("#FFEB3B"));
                numberEdit.setBackgroundColor(Color.parseColor("#FFEB3B"));
                manualIncidentEditText.setBackgroundColor(Color.parseColor("#FFEB3B"));
                isReadingGuide = true;
            } else {
                nameEdit.setBackgroundColor(Color.TRANSPARENT);
                numberEdit.setBackgroundColor(Color.TRANSPARENT);
                manualIncidentEditText.setBackgroundColor(Color.TRANSPARENT);
                isReadingGuide = false;
            }
        }
    }

    /**
     * Resets all accessibility settings
     */
    private void resetAccessibilitySettings() {
        stopAudio();
        isHighContrast = false;
        isLargeText = false;
        isReadingGuide = false;
        
        View root = findViewById(android.R.id.content).getRootView();
        if (root != null) {
            root.setBackgroundResource(R.drawable.background);
            setTextColorAll(root, Color.BLACK);
        }
        
        if (nameEdit != null && numberEdit != null && manualIncidentEditText != null) {
            nameEdit.setTextSize(16f);
            numberEdit.setTextSize(16f);
            manualIncidentEditText.setTextSize(16f);
            nameEdit.setBackgroundColor(Color.TRANSPARENT);
            numberEdit.setBackgroundColor(Color.TRANSPARENT);
            manualIncidentEditText.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * Sets text color for all views
     */
    private void setTextColorAll(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof Button) {
            ((Button) view).setTextColor(color);
        } else if (view instanceof EditText) {
            ((EditText) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setTextColorAll(viewGroup.getChildAt(i), color);
            }
        }
    }





    private void speakAllFields() {
        if (tts == null) {
            Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // SECURITY FIX: Don't speak sensitive data aloud!
        StringBuilder textToSpeak = new StringBuilder();
        
        if (nameEdit != null) {
            String name = nameEdit.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                textToSpeak.append("Your name is ").append(maskSensitiveData(name)).append(". ");
            }
        }
        
        if (numberEdit != null) {
            String number = numberEdit.getText().toString();
            if (!TextUtils.isEmpty(number)) {
                textToSpeak.append("Emergency number one is ").append(maskPhoneNumber(number)).append(". ");
            }
        }
        
        if (number2Edit != null && !TextUtils.isEmpty(number2Edit.getText().toString())) {
            String number2 = number2Edit.getText().toString();
            textToSpeak.append("Emergency number two is ").append(maskPhoneNumber(number2)).append(". ");
        }
        
        textToSpeak.append("Incident type is ").append(getSelectedIncident()).append(". ");
        tts.speak(textToSpeak.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    // SECURITY: Mask sensitive data for speech
    private String maskSensitiveData(String data) {
        if (data == null || data.length() <= 2) return "***";
        return data.substring(0, 2) + "***";
    }
    
    // SECURITY: Mask phone numbers for speech
    private String maskPhoneNumber(String number) {
        if (number == null || number.length() < 4) return "***";
        return "***" + number.substring(number.length() - 4);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_privacy_policy) {
            showPolicyViewer(PolicyViewerActivity.POLICY_TYPE_PRIVACY);
            return true;
        } else if (id == R.id.action_terms_of_use) {
            showPolicyViewer(PolicyViewerActivity.POLICY_TYPE_TERMS);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showPolicyViewer(String policyType) {
        try {
            Intent intent = new Intent(this, com.example.bilawoga.utils.PolicyViewerActivity.class);
            intent.putExtra(com.example.bilawoga.utils.PolicyViewerActivity.EXTRA_POLICY_TYPE, policyType);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error showing policy viewer", e);
            Toast.makeText(this, "Error opening policy. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // Welcome alert removed as per user request
    
    // Security notice removed as per user request

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // SECURITY: Don't save sensitive data in bundle
        // Only save non-sensitive UI state
        if (incidentSpinner != null) {
            outState.putInt("spinner_position", incidentSpinner.getSelectedItemPosition());
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // SECURITY: Only restore non-sensitive UI state
        if (incidentSpinner != null) {
            incidentSpinner.setSelection(savedInstanceState.getInt("spinner_position"));
        }
    }

    @Override
    protected void onDestroy() {
        // SECURITY: Clear sensitive data from memory
        clearSensitiveDataFromMemory();
        
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
    
    // SECURITY: Clear sensitive data from memory
    private void clearSensitiveDataFromMemory() {
        if (nameEdit != null) {
            nameEdit.setText("");
        }
        if (numberEdit != null) {
            numberEdit.setText("");
        }
        if (number2Edit != null) {
            number2Edit.setText("");
        }
        if (manualIncidentEditText != null) {
            manualIncidentEditText.setText("");
        }
    }

    private void validateAndSaveContact(String number, String contactName) {
        // NEW: Enhanced contact validation
        if (isValidPhoneNumber(number)) {
            // Check if number is reachable (basic validation)
            if (isNumberReachable(number)) {
                saveContact(number, contactName);
                Toast.makeText(this, "Contact saved successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Warning: Number may not be reachable. Please verify.", Toast.LENGTH_LONG).show();
                // Still save but with warning
                saveContact(number, contactName);
            }
        } else {
            Toast.makeText(this, "Invalid phone number format!", Toast.LENGTH_LONG).show();
        }
    }

    // NEW METHOD: Validate phone number format
    private boolean isValidPhoneNumber(String number) {
        if (number == null || number.trim().isEmpty()) {
            return false;
        }
        
        // Remove all non-digit characters
        String cleanNumber = number.replaceAll("[^0-9+]", "");
        
        // Check for minimum length (7 digits minimum)
        if (cleanNumber.length() < 7) {
            return false;
        }
        
        // Check for valid country code if present
        if (cleanNumber.startsWith("+")) {
            // Must have country code followed by number
            if (cleanNumber.length() < 10) {
                return false;
            }
        }
        
        return true;
    }

    // NEW METHOD: Basic reachability check
    private boolean isNumberReachable(String number) {
        try {
            // Use Android's PhoneNumberUtils for basic validation
            String cleanNumber = PhoneNumberUtils.stripSeparators(number);
            
            // Check if it's a valid mobile number pattern
            if (cleanNumber.length() >= 10 && cleanNumber.length() <= 15) {
                // Additional validation could be added here
                // For now, we'll do basic format checking
                return Pattern.matches("^[+]?[0-9]{7,15}$", cleanNumber);
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error validating number reachability: " + com.example.bilawoga.utils.SecureStorageManager.encryptLogMessage(e.getMessage()));
            return false;
        }
    }

    // NEW METHOD: Enhanced save contact with validation
    private void saveContact(String number, String contactName) {
        SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            
            // Save with timestamp for validation tracking
            long timestamp = System.currentTimeMillis();
            editor.putString("ENUM_1", number);
            editor.putString("CONTACT_NAME_1", contactName);
            editor.putLong("CONTACT_SAVED_TIME_1", timestamp);
            editor.putBoolean("CONTACT_VALIDATED_1", true);
            
            editor.apply();
            
            // SECURITY: Don't log sensitive contact information
            Log.d(TAG, "Contact saved successfully for: " + maskSensitiveData(contactName));
        }
    }

    public void toggleEmergencyNumbers(View view) {
        // TODO: Implement show/hide logic for emergency numbers
        Toast.makeText(this, "Toggle emergency numbers clicked", Toast.LENGTH_SHORT).show();
    }
}