package com.sentinel.lock;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Setup screen for SentinelLock. 
 * Handles essential permissions (Overlay, Camera, and Notifications).
 */
public class SetupActivity extends AppCompatActivity {
    private static final String TAG = "SetupActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_setup);
            setupNavigationDrawer();
            initUI();
            checkAndRequestPermissions();
        } catch (Exception e) {
            Log.e(TAG, "Failed to inflate layout", e);
            finish();
        }
    }

    private void setupNavigationDrawer() {
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        com.google.android.material.navigation.NavigationView navigationView = findViewById(R.id.navigation_view);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_enroll) {
                startActivity(new Intent(this, EnrollActivity.class));
            } else if (id == R.id.nav_dashboard) {
                startActivity(new Intent(this, DashboardActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
            return true;
        });
    }

    private void checkAndRequestPermissions() {
        updatePermissionStates();
        // 1. Check Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Required")
                    .setMessage("SentinelLock needs to draw over other apps to show the lock screen. Please grant this permission in the next screen.")
                    .setPositiveButton("Grant", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return; 
        }

        // 2. Battery Optimization (for Always-On)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Background Guardian")
                        .setMessage("To ensure Sentinel is always active on every unlock, please disable battery optimization for this app.")
                        .setPositiveButton("Configure", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }

        // 3. Check Device Admin
        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName componentName = new android.content.ComponentName(this, com.sentinel.lock.admin.SentinelDeviceAdmin.class);
        if (!dpm.isAdminActive(componentName)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Admin Access Required")
                    .setMessage("To capture photos when the wrong password is entered, SentinelLock needs Device Admin access.")
                    .setPositiveButton("Grant Admin", (dialog, which) -> {
                        Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Detects failed password attempts to catch intruders.");
                        startActivity(intent);
                    })
                    .setNegativeButton("Skip", null)
                    .show();
            return;
        }

        // 3. Runtime Permissions (Camera, Notifications, and Media)
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage("Camera, Notification, and Media permissions are needed for the security features and dashboard to work.")
                    .setPositiveButton("Allow", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 1234);
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    private void initUI() {
        Button overlayBtn = findViewById(R.id.grantOverlayBtn);
        if (overlayBtn != null) {
            overlayBtn.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Manual permission grant required", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Button cameraBtn = findViewById(R.id.grantCameraBtn);
        if (cameraBtn != null) {
            cameraBtn.setOnClickListener(v -> {
                List<String> permissions = new ArrayList<>();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.CAMERA);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS);
                    }
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                    }
                }
                
                if (!permissions.isEmpty()) {
                    ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 1234);
                } else {
                    Toast.makeText(this, "All core permissions granted", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Button adminBtn = findViewById(R.id.grantAdminBtn);
        if (adminBtn != null) {
            adminBtn.setOnClickListener(v -> {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName componentName = new android.content.ComponentName(this, com.sentinel.lock.admin.SentinelDeviceAdmin.class);
                if (!dpm.isAdminActive(componentName)) {
                    Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                    intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "SentinelLock needs admin privileges to detect failed password attempts.");
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Device Admin is already active", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Button enrollBtn = findViewById(R.id.launchEnroll);
        if (enrollBtn != null) {
            enrollBtn.setOnClickListener(v -> {
                Intent i = new Intent(this, EnrollActivity.class);
                startActivity(i);
            });
        }

        Button dashBtn = findViewById(R.id.viewDashboard);
        if (dashBtn != null) {
            dashBtn.setOnClickListener(v -> {
                Intent i = new Intent(this, DashboardActivity.class);
                startActivity(i);
            });
        }

        Button activateBtn = findViewById(R.id.activateSentinel);
        if (activateBtn != null) {
            activateBtn.setOnClickListener(v -> {
                // Ensure overlay is granted
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Please grant Overlay permission first!", Toast.LENGTH_LONG).show();
                    return;
                }
                
                Intent svc = new Intent(this, com.sentinel.lock.services.SentinelService.class);
                ContextCompat.startForegroundService(this, svc);
                
                // UX: Visual feedback on activation
                activateBtn.setText("SENTINEL ACTIVE");
                activateBtn.setEnabled(false);
                activateBtn.setAlpha(0.5f);
                
                Toast.makeText(this, "Sentinel Protection Activated!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updatePermissionStates() {
        Button overlayBtn = findViewById(R.id.grantOverlayBtn);
        if (overlayBtn != null && Settings.canDrawOverlays(this)) {
            overlayBtn.setText("GRANTED");
            overlayBtn.setEnabled(false);
            overlayBtn.setTextColor(ContextCompat.getColor(this, R.color.accent));
        }

        Button cameraBtn = findViewById(R.id.grantCameraBtn);
        if (cameraBtn != null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraBtn.setText("GRANTED");
            cameraBtn.setEnabled(false);
            cameraBtn.setTextColor(ContextCompat.getColor(this, R.color.accent));
        }

        Button adminBtn = findViewById(R.id.grantAdminBtn);
        if (adminBtn != null) {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName componentName = new android.content.ComponentName(this, com.sentinel.lock.admin.SentinelDeviceAdmin.class);
            if (dpm.isAdminActive(componentName)) {
                adminBtn.setText("GRANTED");
                adminBtn.setEnabled(false);
                adminBtn.setTextColor(ContextCompat.getColor(this, R.color.accent));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStates();
    }
}
