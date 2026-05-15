package com.example.bilawoga;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.speech.tts.TextToSpeech;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bilawoga.safety.R;
import com.google.android.material.button.MaterialButton;
import com.example.bilawoga.utils.OnboardingManager;
import com.example.bilawoga.utils.TermsOfUseManager;

import java.util.Locale;

public class OnboardingActivity extends AppCompatActivity {

    private OnboardingManager onboardingManager;
    private int currentStep = 0;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_PRIVACY_POLICY = 1;
    private static final int STEP_TERMS_OF_USE = 2;
    private static final int STEP_ACCEPTANCE = 3;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean pendingAutoRead = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // SECURITY: Prevent screenshots and screen recording
        com.example.bilawoga.utils.ScreenSecurityManager.preventScreenshots(this);
        
        onboardingManager = new OnboardingManager(this);

        // Check if user has already completed onboarding
        if (!onboardingManager.isNewUser()) {
            startMainActivity();
            return;
        }

        // Initialize TTS defaults and engine for first launch
        com.example.bilawoga.utils.TTSLanguageManager.initDefaultOnFirstLaunch(this);

        // Set app locale based on selected language
        java.util.Locale selectedLocale = com.example.bilawoga.utils.TTSLanguageManager.getSelectedLocale(this);
        java.util.Locale.setDefault(selectedLocale);
        android.content.res.Configuration config = getResources().getConfiguration();
        config.setLocale(selectedLocale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        // Pre-initialize TTS early to reduce delay
        setupTextToSpeech();

        // IMMEDIATE TTS PREFERENCE POP-UP: Show on welcome screen first launch
        // Ask user if they want TTS auto-talk enabled (YES/NO) - appears BEFORE welcome screen
        // This pop-up appears IMMEDIATELY on first launch, before any other content
        if (com.example.bilawoga.utils.TTSLanguageManager.shouldShowTTSPreferenceDialog(this)) {
            // Show TTS preference dialog first - it will show welcome screen after user chooses
            showTTSPreferenceDialog();
        } else {
            // If preference already asked, show welcome screen directly
            showWelcomeScreen();
        }
    }

    private void showWelcomeScreen() {
        currentStep = STEP_WELCOME;

        // Inflate the welcome layout
        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_welcome_onboarding, null);

        // Create the alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();

        // Set dialog background to transparent and ensure proper sizing
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Set dialog window size to ensure button is visible
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            boolean isLandscape = screenWidth > screenHeight;

            // Set dialog window size - use original sizing
            params.width = (int) (screenWidth * 0.92);
            params.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
            // Ensure dialog doesn't extend beyond screen
            params.gravity = android.view.Gravity.CENTER;
            dialog.getWindow().setAttributes(params);
            // Add padding to ensure button is visible
            dialog.getWindow().setLayout(
                params.width,
                params.height
            );
            // Add bottom margin to ensure button is not cut off
            android.view.ViewGroup.MarginLayoutParams marginParams = (android.view.ViewGroup.MarginLayoutParams) dialogView.getLayoutParams();
            if (marginParams != null) {
                marginParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
            }
        }

        // Initialize views
        View startButton = dialogView.findViewById(R.id.btnStartOnboarding);
        MaterialButton restoreButton = dialogView.findViewById(R.id.btnRestoreNow);
        android.widget.Switch switchAutoRead = dialogView.findViewById(R.id.switchAutoReadLaunch);
        com.google.android.material.textfield.MaterialAutoCompleteTextView languageDropdown = dialogView.findViewById(R.id.languageDropdown);

        // Set localized text for welcome screen elements
        updateWelcomeScreenText(dialogView);

        // Show Restore button if a cloud backup exists
        try {
            com.google.firebase.installations.FirebaseInstallations.getInstance().getId()
                .addOnSuccessListener(fid -> {
                    if (fid != null && !fid.isEmpty()) {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("backups").document(fid).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    restoreButton.setVisibility(View.VISIBLE);
                                }
                            });
                    }
                });
        } catch (Throwable ignore) {}

        // Set click listeners
        startButton.setOnClickListener(v -> {
            dialog.dismiss();
            showPrivacyPolicyScreen();
        });

        // Ensure button is visible after dialog is shown (especially after rotation)
        dialog.setOnShowListener(dialogInterface -> {
            // Post to ensure layout is complete
            dialogView.post(() -> {
                View buttonArea = dialogView.findViewById(R.id.buttonArea);
                View scrollContent = dialogView.findViewById(R.id.scrollContent);
                // Use the existing startButton variable declared above, don't redeclare

                if (buttonArea != null && scrollContent != null && startButton != null) {
                    // Ensure button area is visible and at the front
                    buttonArea.setVisibility(View.VISIBLE);
                    buttonArea.bringToFront();
                    startButton.setVisibility(View.VISIBLE);

                    // Use ViewTreeObserver to ensure button is visible after layout
                    android.view.ViewTreeObserver observer = dialogView.getViewTreeObserver();
                    observer.addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            // Remove listener to avoid multiple calls
                            dialogView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                            // Scroll to ensure button is visible
                            if (scrollContent instanceof android.widget.ScrollView) {
                                ((android.widget.ScrollView) scrollContent).fullScroll(android.view.View.FOCUS_DOWN);
                            } else if (scrollContent instanceof androidx.core.widget.NestedScrollView) {
                                ((androidx.core.widget.NestedScrollView) scrollContent).fullScroll(android.view.View.FOCUS_DOWN);
                            }

                            // Force button to be visible
                            int[] location = new int[2];
                            startButton.getLocationOnScreen(location);
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;

                            // If button is below visible area, scroll to it
                            if (location[1] + startButton.getHeight() > screenHeight) {
                                if (scrollContent instanceof androidx.core.widget.NestedScrollView) {
                                    ((androidx.core.widget.NestedScrollView) scrollContent).smoothScrollTo(0, startButton.getBottom());
                                }
                            }
                        }
                    });
                }
            });
        });

        restoreButton.setOnClickListener(v -> {
            // MANUAL RESTORE: Try to restore data using user ID
            try {
                // Get user ID (persists across logout/login on same device)
                String userId = com.example.bilawoga.utils.UserIdentityManager.getOrCreateUserId(this);
                android.util.Log.d("OnboardingActivity", "Manual restore attempt for user: " + com.example.bilawoga.utils.UserIdentityManager.maskUserId(userId));

                com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();

                // Try restore using user ID first (more reliable)
                db.collection("backups").document(userId).get()
                    .addOnSuccessListener(doc -> {
                        android.content.SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
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
                                android.widget.Toast.makeText(this, getString(R.string.restore_complete), android.widget.Toast.LENGTH_SHORT).show();
                                android.util.Log.d("OnboardingActivity", "Manual restore successful: Data restored from backup");
                                dialog.dismiss();
                                startMainActivity();
                            } else {
                                android.widget.Toast.makeText(this, "Backup found but from different device. Cannot restore for security.", android.widget.Toast.LENGTH_LONG).show();
                                android.util.Log.w("OnboardingActivity", "Device hash mismatch - backup from different device");
                            }
                        } else {
                            // Fallback: Try FID-based restore (legacy)
                            android.util.Log.d("OnboardingActivity", "User ID restore failed, trying FID-based restore");
                            try {
                                com.google.firebase.installations.FirebaseInstallations.getInstance().getId()
                                    .addOnSuccessListener(fid -> {
                                        if (fid != null && !fid.isEmpty()) {
                                            db.collection("backups").document(fid).get()
                                                .addOnSuccessListener(doc2 -> {
                                                    if (doc2.exists() && prefs != null) {
                                                        android.content.SharedPreferences.Editor ed = prefs.edit();
                                                        String u = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc2.get("username"));
                                                        String e1 = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc2.get("enum1"));
                                                        String e2 = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc2.get("enum2"));
                                                        String it = com.example.bilawoga.utils.CloudBackupCrypto.tryDecryptString(doc2.get("incident_type"));
                                                        if (u != null) ed.putString("USERNAME", u);
                                                        if (e1 != null) ed.putString("ENUM_1", e1);
                                                        if (e2 != null) ed.putString("ENUM_2", e2);
                                                        if (it != null) ed.putString("INCIDENT_TYPE", it);
                                                        ed.apply();
                                                        android.widget.Toast.makeText(this, getString(R.string.restore_complete), android.widget.Toast.LENGTH_SHORT).show();
                                                        dialog.dismiss();
                                                        startMainActivity();
                                                    } else {
                                                        android.widget.Toast.makeText(this, getString(R.string.no_backup_found), android.widget.Toast.LENGTH_SHORT).show();
                                                    }
                                                })
                                                .addOnFailureListener(e -> android.widget.Toast.makeText(this, getString(R.string.restore_failed), android.widget.Toast.LENGTH_SHORT).show());
                                        } else {
                                            android.widget.Toast.makeText(this, getString(R.string.no_backup_found), android.widget.Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> android.widget.Toast.makeText(this, getString(R.string.restore_failed), android.widget.Toast.LENGTH_SHORT).show());
                            } catch (Throwable t2) {
                                android.widget.Toast.makeText(this, getString(R.string.restore_failed), android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("OnboardingActivity", "Restore failed: " + e.getMessage());
                        android.widget.Toast.makeText(this, getString(R.string.restore_failed), android.widget.Toast.LENGTH_SHORT).show();
                    });
            } catch (Throwable t) {
                android.util.Log.e("OnboardingActivity", "Restore error: " + t.getMessage());
                android.widget.Toast.makeText(this, getString(R.string.restore_failed), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        // Policy quick links removed on welcome screen; users proceed via Start
        
        // Initialize switch state and listener
        if (switchAutoRead != null) {
            boolean autoPref = com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this);
            switchAutoRead.setChecked(autoPref);
            switchAutoRead.setOnCheckedChangeListener((button, checked) -> {
                com.example.bilawoga.utils.TTSLanguageManager.setAutoReadEnabled(this, checked);
                if (checked) {
                    // Update TTS language when enabling
                    updateTTSLanguage();
                    speakWelcomeDialog();
                } else {
                    // Stop TTS immediately when disabled
                    if (tts != null) {
                        tts.stop();
                    }
                }
            });
        }

        // Initialize language dropdown
        if (languageDropdown != null) {
            java.util.List<String> langs = java.util.Arrays.asList(
                getString(R.string.swahili), 
                getString(R.string.english)
            );
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this, R.layout.spinner_dropdown_item, langs);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
            languageDropdown.setAdapter(adapter);
            languageDropdown.setText(isSw() ? getString(R.string.swahili) : getString(R.string.english), false);
            languageDropdown.setOnItemClickListener((parent, view, position, id) -> {
                boolean wantSw = position == 0;
                boolean curSw = isSw();
                if (wantSw != curSw) {
                    com.example.bilawoga.utils.TTSLanguageManager.toggleLanguage(OnboardingActivity.this);
                    
                    // Update app locale to load correct string resources
                    java.util.Locale newLocale = wantSw ? new java.util.Locale("sw", "KE") : java.util.Locale.ENGLISH;
                    java.util.Locale.setDefault(newLocale);
                    android.content.res.Configuration config = getResources().getConfiguration();
                    config.setLocale(newLocale);
                    getResources().updateConfiguration(config, getResources().getDisplayMetrics());
                    
                    // Update TTS language immediately
                    updateTTSLanguage();
                    
                    // Update welcome screen text if we're on welcome screen
                    if (currentStep == STEP_WELCOME) {
                        // Small delay to ensure locale change takes effect
                        final boolean finalWantSw = wantSw; // Make final for lambda
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            updateWelcomeScreenText(dialogView);
                            // Force button text update with explicit locale-aware string
                            TextView startBtn = dialogView.findViewById(R.id.btnStartOnboarding);
                            if (startBtn != null) {
                                // Get string with current locale
                                String btnText = getString(R.string.start);
                                // Fallback if needed
                                if (btnText == null || btnText.trim().isEmpty()) {
                                    btnText = finalWantSw ? "ANZA" : "START";
                                }
                                final String finalBtnText = btnText; // Make final for inner lambda
                                startBtn.setText(finalBtnText);
                                startBtn.setAllCaps(false);
                                startBtn.setTextColor(0xFFFFFFFF);
                                startBtn.setTextSize(18);
                                startBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                                startBtn.setVisibility(View.VISIBLE);
                                startBtn.setAlpha(1.0f);
                                startBtn.setIncludeFontPadding(false);
                                // Set compact padding and size to fit text
                                int paddingPx = (int) (28 * getResources().getDisplayMetrics().density);
                                int paddingVertPx = (int) (12 * getResources().getDisplayMetrics().density);
                                startBtn.setPadding(paddingPx, paddingVertPx, paddingPx, paddingVertPx);
                                startBtn.setMinWidth((int) (160 * getResources().getDisplayMetrics().density));
                                startBtn.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
                                // Force refresh
                                startBtn.post(() -> {
                                    startBtn.setText(finalBtnText);
                                    startBtn.invalidate();
                                    startBtn.requestLayout();
                                });
                            }
                        }, 100);
                    } else if (currentStep == STEP_ACCEPTANCE) {
                        // Update acceptance screen text if we're on acceptance screen
                        final boolean finalWantSw = wantSw; // Make final for lambda
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            // Find the acceptance dialog view - need to get it from the current dialog
                            // Since we don't have direct access, we'll need to update when dialog is shown
                            // This will be handled by re-showing the screen
                            showAcceptanceScreen();
                        }, 100);
                    }
                    
                    // Re-speak current step in new language if TTS is enabled
                    if (com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(OnboardingActivity.this)) {
                        // Stop current speech first
                        if (tts != null) {
                            tts.stop();
                        }
                        // Wait a moment then speak in new language
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            speakCurrentStep();
                        }, 300);
                    }
                }
            });
        }

        // Show the dialog
        dialog.show();

        // Auto-read welcome if enabled (will trigger when TTS is ready via setupTextToSpeech)
        // The speakWelcomeDialog will be called automatically when TTS initializes
        if (com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) {
            if (ttsReady) {
                // TTS is already ready, speak immediately
                speakWelcomeDialog();
            } else {
                // TTS not ready yet, mark as pending
                pendingAutoRead = true;
            }
        }
    }

    private void showPrivacyPolicyScreen() {
        currentStep = STEP_PRIVACY_POLICY;
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_policy_onboarding, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        final AlertDialog dialog = builder.create();
        
        // Set dialog window properties
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.95), // 95% of screen width
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        // Initialize views
        TextView titleText = dialogView.findViewById(R.id.policyTitle);
        TextView contentText = dialogView.findViewById(R.id.policyContent);
        MaterialButton continueButton = dialogView.findViewById(R.id.btnContinue);
        MaterialButton backButton = dialogView.findViewById(R.id.btnBack);
        
        // Set content
        titleText.setText(getString(R.string.privacy_policy));
        TextView readCarefully = dialogView.findViewById(R.id.readCarefully);
        if (readCarefully != null) {
            readCarefully.setText(getString(R.string.please_read_carefully));
        }
        contentText.setText(Html.fromHtml(getString(R.string.privacy_policy_summary), Html.FROM_HTML_MODE_COMPACT));
        
        // Update progress indicator (Step 1 of 3)
        updateProgressIndicator(dialogView, 1);
        
        // Set button text
        if (continueButton != null) continueButton.setText(getString(R.string.i_accept_continue));
        if (backButton != null) backButton.setText(getString(R.string.back));
        
        // Set button click listeners
        continueButton.setOnClickListener(v -> {
            dialog.dismiss();
            showTermsOfUseScreen();
        });
        
        backButton.setOnClickListener(v -> {
            dialog.dismiss();
            showWelcomeScreen();
        });
        
        // Show the dialog
        dialog.show();
        
        // Auto-read Privacy Policy if enabled
        if (com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) {
            if (ttsReady) {
                String title = isSw() ? "Sera ya Faragha" : "Privacy Policy";
                speakPolicySummary(title, getString(R.string.privacy_policy_summary));
            } else {
                pendingAutoRead = true;
            }
        }
    }

    private void showTermsOfUseScreen() {
        currentStep = STEP_TERMS_OF_USE;
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_policy_onboarding, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        final AlertDialog dialog = builder.create();
        
        // Set dialog window properties
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.95), // 95% of screen width
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        // Initialize views
        TextView titleText = dialogView.findViewById(R.id.policyTitle);
        TextView contentText = dialogView.findViewById(R.id.policyContent);
        MaterialButton continueButton = dialogView.findViewById(R.id.btnContinue);
        MaterialButton backButton = dialogView.findViewById(R.id.btnBack);
        
        // Set content
        titleText.setText(getString(R.string.terms_of_use));
        TextView readCarefully = dialogView.findViewById(R.id.readCarefully);
        if (readCarefully != null) {
            readCarefully.setText(getString(R.string.please_read_carefully));
        }
        contentText.setText(Html.fromHtml(getString(R.string.terms_of_use_summary), Html.FROM_HTML_MODE_COMPACT));
        
        // Update progress indicator (Step 2 of 3)
        updateProgressIndicator(dialogView, 2);
        
        // Set button text
        if (continueButton != null) continueButton.setText(getString(R.string.i_accept_continue));
        if (backButton != null) backButton.setText(getString(R.string.back));
        
        // Set button click listeners
        continueButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAcceptanceScreen();
        });
        
        backButton.setOnClickListener(v -> {
            dialog.dismiss();
            showPrivacyPolicyScreen();
        });
        
        // Show the dialog
        dialog.show();
        
        // Auto-read Terms of Use if enabled
        if (com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) {
            if (ttsReady) {
                String title = isSw() ? "Masharti ya Matumizi" : "Terms of Use";
                speakPolicySummary(title, getString(R.string.terms_of_use_summary));
            } else {
                pendingAutoRead = true;
            }
        }
    }

    private void showAcceptanceScreen() {
        currentStep = STEP_ACCEPTANCE;
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_acceptance_onboarding, null);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        final AlertDialog dialog = builder.create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // Set dialog to use most of screen but leave some margin
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.95), // 95% of screen width
                (int) (getResources().getDisplayMetrics().heightPixels * 0.85)  // 85% of screen height
            );
        }
        
        CheckBox privacyCheckbox = dialogView.findViewById(R.id.checkboxPrivacy);
        CheckBox termsCheckbox = dialogView.findViewById(R.id.checkboxTerms);
        CheckBox emergencyCheckbox = dialogView.findViewById(R.id.checkboxEmergency);
        MaterialButton acceptButton = dialogView.findViewById(R.id.btnAccept);
        MaterialButton backButton = dialogView.findViewById(R.id.btnBack);
        
        // Set localized text for all elements
        TextView finalStepTitle = dialogView.findViewById(R.id.finalStepTitle);
        TextView pleaseConfirmAcceptance = dialogView.findViewById(R.id.pleaseConfirmAcceptance);
        TextView step1Indicator = dialogView.findViewById(R.id.step1Indicator);
        TextView step2Indicator = dialogView.findViewById(R.id.step2Indicator);
        TextView step3Indicator = dialogView.findViewById(R.id.step3Indicator);
        TextView toCompleteSetupText = dialogView.findViewById(R.id.toCompleteSetupText);
        TextView privacyCheckboxText = dialogView.findViewById(R.id.privacyCheckboxText);
        TextView termsCheckboxText = dialogView.findViewById(R.id.termsCheckboxText);
        TextView emergencyCheckboxText = dialogView.findViewById(R.id.emergencyCheckboxText);
        TextView importantNoticeTitle = dialogView.findViewById(R.id.importantNoticeTitle);
        TextView importantNoticeText = dialogView.findViewById(R.id.importantNoticeText);
        
        if (finalStepTitle != null) finalStepTitle.setText(getString(R.string.final_step));
        if (pleaseConfirmAcceptance != null) pleaseConfirmAcceptance.setText(getString(R.string.please_confirm_acceptance));
        if (step1Indicator != null) step1Indicator.setText(getString(R.string.step_1_of_3));
        if (step2Indicator != null) step2Indicator.setText(getString(R.string.step_2_of_3));
        if (step3Indicator != null) step3Indicator.setText(getString(R.string.step_3_of_3));
        if (toCompleteSetupText != null) toCompleteSetupText.setText(getString(R.string.to_complete_setup));
        if (privacyCheckboxText != null) privacyCheckboxText.setText(getString(R.string.i_have_read_privacy_policy));
        if (termsCheckboxText != null) termsCheckboxText.setText(getString(R.string.i_have_read_terms_of_use));
        if (emergencyCheckboxText != null) emergencyCheckboxText.setText(getString(R.string.i_understand_emergency_use));
        if (importantNoticeTitle != null) importantNoticeTitle.setText(getString(R.string.important_notice));
        if (importantNoticeText != null) importantNoticeText.setText(getString(R.string.by_accepting_acknowledge));
        if (acceptButton != null) acceptButton.setText(getString(R.string.i_accept_continue));
        if (backButton != null) backButton.setText(getString(R.string.back));
        
        // Initially disable accept button
        acceptButton.setEnabled(false);
        
        // Make dialog more compact for better visibility
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.95), // 95% of screen width
                (int) (getResources().getDisplayMetrics().heightPixels * 0.75)  // 75% of screen height
            );
        }
        
        // Update accept button state based on checkboxes
        View.OnClickListener checkboxListener = v -> {
            boolean allChecked = privacyCheckbox.isChecked() && 
                               termsCheckbox.isChecked() && 
                               emergencyCheckbox.isChecked();
            acceptButton.setEnabled(allChecked);
        };
        
        privacyCheckbox.setOnClickListener(checkboxListener);
        termsCheckbox.setOnClickListener(checkboxListener);
        emergencyCheckbox.setOnClickListener(checkboxListener);
        
        acceptButton.setOnClickListener(v -> {
            // Mark policies as accepted
            TermsOfUseManager.markPolicyAccepted(this, "privacy");
            TermsOfUseManager.markPolicyAccepted(this, "terms");
            
            // Ensure SOS is enabled by default to protect users
            try {
                android.content.SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
                if (prefs != null) { prefs.edit().putBoolean("TEST_MODE", false).apply(); }
            } catch (Throwable ignore) {}

            // Complete onboarding
            onboardingManager.completeOnboarding();
            
            // Dismiss dialog and start registration activity
            dialog.dismiss();
            
            // Start Registration Activity
            Intent intent = new Intent(OnboardingActivity.this, RegisterNumberActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        
        backButton.setOnClickListener(v -> {
            dialog.dismiss();
            showTermsOfUseScreen();
        });
        
        dialog.show();

        // Auto-read acceptance instructions if enabled
        if (com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) {
            if (ttsReady) {
                speakAcceptanceInstructions();
            } else {
                pendingAutoRead = true;
            }
        }
    }
    
    private void showPolicyDialog(String title, String content) {
        View policyView = LayoutInflater.from(this).inflate(R.layout.dialog_policy, null);
        TextView policyContent = policyView.findViewById(R.id.policyContent);
        Button continueButton = policyView.findViewById(R.id.btnContinue);
        
        // Debug logging
        android.util.Log.d("OnboardingActivity", "Showing policy dialog: " + title);
        android.util.Log.d("OnboardingActivity", "Content length: " + content.length());
        
        if (policyContent != null) {
            policyContent.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT));
        }
        
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(title)
            .setView(policyView)
            .setCancelable(false)
            .create();
        
        // Set click listener for the continue button
        if (continueButton != null) {
            continueButton.setOnClickListener(v -> dialog.dismiss());
        }
        
        // Set dialog window properties
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.95), // 95% of screen width
                (int) (getResources().getDisplayMetrics().heightPixels * 0.85)  // 85% of screen height
            );
        }
        
        dialog.show();
    }
    
    /**
     * Update the progress indicator based on current step
     */
    private void updateProgressIndicator(View dialogView, int currentStep) {
        try {
            // Try to find by ID first (for dialog_policy_onboarding.xml)
            TextView step1 = dialogView.findViewById(R.id.step1Indicator);
            TextView step2 = dialogView.findViewById(R.id.step2Indicator);
            TextView step3 = dialogView.findViewById(R.id.step3Indicator);
            
            // If not found by ID, try to find by container (for dialog_acceptance_onboarding.xml)
            if (step1 == null || step2 == null || step3 == null) {
                LinearLayout progressContainer = null;
                if (dialogView instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) dialogView;
                    for (int i = 0; i < viewGroup.getChildCount(); i++) {
                        View child = viewGroup.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            LinearLayout linearLayout = (LinearLayout) child;
                            // Check if this is the progress indicator (has 3 TextViews)
                            if (linearLayout.getChildCount() == 3) {
                                progressContainer = linearLayout;
                                break;
                            }
                        }
                    }
                }
                
                if (progressContainer != null) {
                    step1 = (TextView) progressContainer.getChildAt(0);
                    step2 = (TextView) progressContainer.getChildAt(1);
                    step3 = (TextView) progressContainer.getChildAt(2);
                }
            }
            
            if (step1 != null && step2 != null && step3 != null) {
                // Set localized text
                step1.setText(getString(R.string.step_1_of_3));
                step2.setText(getString(R.string.step_2_of_3));
                step3.setText(getString(R.string.step_3_of_3));
                
                // Update colors based on current step
                if (currentStep == 1) {
                    step1.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                    step1.setTypeface(null, android.graphics.Typeface.BOLD);
                    step2.setTextColor(ContextCompat.getColor(this, R.color.gray_700));
                    step2.setTypeface(null, android.graphics.Typeface.NORMAL);
                    step3.setTextColor(ContextCompat.getColor(this, R.color.gray_700));
                    step3.setTypeface(null, android.graphics.Typeface.NORMAL);
                } else if (currentStep == 2) {
                    step1.setTextColor(ContextCompat.getColor(this, R.color.gray_700));
                    step1.setTypeface(null, android.graphics.Typeface.NORMAL);
                    step2.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                    step2.setTypeface(null, android.graphics.Typeface.BOLD);
                    step3.setTextColor(ContextCompat.getColor(this, R.color.gray_700));
                    step3.setTypeface(null, android.graphics.Typeface.NORMAL);
                } else if (currentStep == 3) {
                    step1.setTextColor(ContextCompat.getColor(this, R.color.gray_700));
                    step1.setTypeface(null, android.graphics.Typeface.NORMAL);
                    step2.setTextColor(ContextCompat.getColor(this, R.color.gray_700));
                    step2.setTypeface(null, android.graphics.Typeface.NORMAL);
                    step3.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                    step3.setTypeface(null, android.graphics.Typeface.BOLD);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("OnboardingActivity", "Error updating progress indicator: " + e.getMessage());
        }
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Show TTS preference dialog immediately on welcome screen first launch
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
                    android.util.Log.d("OnboardingActivity", "User enabled TTS auto-talk");
                    d.dismiss();
                    // Now show welcome screen after user makes choice
                    showWelcomeScreen();
                })
                .setNegativeButton(noButton, (d, which) -> {
                    // User does not want TTS enabled
                    com.example.bilawoga.utils.TTSLanguageManager.setAutoReadEnabled(this, false);
                    com.example.bilawoga.utils.TTSLanguageManager.markTTSPreferenceAsked(this);
                    android.util.Log.d("OnboardingActivity", "User disabled TTS auto-talk");
                    d.dismiss();
                    // Now show welcome screen after user makes choice
                    showWelcomeScreen();
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
                android.util.Log.e("OnboardingActivity", "Error setting dialog text colors: " + e.getMessage());
            }
            
            android.util.Log.d("OnboardingActivity", "TTS preference dialog shown on welcome screen");
        } catch (Exception e) {
            android.util.Log.e("OnboardingActivity", "Error showing TTS preference dialog: " + e.getMessage());
            // Mark as asked even if error to prevent infinite loop
            com.example.bilawoga.utils.TTSLanguageManager.markTTSPreferenceAsked(this);
            // Show welcome screen even if error
            showWelcomeScreen();
        }
    }
    
    private void showTTSInfoDialog() {
        try {
            androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Text-to-Speech (TTS) Feature")
                .setMessage("BilaWoga can read text aloud to help you use the app.\n\n" +
                           "This feature is OPTIONAL and can be turned on or off anytime.\n\n" +
                           "To turn it on/off:\n" +
                           "1. Go to Settings\n" +
                           "2. Find 'Text-to-Speech' or 'Accessibility' options\n" +
                           "3. Toggle 'Auto-read' on or off\n\n" +
                           "You can also use the accessibility button (floating button) to read specific screens.\n\n" +
                           "Click 'Got it' to continue.")
                .setPositiveButton("Got it", (d, which) -> {
                    com.example.bilawoga.utils.TTSLanguageManager.markTTSInfoShown(this);
                    d.dismiss();
                })
                .setCancelable(false) // Make it non-cancelable so user must acknowledge
                .create();
            
            // Show dialog immediately
            dialog.show();
            
            android.util.Log.d("OnboardingActivity", "TTS info dialog shown");
        } catch (Exception e) {
            android.util.Log.e("OnboardingActivity", "Error showing TTS info dialog: " + e.getMessage());
            // Mark as shown even if error to prevent infinite loop
            com.example.bilawoga.utils.TTSLanguageManager.markTTSInfoShown(this);
        }
    }
    
    private void setupTextToSpeech() {
        // Initialize TTS immediately to reduce delay - must be on main thread
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                updateTTSLanguage();
                // Auto-read if enabled (now defaults to false - user must opt-in)
                if (com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this) || pendingAutoRead) {
                    pendingAutoRead = false;
                    // Small delay to ensure UI is ready
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    speakCurrentStep();
                    }, 500);
                }
            }
        });
    }
    
    private void updateTTSLanguage() {
        if (tts == null) return;
        java.util.Locale selected = com.example.bilawoga.utils.TTSLanguageManager.getSelectedLocale(this);
        
        // Handle null locale - use default
        if (selected == null) {
            selected = new java.util.Locale("sw", "KE"); // Default to Swahili
            android.util.Log.w("OnboardingActivity", "TTSLanguageManager returned null locale, using default: " + selected);
        }
        
        // Try to set the language
        int result = tts.setLanguage(selected);
        
        // If language not available, try alternative locales
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            boolean isSwahili = selected != null && "sw".equalsIgnoreCase(selected.getLanguage());
            if (isSwahili) {
                // Try other Swahili locales
                java.util.Locale[] swahiliLocales = {
                    new Locale("sw", "KE"), // Kenya Swahili
                    new Locale("sw", "TZ"), // Tanzania Swahili
                    new Locale("sw"),       // Generic Swahili
                };
                for (java.util.Locale loc : swahiliLocales) {
                    if (tts.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) {
                        tts.setLanguage(loc);
                        android.util.Log.d("OnboardingActivity", "Swahili TTS set to: " + loc);
                        break;
                    }
                }
            }
            
            // Prompt user to install TTS data if needed
            try {
                java.util.Locale currentTTSLang = tts.getLanguage();
                if (currentTTSLang != null && "en".equals(currentTTSLang.getLanguage()) && isSwahili) {
                try { 
                    startActivity(new android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)); 
                } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                android.util.Log.e("OnboardingActivity", "Error checking TTS language: " + e.getMessage());
            }
        } else {
            android.util.Log.d("OnboardingActivity", "TTS language set successfully: " + selected);
        }
        
        tts.setSpeechRate(0.9f); // Slightly slower for better clarity
        tts.setPitch(1.0f);
    }

    private void speakCurrentStep() {
        if (!ttsReady || tts == null) return;
        // Update TTS language first
        updateTTSLanguage();
        // Small delay to ensure language is set
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
        switch (currentStep) {
            case STEP_WELCOME:
                speakWelcomeDialog();
                break;
            case STEP_PRIVACY_POLICY:
                    String privacyTitle = isSw() ? "Sera ya Faragha" : "Privacy Policy";
                    speakPolicySummary(privacyTitle, getString(R.string.privacy_policy_summary));
                break;
            case STEP_TERMS_OF_USE:
                    String termsTitle = isSw() ? "Masharti ya Matumizi" : "Terms of Use";
                    speakPolicySummary(termsTitle, getString(R.string.terms_of_use_summary));
                break;
            case STEP_ACCEPTANCE:
                speakAcceptanceInstructions();
                break;
        }
        }, 200);
    }

    private boolean isSw() {
        java.util.Locale loc = com.example.bilawoga.utils.TTSLanguageManager.getSelectedLocale(this);
        return loc != null && "sw".equalsIgnoreCase(loc.getLanguage());
    }

    private void speakWelcomeDialog() {
        if (!com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) return;
        if (!ttsReady) { pendingAutoRead = true; return; }
        if (tts == null) return;
        
        // Ensure TTS language is correct before speaking
        updateTTSLanguage();
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (isSw()) {
            lines.add("Karibu BilaWoga. Tuanze kuweka usalama wako haraka.");
            lines.add("Unaweza kupitia sera ya faragha na masharti ya matumizi kabla ya kuendelea.");
            lines.add("Bofya kitufe cha Anza ili kusonga mbele.");
        } else {
            lines.add("Welcome to BilaWoga. Let's set up your safety quickly.");
            lines.add("You can review the privacy policy and terms of use before continuing.");
            lines.add("Press the Start button to proceed.");
        }
        speakQueued(lines);
    }

    private void speakPolicySummary(String title, String content) {
        if (!com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) return;
        if (!ttsReady) { 
            pendingAutoRead = true; 
            return; 
        }
        if (tts == null) return;
        
        // Ensure TTS language is correct before speaking
        updateTTSLanguage();
        
        // Strip HTML tags and clean up the content
        String cleanContent = content == null ? "" : content;
        // Remove HTML tags
        cleanContent = cleanContent.replaceAll("<[^>]+>", " ");
        // Replace multiple spaces with single space
        cleanContent = cleanContent.replaceAll("\\s+", " ");
        // Replace newlines with spaces
        cleanContent = cleanContent.replaceAll("\n", " ").trim();
        
        // Split into chunks if too long (max 400 chars per chunk for better TTS)
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (isSw()) {
            lines.add(title + ".");
            if (cleanContent.length() > 400) {
                // Split into readable chunks at sentence boundaries
                int start = 0;
                while (start < cleanContent.length()) {
                    int end = Math.min(start + 400, cleanContent.length());
                    if (end < cleanContent.length()) {
                        // Try to break at sentence end (period, exclamation, question mark)
                        int lastPeriod = Math.max(
                            Math.max(cleanContent.lastIndexOf(".", end),
                                    cleanContent.lastIndexOf("!", end)),
                            cleanContent.lastIndexOf("?", end));
                        if (lastPeriod > start + 150) {
                            end = lastPeriod + 1;
                        }
                    }
                    String chunk = cleanContent.substring(start, end).trim();
                    if (!chunk.isEmpty()) {
                        lines.add(chunk);
                    }
                    start = end;
                }
            } else {
                if (!cleanContent.isEmpty()) {
                    lines.add(cleanContent);
                }
            }
            lines.add("Tumia vitufe vya Nyuma au Endelea kuendelea.");
        } else {
            lines.add(title + ".");
            if (cleanContent.length() > 400) {
                // Split into readable chunks at sentence boundaries
                int start = 0;
                while (start < cleanContent.length()) {
                    int end = Math.min(start + 400, cleanContent.length());
                    if (end < cleanContent.length()) {
                        // Try to break at sentence end
                        int lastPeriod = Math.max(
                            Math.max(cleanContent.lastIndexOf(".", end),
                                    cleanContent.lastIndexOf("!", end)),
                            cleanContent.lastIndexOf("?", end));
                        if (lastPeriod > start + 150) {
                            end = lastPeriod + 1;
                        }
                    }
                    String chunk = cleanContent.substring(start, end).trim();
                    if (!chunk.isEmpty()) {
                        lines.add(chunk);
                    }
                    start = end;
                }
            } else {
                if (!cleanContent.isEmpty()) {
                    lines.add(cleanContent);
                }
            }
            lines.add("Use the Back or Continue buttons to navigate.");
        }
        speakQueued(lines);
    }

    private void speakAcceptanceInstructions() {
        if (!com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) return;
        if (!ttsReady) { pendingAutoRead = true; return; }
        if (tts == null) return;
        
        // Ensure TTS language is correct before speaking
        updateTTSLanguage();
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (isSw()) {
            lines.add("Hatua ya mwisho. Tafadhali weka alama kwenye visanduku vitatu kuthibitisha sera ya faragha, masharti ya matumizi, na matumizi ya dharura.");
            lines.add("Kisha bonyeza kitufe cha Kubali ili kuendelea na usajili wa mawasiliano ya dharura.");
        } else {
            lines.add("Final step. Please check the three boxes to confirm privacy policy, terms of use, and emergency usage.");
            lines.add("Then press the Accept button to continue to emergency contact registration.");
        }
        speakQueued(lines);
    }

    private void updateWelcomeScreenText(View dialogView) {
        TextView welcomeTitle = dialogView.findViewById(R.id.welcomeTitle);
        TextView subtitle = dialogView.findViewById(R.id.subtitle);
        TextView appDescription = dialogView.findViewById(R.id.appDescription);
        TextView keyFeaturesTitle = dialogView.findViewById(R.id.keyFeaturesTitle);
        TextView keyFeaturesList = dialogView.findViewById(R.id.keyFeaturesList);
        TextView setupProcessTitle = dialogView.findViewById(R.id.setupProcessTitle);
        TextView setupSteps = dialogView.findViewById(R.id.setupSteps);
        TextView startButton = dialogView.findViewById(R.id.btnStartOnboarding);
        TextView ttsLabel = dialogView.findViewById(R.id.ttsLabel);
        MaterialButton restoreButton = dialogView.findViewById(R.id.btnRestoreNow);
        
        if (welcomeTitle != null) welcomeTitle.setText(getString(R.string.welcome_to_bilawoga_title));
        if (subtitle != null) subtitle.setText(getString(R.string.your_personal_emergency_protection_system));
        if (appDescription != null) appDescription.setText(getString(R.string.app_description));
        if (keyFeaturesTitle != null) keyFeaturesTitle.setText(getString(R.string.key_features));
        if (keyFeaturesList != null) keyFeaturesList.setText(getString(R.string.key_features_list));
        if (setupProcessTitle != null) setupProcessTitle.setText(getString(R.string.setup_process));
        if (setupSteps != null) setupSteps.setText(getString(R.string.setup_steps));
        if (startButton != null) {
            // Force get the correct string resource for current locale
            String startText = getString(R.string.start);
            // Fallback if string is null or empty
            if (startText == null || startText.trim().isEmpty()) {
                startText = isSw() ? "ANZA" : "START";
            }
            // Explicitly set the text
            startButton.setText(startText);
            startButton.setAllCaps(false);
            startButton.setTextColor(0xFFFFFFFF); // Explicit white color
            // Use smaller text size that fits both START and ANZA
            startButton.setTextSize(18); // 18sp - fits both START and ANZA
            startButton.setTypeface(null, android.graphics.Typeface.BOLD);
            startButton.setVisibility(View.VISIBLE);
            // Ensure text is visible and not cut off
            startButton.setAlpha(1.0f);
            startButton.setIncludeFontPadding(false); // Prevent text cutoff
            // Set smaller padding to fit text - compact size for both START and ANZA
            int paddingPx = (int) (28 * getResources().getDisplayMetrics().density);
            int paddingVertPx = (int) (12 * getResources().getDisplayMetrics().density);
            startButton.setPadding(paddingPx, paddingVertPx, paddingPx, paddingVertPx);
            // Set compact button size that fits both START and ANZA
            startButton.setMinWidth((int) (160 * getResources().getDisplayMetrics().density));
            startButton.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
            startButton.setMaxLines(1);
            startButton.setEllipsize(null);
            // Force text to be visible - clear any previous state
            final String finalStartText = startText; // Make final for lambda
            startButton.post(() -> {
                startButton.setText(finalStartText);
                startButton.invalidate();
                startButton.requestLayout();
            });
        }
        if (ttsLabel != null) ttsLabel.setText(getString(R.string.tts));
        if (restoreButton != null) restoreButton.setText(getString(R.string.restore_from_cloud));
    }

    private void speakQueued(java.util.List<String> parts) {
        // Double-check TTS is enabled before speaking
        if (!com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) {
            if (tts != null) {
                tts.stop();
            }
            return;
        }
        if (tts == null || parts == null || parts.isEmpty()) return;
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            // Check again before each utterance
            if (!com.example.bilawoga.utils.TTSLanguageManager.isAutoReadEnabled(this)) {
                tts.stop();
                break;
            }
            int mode = first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            tts.speak(p, mode, null, java.util.UUID.randomUUID().toString());
            tts.playSilentUtterance(150, TextToSpeech.QUEUE_ADD, java.util.UUID.randomUUID().toString());
            first = false;
        }
    }
}




