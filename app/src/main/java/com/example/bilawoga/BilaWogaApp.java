package com.example.bilawoga;

import android.app.Application;

public class BilaWogaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Apply telemetry consent on startup (will disable by default until consent is stored)
            AnalyticsConsentManager.applyConsent(this);
        } catch (Exception e) {
            android.util.Log.e("BilaWogaApp", "Error in onCreate: " + e.getMessage(), e);
            // Don't crash - continue anyway
        }
    }
}
