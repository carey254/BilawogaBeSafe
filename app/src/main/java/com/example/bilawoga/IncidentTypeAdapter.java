package com.example.bilawoga;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class IncidentTypeAdapter extends ArrayAdapter<String> {
    private final String[] items;
    private final LayoutInflater inflater;

    public IncidentTypeAdapter(@NonNull Context context, @NonNull String[] items) {
        super(context, R.layout.spinner_item, items);
        this.items = items;
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = inflater.inflate(R.layout.spinner_item, parent, false);
        TextView text = view.findViewById(R.id.spinnerText);
        text.setText(items[position]);
        return view;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = inflater.inflate(R.layout.spinner_dropdown_item, parent, false);
        TextView text = view.findViewById(R.id.spinnerDropdownText);
        text.setText(items[position]);
        return view;
    }
}
