package com.sentinel.lock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("sentinel_settings", MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 1. Biometric Threshold
        Slider thresholdSlider = findViewById(R.id.thresholdSlider);
        TextView thresholdValue = findViewById(R.id.thresholdValue);
        
        float currentThreshold = prefs.getFloat("biometric_threshold", 0.82f);
        thresholdSlider.setValue(currentThreshold);
        thresholdValue.setText("Current: " + String.format("%.2f", currentThreshold));

        thresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            prefs.edit().putFloat("biometric_threshold", value).apply();
            thresholdValue.setText("Current: " + String.format("%.2f", value));
        });

        // 2. Switches
        SwitchMaterial flashbangSwitch = findViewById(R.id.flashbangSwitch);
        SwitchMaterial vibrationSwitch = findViewById(R.id.vibrationSwitch);

        flashbangSwitch.setChecked(prefs.getBoolean("enable_flashbang", true));
        flashbangSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            prefs.edit().putBoolean("enable_flashbang", isChecked).apply());

        vibrationSwitch.setChecked(prefs.getBoolean("enable_vibration", true));
        vibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            prefs.edit().putBoolean("enable_vibration", isChecked).apply());

        // 3. Theme Slider
        Slider themeSlider = findViewById(R.id.themeSlider);
        TextView themeValue = findViewById(R.id.themeValue);
        
        int savedTheme = prefs.getInt("theme_mode", 1); // 0: Light, 1: System, 2: Dark
        themeSlider.setValue((float) savedTheme);
        updateThemeUI(themeValue, savedTheme);

        themeSlider.addOnChangeListener((slider, value, fromUser) -> {
            int mode = (int) value;
            prefs.edit().putInt("theme_mode", mode).apply();
            updateThemeUI(themeValue, mode);
            
            int nightMode;
            switch (mode) {
                case 0: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
                case 2: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
                default: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
            }
            AppCompatDelegate.setDefaultNightMode(nightMode);
        });

        // 4. Re-Enroll
        Button reEnrollBtn = findViewById(R.id.reEnrollBtn);
        reEnrollBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, EnrollActivity.class));
        });
    }

    private void updateThemeUI(TextView view, int mode) {
        String text;
        switch (mode) {
            case 0: text = "Mode: Tactical Light"; break;
            case 2: text = "Mode: Stealth Dark"; break;
            default: text = "Mode: System Synchronized"; break;
        }
        view.setText(text);
    }
}
