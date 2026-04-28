package com.sentinel.lock;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;
import com.sentinel.lock.adapters.LogAdapter;
import com.sentinel.lock.data.LogManager;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {
    private LogManager logManager;
    private RecyclerView stalkerRecyclerView;
    private RecyclerView friendlyRecyclerView;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.action_theme) {
                // Cycle theme via slider logic
                int currentMode = getSharedPreferences("sentinel_settings", MODE_PRIVATE)
                        .getInt("theme_mode", 1);
                int nextMode = (currentMode + 1) % 3;
                getSharedPreferences("sentinel_settings", MODE_PRIVATE)
                        .edit().putInt("theme_mode", nextMode).apply();
                
                int nightMode;
                switch (nextMode) {
                    case 0: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
                    case 2: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
                    default: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
                }
                AppCompatDelegate.setDefaultNightMode(nightMode);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        logManager = new LogManager(this);
        stalkerRecyclerView = findViewById(R.id.stalkerRecyclerView);
        friendlyRecyclerView = findViewById(R.id.friendlyRecyclerView);
        Button clearBtn = findViewById(R.id.clearLogsBtn);

        stalkerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        friendlyRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        refreshLogs();

        clearBtn.setOnClickListener(v -> {
            logManager.clearLogs();
            refreshLogs();
        });
    }

    private void refreshLogs() {
        List<LogManager.LogEntry> stalkerEntries = logManager.getStalkerLogs();
        stalkerRecyclerView.setAdapter(new LogAdapter(stalkerEntries));

        List<LogManager.LogEntry> friendlyEntries = logManager.getFriendlyLogs();
        friendlyRecyclerView.setAdapter(new LogAdapter(friendlyEntries));
    }
}
