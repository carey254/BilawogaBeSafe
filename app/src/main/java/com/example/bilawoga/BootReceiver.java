package com.example.bilawoga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed event received");

            // Auto-start services if AI monitoring is enabled
            try {
                // Check if AI monitoring permission is granted
                if (com.example.bilawoga.utils.AIMonitoringPermission.hasPermission(context)) {
                    Log.d(TAG, "AI monitoring enabled - starting services on boot");
                    
                    // Start ServiceMine
                    Intent serviceIntent = new Intent(context, ServiceMine.class);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    
                    // Start BackgroundAudioMonitor for continuous monitoring
                    Intent audioMonitorIntent = new Intent(context, com.example.bilawoga.utils.BackgroundAudioMonitor.class);
                    audioMonitorIntent.putExtra("emergency_listener", true);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(audioMonitorIntent);
                    } else {
                        context.startService(audioMonitorIntent);
                    }
                    
                    Log.d(TAG, "Both ServiceMine and BackgroundAudioMonitor started on boot");
                } else {
                    Log.d(TAG, "AI monitoring not enabled - services not started on boot");
                }
            } catch (Throwable t) {
                Log.w(TAG, "BootReceiver: error starting services: " + t.getMessage());
            }
        }
    }
}




















