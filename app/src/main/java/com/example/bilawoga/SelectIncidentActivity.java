package com.example.bilawoga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bilawoga.utils.SecureStorageManager;

public class SelectIncidentActivity extends AppCompatActivity {

    private Spinner incidentSpinner;
    private EditText manualIncidentEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_incident);

        incidentSpinner = findViewById(R.id.incidentSpinner);
        manualIncidentEditText = findViewById(R.id.manualIncidentEditText);

        // Setup Spinner with enhanced options
        String[] incidents = {
                "Select Incident Type",
                "No Current Incident - Setup for Future",
                "Abduction",
                "Sexual Assault / Harassment",
                "Domestic Violence",
                "Medical Emergency",
                "Physical Assault",
                "Stalking",
                "Other Emergency"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, incidents);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        incidentSpinner.setAdapter(adapter);
        
        // Set up spinner selection listener to show/hide manual input
        incidentSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString();
                
                if (selectedItem.equals("No Current Incident - Setup for Future")) {
                    // Hide manual input for setup users
                    manualIncidentEditText.setVisibility(View.GONE);
                    manualIncidentEditText.setHint("Setup mode - no current emergency");
                } else if (selectedItem.equals("Other Emergency")) {
                    // Show manual input for custom emergencies
                    manualIncidentEditText.setVisibility(View.VISIBLE);
                    manualIncidentEditText.setHint("Please describe your emergency...");
                } else if (!selectedItem.equals("Select Incident Type")) {
                    // Hide manual input for predefined incidents
                    manualIncidentEditText.setVisibility(View.GONE);
                    manualIncidentEditText.setHint("Or describe your emergency...");
                } else {
                    // Show manual input for initial state
                    manualIncidentEditText.setVisibility(View.VISIBLE);
                    manualIncidentEditText.setHint("Or describe your emergency...");
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                manualIncidentEditText.setVisibility(View.VISIBLE);
                manualIncidentEditText.setHint("Or describe your emergency...");
            }
        });
    }

    public void confirmIncident(View view) {
        String selectedIncident = incidentSpinner.getSelectedItem().toString();
        String manualIncident = manualIncidentEditText.getText().toString().trim();

        // Handle "No Current Incident" selection
        if (selectedIncident.equals("No Current Incident - Setup for Future")) {
            // Save as setup mode
            SharedPreferences sharedPreferences = SecureStorageManager.getEncryptedSharedPreferences(this);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("INCIDENT_TYPE", "Setup Mode - No Current Emergency");
            myEdit.putBoolean("IS_SETUP_MODE", true);
            myEdit.apply();

            Toast.makeText(this, "App setup complete! BilaWoga is ready for future emergencies.", Toast.LENGTH_LONG).show();

            // Go to main activity
            Intent intent = new Intent(SelectIncidentActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Handle other selections
        if (selectedIncident.equals("Select Incident Type") && manualIncident.isEmpty()) {
            Toast.makeText(this, "Please select or describe your emergency!", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalIncident = "";
        if (!manualIncident.isEmpty()) {
            finalIncident = manualIncident;
        } else {
            finalIncident = selectedIncident;
        }

        // Save the incident
        SharedPreferences sharedPreferences = SecureStorageManager.getEncryptedSharedPreferences(this);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();
        myEdit.putString("INCIDENT_TYPE", finalIncident);
        myEdit.putBoolean("IS_SETUP_MODE", false);
        myEdit.apply();

        Toast.makeText(this, "Incident saved: " + finalIncident, Toast.LENGTH_SHORT).show();

        // Go to main activity
        Intent intent = new Intent(SelectIncidentActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public void goBack(View view) {
        // Go back to previous activity
        finish();
    }
}

