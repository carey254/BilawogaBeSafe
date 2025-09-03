package com.example.bilawoga;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bilawoga.utils.SOSHelper;
import com.example.bilawoga.utils.SecureStorageManager;

public class CountdownActivity extends AppCompatActivity {
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_INCIDENT = "incident";
    public static final String EXTRA_EM1 = "em1";
    public static final String EXTRA_EM2 = "em2";

    private CountDownTimer timer;
    private boolean cancelled = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_countdown);

        TextView title = findViewById(R.id.title);
        TextView timerText = findViewById(R.id.timerText);
        Button cancelBtn = findViewById(R.id.cancelBtn);
        Button sendNowBtn = findViewById(R.id.sendNowBtn);

        title.setText("Emergency SOS will send in:");
        timer = new CountDownTimer(5000, 1000) {
            @Override public void onTick(long ms) {
                timerText.setText(String.valueOf(ms/1000));
            }
            @Override public void onFinish() {
                if (!cancelled) doSend();
            }
        }.start();

        cancelBtn.setOnClickListener(v -> { cancelled = true; finish(); });
        sendNowBtn.setOnClickListener(v -> { cancelled = true; doSend(); });
    }

    private void doSend() {
        SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(this);
        boolean testMode = prefs != null && prefs.getBoolean("TEST_MODE", false);

        String user = getIntent().getStringExtra(EXTRA_USER);
        String incident = getIntent().getStringExtra(EXTRA_INCIDENT);
        String em1 = getIntent().getStringExtra(EXTRA_EM1);
        String em2 = getIntent().getStringExtra(EXTRA_EM2);

        if (testMode) {
            // Skip real send in test mode
            android.widget.Toast.makeText(this, "Test Mode: SOS not sent", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        SOSHelper helper = new SOSHelper(this);
        helper.sendEmergencySOS(user, incident, em1, em2);
        finish();
    }
}