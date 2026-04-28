package com.sentinel.lock.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.Camera;
import androidx.camera.core.Preview;
import com.google.common.util.concurrent.ListenableFuture;

import com.sentinel.lock.ml.FaceAnalyzer;

import androidx.camera.core.ImageProxy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that starts CameraX and the ML pipeline on start.
 */
public class SentinelService extends LifecycleService {
    private static final String TAG = "SentinelService";
    private static final String CHANNEL_ID = "sentinel_core_channel";

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private FaceAnalyzer faceAnalyzer;
    private Camera camera;

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Service stopReceiver action: " + action);
            if ("com.sentinel.lock.STOP_VERIFICATION".equals(action)) {
                stopCameraVerification();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.i(TAG, "Screen ON - Resetting and starting fresh verification");
                
                // CRITICAL: Reset state for EVERY new attempt
                ownerVerified = false;
                if (faceAnalyzer != null) faceAnalyzer.reset();
                
                // Always attempt to start analysis on screen on
                isScanning = true;
                startCameraAndAnalysis();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.i(TAG, "User unlocked - Checking verification for troll logic");
                if (!isOwnerVerified()) {
                    Log.w(TAG, "Intruder detected on unlock! Launching FreezeActivity on Home Screen.");
                    
                    // Capture photo for logs before showing the freeze
                    if (faceAnalyzer != null) {
                        faceAnalyzer.captureAndLogIntruder("Unverified Unlock");
                    }

                    Intent freeze = new Intent(context, com.sentinel.lock.FreezeActivity.class);
                    freeze.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | 
                                   Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                   Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    context.startActivity(freeze);
                }

                // Stop camera so it's ready for the next wake cycle
                stopCameraVerification();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // Register receiver for stopping verification and Screen ON/User Present events
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.sentinel.lock.STOP_VERIFICATION");
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        
        // Use RECEIVER_EXPORTED for system broadcasts to ensure reception across OS versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android Core UI")
                .setContentText("System security active")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(101, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            } else {
                startForeground(101, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        faceAnalyzer = new FaceAnalyzer(this);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
    }

    private boolean isScanning = false;
    private static boolean ownerVerified = false;

    public static void setOwnerVerified(boolean verified) {
        ownerVerified = verified;
    }

    public static boolean isOwnerVerified() {
        return ownerVerified;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand received");
        super.onStartCommand(intent, flags, startId);
        
        // If the service is just starting or being pinged, ensure it's scanning
        if (!isScanning) {
            isScanning = true;
            startCameraAndAnalysis();
        }
        
        return START_STICKY;
    }

    private final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timeoutRunnable = () -> {
        if (!ownerVerified) {
            Log.w(TAG, "Verification timeout - triggering intruder alert");
            sendBroadcast(new Intent("com.sentinel.lock.INTRUDER"));
        }
        stopCameraVerification();
    };

    private void startCameraAndAnalysis() {
        Log.i(TAG, "Waking up camera for verification...");
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (isScanning) {
                        faceAnalyzer.onImageProxy(image);
                    } else {
                        image.close();
                    }
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                cameraProvider.unbindAll();
                // Bind to LifecycleService lifecycle
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
                
                Log.d(TAG, "Camera bound successfully to service lifecycle. Lifecycle State: " + getLifecycle().getCurrentState());

                // Clear any existing timeout and start a new one
                timeoutHandler.removeCallbacks(timeoutRunnable);
                timeoutHandler.postDelayed(timeoutRunnable, 20000); // 20 seconds window

            } catch (Exception e) {
                Log.e(TAG, "Verification wakeup failed", e);
                isScanning = false;
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    private void stopCameraVerification() {
        Log.i(TAG, "Verification window closed, camera going back to sleep.");
        timeoutHandler.removeCallbacks(timeoutRunnable);
        try {
            if (cameraProviderFuture.isDone()) {
                cameraProviderFuture.get().unbindAll();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error putting camera to sleep", e);
        }
        isScanning = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stopReceiver);
        } catch (Exception ignored) {}
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "System Security", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Background security protection");
                nm.createNotificationChannel(channel);
            }
        }
    }
}
