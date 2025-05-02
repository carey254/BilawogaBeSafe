package com.example.bilawoga;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float accelValue;
    private float accelLast;
    private float shake;
    private FusedLocationProviderClient fusedLocationClient;
    private String userName;
    private String ENUM;

    private final ActivityResultLauncher<String[]> multiplePermissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            new ActivityResultCallback<Map<String, Boolean>>() {
                @Override
                public void onActivityResult(Map<String, Boolean> result) {
                    boolean allGranted = true;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allGranted = false;
                            Snackbar snackbar = Snackbar.make(
                                    findViewById(android.R.id.content),
                                    "Permission must be granted!",
                                    Snackbar.LENGTH_INDEFINITE
                            );
                            snackbar.setAction("Grant Permission", v -> {
                                multiplePermissions.launch(new String[]{entry.getKey()});
                                snackbar.dismiss();
                            });
                            snackbar.show();
                        }
                    }
                    if (allGranted) {
                        startServiceV(null);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        checkUserInfo();
        createNotificationChannel();
        initializeSensors();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "MYID",
                    "CHANNEL FOREGROUND",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        accelValue = SensorManager.GRAVITY_EARTH;
        accelLast = SensorManager.GRAVITY_EARTH;
        shake = 0.00f;
    }

    private void checkUserInfo() {
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        userName = sharedPreferences.getString("USERNAME", "NONE");
        ENUM = sharedPreferences.getString("ENUM", "NONE");

        if (userName.equals("NONE")) {
            requestUserInfo();
        } else if (ENUM.equals("NONE")) {
            startActivity(new Intent(this, RegisterNumberActivity.class));
        } else {
            displayEmergencyNumber();
        }
    }

    @SuppressLint("SetTextI18n")
    private void displayEmergencyNumber() {
        TextView textView = findViewById(R.id.textNum);
        textView.setText("SOS will be sent to\n" + ENUM);
    }

    private void requestUserInfo() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("User Information")
                .setMessage("Please enter your name:")
                .setCancelable(false);

        final EditText input = new EditText(this);
        input.setHint("Name");
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            userName = input.getText().toString().trim();
            if (!userName.isEmpty()) {
                saveUserInfo(userName);
                startActivity(new Intent(this, RegisterNumberActivity.class));
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Name cannot be empty", Snackbar.LENGTH_LONG).show();
            }
        }).setNegativeButton("Cancel", (dialog, which) -> dialog.cancel()).show();
    }

    private void saveUserInfo(String name) {
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("USERNAME", name);
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        ENUM = sharedPreferences.getString("ENUM", "NONE");
        if (!ENUM.equalsIgnoreCase("NONE")) {
            displayEmergencyNumber();
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            accelLast = accelValue;
            accelValue = (float) Math.sqrt(x * x + y * y + z * z);
            float delta = accelValue - accelLast;
            shake = shake * 0.9f + delta;

            if (shake > 12) {
                sendSOS();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Can be left empty
    }

    public void stopService(View view) {
        Intent notificationIntent = new Intent(this, ServiceMine.class);
        notificationIntent.setAction("stop");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(notificationIntent);
            Snackbar.make(findViewById(android.R.id.content), "Service Stopped!", Snackbar.LENGTH_LONG).show();
        }
    }

    public void startServiceV(View view) {
        if (checkPermissions()) {
            Intent notificationIntent = new Intent(this, ServiceMine.class);
            notificationIntent.setAction("Start");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplicationContext().startForegroundService(notificationIntent);
                Snackbar.make(findViewById(android.R.id.content), "Service Started!", Snackbar.LENGTH_LONG).show();
            }
        } else {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        multiplePermissions.launch(new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        });
    }

    public void showChangeNumberMenu(View view) {
        Log.d("ChangeNumber", "Change Number button clicked!");
        Intent intent = new Intent(MainActivity.this, RegisterNumberActivity.class);
        startActivity(intent);
    }

    private boolean checkLocationPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void sendSOS() {
        if (!checkPermissions()) {
            requestPermissions();
            return;
        }

        if (!checkLocationPermissions()) {
            requestPermissions();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(findViewById(android.R.id.content), "Location permissions are required!", Snackbar.LENGTH_LONG).show();
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        String locationLink = "https://maps.google.com/?q=" + latitude + "," + longitude;

                        // Run Geocoder in background
                        new Thread(() -> {
                            String addressLine = getAddressFromLocation(latitude, longitude);

                            runOnUiThread(() -> {
                                SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
                                String userName = sharedPreferences.getString("USERNAME", "Someone");
                                String incidentType = sharedPreferences.getString("INCIDENT_TYPE", "an emergency");
                                String emergencyNumber = sharedPreferences.getString("ENUM", "NONE");

                                if (!emergencyNumber.equals("NONE")) {

                                    String locationDescription;
                                    if (addressLine == null || addressLine.isEmpty() || addressLine.contains("Unnamed Road") || addressLine.equalsIgnoreCase("Unknown location")) {
                                        locationDescription = "somewhere in Kenya"; // fallback message
                                    } else {
                                        locationDescription = addressLine;
                                    }


                                    // 🚨 FINAL CORRECT MESSAGE:
                                    String message = "🚨 My name is " + userName +
                                            ". I am experiencing " + incidentType +
                                            " near " + locationDescription +
                                            ".\n📍 Find my location here: " + locationLink;


                                    try {
                                        SmsManager smsManager = SmsManager.getDefault();
                                        smsManager.sendTextMessage(emergencyNumber, null, message, null, null);
                                        Snackbar.make(findViewById(android.R.id.content), "🚨 SOS Sent Successfully!", Snackbar.LENGTH_LONG).show();
                                    } catch (Exception e) {
                                        Snackbar.make(findViewById(android.R.id.content), "Failed to send SOS: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                                    }
                                } else {
                                    Snackbar.make(findViewById(android.R.id.content), "No emergency number saved!", Snackbar.LENGTH_LONG).show();
                                }
                            });
                        }).start();

                    } else {
                        Snackbar.make(findViewById(android.R.id.content), "Location not found. Try again.", Snackbar.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(findViewById(android.R.id.content), "Error getting location: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
    }


    private String getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);

                String area = (address.getSubLocality() != null) ? address.getSubLocality() : "";
                String city = (address.getLocality() != null) ? address.getLocality() : "";
                String region = (address.getAdminArea() != null) ? address.getAdminArea() : "";

                // Build location nicely even if some parts missing
                StringBuilder locationBuilder = new StringBuilder();
                if (!area.isEmpty()) {
                    locationBuilder.append(area).append(", ");
                }
                if (!city.isEmpty()) {
                    locationBuilder.append(city).append(", ");
                }
                if (!region.isEmpty()) {
                    locationBuilder.append(region);
                }

                return locationBuilder.toString().trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Unknown location";
    }
}



