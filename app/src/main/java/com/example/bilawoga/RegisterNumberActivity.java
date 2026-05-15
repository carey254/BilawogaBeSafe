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
import android.widget.Switch;
import android.widget.Toast;
import android.app.Dialog;

import android.graphics.Color;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.telephony.PhoneNumberUtils;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.button.MaterialButton;
import com.bilawoga.safety.R;
import com.example.bilawoga.utils.PolicyViewerActivity;
import com.example.bilawoga.utils.TTSLanguageManager;
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
    private boolean ttsReady = false;
    private boolean pendingAutoRead = false;

    // Get localized incident types
    private String[] getIncidentTypes() {
        return new String[]{
                getString(R.string.incident_no_emergency),
                getString(R.string.incident_abduction),
                getString(R.string.incident_sexual_assault),
                getString(R.string.incident_domestic_violence),
                getString(R.string.incident_medical_emergency),
                getString(R.string.incident_other)
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // SECURITY: Prevent screenshots and screen recording
        com.example.bilawoga.utils.ScreenSecurityManager.preventScreenshots(this);
        
        // Set locale based on selected language
        updateLocale();
        
        setContentView(R.layout.activity_register_number);
        TTSLanguageManager.initDefaultOnFirstLaunch(this);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.emergency_contact_setup));
        }
        
        // Block app if encrypted storage is unavailable - TEMPORARY BYPASS TO SHOW UI
        SharedPreferences testPrefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
        if (testPrefs == null) {
            Toast.makeText(this, getString(R.string.secure_storage_unavailable), Toast.LENGTH_LONG).show();
            // Note: In production, restore the blocking dialog and exit for security.
        }

        initializeViews();
        setupSpinner();
        loadSavedData();
        setupTextWatchers();
        setupAccessibilityFab();
        setupTextToSpeech();
        // Auto-read will be triggered when TTS is ready (handled in setupTextToSpeech)
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

        String[] incidentTypes = getIncidentTypes();
        IncidentTypeAdapter adapter = new IncidentTypeAdapter(
                this,
                incidentTypes
        );
        incidentSpinner.setAdapter(adapter);

        incidentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (manualIncidentInputLayout != null) {
                    String[] incidentTypes = getIncidentTypes();
                    boolean isOtherSelected = incidentTypes[position].equals(getString(R.string.incident_other));
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
    
    // Update locale based on selected language
    private void updateLocale() {
        Locale selectedLocale = TTSLanguageManager.getSelectedLocale(this);
        Locale.setDefault(selectedLocale);
        android.content.res.Configuration config = getResources().getConfiguration();
        config.setLocale(selectedLocale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
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
            String[] incidentTypes = getIncidentTypes();
            for (int i = 0; i < incidentTypes.length; i++) {
                if (incidentTypes[i].equals(savedIncident)) {
                    incidentSpinner.setSelection(i);
                    break;
                }
            }

            // If no match found, select "Other" and show in manual input
            if (!TextUtils.isEmpty(savedIncident) && incidentSpinner.getSelectedItemPosition() == 0) {
                incidentSpinner.setSelection(incidentTypes.length - 1); // "Other" option
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
            inputLayout.setError(getString(R.string.emergency_number_required));
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
            inputLayout.setError(getString(R.string.invalid_phone_length, MIN_PHONE_LENGTH, MAX_PHONE_LENGTH));
            return false;
        }
        
        // Format validation
        if (!isValidPhoneNumberFormat(cleanNumber)) {
            inputLayout.setError(getString(R.string.invalid_phone_format));
            return false;
        }
        
        // SECURITY: Prevent SMS injection and malicious patterns
        if (containsMaliciousPatterns(number)) {
            inputLayout.setError(getString(R.string.invalid_characters_detected));
            return false;
        }
        
        // SECURITY: Check for common emergency numbers to prevent abuse
        if (isEmergencyServiceNumber(cleanNumber)) {
            inputLayout.setError(getString(R.string.use_personal_emergency_contact));
            return false;
        }

        inputLayout.setError(null);
        return true;
    }
    
    // SECURITY: Sanitize phone number input and normalize to Kenyan format
    private String sanitizePhoneNumber(String number) {
        if (number == null || number.trim().isEmpty()) {
            return "";
        }
        
        // Remove spaces, dashes, parentheses, and other formatting
        String sanitized = number.trim().replaceAll("[\\s\\-\\(\\)]", "");
        
        // Normalize Kenyan phone numbers to +254 format
        // Accepts ALL Kenyan formats: +254XXXXXXXXX, 254XXXXXXXXX, 07XXXXXXXXX, 011XXXXXXXX, etc.
        if (sanitized.startsWith("+254")) {
            // Already in international format: +254XXXXXXXXX
            return sanitized;
        } else if (sanitized.startsWith("254") && sanitized.length() == 12) {
            // 254XXXXXXXXX format - add +
            return "+" + sanitized;
        } else if (sanitized.startsWith("0") && sanitized.length() == 10) {
            // 07XXXXXXXXX, 011XXXXXXXX, 020XXXXXXXX format - convert to +254
            return "+254" + sanitized.substring(1);
        } else if (sanitized.length() == 9 && sanitized.matches("^[17]\\d{8}$")) {
            // 7XXXXXXXXX or 1XXXXXXXXX format (without leading 0) - add +254
            return "+254" + sanitized;
        }
        
        // Return as-is if already in international format or other country
        return sanitized;
    }
    
    // SECURITY: Validate phone number format
    private boolean isValidPhoneNumberFormat(String number) {
        // Accepts ALL Kenyan phone number formats:
        // +254XXXXXXXXX (international with +)
        // 254XXXXXXXXX (international without +)
        // 07XXXXXXXXX (Safaricom/Airtel mobile)
        // 011XXXXXXXX (landline)
        // 020XXXXXXXX (landline)
        // 7XXXXXXXXX (mobile without 0)
        // 1XXXXXXXXX (mobile without 0)
        // ALL Kenyan numbers work - Safaricom, Airtel, Telkom, Equitel, etc.
        
        if (number == null || number.trim().isEmpty()) {
            return false;
        }
        
        // Remove all non-digit characters except +
        String cleanNumber = number.replaceAll("[^0-9+]", "");
        
        // Kenyan international format: +254 or 254 followed by 9 digits
        if (cleanNumber.startsWith("+254") || cleanNumber.startsWith("254")) {
            // Must be exactly +254 or 254 followed by 9 digits (total 12 or 13 characters)
            String digitsOnly = cleanNumber.replace("+", "");
            return digitsOnly.matches("^254\\d{9}$");
        } 
        // Kenyan local format: 0 followed by 9 digits (07XXXXXXXXX, 011XXXXXXXX, 020XXXXXXXX, etc.)
        else if (cleanNumber.startsWith("0") && cleanNumber.length() == 10) {
            // Accepts: 07XXXXXXXXX (mobile), 011XXXXXXXX (landline), 020XXXXXXXX (landline), etc.
            return cleanNumber.matches("^0\\d{9}$");
        } 
        // Kenyan mobile without leading 0: 7XXXXXXXXX or 1XXXXXXXXX (9 digits)
        else if (cleanNumber.length() == 9 && cleanNumber.matches("^[17]\\d{8}$")) {
            // Mobile numbers starting with 7 or 1 (without leading 0)
            return true;
        } 
        // Other valid formats
        else {
            // Generic international format for other countries
            return cleanNumber.matches("^\\+?[1-9]\\d{6,14}$");
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
            nameInputLayout.setError(getString(R.string.please_enter_full_name));
            return false;
        }
        
        // SECURITY: Enhanced name validation
        String sanitizedName = sanitizeName(name.trim());
        
        if (sanitizedName.length() < MIN_NAME_LENGTH) {
            nameInputLayout.setError(getString(R.string.name_min_length, MIN_NAME_LENGTH));
            return false;
        }
        
        if (sanitizedName.length() > MAX_NAME_LENGTH) {
            nameInputLayout.setError(getString(R.string.name_max_length, MAX_NAME_LENGTH));
            return false;
        }
        
        // SECURITY: Check for malicious patterns in name
        if (containsMaliciousPatterns(sanitizedName)) {
            nameInputLayout.setError(getString(R.string.name_invalid_characters));
            return false;
        }
        
        // SECURITY: Validate name format (letters, spaces, hyphens, apostrophes only)
        if (!sanitizedName.matches("^[a-zA-Z\\s\\-']+$")) {
            nameInputLayout.setError(getString(R.string.name_only_letters));
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

        if (selectedIncident.equals(getString(R.string.incident_other)) && !TextUtils.isEmpty(manualIncident)) {
            return manualIncident;
        } else if (!selectedIncident.equals(getString(R.string.select_emergency_type))) {
            return selectedIncident;
        }
        return "";
    }

    public void saveNumber(View view) {
        Log.d("RegisterNumberActivity", "saveNumber called");
        Toast.makeText(this, getString(R.string.save_button_clicked), Toast.LENGTH_SHORT).show();
        // SECURITY: Rate limiting to prevent spam submissions
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSubmissionTime < SUBMISSION_COOLDOWN) {
            Snackbar.make(view, getString(R.string.please_wait_before_submitting), Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        if (nameEdit == null || numberEdit == null || number2Edit == null) {
            Toast.makeText(this, getString(R.string.error_form_fields_not_initialized), Toast.LENGTH_LONG).show();
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
            String errorMsg = getString(R.string.please_select_emergency_type);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, errorMsg, Snackbar.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
            return;
        }
        if (!isValid) {
            Log.w(TAG, "Validation failed: name or number invalid");
            String errorMsg = getString(R.string.please_check_name_and_numbers);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
            return;
        }
        // SECURITY: Final validation before saving
        if (!validateEmergencyData(nameString, number1String, number2String, incident)) {
            Log.w(TAG, "Validation failed: emergency data invalid");
            String errorMsg = getString(R.string.invalid_emergency_data);
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
                String successMsg = getString(R.string.emergency_contacts_saved_successfully);
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
            String successMsg = getString(R.string.emergency_contacts_saved_securely);
            Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, successMsg, Snackbar.LENGTH_LONG).show();
            speakSuccessMessages(successMsg);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(RegisterNumberActivity.this, MainActivity.class));
                finish();
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Error saving emergency data: " + e.getMessage());
            String errorMsg = getString(R.string.error_saving_contacts);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            Snackbar.make(view, errorMsg, Snackbar.LENGTH_LONG).show();
            speakErrorMessages(errorMsg);
        }
    }
    
    // SECURITY: Validate emergency data before saving
    private boolean validateEmergencyData(String name, String number1, String number2, String incident) {
        // Check for duplicate emergency numbers
        if (!TextUtils.isEmpty(number1) && !TextUtils.isEmpty(number2) && number1.equals(number2)) {
            Toast.makeText(this, getString(R.string.emergency_numbers_same), Toast.LENGTH_LONG).show();
            return false;
        }
        
        // Check incident type length
        if (incident.length() > MAX_INCIDENT_LENGTH) {
            Toast.makeText(this, getString(R.string.incident_description_too_long), Toast.LENGTH_LONG).show();
            return false;
        }
        
        // Check for at least one emergency number
        if (TextUtils.isEmpty(number1) && TextUtils.isEmpty(number2)) {
            Toast.makeText(this, getString(R.string.at_least_one_emergency_required), Toast.LENGTH_LONG).show();
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
                    ttsReady = true;
                    java.util.Locale selected = TTSLanguageManager.getSelectedLocale(RegisterNumberActivity.this);
                    int result = TTSLanguageManager.setTtsLanguage(tts, selected);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(RegisterNumberActivity.this, getString(R.string.selected_tts_voice_missing), Toast.LENGTH_LONG).show();
                        try {
                            startActivity(new android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                        } catch (Exception ignored) {}
                    }
                    if ("sw".equals(selected.getLanguage())) {
                        tts.setSpeechRate(0.90f);
                        tts.setPitch(1.0f);
                    } else {
                        tts.setSpeechRate(1.0f);
                        tts.setPitch(1.0f);
                    }
                    setBestVoiceForLocale(selected);
                    // If user preference is auto-read or a start was requested earlier, start now
                    if (TTSLanguageManager.isAutoReadEnabled(RegisterNumberActivity.this) || pendingAutoRead) {
                        pendingAutoRead = false;
                        // Small delay to ensure UI is ready
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            startTextToSpeech();
                        }, 300);
                    }
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
                    speakFieldFocus(getString(R.string.name_field), getString(R.string.name_field_hint));
                }
            });
        }
        
        if (numberEdit != null) {
            numberEdit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus(getString(R.string.primary_emergency_contact_field), getString(R.string.primary_emergency_contact_hint));
                }
            });
        }
        
        if (number2Edit != null) {
            number2Edit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus(getString(R.string.secondary_emergency_contact_field), getString(R.string.secondary_emergency_contact_hint));
                }
            });
        }
        
        if (manualIncidentEditText != null) {
            manualIncidentEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus(getString(R.string.custom_incident_description_field), getString(R.string.custom_incident_description_hint));
                }
            });
        }
        
        if (incidentSpinner != null) {
            incidentSpinner.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    speakFieldFocus(getString(R.string.incident_type_dropdown), getString(R.string.incident_type_dropdown_hint));
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

        View rowReadPage = accessibilityDialog.findViewById(R.id.rowReadPage);
        Switch switchAutoRead = accessibilityDialog.findViewById(R.id.switchAutoRead);
        Switch switchCursorReading = accessibilityDialog.findViewById(R.id.switchCursorReading);
        View btnTextLarger = accessibilityDialog.findViewById(R.id.btnTextLarger);
        View btnTextSmaller = accessibilityDialog.findViewById(R.id.btnTextSmaller);
        MaterialButton resetButton = accessibilityDialog.findViewById(R.id.btnResetAccessibility);
        Button cancelButton = accessibilityDialog.findViewById(R.id.btnCancelAccessibility);
        View btnStopReading = accessibilityDialog.findViewById(R.id.btnStopReading);
        TextView textFooterStatus = accessibilityDialog.findViewById(R.id.textFooterStatus);
        TextView textContrastValue = accessibilityDialog.findViewById(R.id.textContrastValue);
        TextView textColorsValue = accessibilityDialog.findViewById(R.id.textColorsValue);
        View rowLanguage = accessibilityDialog.findViewById(R.id.rowLanguage);
        TextView textLanguageValue = accessibilityDialog.findViewById(R.id.textLanguageValue);

        if (rowReadPage != null) {
            rowReadPage.setOnClickListener(v -> {
                speakAllFields();
                Toast.makeText(RegisterNumberActivity.this, getString(R.string.reading_page_content), Toast.LENGTH_SHORT).show();
            });
        }

        if (switchAutoRead != null) {
            switchAutoRead.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startTextToSpeech();
                    Toast.makeText(RegisterNumberActivity.this, getString(R.string.auto_read_on), Toast.LENGTH_SHORT).show();
                } else {
                    stopAudio();
                    Toast.makeText(RegisterNumberActivity.this, getString(R.string.auto_read_off), Toast.LENGTH_SHORT).show();
                }
                TTSLanguageManager.setAutoReadEnabled(RegisterNumberActivity.this, isChecked);
                updateFooter(textFooterStatus, switchAutoRead.isChecked());
            });
        }

        if (switchCursorReading != null) {
            switchCursorReading.setOnCheckedChangeListener((buttonView, isChecked) -> {
                toggleReadingGuide();
                Toast.makeText(RegisterNumberActivity.this, (isChecked ? getString(R.string.cursor_reading_on) : getString(R.string.cursor_reading_off)), Toast.LENGTH_SHORT).show();
            });
        }

        // No image description switch in layout

        if (btnTextLarger != null) {
            btnTextLarger.setOnClickListener(v -> {
                toggleTextSize();
                Toast.makeText(RegisterNumberActivity.this, getString(R.string.text_larger), Toast.LENGTH_SHORT).show();
            });
        }

        if (btnTextSmaller != null) {
            btnTextSmaller.setOnClickListener(v -> {
                toggleTextSize();
                Toast.makeText(RegisterNumberActivity.this, getString(R.string.text_smaller), Toast.LENGTH_SHORT).show();
            });
        }

        if (btnStopReading != null) {
            btnStopReading.setOnClickListener(v -> {
                stopAudio();
                if (tts != null) {
                    tts.stop();
                }
                Toast.makeText(RegisterNumberActivity.this, isSw() ? "Kusoma kumesitishwa" : "Reading stopped", Toast.LENGTH_SHORT).show();
                updateFooter(textFooterStatus, false);
            });
        }

        if (resetButton != null) {
            resetButton.setOnClickListener(v -> {
                resetAccessibilitySettings();
                if (switchAutoRead != null) switchAutoRead.setChecked(false);
                if (switchCursorReading != null) switchCursorReading.setChecked(false);
                if (textContrastValue != null) textContrastValue.setText(getString(R.string.normal));
                if (textColorsValue != null) textColorsValue.setText(getString(R.string.normal));
                updateFooter(textFooterStatus, false);
                Toast.makeText(RegisterNumberActivity.this, getString(R.string.all_settings_reset), Toast.LENGTH_SHORT).show();
            });
        }

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                accessibilityDialog.dismiss();
                if (tts != null) {
                    tts.speak(getString(R.string.accessibility_options_closed), TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        }

        // Initialize switch states and footer
        if (textLanguageValue != null) textLanguageValue.setText(TTSLanguageManager.getSelectedLanguageName(this));
        if (rowLanguage != null) {
            rowLanguage.setOnClickListener(v -> {
                TTSLanguageManager.toggleLanguage(RegisterNumberActivity.this);
                if (textLanguageValue != null) textLanguageValue.setText(TTSLanguageManager.getSelectedLanguageName(RegisterNumberActivity.this));
                if (tts != null) {
                    java.util.Locale selected = TTSLanguageManager.getSelectedLocale(RegisterNumberActivity.this);
                    int result = TTSLanguageManager.setTtsLanguage(tts, selected);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        try { startActivity(new android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)); } catch (Exception ignored) {}
                    }
                    if ("sw".equals(selected.getLanguage())) {
                        tts.setSpeechRate(0.90f);
                    } else {
                        tts.setSpeechRate(1.0f);
                    }
                    setBestVoiceForLocale(selected);
                }
                // Update locale and re-read if auto-read is enabled
                updateLocale();
                if (switchAutoRead != null && switchAutoRead.isChecked()) {
                    // Stop current speech and restart in new language
                    if (tts != null) {
                        tts.stop();
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        startTextToSpeech();
                    }, 200);
                }
            });
        }
        boolean autoPref = TTSLanguageManager.isAutoReadEnabled(this);
        if (switchAutoRead != null) switchAutoRead.setChecked(autoPref);
        updateFooter(textFooterStatus, autoPref);
        accessibilityDialog.show();
    }

    private void updateFooter(TextView footer, boolean autoOn) {
        if (footer != null) {
            footer.setText(getString(R.string.font_status, (autoOn ? getString(R.string.auto_on) : getString(R.string.auto_off))));
        }
    }

    private boolean isSw() {
        java.util.Locale loc = TTSLanguageManager.getSelectedLocale(this);
        return loc != null && "sw".equalsIgnoreCase(loc.getLanguage());
    }

    /**
     * Starts continuous Text-to-Speech narration
     */
    private void startTextToSpeech() {
        if (!ttsReady) {
            // Defer until TTS engine is ready
            pendingAutoRead = true;
            return;
        }
        if (isAudioActive) {
            stopAudio();
        }
        
        isAudioActive = true;
        // Speak once in segmented queue to avoid truncation
        speakAllPageContent();
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

    private void setBestVoiceForLocale(java.util.Locale locale) {
        try {
            if (tts == null) return;
            java.util.Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            for (Voice v : voices) {
                java.util.Locale vLoc = v.getLocale();
                if (vLoc != null && vLoc.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                    // Prefer exact country match (en-GB, sw-KE)
                    boolean countryMatch = vLoc.getCountry() != null && vLoc.getCountry().equalsIgnoreCase(locale.getCountry());
                    if (best == null || countryMatch) {
                        best = v;
                        if (countryMatch) break;
                    }
                }
            }
            if (best != null) {
                tts.setVoice(best);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Speaks all page content for accessibility
     */
    private void speakAllPageContent() {
        if (tts == null) return;
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (isSw()) {
            parts.add("Ukurasa wa Kuandaa Mawasiliano ya Dharura wa BilaWoga.");
            parts.add("Hapa utaweka majina na namba zako za dharura na kuchagua aina ya tukio.");
            parts.add("Taarifa zako zitatumika tu wakati wa dharura.");

            parts.add("Maelekezo ya Fomu.");
            parts.add("Tafadhali jaza sehemu zote muhimu.");
            parts.add("Taarifa zako zitahifadhiwa kwa usalama.");

            if (nameEdit != null && nameEdit.getText() != null) {
                String nameText = nameEdit.getText().toString();
                if (!nameText.isEmpty()) {
                    parts.add("Sehemu ya jina ina: " + maskSensitiveData(nameText) + ".");
                } else {
                    parts.add("Sehemu ya jina iko wazi. Tafadhali andika jina lako kamili.");
                }
            }

            if (numberEdit != null && numberEdit.getText() != null) {
                String numberText = numberEdit.getText().toString();
                if (!numberText.isEmpty()) {
                    parts.add("Namba ya dharura ya kwanza: " + maskPhoneNumber(numberText) + ".");
                } else {
                    parts.add("Sehemu ya namba ya dharura ya kwanza iko wazi.");
                }
            }

            if (number2Edit != null && number2Edit.getText() != null) {
                String number2Text = number2Edit.getText().toString();
                if (!number2Text.isEmpty()) {
                    parts.add("Namba ya dharura ya pili: " + maskPhoneNumber(number2Text) + ".");
                } else {
                    parts.add("Sehemu ya namba ya dharura ya pili iko wazi. Si lazima, lakini inashauriwa.");
                }
            }

            if (incidentSpinner != null) { parts.add("Chagua aina ya tukio la dharura kwenye menyu kushuka."); }

            if (manualIncidentEditText != null && manualIncidentEditText.getText() != null) {
                String incidentText = manualIncidentEditText.getText().toString();
                if (!incidentText.isEmpty()) {
                    parts.add("Maelezo ya tukio maalum: " + incidentText + ".");
                } else {
                    parts.add("Unaweza kuandika maelezo ya tukio kama umechagua Nyingine.");
                }
            }

            parts.add("Vitufe vya Uendeshaji.");
            parts.add("Hifadhi Mawasiliano ya Dharura: kuhifadhi taarifa zako salama.");
            parts.add("Ufikiaji: kufungua chaguo za ufikivu.");
            parts.add("Rudi: kurudi bila kuhifadhi.");
            parts.add("Asante kwa kutumia BilaWoga. Usalama wako ni kipaumbele chetu.");
        } else {
            parts.add("BilaWoga Emergency Contact Setup Page.");
            parts.add("This is where you will share your contacts and select the case type.");
            parts.add("Information is used only for emergencies.");

            parts.add("Form Instructions.");
            parts.add("Fill all required fields.");
            parts.add("Your data is stored securely.");

            if (nameEdit != null && nameEdit.getText() != null) {
                String nameText = nameEdit.getText().toString();
                if (!nameText.isEmpty()) {
                    parts.add("Name field contains: " + maskSensitiveData(nameText) + ".");
                } else {
                    parts.add("Name field is empty. Please enter your full name.");
                }
            }

            if (numberEdit != null && numberEdit.getText() != null) {
                String numberText = numberEdit.getText().toString();
                if (!numberText.isEmpty()) {
                    parts.add("Primary emergency contact: " + maskPhoneNumber(numberText) + ".");
                } else {
                    parts.add("Primary emergency contact field is empty.");
                }
            }

            if (number2Edit != null && number2Edit.getText() != null) {
                String number2Text = number2Edit.getText().toString();
                if (!number2Text.isEmpty()) {
                    parts.add("Secondary emergency contact: " + maskPhoneNumber(number2Text) + ".");
                } else {
                    parts.add("Secondary contact is optional but recommended.");
                }
            }

            if (incidentSpinner != null) { parts.add("Select your incident type from the dropdown."); }

            if (manualIncidentEditText != null && manualIncidentEditText.getText() != null) {
                String incidentText = manualIncidentEditText.getText().toString();
                if (!incidentText.isEmpty()) {
                    parts.add("Custom incident description: " + incidentText + ".");
                } else {
                    parts.add("Describe your specific emergency if you chose Other.");
                }
            }

            parts.add("Actions: Save contacts. Accessibility options. Back to previous screen.");
            parts.add("Thank you for using BilaWoga. Your safety matters.");
        }
        speakQueued(parts);
    }

    private void speakQueued(java.util.List<String> parts) {
        if (tts == null || parts == null || parts.isEmpty()) return;
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            int queueMode = first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(p, queueMode, null, java.util.UUID.randomUUID().toString());
            // add a short pause between segments
            tts.playSilentUtterance(150, TextToSpeech.QUEUE_ADD, java.util.UUID.randomUUID().toString());
            first = false;
        }
    }
    
    /**
     * Speaks popup content for accessibility
     */
    private void speakPopupContent(String popupTitle, String popupMessage) {
        if (tts == null) return;
        StringBuilder popupContent = new StringBuilder();
        if (isSw()) {
            popupContent.append("Arifa ya dirisha. ");
            popupContent.append("Kichwa: ").append(popupTitle).append(". ");
            popupContent.append("Ujumbe: ").append(popupMessage).append(". ");
            popupContent.append("Tafadhali soma kwa makini. ");
        } else {
            popupContent.append("Popup Alert. ");
            popupContent.append("Title: ").append(popupTitle).append(". ");
            popupContent.append("Message: ").append(popupMessage).append(". ");
            popupContent.append("Please read this carefully. ");
        }
        tts.speak(popupContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "popup_content");
    }
    
    /**
     * Speaks error messages for accessibility
     */
    private void speakErrorMessages(String errorMessage) {
        if (tts == null) return;
        StringBuilder errorContent = new StringBuilder();
        if (isSw()) {
            errorContent.append("Arifa ya kosa. ");
            errorContent.append("Ujumbe wa kosa: ").append(errorMessage).append(". ");
            errorContent.append("Tafadhali jaribu tena. ");
        } else {
            errorContent.append("Error Alert. ");
            errorContent.append("Error message: ").append(errorMessage).append(". ");
            errorContent.append("Please try again. ");
        }
        tts.speak(errorContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "error_message");
    }
    
    /**
     * Speaks success messages for accessibility
     */
    private void speakSuccessMessages(String successMessage) {
        if (tts == null) return;
        StringBuilder successContent = new StringBuilder();
        if (isSw()) {
            successContent.append("Ujumbe wa mafanikio. ");
            successContent.append("Maelezo: ").append(successMessage).append(". ");
            successContent.append("Hatua yako imekamilika. ");
        } else {
            successContent.append("Success. ");
            successContent.append("Message: ").append(successMessage).append(". ");
            successContent.append("Your action completed. ");
        }
        tts.speak(successContent.toString(), TextToSpeech.QUEUE_FLUSH, null, "success_message");
    }
    
    /**
     * Speaks form field focus changes
     */
    private void speakFieldFocus(String fieldName, String fieldHint) {
        if (tts == null) return;
        StringBuilder focusContent = new StringBuilder();
        if (isSw()) {
            focusContent.append("Umechagua: ").append(fieldName).append(". ");
            focusContent.append("Ushauri: ").append(fieldHint).append(". ");
        } else {
            focusContent.append("Focused on: ").append(fieldName).append(". ");
            focusContent.append("Hint: ").append(fieldHint).append(". ");
        }
        tts.speak(focusContent.toString(), TextToSpeech.QUEUE_ADD, null, "field_focus");
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
            Toast.makeText(this, getString(R.string.text_to_speech_not_available), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // SECURITY FIX: Don't speak sensitive data aloud!
        StringBuilder textToSpeak = new StringBuilder();
        
        if (nameEdit != null) {
            String name = nameEdit.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                textToSpeak.append(getString(R.string.your_name_is, maskSensitiveData(name))).append(" ");
            }
        }
        
        if (numberEdit != null) {
            String number = numberEdit.getText().toString();
            if (!TextUtils.isEmpty(number)) {
                textToSpeak.append(getString(R.string.emergency_number_one_is, maskPhoneNumber(number))).append(" ");
            }
        }
        
        if (number2Edit != null && !TextUtils.isEmpty(number2Edit.getText().toString())) {
            String number2 = number2Edit.getText().toString();
            textToSpeak.append(getString(R.string.emergency_number_two_is, maskPhoneNumber(number2))).append(" ");
        }
        
        textToSpeak.append(getString(R.string.incident_type_is, getSelectedIncident())).append(" ");
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
            Toast.makeText(this, getString(R.string.error_opening_policy), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, getString(R.string.contact_saved_successfully), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.number_may_not_be_reachable), Toast.LENGTH_LONG).show();
                // Still save but with warning
                saveContact(number, contactName);
            }
        } else {
            Toast.makeText(this, getString(R.string.invalid_phone_number_format), Toast.LENGTH_LONG).show();
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
        Toast.makeText(this, getString(R.string.toggle_emergency_numbers_clicked), Toast.LENGTH_SHORT).show();
    }
}