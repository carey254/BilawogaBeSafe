package com.example.bilawoga;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.example.bilawoga.utils.OnboardingManager;
import com.example.bilawoga.utils.TermsOfUseManager;

public class OnboardingActivity extends AppCompatActivity {

    private OnboardingManager onboardingManager;
    private int currentStep = 0;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_PRIVACY_POLICY = 1;
    private static final int STEP_TERMS_OF_USE = 2;
    private static final int STEP_ACCEPTANCE = 3;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        onboardingManager = new OnboardingManager(this);
        
        // Check if user has already completed onboarding
        if (!onboardingManager.isNewUser()) {
            startMainActivity();
            return;
        }
        
        // Start with welcome screen
        showWelcomeScreen();
    }

    private void showWelcomeScreen() {
        currentStep = STEP_WELCOME;
        
        // Inflate the welcome layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_welcome_onboarding, null);
        
        // Create the alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        final AlertDialog dialog = builder.create();
        
        // Set dialog background to transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // Initialize views
        MaterialButton startButton = dialogView.findViewById(R.id.btnStartOnboarding);
        TextView privacyPolicyLink = dialogView.findViewById(R.id.privacyPolicyLink);
        TextView termsLink = dialogView.findViewById(R.id.termsLink);
        MaterialButton restoreButton = dialogView.findViewById(R.id.btnRestoreNow);
        
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
        
        restoreButton.setOnClickListener(v -> {
            // Manual restore using FID
            try {
                com.google.firebase.installations.FirebaseInstallations.getInstance().getId()
                    .addOnSuccessListener(fid -> {
                        if (fid != null && !fid.isEmpty()) {
                            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                            db.collection("backups").document(fid).get()
                                .addOnSuccessListener(doc -> {
                                    android.content.SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
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
                                        android.widget.Toast.makeText(this, "Restore complete", android.widget.Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                        startMainActivity();
                                    } else {
                                        android.widget.Toast.makeText(this, "No backup found", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> android.widget.Toast.makeText(this, "Restore failed", android.widget.Toast.LENGTH_SHORT).show());
                        }
                    });
            } catch (Throwable t) {
                android.widget.Toast.makeText(this, "Restore failed", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        privacyPolicyLink.setOnClickListener(v -> {
            showPolicyDialog("Privacy Policy", getString(R.string.privacy_policy_summary));
        });
        
        termsLink.setOnClickListener(v -> {
            showPolicyDialog("Terms of Use", getString(R.string.terms_of_use_summary));
        });
        
        // Show the dialog
        dialog.show();
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
        titleText.setText("Privacy Policy");
        contentText.setText(Html.fromHtml(getString(R.string.privacy_policy_summary), Html.FROM_HTML_MODE_COMPACT));
        
        // Update progress indicator (Step 1 of 3)
        updateProgressIndicator(dialogView, 1);
        
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
        titleText.setText("Terms of Use");
        contentText.setText(Html.fromHtml(getString(R.string.terms_of_use_summary), Html.FROM_HTML_MODE_COMPACT));
        
        // Update progress indicator (Step 2 of 3)
        updateProgressIndicator(dialogView, 2);
        
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
            
            // Enable Test Mode for first-time users
            try {
                android.content.SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
                if (prefs != null) { prefs.edit().putBoolean("TEST_MODE", true).apply(); }
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
            // Find the progress indicator container
            View progressContainer = dialogView.findViewById(android.R.id.content);
            if (progressContainer != null) {
                // Find all TextViews in the progress container
                if (progressContainer instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) progressContainer;
                    for (int i = 0; i < viewGroup.getChildCount(); i++) {
                        View child = viewGroup.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            LinearLayout linearLayout = (LinearLayout) child;
                            for (int j = 0; j < linearLayout.getChildCount(); j++) {
                                View grandChild = linearLayout.getChildAt(j);
                                if (grandChild instanceof TextView) {
                                    TextView textView = (TextView) grandChild;
                                    String text = textView.getText().toString();
                                    if (text.contains("Step")) {
                                        // Update step colors based on current step
                                        if (text.contains("Step " + currentStep)) {
                                            textView.setTextColor(getResources().getColor(R.color.colorPrimary));
                                            textView.setTypeface(null, android.graphics.Typeface.BOLD);
                                        } else {
                                            textView.setTextColor(getResources().getColor(R.color.gray_700));
                                            textView.setTypeface(null, android.graphics.Typeface.NORMAL);
                                        }
                                    }
                                }
                            }
                        }
                    }
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
}




