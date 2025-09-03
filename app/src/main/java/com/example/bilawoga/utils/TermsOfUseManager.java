package com.example.bilawoga.utils;

import static com.example.bilawoga.utils.PolicyViewerActivity.POLICY_TYPE_PRIVACY;
import static com.example.bilawoga.utils.PolicyViewerActivity.POLICY_TYPE_TERMS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class TermsOfUseManager {
    private static final String TAG = "TermsOfUseManager";
    private static final String TERMS_PREFS = "terms_of_use_prefs";
    private static final String KEY_TERMS_ACCEPTED = "terms_accepted";
    private static final String KEY_TERMS_VERSION = "terms_version";
    private static final String CURRENT_TERMS_VERSION = "1.0";

    public static boolean hasAcceptedTerms(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            boolean accepted = prefs.getBoolean(KEY_TERMS_ACCEPTED, false);
            String version = prefs.getString(KEY_TERMS_VERSION, "");
            
            return accepted && CURRENT_TERMS_VERSION.equals(version);
        } catch (Exception e) {
            Log.e(TAG, "Error checking terms acceptance: " + e.getMessage());
            return false;
        }
    }

    public static void showTermsDialog(Activity activity, TermsAcceptanceCallback callback) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Terms of Use & Safety Guidelines")
                .setMessage(getTermsMessage())
                .setPositiveButton("I Accept", (dialog, which) -> {
                    acceptTerms(activity);
                    if (callback != null) {
                        callback.onTermsAccepted();
                    }
                })
                .setNegativeButton("Decline", (dialog, which) -> {
                    if (callback != null) {
                        callback.onTermsDeclined();
                    }
                })
                .setCancelable(false)
                .show();
    }

    public static void showAbuseReportingDialog(Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Report Misuse")
                .setMessage("If you believe this SOS alert was sent in error or is being misused, please report it.\n\n" +
                        "This helps us maintain the safety and integrity of the BilaWoga app for all users.")
                .setPositiveButton("Report Misuse", (dialog, which) -> {
                    reportAbuse(activity, "User reported misuse");
                    AbusePreventionManager.reportAbuse(activity, "Manual abuse report");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String getTermsMessage() {
        return "BILAWOGA SAFETY APP - TERMS OF USE\n\n" +
                "IMPORTANT: This app is for genuine emergency situations only.\n\n" +
                "By accepting these terms, you agree to:\n\n" +
                "1. Use the SOS feature only in real emergency situations\n" +
                "2. Not misuse the app for pranks or false alarms\n" +
                "3. Keep your emergency contacts updated and informed\n" +
                "4. Respect the safety of emergency responders\n" +
                "5. Report any misuse or abuse of the app\n\n" +
                "MISUSE CONSEQUENCES:\n" +
                "• False alarms waste emergency resources\n" +
                "• Repeated misuse may result in app restrictions\n" +
                "• Legal consequences may apply for deliberate misuse\n\n" +
                "SAFETY FEATURES:\n" +
                "• Usage limits to prevent abuse\n" +
                "• Double-shake confirmation to prevent accidents\n" +
                "• Abuse reporting system\n" +
                "• Automatic monitoring for suspicious activity\n\n" +
                "Your safety and the safety of others depend on responsible use of this app.";
    }

    private static void acceptTerms(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit()
                    .putBoolean(KEY_TERMS_ACCEPTED, true)
                    .putString(KEY_TERMS_VERSION, CURRENT_TERMS_VERSION)
                    .apply();
            
            Log.d(TAG, "Terms of use accepted");
        } catch (Exception e) {
            Log.e(TAG, "Error accepting terms: " + e.getMessage());
        }
    }

    private static void reportAbuse(Context context, String reason) {
        try {
            AbusePreventionManager.reportAbuse(context, reason);
            
            // Show confirmation
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(context)
                            .setTitle("Report Submitted")
                            .setMessage("Thank you for reporting. We take misuse seriously and will investigate this matter.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting abuse: " + e.getMessage());
        }
    }

    public static void showSafetyReminder(Context context) {
        if (context instanceof Activity) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Safety Reminder")
                    .setMessage("Remember: BilaWoga is for genuine emergencies only.\n\n" +
                            "• Use only when you need immediate help\n" +
                            "• Keep your emergency contacts informed\n" +
                            "• Report any misuse you encounter\n\n" +
                            "Your safety is our priority.")
                    .setPositiveButton("I Understand", null)
                    .show();
        }
    }

    public static void markPolicyAccepted(Context context, String policyType) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            if (POLICY_TYPE_PRIVACY.equals(policyType)) {
                prefs.edit()
                        .putBoolean("privacy_policy_accepted", true)
                        .putLong("privacy_policy_accepted_time", currentTime)
                        .apply();
                Log.d(TAG, "Privacy policy accepted");
            } else if (POLICY_TYPE_TERMS.equals(policyType)) {
                prefs.edit()
                        .putBoolean("terms_of_use_accepted", true)
                        .putLong("terms_of_use_accepted_time", currentTime)
                        .apply();
                Log.d(TAG, "Terms of use accepted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking policy accepted: " + e.getMessage());
        }
    }

    public static boolean hasAcceptedPrivacyPolicy(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            return prefs.getBoolean("privacy_policy_accepted", false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking privacy policy acceptance: " + e.getMessage());
            return false;
        }
    }

    public static boolean hasAcceptedTermsOfUse(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            return prefs.getBoolean("terms_of_use_accepted", false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking terms acceptance: " + e.getMessage());
            return false;
        }
    }

    public static void showPolicyFromMainActivity(Activity activity, String policyType) {
        Intent intent = new Intent(activity, PolicyViewerActivity.class);
        intent.putExtra(PolicyViewerActivity.EXTRA_POLICY_TYPE, policyType);
        activity.startActivity(intent);
    }

    public interface TermsAcceptanceCallback {
        void onTermsAccepted();
        void onTermsDeclined();
    }
} 