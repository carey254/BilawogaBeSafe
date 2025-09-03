package com.example.bilawoga;

import android.Manifest;
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
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.bilawoga.utils.SOSHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;

public class ServiceMine extends Service implements SensorEventListener {
    private static final String TAG = "ServiceMine";
    private static final String CHANNEL_ID = "BilaWoga_Service";
    private static final float SHAKE_THRESHOLD = 8.0f; // Reduced from 12.0f to make it more sensitive
    private static final long SHAKE_COOLDOWN_MS = 10000; // 10 seconds cooldown
    private static final int NOTIFICATION_ID = 1;
    
    // Battery optimization settings
    private static final int BATTERY_OPTIMIZATION_INTERVAL = 30000; // 30 seconds
    private static final int SERVICE_RESTART_DELAY = 5000; // 5 seconds

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float accelValue;
    private float accelLast;
    private float shake;
    private boolean isShakeCooldown = false;

    private FusedLocationProviderClient fusedLocationClient;
    private String userName;
    private String incidentType;
    private boolean isServiceRunning = false;
    private MediaPlayer emergencySound;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        checkUserInfo();
        initializeSensors();
        initializeEmergencySound();

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());
        isServiceRunning = true;
        
        // Start battery optimization monitoring
        startBatteryOptimizationMonitoring();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SecureHer Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors phone movement for emergency situations");
            channel.enableVibration(false);
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // Create an intent to launch the app when notification is clicked
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BilaWoga is Active")
                .setContentText("Monitoring for emergency situations")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
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
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
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
                    
                    // Schedule next check
                    handler.postDelayed(this, BATTERY_OPTIMIZATION_INTERVAL);
                }
            }
        }, BATTERY_OPTIMIZATION_INTERVAL);
    }
    
    private void updateNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification());
        }
    }

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
                case "sendManually":
                    if (!isShakeCooldown) {
                        sendEmergencyAlert();
                    } else {
                        Log.d(TAG, "Manual send blocked by cooldown");
                    }
                    break;
                case "Start":
                    if (!isServiceRunning) {
                        startService();
                    }
                    break;
            }
        }

        return START_STICKY;
    }

    private void startService() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
            );
            isServiceRunning = true;
            Log.d(TAG, "Service started successfully");
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
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (emergencySound != null) {
            emergencySound.release();
            emergencySound = null;
        }
        isServiceRunning = false;
        Log.d(TAG, "Service destroyed");
    }
}