package com.example.bilawoga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.media.AudioManager;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";
    private MediaPlayer emergencySound;
    private AudioManager audioManager;
    private int originalAlarmVolume = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            
            for (SmsMessage message : messages) {
                String messageBody = message.getMessageBody();
                // Check if this is an emergency message
                if (messageBody.contains("🚨 EMERGENCY ALERT 🚨")) {
                    playEmergencySound(context);
                    break;
                }
            }
        }
    }

    private void playEmergencySound(Context context) {
        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                int maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0);
                Log.d(TAG, "Set alarm volume to max: " + maxAlarmVolume + ", original: " + originalAlarmVolume);
            }

            // Create MediaPlayer with emergency sound
            emergencySound = MediaPlayer.create(context, R.raw.emergency_alert);
            
            // Set audio attributes for alarm usage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                emergencySound.setAudioAttributes(audioAttributes);
            }
            
            // Set volume to maximum (relative to the stream volume)
            emergencySound.setVolume(1.0f, 1.0f);
            
            // Play the sound
            emergencySound.start();
            
            // Set completion listener to release resources and restore volume
            emergencySound.setOnCompletionListener(mp -> {
                mp.release();
                emergencySound = null;
                // Restore original alarm volume
                if (audioManager != null && originalAlarmVolume != -1) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
                    Log.d(TAG, "Restored alarm volume to original: " + originalAlarmVolume);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error playing emergency sound: " + e.getMessage());
            // Ensure volume is restored even if an error occurs
            if (audioManager != null && originalAlarmVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
                Log.d(TAG, "Restored alarm volume after error: " + originalAlarmVolume);
            }
        }
    }
} 