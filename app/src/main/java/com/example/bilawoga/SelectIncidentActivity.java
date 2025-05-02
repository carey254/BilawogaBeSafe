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

public class SelectIncidentActivity extends AppCompatActivity {

    private Spinner incidentSpinner;
    private EditText manualIncidentEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_incident);

        incidentSpinner = findViewById(R.id.incidentSpinner);
        manualIncidentEditText = findViewById(R.id.manualIncidentEditText);

        // Setup Spinner
        String[] incidents = {
                "Select Incident Type",
                "Abduction",
                "Sexual Assault / Harassment",
                "Domestic Violence",
                "Medical Emergency"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, incidents);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        incidentSpinner.setAdapter(adapter);
    }

    public void confirmIncident(View view) {
        String selectedIncident = incidentSpinner.getSelectedItem().toString();
        String manualIncident = manualIncidentEditText.getText().toString().trim();

        if (selectedIncident.equals("Select Incident Type") && manualIncident.isEmpty()) {
            Toast.makeText(this, "Please select or describe your emergency!", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalIncident;
        if (!manualIncident.isEmpty()) {
            finalIncident = manualIncident;
        } else {
            finalIncident = selectedIncident;
        }

        // Save the incident
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();
        myEdit.putString("INCIDENT_TYPE", finalIncident);
        myEdit.apply();

        Toast.makeText(this, "Incident saved: " + finalIncident, Toast.LENGTH_SHORT).show();

        // Optionally: move back to MainActivity or Home
        Intent intent = new Intent(SelectIncidentActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}

