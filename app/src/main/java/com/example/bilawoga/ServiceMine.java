package com.example.bilawoga;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.bilawoga.safety.R;
import com.example.bilawoga.utils.SOSHelper;
import com.example.bilawoga.utils.AIMonitoringPermission;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;

public class ServiceMine extends Service implements SensorEventListener {
    private static final String TAG = "ServiceMine";
    private static final String CHANNEL_ID = "BilaWoga_Service";
    private static final float SHAKE_THRESHOLD = 8.0f; // Reduced from 12.0f to make it more sensitive
    private static final long SHAKE_COOLDOWN_MS = 10000; // 10 seconds cooldown
    private static final int NOTIFICATION_ID = 1;
    private static final long REMINDER_INTERVAL_MS = 6L * 60L * 60L * 1000L; // 6 hours
    private static final String PREFS_NAME = "ServicePrefs";
    private static final String KEY_LAST_POPUP_TS = "last_popup_ts";
    
    // Battery optimization settings
    private static final int BATTERY_OPTIMIZATION_INTERVAL = 30000; // 30 seconds
    private static final int SERVICE_RESTART_DELAY = 5000; // 5 seconds

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float accelValue;
    private float accelLast;
    private float shake;
    private boolean isShakeCooldown = false;
    private long serviceStartEpochMs = 0L; // grace period start time

    private FusedLocationProviderClient fusedLocationClient;
    private String userName;
    private String incidentType;
    private boolean isServiceRunning = false;
    private MediaPlayer emergencySound;
    private Handler batteryOptimizationHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        serviceStartEpochMs = System.currentTimeMillis(); // start grace period
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        createNotificationChannel();
        
        // Start as foreground service with a persistent, quiet notification
        // Android 14+ requires foregroundServiceType to be specified
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        
        // Initialize emergency sound
        initializeEmergencySound();
        
        // Start monitoring battery optimization
        startBatteryOptimizationMonitoring();
        
        // Check AI monitoring permission and update service state
        checkAIMonitoringState();
        
        // Ensure BackgroundAudioMonitor is running if AI monitoring is enabled
        if (AIMonitoringPermission.hasPermission(this)) {
            // Small delay to ensure service is fully initialized
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isBackgroundAudioMonitorRunning()) {
                    restartBackgroundAudioMonitor();
                }
            }, 2000);
        }
    }
    
    private void checkAIMonitoringState() {
        if (AIMonitoringPermission.hasPermission(this)) {
            enableAIMonitoring();
        } else {
            disableAIMonitoring();
        }
        // Update notification to reflect current state
        updateNotification();
    }
    
    private void enableAIMonitoring() {
        Log.d(TAG, "Enabling AI monitoring");
        // Register sensor listener if not already registered
        if (sensorManager != null && accelerometer != null) {
            try {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                isServiceRunning = true;
                // Keep a quiet foreground notification only
                updateNotification();
                
                // Start BackgroundAudioMonitor for continuous audio monitoring
                restartBackgroundAudioMonitor();
            } catch (Exception e) {
                Log.e(TAG, "Failed to enable AI monitoring: " + e.getMessage());
            }
        }
    }
    
    private void disableAIMonitoring() {
        Log.d(TAG, "Disabling AI monitoring");
        // Unregister sensor listener if registered
        if (sensorManager != null) {
            try {
                sensorManager.unregisterListener(this);
                isServiceRunning = false;
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering sensor listener: " + e.getMessage());
            }
        }
        // Stop BackgroundAudioMonitor
        stopBackgroundAudioMonitor();
        // Remove monitoring notification if present
        try {
            if (canPostNotifications()) {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                notificationManager.cancel(NOTIFICATION_ID + 1);
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS not granted; skipping cancel() for monitoring notification");
            }
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException while canceling notification: " + se.getMessage());
        }
    }
    
    /**
     * Restart BackgroundAudioMonitor to ensure continuous monitoring
     */
    private void restartBackgroundAudioMonitor() {
        try {
            // Check if audio permission is granted
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted - BackgroundAudioMonitor cannot start");
                return;
            }
            
            Intent audioMonitorIntent = new Intent(this, com.example.bilawoga.utils.BackgroundAudioMonitor.class);
            audioMonitorIntent.putExtra("emergency_listener", true);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(audioMonitorIntent);
            } else {
                startService(audioMonitorIntent);
            }
            
            Log.d(TAG, "BackgroundAudioMonitor restarted for continuous AI monitoring");
        } catch (Exception e) {
            Log.e(TAG, "Error restarting BackgroundAudioMonitor: " + e.getMessage());
        }
    }
    
    /**
     * Stop BackgroundAudioMonitor
     */
    private void stopBackgroundAudioMonitor() {
        try {
            Intent stopIntent = new Intent(this, com.example.bilawoga.utils.BackgroundAudioMonitor.class);
            stopIntent.setAction("com.example.bilawoga.action.STOP_MONITORING");
            stopService(stopIntent);
            Log.d(TAG, "BackgroundAudioMonitor stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping BackgroundAudioMonitor: " + e.getMessage());
        }
    }
    
    /**
     * Check if BackgroundAudioMonitor is currently running
     */
    private boolean isBackgroundAudioMonitorRunning() {
        try {
            android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if ("com.example.bilawoga.utils.BackgroundAudioMonitor".equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking BackgroundAudioMonitor status: " + e.getMessage());
        }
        return false;
    }
    
@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private void showMonitoringNotification() {
        // Deprecated: we will rely on the single foreground notification only
        updateNotification();
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void updateNotification() {
        // Update the main service notification to reflect current state
        Notification notification = createNotification();
        if (notification != null) {
            try {
                if (canPostNotifications()) {
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
                } else {
                    Log.d(TAG, "POST_NOTIFICATIONS not granted; skipping notify() for service notification");
                }
            } catch (SecurityException se) {
                Log.w(TAG, "SecurityException while posting notification: " + se.getMessage());
            }
        }
    }

    private boolean canPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true; // Permission not required pre-Android 13
        }
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private Notification createAIMonitoringNotification() {
        // Use the same quiet foreground notification builder
        return createNotification();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for service notification
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "BilaWoga Safety Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Background service for your safety");
            
            // Alert channel for rare heads-up reminder (at most once every 6 hours)
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ID + "_ALERT",
                    "BilaWoga Safety Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Occasional safety status reminder");
            // Keep it less intrusive: no vibration, no custom sound
            alertChannel.enableVibration(false);
            alertChannel.enableLights(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(alertChannel);
            }
        }
    }

    private Notification createNotification() {
        // Service command intents for notification actions
        Intent allowIntent = new Intent(this, ServiceMine.class).setAction("allow_ai");
        PendingIntent allowPendingIntent = PendingIntent.getService(
                this,
                1,
                allowIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent disableIntent = new Intent(this, ServiceMine.class).setAction("disable_ai");
        PendingIntent disablePendingIntent = PendingIntent.getService(
                this,
                2,
                disableIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Check if AI monitoring is enabled
        boolean isAIMonitoringEnabled = AIMonitoringPermission.hasPermission(this);
        String notificationText = isAIMonitoringEnabled ?
                "AI monitoring is active for your safety" :
                "AI monitoring is OFF (tap Allow to enable)";

        // Always use the quiet service channel
        String channelId = CHANNEL_ID;

        // Build a quiet, persistent notification with Allow/Stop actions
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("BilaWoga Safety")
                .setContentText(notificationText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .addAction(R.drawable.ic_launcher_foreground, "Allow", allowPendingIntent)
                .addAction(R.drawable.ic_launcher_foreground, "Stop", disablePendingIntent);

        return builder.build();
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer == null) {
                Log.w(TAG, "No accelerometer found on device");
                return;
            }

            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
            );

            accelValue = SensorManager.GRAVITY_EARTH;
            accelLast = SensorManager.GRAVITY_EARTH;
            shake = 0.00f;
            Log.d(TAG, "Sensors initialized successfully");
        } else {
            Log.e(TAG, "Could not initialize sensor manager");
        }
    }

    private void checkUserInfo() {
        SharedPreferences sharedPreferences = com.example.bilawoga.utils.SecureStorageManager.getEncryptedSharedPreferences(this);
        userName = sharedPreferences.getString("USERNAME", "Unknown User");
        incidentType = sharedPreferences.getString("INCIDENT_TYPE", "an emergency");

        Log.d(TAG, "User info loaded - Name: " + userName);
    }

    private void initializeEmergencySound() {
        try {
            emergencySound = MediaPlayer.create(this, R.raw.emergency_alert);
            emergencySound.setLooping(true);
            emergencySound.setVolume(1.0f, 1.0f);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing emergency sound", e);
        }
    }
    
    /**
     * Battery optimization monitoring to prevent service from being killed
     */
    private void startBatteryOptimizationMonitoring() {
        batteryOptimizationHandler = new Handler(Looper.getMainLooper());
        batteryOptimizationHandler.postDelayed(new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                if (isServiceRunning) {
                    // Update notification to show service is still active
                    updateNotification();
                    
                    // Check if sensors are still registered
                    if (sensorManager != null && accelerometer != null) {
                        try {
                            sensorManager.registerListener(ServiceMine.this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
                        } catch (Exception e) {
                            Log.w(TAG, "Re-registering sensor listener: " + e.getMessage());
                        }
                    }
                    
                    // Ensure BackgroundAudioMonitor is running if AI monitoring is enabled
                    if (AIMonitoringPermission.hasPermission(ServiceMine.this)) {
                        if (!isBackgroundAudioMonitorRunning()) {
                            Log.d(TAG, "BackgroundAudioMonitor not running - restarting");
                            restartBackgroundAudioMonitor();
                        }
                    }

                    // Show at most one heads-up reminder every 6 hours
                    maybeShowSixHourlyReminder();
                    
                    // Schedule next check
                    batteryOptimizationHandler.postDelayed(this, BATTERY_OPTIMIZATION_INTERVAL);
                }
            }
        }, BATTERY_OPTIMIZATION_INTERVAL);
    }

    private void maybeShowSixHourlyReminder() {
        try {
            boolean aiOn = AIMonitoringPermission.hasPermission(this);
            if (!aiOn) return; // Only remind when AI is active
            if (!canPostNotifications()) return;

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            long last = prefs.getLong(KEY_LAST_POPUP_TS, 0L);
            long now = System.currentTimeMillis();
            if (now - last < REMINDER_INTERVAL_MS) return;

            // Create notification channel for reminder if it doesn't exist
            createReminderNotificationChannel();

            // Build reminder notification asking if user wants to continue
            Intent continueIntent = new Intent(this, ServiceMine.class).setAction("continue_ai");
            PendingIntent continuePending = PendingIntent.getService(
                    this, 201, continueIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            
            Intent disableIntent = new Intent(this, ServiceMine.class).setAction("disable_ai");
            PendingIntent disablePending = PendingIntent.getService(
                    this, 200, disableIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            // Create full-screen intent to show dialog when possible
            Intent fullScreenIntent = new Intent(this, MainActivity.class);
            fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            fullScreenIntent.putExtra("show_ai_reminder", true);
            PendingIntent fullScreenPending = PendingIntent.getActivity(
                    this, 202, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID + "_REMINDER")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("BilaWoga AI Monitoring Reminder")
                    .setContentText("AI monitoring has been active for 6 hours. Do you want to continue?")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(fullScreenPending, true)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("AI monitoring has been protecting you for 6 hours. Would you like to continue monitoring for your safety?"))
                    .addAction(R.drawable.ic_launcher_foreground, "Continue", continuePending)
                    .addAction(R.drawable.ic_launcher_foreground, "Stop", disablePending);

            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID + 2, nb.build());
            prefs.edit().putLong(KEY_LAST_POPUP_TS, now).apply();
            
            Log.d(TAG, "6-hour reminder shown - asking user if they want to continue AI monitoring");
        } catch (Throwable t) {
            Log.w(TAG, "maybeShowSixHourlyReminder failed: " + t.getMessage());
        }
    }
    
    private void createReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID + "_REMINDER",
                "AI Monitoring Reminders",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders about AI monitoring status");
            channel.setShowBadge(true);
            channel.enableVibration(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    // (keep only the earlier updateNotification() definition above)

    private void playEmergencySound() {
        try {
            if (emergencySound != null && !emergencySound.isPlaying()) {
                emergencySound.start();
                // Stop the sound after 10 seconds
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (emergencySound != null && emergencySound.isPlaying()) {
                        try {
                            emergencySound.stop();
                            emergencySound.prepare();
                        } catch (IOException e) {
                            Log.e(TAG, "Error preparing emergency sound after stopping", e);
                        }
                    }
                }, 10000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing emergency sound", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "Service action received: " + action);

            switch (action) {
                case "stop":
                    stopService();
                    return START_NOT_STICKY;
                case "allow_ai":
                    // Grant AI monitoring permission directly from notification action
                    com.example.bilawoga.utils.AIMonitoringPermission.grantPermission(this);
                    checkAIMonitoringState();
                    // Restart BackgroundAudioMonitor if needed
                    restartBackgroundAudioMonitor();
                    break;
                case "continue_ai":
                    // User wants to continue AI monitoring - just update timestamp
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putLong(KEY_LAST_POPUP_TS, System.currentTimeMillis()).apply();
                    // Cancel reminder notification
                    NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID + 2);
                    // Ensure monitoring is still active
                    checkAIMonitoringState();
                    restartBackgroundAudioMonitor();
                    Log.d(TAG, "User confirmed to continue AI monitoring");
                    break;
                case "disable_ai":
                    // Disable AI monitoring
                    com.example.bilawoga.utils.AIMonitoringPermission.resetPermission(this);
                    checkAIMonitoringState();
                    // Stop BackgroundAudioMonitor
                    stopBackgroundAudioMonitor();
                    break;
                case "sendManually":
                    if (!isShakeCooldown) {
                        sendEmergencyAlert();
                    } else {
                        Log.d(TAG, "Manual send blocked by cooldown");
                    }
                    break;
                case "Start":
                    if (!isServiceRunning) {
                        // Check AI monitoring permission before starting service
                        AIMonitoringPermission.checkAndRequestPermission(this, new AIMonitoringPermission.PermissionCallback() {
                            @Override
                            public void onPermissionGranted() {
                                // Permission granted, start the service
                                startService();
                                Log.d(TAG, "AI monitoring permission granted, starting service");
                            }

                            @Override
                            public void onPermissionDenied() {
                                // User denied permission, stop the service
                                Log.w(TAG, "AI monitoring permission denied, stopping service");
                                stopSelf();
                            }
                        }, true);
                    }
                    break;
            }
        }

        return START_STICKY;
    }

    private void startService() {
        // Check if we have AI monitoring permission before starting
        if (AIMonitoringPermission.hasPermission(this)) {
            if (sensorManager != null && accelerometer != null) {
                sensorManager.registerListener(
                        this,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL
                );
                isServiceRunning = true;
                Log.d(TAG, "Service started with AI monitoring");
                
                // Update notification to show monitoring is active
                updateNotification();
            }
        } else {
            Log.w(TAG, "Cannot start service: AI monitoring permission not granted");
            stopSelf();
        }
    }

    private void stopService() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        isServiceRunning = false;
        stopForeground(true);
        stopSelf();
        Log.d(TAG, "Service stopped");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Ignore shakes for first 8 seconds after service start to avoid auto SOS on launch
        if ((System.currentTimeMillis() - serviceStartEpochMs) < 8000) {
            return;
        }

        if (!isServiceRunning || isShakeCooldown ||
                event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        accelLast = accelValue;
        accelValue = (float) Math.sqrt(x * x + y * y + z * z);
        float delta = accelValue - accelLast;
        shake = shake * 0.9f + delta;

        if (shake > SHAKE_THRESHOLD) {
            isShakeCooldown = true;
            sendEmergencyAlert();

            // Reset shake detection after cooldown
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isShakeCooldown = false;
                shake = 0.0f;
                Log.d(TAG, "Shake detection reset after cooldown");
            }, SHAKE_COOLDOWN_MS);
        }
    }

    private void sendEmergencyAlert() {
        if (!checkPermissions()) {
            Log.e(TAG, "Missing required permissions for emergency alert");
            return;
        }

        // EMERGENCY BYPASS: Shake detection always works even if app is locked
        // Background service continues monitoring regardless of app lock status
        // This ensures user safety is never compromised by security measures
        Log.d(TAG, "EMERGENCY BYPASS: Shake-triggered SOS (bypassed app lock)");

        SOSHelper.sendSOSFromService(
                this,
                fusedLocationClient,
                userName,
                incidentType
        );

        Log.d(TAG, "Emergency alert triggered");
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used but required by interface
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        // Unregister sensor listener
        if (sensorManager != null) {
            try {
                sensorManager.unregisterListener(this);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering sensor listener: " + e.getMessage());
            }
        }
        // Release media player resources
        if (emergencySound != null) {
            try {
                if (emergencySound.isPlaying()) {
                    emergencySound.stop();
                }
                emergencySound.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing media player: " + e.getMessage());
            } finally {
                emergencySound = null;
            }
        }
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, stopping service");
        onDestroy();
    }
}