package com.example.bilawoga;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

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
    private final SmsManager smsManager;
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
        smsManager = SmsManager.getDefault();
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
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);
        String emergencyNumber = sharedPreferences.getString("ENUM", "NONE");

        if (emergencyNumber.equalsIgnoreCase("NONE")) {
            Log.e("ShakeService", "Emergency number not set!");
            Toast.makeText(context, "Emergency number not set!", Toast.LENGTH_LONG).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            Log.d("ShakeService", "SMS permission granted. Proceeding to get location and send SMS.");
            getLastLocationAndSendSMS(emergencyNumber);
        } else {
            Log.e("ShakeService", "SMS permission not granted!");
            Toast.makeText(context, "SMS permission denied. Cannot send SOS.", Toast.LENGTH_LONG).show();
        }
    }

    private void getLastLocationAndSendSMS(String emergencyNumber) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ShakeService", "Location permissions are not granted!");
            Toast.makeText(context, "Location permission denied.", Toast.LENGTH_LONG).show();
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                myLocation = "http://maps.google.com/maps?q=loc:" + location.getLatitude() + "," + location.getLongitude();
                Log.d("ShakeService", "Location retrieved: " + myLocation);
            } else {
                myLocation = "Unable to find location :(";
                Log.e("ShakeService", "Failed to retrieve location.");
            }
            sendSMS(emergencyNumber, myLocation);
        }).addOnFailureListener(e -> {
            Log.e("ShakeService", "Location retrieval failed: " + e.getMessage());
            sendSMS(emergencyNumber, "Unable to retrieve location, but I need help urgently!");
        });
    }

    private void sendSMS(String emergencyNumber, String locationMessage) {
        try {
            smsManager.sendTextMessage(emergencyNumber, null, locationMessage, null, null);
            Log.d("ShakeService", "SOS message sent to: " + emergencyNumber);
            Toast.makeText(context, "SOS sent successfully!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("ShakeService", "Failed to send SOS message.", e);
            Toast.makeText(context, "Failed to send SOS!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used here
    }

    public interface ShakeListener {
        void onShake();
    }
}
