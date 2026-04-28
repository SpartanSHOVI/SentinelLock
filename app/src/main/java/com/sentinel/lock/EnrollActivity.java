package com.sentinel.lock;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.sentinel.lock.ml.FaceAnalyzer;
import com.sentinel.lock.services.SentinelService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enrollment activity: shows front camera preview and captures multiple embeddings,
 * averages them and saves the owner embedding securely using FaceAnalyzer.saveOwnerEmbedding().
 */
public class EnrollActivity extends AppCompatActivity {
    private static final String TAG = "EnrollActivity";
    private static final int REQ_CAMERA = 4321;
    private static final int ENROLL_FRAMES_DEFAULT = 10;
    private static final int ENROLL_INTERVAL_DEFAULT_MS = 500;
    private static final int MIN_FRAMES = 3;
    private static final int MAX_FRAMES = 60;
    private static final int MIN_INTERVAL_MS = 100;
    private static final int MAX_INTERVAL_MS = 5000;

    private PreviewView previewView;
    private Button enrollBtn;
    private TextView statusText;
    private ProgressBar progressBar;

    private FaceAnalyzer faceAnalyzer;
    private ExecutorService cameraExecutor;
    private List<float[]> embeddings = new ArrayList<>();
    private volatile boolean enrolling = false;
    private volatile boolean isBusy = false;
    private int targetFrames = ENROLL_FRAMES_DEFAULT;
    private int captureIntervalMs = ENROLL_INTERVAL_DEFAULT_MS;
    private volatile long lastCaptureTime = 0L;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enroll);

        previewView = findViewById(R.id.previewView);
        enrollBtn = findViewById(R.id.enrollBtn);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(targetFrames);

        faceAnalyzer = new FaceAnalyzer(this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        enrollBtn.setOnClickListener(v -> {
            if (!enrolling) {
                embeddings.clear();
                enrolling = true;
                enrollBtn.setText("STOP SCANNING");
                statusText.setText("ALIGN FACE IN FRAME");
                progressBar.setProgress(0);
                progressBar.setMax(targetFrames);
                
                // Dim HUD
                findViewById(R.id.scanningOverlay).setVisibility(android.view.View.GONE);
            } else {
                enrolling = false;
                enrollBtn.setText("START SCANNING");
                statusText.setText("Cancelled");
                findViewById(R.id.scanningOverlay).setVisibility(android.view.View.VISIBLE);
            }
        });

        // Request camera permission if needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Unable to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (!enrolling) {
                imageProxy.close();
                return;
            }
            long now = System.currentTimeMillis();
            long elapsed = now - lastCaptureTime;
            long remaining = captureIntervalMs - elapsed;
            if (remaining > 0 && lastCaptureTime != 0L) {
                // update status and skip this frame
                imageProxy.close();
                return;
            }

            if (isBusy) {
                imageProxy.close();
                return;
            }

            // time to capture
            lastCaptureTime = now;
            isBusy = true;

            faceAnalyzer.computeEmbedding(imageProxy, new FaceAnalyzer.EmbeddingListener() {
                @Override
                public void onEmbeddingComputed(float[] embedding) {
                    runOnUiThread(() -> {
                        embeddings.add(embedding);
                        statusText.setText("Enrolling: " + embeddings.size() + "/" + targetFrames);
                        progressBar.setProgress(embeddings.size());
                        isBusy = false;
                        if (embeddings.size() >= targetFrames) {
                            enrolling = false;
                            enrollBtn.setText("Start Enrollment");
                            finishEnrollment();
                        }
                    });
                }

                @Override
                public void onError(String reason) {
                    runOnUiThread(() -> {
                        statusText.setText("Frame failed: " + reason);
                        isBusy = false;
                    });
                }
            });
        });

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void finishEnrollment() {
        if (embeddings.isEmpty()) {
            Toast.makeText(this, "No embeddings captured", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.i(TAG, "Finishing enrollment, averaging " + embeddings.size() + " frames");
        
        // 1. Force stop camera IMMEDIATELY and shut down executor
        try {
            if (cameraProviderFuture != null && cameraProviderFuture.isDone()) {
                cameraProviderFuture.get().unbindAll();
            }
            if (cameraExecutor != null) {
                cameraExecutor.shutdownNow();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unbinding camera", e);
        }

        // 2. Process data
        int len = embeddings.get(0).length;
        float[] avg = new float[len];
        for (float[] e : embeddings) {
            for (int i = 0; i < len; i++) {
                if (e != null && i < e.length) {
                    avg[i] += e[i];
                }
            }
        }
        for (int i = 0; i < len; i++) avg[i] /= embeddings.size();

        faceAnalyzer.saveOwnerEmbedding(avg);
        Toast.makeText(this, "Enrollment complete!", Toast.LENGTH_SHORT).show();

        // 3. Start the service with a long delay using the APPLICATION context
        final android.content.Context appContext = getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Log.i(TAG, "Handover: Starting service now...");
            try {
                Intent svc = new Intent(appContext, SentinelService.class);
                androidx.core.content.ContextCompat.startForegroundService(appContext, svc);
            } catch (Exception e) {
                Log.e(TAG, "Service handover failed", e);
            }
        }, 3000);

        // 4. Close the activity now to free up resources
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required for enrollment", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
