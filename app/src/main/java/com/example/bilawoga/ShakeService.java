package com.example.bilawoga;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
// import android.telephony.SmsManager; // Removed: Using Intent-based SMS instead
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.example.bilawoga.utils.SOSHelper;

import com.example.bilawoga.utils.SecureStorageManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class ShakeService implements SensorEventListener {

    private static final float SHAKE_THRESHOLD_GRAVITY = 1.3F;
    private static final int SHAKE_SLOP_TIME_MS = 1000;
    private long mShakeTimestamp = 0;

    private final ShakeListener shakeListener;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final FusedLocationProviderClient fusedLocationClient;
    // private final SmsManager smsManager; // Removed: Using Intent-based SMS instead
    private final Context context;
    private String myLocation = "Location not available";

    public ShakeService(Context context, ShakeListener listener) {
        this.context = context;
        this.shakeListener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = (sensorManager != null) ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        if (accelerometer == null) {
            Log.e("ShakeService", "Accelerometer sensor is not available!");
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        // smsManager = SmsManager.getDefault(); // Removed: Using Intent-based SMS instead
    }

    public void startListening() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            Log.d("ShakeService", "Accelerometer registered successfully.");
        } else {
            Log.e("ShakeService", "Cannot register listener: accelerometer is null.");
        }
    }

    public void stopListening() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.d("ShakeService", "Sensor unregistered.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float gForce = (float) Math.sqrt((x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH));
            Log.d("ShakeService", "Acceleration Detected: " + gForce);

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                final long now = System.currentTimeMillis();
                if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    Log.d("ShakeService", "Shake detected too soon. Ignoring.");
                    return;
                }
                mShakeTimestamp = now;
                Log.d("ShakeService", "Shake detected! Triggering SOS...");
                if (shakeListener != null) {
                    shakeListener.onShake();
                }
                sendSOS();
            }
        }
    }

    private void sendSOS() {
        SharedPreferences sharedPreferences = SecureStorageManager.getEncryptedSharedPreferences(context);
        String userName = sharedPreferences.getString("USERNAME", "Unknown User");
        String incidentType = sharedPreferences.getString("INCIDENT_TYPE", "Unspecified Emergency");

        SOSHelper.sendSOSFromService((ServiceMine) context, fusedLocationClient, userName, incidentType);
    }



    private void sendSMS(String emergencyNumber, String locationMessage) {
        // Deprecated: route all SMS sending through SOSHelper to centralize permission checks
        try {
            new com.example.bilawoga.utils.SOSHelper(context).testSMSSending(emergencyNumber);
        } catch (Exception e) {
            Log.e("ShakeService", "Failed to trigger SMS via SOSHelper.", e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used here
    }

    public String getMyLocation() {
        return myLocation;
    }

    public void setMyLocation(String myLocation) {
        this.myLocation = myLocation;
    }

    public interface ShakeListener {
        void onShake();
    }
}
