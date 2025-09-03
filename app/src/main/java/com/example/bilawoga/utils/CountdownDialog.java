package com.example.bilawoga.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.example.bilawoga.R;

public class CountdownDialog {
    private AlertDialog dialog;
    private CountDownTimer countDownTimer;
    private final Context context;
    private final String userName;
    private final String incidentType;
    private final String emergencyNumber1;
    private final String emergencyNumber2;
    private final CountdownListener listener;
    private static final int COUNTDOWN_TIME = 7; // 7 seconds per requirement

    public interface CountdownListener {
        void onCountdownFinished(String userName, String incidentType, String emergencyNumber1, String emergencyNumber2);
        void onCountdownCancelled();
    }

    public CountdownDialog(Context context, String userName, String incidentType, 
                          String emergencyNumber1, String emergencyNumber2, CountdownListener listener) {
        this.context = context;
        this.userName = userName;
        this.incidentType = incidentType;
        this.emergencyNumber1 = emergencyNumber1;
        this.emergencyNumber2 = emergencyNumber2;
        this.listener = listener;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_countdown, null);
        
        TextView countdownText = view.findViewById(R.id.countdown_text);
        Button cancelButton = view.findViewById(R.id.cancel_button);
        Button sendNowButton = view.findViewById(R.id.send_now_button);

        // Set up the dialog
        builder.setView(view);
        builder.setCancelable(false);
        dialog = builder.create();
        
        // Start the countdown
        startCountdown(countdownText);
        
        // Set up button click listeners
        cancelButton.setOnClickListener(v -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            if (listener != null) {
                listener.onCountdownCancelled();
            }
            dialog.dismiss();
        });
        
        sendNowButton.setOnClickListener(v -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            if (listener != null) {
                listener.onCountdownFinished(userName, incidentType, emergencyNumber1, emergencyNumber2);
            }
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void startCountdown(TextView countdownText) {
        countDownTimer = new CountDownTimer(COUNTDOWN_TIME * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000) + 1;
                String message = String.format("🚨 Sending SOS Alert...\n\n⏱️ %d seconds remaining\n\nSOS alert will be sent to emergency contacts.", 
                        secondsRemaining);
                countdownText.setText(message);
            }

            @Override
            public void onFinish() {
                if (listener != null) {
                    listener.onCountdownFinished(userName, incidentType, emergencyNumber1, emergencyNumber2);
                }
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            }
        }.start();
    }
    
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            dialog.dismiss();
        }
    }
}
