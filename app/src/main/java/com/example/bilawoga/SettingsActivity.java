package com.example.bilawoga;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bilawoga.safety.R;
import com.example.bilawoga.utils.AIMonitoringPermission;
import com.example.bilawoga.utils.TermsOfUseManager;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private Switch aiMonitoringSwitch;
    private Switch telemetrySwitch;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // SECURITY: Prevent screenshots and screen recording
        com.example.bilawoga.utils.ScreenSecurityManager.preventScreenshots(this);
        
        setContentView(R.layout.activity_settings);

        // Set up the action bar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.settings_title);
        }

        // Initialize views
        aiMonitoringSwitch = findViewById(R.id.switch_ai_monitoring);
        telemetrySwitch = findViewById(R.id.switch_telemetry);
        
        // Set initial state
        updateSwitchState();
        updateTelemetryState();

        // Policy buttons
        View btnPrivacy = findViewById(R.id.btn_view_privacy);
        View btnTerms = findViewById(R.id.btn_view_terms);
        if (btnPrivacy != null) {
            btnPrivacy.setOnClickListener(v ->
                com.example.bilawoga.utils.TermsOfUseManager.showPolicyFromMainActivity(
                        this,
                        com.example.bilawoga.utils.PolicyViewerActivity.POLICY_TYPE_PRIVACY
                )
            );
        }
        if (btnTerms != null) {
            btnTerms.setOnClickListener(v ->
                com.example.bilawoga.utils.TermsOfUseManager.showPolicyFromMainActivity(
                        this,
                        com.example.bilawoga.utils.PolicyViewerActivity.POLICY_TYPE_TERMS
                )
            );
        }
        
        // Set switch change listener
        aiMonitoringSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Show permission dialog if enabling
                    AIMonitoringPermission.checkAndRequestPermission(SettingsActivity.this, 
                        new AIMonitoringPermission.PermissionCallback() {
                            @Override
                            public void onPermissionGranted() {
                                Log.d(TAG, "AI Monitoring enabled by user");
                                // Restart service to apply changes
                                restartService();
                            }

                            @Override
                            public void onPermissionDenied() {
                                Log.d(TAG, "User denied AI Monitoring permission");
                                // Revert switch state if permission denied
                                aiMonitoringSwitch.setChecked(false);
                            }
                        }, true);
                } else {
                    // If disabling, just update the preference
                    AIMonitoringPermission.resetPermission(SettingsActivity.this);
                    Log.d(TAG, "AI Monitoring disabled by user");
                    // Restart service to apply changes
                    restartService();
                }
            }
        });

        telemetrySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean hasPrivacy = TermsOfUseManager.hasAcceptedPrivacyPolicy(this);
            boolean hasTerms = TermsOfUseManager.hasAcceptedTermsOfUse(this);
            if (!hasPrivacy || !hasTerms) {
                Toast.makeText(this, "Accept Privacy Policy and Terms first", Toast.LENGTH_LONG).show();
                telemetrySwitch.setChecked(false);
                return;
            }
            AnalyticsConsentManager.setConsent(this, isChecked);
            Log.d(TAG, "Telemetry consent set to " + isChecked);
        });
    }

    private void updateSwitchState() {
        boolean isEnabled = AIMonitoringPermission.hasPermission(this);
        aiMonitoringSwitch.setChecked(isEnabled);
        Log.d(TAG, "Updating switch state to: " + isEnabled);
    }

    private void updateTelemetryState() {
        boolean allowed = AnalyticsConsentManager.isTelemetryAllowed(this);
        telemetrySwitch.setChecked(allowed);
        Log.d(TAG, "Updating telemetry switch to: " + allowed);
    }

    private void restartService() {
        // Stop the service first
        Intent serviceIntent = new Intent(this, ServiceMine.class);
        stopService(serviceIntent);
        
        // Start it again if AI monitoring is enabled
        if (AIMonitoringPermission.hasPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update switch state in case it was changed from another activity
        updateSwitchState();
        updateTelemetryState();
    }
}
