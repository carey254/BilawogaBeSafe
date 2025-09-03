package com.example.bilawoga.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.bilawoga.MainActivity;
import com.example.bilawoga.R;

/**
 * Smart notification system for BilaWoga app
 * Provides contextual alerts and user guidance
 */
public class SmartNotificationManager {
    private static final String TAG = "SmartNotificationManager";
    
    // Notification Channels
    private static final String CHANNEL_EMERGENCY = "emergency_channel";
    private static final String CHANNEL_STATUS = "status_channel";
    private static final String CHANNEL_AI = "ai_channel";
    private static final String CHANNEL_GUIDANCE = "guidance_channel";
    
    // Notification IDs
    private static final int NOTIFICATION_SERVICE_STATUS = 1001;
    private static final int NOTIFICATION_AI_DETECTION = 1002;
    private static final int NOTIFICATION_EMERGENCY_CONFIRMATION = 1003;
    private static final int NOTIFICATION_GUIDANCE = 1004;
    private static final int NOTIFICATION_PERMISSION_REMINDER = 1005;
    
    private Context context;
    private NotificationManagerCompat notificationManager;
    
    public SmartNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannels();
    }
    
    /**
     * Create notification channels for Android 8.0+
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            
            // Emergency Channel - High priority
            NotificationChannel emergencyChannel = new NotificationChannel(
                CHANNEL_EMERGENCY,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            emergencyChannel.setDescription("Critical emergency notifications");
            emergencyChannel.enableVibration(true);
            emergencyChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
            emergencyChannel.enableLights(true);
            emergencyChannel.setLightColor(android.graphics.Color.RED);
            
            // Status Channel - Default priority
            NotificationChannel statusChannel = new NotificationChannel(
                CHANNEL_STATUS,
                "Service Status",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            statusChannel.setDescription("Background service status updates");
            
            // AI Channel - Low priority
            NotificationChannel aiChannel = new NotificationChannel(
                CHANNEL_AI,
                "AI Detection",
                NotificationManager.IMPORTANCE_LOW
            );
            aiChannel.setDescription("AI detection and analysis updates");
            
            // Guidance Channel - Default priority
            NotificationChannel guidanceChannel = new NotificationChannel(
                CHANNEL_GUIDANCE,
                "User Guidance",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            guidanceChannel.setDescription("Helpful tips and guidance");
            
            manager.createNotificationChannels(
                java.util.Arrays.asList(emergencyChannel, statusChannel, aiChannel, guidanceChannel)
            );
        }
    }
    
    /**
     * Show service status notification
     */
    public void showServiceStatusNotification(boolean isActive) {
        String title = isActive ? "🛡️ BilaWoga Active" : "⚠️ BilaWoga Inactive";
        String message = isActive ? 
            "Emergency protection is active. AI monitoring enabled." :
            "Emergency protection is inactive. Tap to start service.";
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(isActive);
        
        notificationManager.notify(NOTIFICATION_SERVICE_STATUS, builder.build());
        Log.d(TAG, "Service status notification: " + (isActive ? "Active" : "Inactive"));
    }
    
    /**
     * Show AI detection notification (non-intrusive)
     */
    public void showAIDetectionNotification(String detectionType, float confidence) {
        String title = "🤖 AI Detection";
        String message = String.format("Detected %s (%.1f%% confidence)", detectionType, confidence * 100);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_AI)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5000); // Auto-dismiss after 5 seconds
        
        notificationManager.notify(NOTIFICATION_AI_DETECTION, builder.build());
        Log.d(TAG, "AI detection notification: " + detectionType);
    }
    
    /**
     * Show emergency confirmation notification
     */
    public void showEmergencyConfirmationNotification(String triggerType) {
        String title = "🚨 Emergency Detected";
        String message = "AI detected potential emergency. Tap to confirm or dismiss.";
        
        // Create confirmation intent
        Intent confirmIntent = new Intent(context, MainActivity.class);
        confirmIntent.setAction("CONFIRM_EMERGENCY");
        confirmIntent.putExtra("trigger_type", triggerType);
        PendingIntent confirmPendingIntent = PendingIntent.getActivity(context, 1, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create dismiss intent
        Intent dismissIntent = new Intent(context, MainActivity.class);
        dismissIntent.setAction("DISMISS_EMERGENCY");
        PendingIntent dismissPendingIntent = PendingIntent.getActivity(context, 2, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(R.drawable.logo, "Confirm", confirmPendingIntent)
            .addAction(R.drawable.logo, "Dismiss", dismissPendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(30000); // Auto-dismiss after 30 seconds
        
        notificationManager.notify(NOTIFICATION_EMERGENCY_CONFIRMATION, builder.build());
        Log.d(TAG, "Emergency confirmation notification: " + triggerType);
    }
    
    /**
     * Show user guidance notification
     */
    public void showGuidanceNotification(String tip) {
        String title = "💡 Tip";
        String message = tip;
        
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_GUIDANCE)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
        
        notificationManager.notify(NOTIFICATION_GUIDANCE, builder.build());
        Log.d(TAG, "Guidance notification: " + tip);
    }
    
    /**
     * Show permission reminder notification
     */
    public void showPermissionReminderNotification(String missingPermission) {
        String title = "⚠️ Permission Required";
        String message = "BilaWoga needs " + missingPermission + " permission for full functionality.";
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction("REQUEST_PERMISSIONS");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_GUIDANCE)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
        
        notificationManager.notify(NOTIFICATION_PERMISSION_REMINDER, builder.build());
        Log.d(TAG, "Permission reminder notification: " + missingPermission);
    }
    
    /**
     * Cancel specific notification
     */
    public void cancelNotification(int notificationId) {
        notificationManager.cancel(notificationId);
        Log.d(TAG, "Cancelled notification: " + notificationId);
    }
    
    /**
     * Cancel all notifications
     */
    public void cancelAllNotifications() {
        notificationManager.cancelAll();
        Log.d(TAG, "Cancelled all notifications");
    }
    
    /**
     * Check if notifications are enabled
     */
    public boolean areNotificationsEnabled() {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}
