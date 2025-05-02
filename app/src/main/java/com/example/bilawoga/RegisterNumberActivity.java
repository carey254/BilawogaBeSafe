package com.example.bilawoga;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class RegisterNumberActivity extends AppCompatActivity {
    private EditText numberEdit, manualIncidentEditText;
    private Spinner incidentSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_number);

        numberEdit = findViewById(R.id.numberEdit);
        incidentSpinner = findViewById(R.id.incidentSpinner);
        manualIncidentEditText = findViewById(R.id.manualIncidentEditText);

        // Set up incident types
        String[] incidents = {"Select Incident Type", "Abduction", "Sexual Assault / Harassment", "Domestic Violence", "Medical Emergency"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, incidents);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        incidentSpinner.setAdapter(adapter);

        if (savedInstanceState != null) {
            numberEdit.setText(savedInstanceState.getString("number"));
        }
    }

    public void saveNumber(View view) {
        String numberString = numberEdit.getText().toString().trim();
        String selectedIncident = incidentSpinner.getSelectedItem().toString();
        String manualIncident = manualIncidentEditText.getText().toString().trim();

        if (!numberString.matches("^\\d{3,4}$") && !numberString.matches("\\d{10,12}")) {
            Toast.makeText(this, "Enter a valid emergency number (3, 4, 10, or 12 digits)!", Toast.LENGTH_SHORT).show();
            return;
        }

        String incident;
        if (!manualIncident.isEmpty()) {
            incident = manualIncident;
        } else if (!selectedIncident.equals("Select Incident Type")) {
            incident = selectedIncident;
        } else {
            Toast.makeText(this, "Please select or describe an emergency!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("ENUM", numberString);
            myEdit.putString("INCIDENT_TYPE", incident);
            myEdit.apply();

            Toast.makeText(this, "Emergency info saved!", Toast.LENGTH_SHORT).show();
            finish(); // back to MainActivity
        } catch (Exception e) {
            Toast.makeText(this, "Error saving information!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("number", numberEdit.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        numberEdit.setText(savedInstanceState.getString("number"));
    }
}
