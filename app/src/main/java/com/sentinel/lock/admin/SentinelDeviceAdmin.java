package com.sentinel.lock.admin;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.sentinel.lock.services.SentinelService;

public class SentinelDeviceAdmin extends DeviceAdminReceiver {
    private static final String TAG = "SentinelDeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin disabled");
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        Log.w(TAG, "FAILED PASSWORD ATTEMPT DETECTED!");
        
        // Log the failure
        com.sentinel.lock.data.LogManager lm = new com.sentinel.lock.data.LogManager(context);
        lm.addStalkerEntry("Wrong Password", null);

        // Start the service immediately to capture the suspect
        Intent svc = new Intent(context, SentinelService.class);
        svc.putExtra("trigger", "password_failed");
        ContextCompat.startForegroundService(context, svc);
        
        // If it's not the owner, freeze them on the lockscreen immediately
        if (!SentinelService.isOwnerVerified()) {
            Log.i(TAG, "Intruder detected on lockscreen - Triggering Freeze");
            Intent freeze = new Intent(context, com.sentinel.lock.FreezeActivity.class);
            freeze.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                           Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                           Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(freeze);
        }
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        Log.i(TAG, "SUCCESSFUL PASSWORD ATTEMPT - Triggering Zero-Trust verification");
        
        // Start the service to begin camera verification
        Intent svc = new Intent(context, SentinelService.class);
        svc.putExtra("trigger", "password_succeeded");
        ContextCompat.startForegroundService(context, svc);
        
        // DO NOT launch freeze here; the UnlockReceiver handles the troll on homescreen
        // This allows the "Right Password -> Home Screen -> Freeze" troll flow.
    }
}
