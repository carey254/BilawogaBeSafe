package com.example.bilawoga;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.example.bilawoga.utils.OnboardingManager;

public class SplashScreen extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        new android.os.Handler().postDelayed(() -> {
            OnboardingManager onboardingManager = new OnboardingManager(this);

            // Attempt automatic restore if no local data but a cloud backup exists
            android.content.SharedPreferences prefs = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
            boolean hasLocalData = prefs != null && (
                (prefs.getString("ENUM_1", null) != null && !"NONE".equals(prefs.getString("ENUM_1", null))) ||
                (prefs.getString("ENUM_2", null) != null && !"NONE".equals(prefs.getString("ENUM_2", null)))
            );

            if (!hasLocalData) {
                try {
                    com.google.firebase.installations.FirebaseInstallations.getInstance().getId()
                        .addOnSuccessListener(fid -> {
                            if (fid != null && !fid.isEmpty()) {
                                com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
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
                                        }
                                        proceed(onboardingManager);
                                    })
                                    .addOnFailureListener(e -> proceed(onboardingManager));
                            } else {
                                proceed(onboardingManager);
                            }
                        })
                        .addOnFailureListener(e -> proceed(onboardingManager));
                } catch (Throwable t) {
                    proceed(onboardingManager);
                }
            } else {
                proceed(onboardingManager);
            }
        }, 2000); // 2 seconds
    }

    private void proceed(OnboardingManager onboardingManager) {
        if (onboardingManager.isNewUser()) {
            startActivity(new Intent(this, OnboardingActivity.class));
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}