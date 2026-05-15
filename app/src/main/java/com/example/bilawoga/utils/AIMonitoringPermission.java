package com.example.bilawoga.utils;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.app.Activity;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ActivityCompat;

import com.example.bilawoga.MainActivity;
import com.bilawoga.safety.R;

public class AIMonitoringPermission {
    private static final String TAG = "AIMonitoringPermission";
    private static final String PREF_NAME = "AIMonitoringPrefs";
    private static final String KEY_PERMISSION_GRANTED = "ai_monitoring_permission_granted";
    private static final String KEY_NEVER_ASK_AGAIN = "never_ask_again";
    private static final String CHANNEL_ID = "ai_monitoring_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static AlertDialog sPermissionDialog; // guard against double-show

    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }

    public static void checkAndRequestPermission(Context context, PermissionCallback callback) {
        checkAndRequestPermission(context, callback, false);
    }

    public static void checkAndRequestPermission(Context context, PermissionCallback callback, boolean showDialog) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // If permission was previously granted
        if (prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            callback.onPermissionGranted();
            return;
        }
        
        // If user selected "Don't ask again"
        if (prefs.getBoolean(KEY_NEVER_ASK_AGAIN, false)) {
            callback.onPermissionDenied();
            return;
        }
        
        if (showDialog) {
            // Show permission dialog
            showPermissionDialog(context, callback);
        } else {
            // Don't show dialog, just return denied
            callback.onPermissionDenied();
        }
    }

    private static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true; // Not required before Android 13
        }
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static void showPermissionDialog(Context context, PermissionCallback callback) {
        // Require an Activity context to show a normal dialog safely
        if (!(context instanceof Activity)) {
            Log.w(TAG, "showPermissionDialog requires an Activity context");
            callback.onPermissionDenied();
            return;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) {
            callback.onPermissionDenied();
            return;
        }

        // Do not stack multiple dialogs
        if (sPermissionDialog != null && sPermissionDialog.isShowing()) {
            Log.d(TAG, "Permission dialog already showing; ignoring duplicate request");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        builder.setTitle("🔒 AI Monitoring Permission Required");
        builder.setMessage("BilaWoga needs your permission to monitor for emergencies. This helps keep you safe by detecting potential threats using AI analysis of sounds and movement patterns.");
        
        builder.setPositiveButton("Enable AI Monitoring", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Save permission granted
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean(KEY_PERMISSION_GRANTED, true)
                        .putBoolean(KEY_NEVER_ASK_AGAIN, false)
                        .apply();
                
                // Show a confirmation notification
                showPermissionGrantedNotification(context);
                
                // Notify the callback
                callback.onPermissionGranted();
            }
        });
        
        builder.setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                callback.onPermissionDenied();
                dialog.dismiss();
            }
        });
        
        builder.setNeutralButton("Never Ask Again", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Save never ask again preference
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean(KEY_NEVER_ASK_AGAIN, true)
                        .putBoolean(KEY_PERMISSION_GRANTED, false)
                        .apply();
                callback.onPermissionDenied();
            }
        });
        
        activity.runOnUiThread(() -> {
            try {
                sPermissionDialog = builder.create();
                sPermissionDialog.setCancelable(false);
                sPermissionDialog.setCanceledOnTouchOutside(false);
                sPermissionDialog.show();

                // Ensure high-contrast, readable content regardless of system theme
                try {
                    android.widget.TextView messageView = sPermissionDialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        messageView.setTextColor(android.graphics.Color.BLACK);
                    }

                    android.view.Window window = sPermissionDialog.getWindow();
                    if (window != null) {
                        android.view.View decor = window.getDecorView();
                        if (decor != null) {
                            decor.setBackgroundColor(android.graphics.Color.WHITE);
                        }
                    }

                    android.widget.Button pos = sPermissionDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    android.widget.Button neg = sPermissionDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    android.widget.Button neu = sPermissionDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    int btnColor = android.graphics.Color.parseColor("#1A1A1A");
                    if (pos != null) { pos.setTextColor(btnColor); pos.setAllCaps(false); }
                    if (neg != null) { neg.setTextColor(btnColor); neg.setAllCaps(false); }
                    if (neu != null) { neu.setTextColor(btnColor); neu.setAllCaps(false); }
                } catch (Throwable stylingError) {
                    Log.w(TAG, "Dialog styling fallback: " + stylingError.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing permission dialog: " + e.getMessage());
            }
        });
    }
    
    public static boolean hasPermission(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
    }
    
    /**
     * Programmatically grant AI monitoring permission (used by notification action 'Allow').
     * Does not open the app; quietly sets the preference and emits an optional toast/notification.
     */
    public static void grantPermission(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean changed = !prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
        prefs.edit()
                .putBoolean(KEY_PERMISSION_GRANTED, true)
                .putBoolean(KEY_NEVER_ASK_AGAIN, false)
                .apply();
        if (changed) {
            try {
                showPermissionGrantedNotification(context);
            } catch (Throwable t) {
                Log.w(TAG, "Unable to show granted notification: " + t.getMessage());
            }
        }
    }
    
    /**
     * Resets the permission state (for when user wants to disable AI monitoring)
     */
    public static void resetPermission(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_PERMISSION_GRANTED, false)
                .putBoolean(KEY_NEVER_ASK_AGAIN, false)
                .apply();
        
        // Show a notification that AI monitoring has been disabled
        showPermissionRevokedNotification(context);
    }
    
    private static void showPermissionRevokedNotification(Context context) {
        createNotificationChannelIfNeeded(context);
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;
        
        // Create an intent to open the app when notification is clicked
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("AI Monitoring Disabled")
                .setContentText("AI monitoring for emergencies has been turned off.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(Color.RED);
        
        // Show the notification (guard for Android 13+)
        try {
            if (canPostNotifications(context)) {
                notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS not granted; skipping notify() (revoked)");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification: " + e.getMessage());
        }
    }
    
    public static void showPermissionGrantedNotification(Context context) {
        createNotificationChannelIfNeeded(context);
        
        // Create an intent to open the app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("AI Monitoring Enabled")
                .setContentText("BilaWoga is now monitoring for emergencies")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        
        // Show the notification (guard for Android 13+)
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            if (canPostNotifications(context)) {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS not granted; skipping notify() (granted)");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show notification: " + e.getMessage());
        }
    }
    
    private static void showMonitoringDisabledNotification(Context context) {
        createNotificationChannelIfNeeded(context);
        
        // Create an intent to open the app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("AI Monitoring Disabled")
                .setContentText("Tap to re-enable safety monitoring")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        
        // Show the notification (guard for Android 13+)
        NotificationManagerCompat notificationManager2 = NotificationManagerCompat.from(context);
        try {
            if (canPostNotifications(context)) {
                notificationManager2.notify(NOTIFICATION_ID + 1, builder.build());
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS not granted; skipping notify() (disabled)");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to show disabled notification: " + e.getMessage());
        }
    }
    
    private static void createNotificationChannelIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AI Monitoring Alerts";
            String description = "Notifications about AI monitoring status";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
