package com.example.bilawoga;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.bilawoga.utils.SecureStorageManager;
import com.example.bilawoga.utils.TermsOfUseManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.perf.FirebasePerformance;
import android.os.Bundle;

public class AnalyticsConsentManager {
    private static final String TAG = "AnalyticsConsentManager";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";

    public static void applyConsent(Context context) {
        try {
            if (context == null) {
                Log.e(TAG, "Context is null - cannot apply consent");
                return;
            }
            
            // Initialize Firebase if not already (may fail if google-services.json is missing)
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context);
                }
            } catch (Exception fe) {
                Log.w(TAG, "Firebase initialization failed (may be missing google-services.json): " + fe.getMessage());
                // Continue without Firebase - app should still work
            }

            boolean allowed = isTelemetryAllowed(context);
            setCollectionStates(allowed);
            Log.d(TAG, "Applied consent. Telemetry allowed=" + allowed);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply consent: " + e.getMessage(), e);
            // Don't crash - continue without analytics
        }
    }

    public static void setConsent(Context context, boolean enabled) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            if (prefs != null) {
                prefs.edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled).apply();
            }
            setCollectionStates(enabled);
            Log.d(TAG, "Telemetry consent set to " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set consent: " + e.getMessage());
        }
    }

    public static boolean isTelemetryAllowed(Context context) {
        try {
            if (context == null) return false;
            
            // Require explicit acceptance of both privacy policy and terms
            boolean policyOk = false;
            try {
                // Try to check if methods exist, fallback if they don't
                policyOk = TermsOfUseManager.hasAcceptedPrivacyPolicy(context)
                        && TermsOfUseManager.hasAcceptedTermsOfUse(context);
            } catch (NoSuchMethodError | Exception e) {
                // Methods might not exist - use hasAcceptedTerms as fallback
                try {
                    policyOk = TermsOfUseManager.hasAcceptedTerms(context);
                } catch (Exception e2) {
                    Log.w(TAG, "Could not check terms acceptance: " + e2.getMessage());
                    policyOk = false; // Default to false if can't check
                }
            }
            
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            boolean toggle = prefs != null && prefs.getBoolean(KEY_TELEMETRY_ENABLED, false);
            return policyOk && toggle;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read consent: " + e.getMessage(), e);
            return false;
        }
    }

    private static void setCollectionStates(boolean enabled) {
        try {
            // Use application context-safe instance toggles
            FirebaseAnalytics.getInstance(FirebaseApp.getInstance().getApplicationContext())
                    .setAnalyticsCollectionEnabled(enabled);
        } catch (Throwable ignored) { }
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled);
        } catch (Throwable ignored) { }
        try {
            FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled);
        } catch (Throwable ignored) { }
    }

    // Helper: log analytics event only if allowed
    public static void logEvent(Context context, String name, Bundle params) {
        try {
            if (!isTelemetryAllowed(context)) return;
            FirebaseAnalytics fa = FirebaseAnalytics.getInstance(context.getApplicationContext());
            if (params == null) params = new Bundle();
            fa.logEvent(name, params);
        } catch (Throwable t) {
            Log.w(TAG, "logEvent failed: " + t.getMessage());
        }
    }

    // Helper: set user property only if allowed
    public static void setUserProperty(Context context, String key, String value) {
        try {
            if (!isTelemetryAllowed(context)) return;
            FirebaseAnalytics fa = FirebaseAnalytics.getInstance(context.getApplicationContext());
            fa.setUserProperty(key, value);
        } catch (Throwable t) {
            Log.w(TAG, "setUserProperty failed: " + t.getMessage());
        }
    }
}
