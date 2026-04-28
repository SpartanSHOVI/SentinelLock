package com.sentinel.lock.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.sentinel.lock.services.SentinelService;

/**
 * BroadcastReceiver that listens for USER_PRESENT and BOOT_COMPLETED.
 * In the real app this starts the SentinelService on unlock events.
 */
public class UnlockReceiver extends BroadcastReceiver {
    private static final String TAG = "UnlockReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);
        
        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            // Screen turned on, start analysis in background
            Intent svc = new Intent(context, SentinelService.class);
            ContextCompat.startForegroundService(context, svc);
        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
            Log.i(TAG, "USER_PRESENT detected. Checking verification...");
            // User successfully unlocked.
            // Check verification. If NOT owner, FREEZE.
            if (!SentinelService.isOwnerVerified()) {
                Log.w(TAG, "Not verified! Launching FreezeActivity...");
                Intent freeze = new Intent(context, com.sentinel.lock.FreezeActivity.class);
                freeze.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                               Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                               Intent.FLAG_ACTIVITY_SINGLE_TOP |
                               Intent.FLAG_ACTIVITY_NO_ANIMATION);
                context.startActivity(freeze);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Ensure service starts on boot and stays in background
            Intent svc = new Intent(context, SentinelService.class);
            ContextCompat.startForegroundService(context, svc);
        }
    }
}
