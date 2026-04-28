package com.sentinel.lock.ml;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.sentinel.lock.data.LogManager;
import com.sentinel.lock.services.SentinelService;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * FaceAnalyzer — The zero-trust AI gate.
 * Synchronized version using 512-D embeddings and rotation-safe cropping.
 */
public class FaceAnalyzer {
    private static final String TAG = "FaceAnalyzer";
    private static final String MODEL_FILE = "mobilefacenet.tflite";
    private static final int FACE_SIZE = 112;
    private static final int EMBEDDING_DIM = 512;
    private static final float DISTANCE_THRESHOLD = 0.90f; // Euclidean distance threshold

    private final Context context;
    private final FaceDetector detector;
    private Interpreter tflite;
    private final LogManager logManager;
    private final SharedPreferences prefs;
    private float[] ownerEmbedding;

    private int intruderConsecutiveFrames = 0;
    private static final int CONFIRMATION_THRESHOLD = 3;

    private boolean forceLogNextFrame = false;
    private String forceLogReason = "";

    public interface EmbeddingListener {
        void onEmbeddingComputed(float[] embedding);
        void onError(String reason);
    }

    public FaceAnalyzer(Context context) {
        this.context = context;
        this.logManager = new LogManager(context);
        this.prefs = context.getSharedPreferences("sentinel_identity", Context.MODE_PRIVATE);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        this.detector = FaceDetection.getClient(options);

        try {
            tflite = new Interpreter(FileUtil.loadMappedFile(context, MODEL_FILE));
            loadOwnerEmbedding();
            Log.d(TAG, "MobileFaceNet 512-D loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load TFLite model", e);
        }
    }

    private void loadOwnerEmbedding() {
        String saved = prefs.getString("owner_embedding", null);
        if (saved != null) {
            String[] parts = saved.split(",");
            if (parts.length == EMBEDDING_DIM) {
                ownerEmbedding = new float[EMBEDDING_DIM];
                for (int i = 0; i < EMBEDDING_DIM; i++) {
                    ownerEmbedding[i] = Float.parseFloat(parts[i]);
                }
            }
        }
    }

    public void saveOwnerEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        prefs.edit().putString("owner_embedding", sb.toString()).apply();
        this.ownerEmbedding = embedding;
    }

    public void computeEmbedding(ImageProxy imageProxy, EmbeddingListener listener) {
        if (tflite == null) {
            imageProxy.close();
            listener.onError("Model not initialized");
            return;
        }

        // 1. Convert ImageProxy to Bitmap using CameraX utility
        Bitmap bitmap = imageProxy.toBitmap();
        if (bitmap == null) {
            imageProxy.close();
            listener.onError("Bitmap conversion failed");
            return;
        }

        // 2. ML Kit detection on the bitmap (0 rotation because toBitmap handles it)
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        listener.onError("No face detected. Keep face centered.");
                    } else {
                        float[] emb = generateEmbedding(faces.get(0), bitmap);
                        if (emb != null) {
                            listener.onEmbeddingComputed(emb);
                        } else {
                            listener.onError("Face features could not be processed.");
                        }
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    listener.onError("Detection error: " + e.getMessage());
                    imageProxy.close();
                });
    }

    public void onImageProxy(ImageProxy imageProxy) {
        Bitmap bitmap = imageProxy.toBitmap();
        if (bitmap == null) {
            imageProxy.close();
            return;
        }

        if (forceLogNextFrame) {
            forceLogNextFrame = false;
            logManager.addStalkerEntry(forceLogReason, bitmap);
        }

        if (ownerEmbedding == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        processFace(faces.get(0), bitmap);
                    }
                    imageProxy.close();
                })
                .addOnFailureListener(e -> imageProxy.close());
    }

    public void captureAndLogIntruder(String reason) {
        forceLogNextFrame = true;
        forceLogReason = reason;
    }

    private void processFace(Face face, Bitmap fullBitmap) {
        float[] currentEmbedding = generateEmbedding(face, fullBitmap);
        if (currentEmbedding == null || ownerEmbedding == null) return;

        float distance = euclideanDistance(currentEmbedding, ownerEmbedding);
        Log.d(TAG, ">>> Euclidean Distance: " + String.format("%.4f", distance) + " (Target: <" + DISTANCE_THRESHOLD + ")");

        if (distance <= DISTANCE_THRESHOLD) {
            intruderConsecutiveFrames = 0;
            SentinelService.setOwnerVerified(true);
            context.sendBroadcast(new Intent("com.sentinel.lock.VERIFIED"));
            context.sendBroadcast(new Intent("com.sentinel.lock.STOP_VERIFICATION"));
        } else {
            intruderConsecutiveFrames++;
            if (intruderConsecutiveFrames >= CONFIRMATION_THRESHOLD) {
                Log.w(TAG, "INTRUDER ALERT - Score: " + distance);
                logManager.addStalkerEntry("Intruder Detected", fullBitmap);
                context.sendBroadcast(new Intent("com.sentinel.lock.INTRUDER"));
            }
        }
    }

    private float[] generateEmbedding(Face face, Bitmap bitmap) {
        try {
            Rect box = face.getBoundingBox();
            Log.d(TAG, "Generating embedding for box: " + box.toShortString() + " | Bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            // Add 15% padding
            int padding = (int) (Math.max(box.width(), box.height()) * 0.15f);
            int left = Math.max(0, box.left - padding);
            int top = Math.max(0, box.top - padding);
            int right = Math.min(bitmap.getWidth(), box.right + padding);
            int bottom = Math.min(bitmap.getHeight(), box.bottom + padding);

            int width = right - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid crop dimensions: " + width + "x" + height);
                return null;
            }

            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, FACE_SIZE, FACE_SIZE, true);

            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * FACE_SIZE * FACE_SIZE * 3 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());

            int[] pixels = new int[FACE_SIZE * FACE_SIZE];
            scaled.getPixels(pixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE);

            for (int pixel : pixels) {
                inputBuffer.putFloat((((pixel >> 16) & 0xFF) - 127.5f) / 128.0f);
                inputBuffer.putFloat((((pixel >> 8) & 0xFF) - 127.5f) / 128.0f);
                inputBuffer.putFloat(((pixel & 0xFF) - 127.5f) / 128.0f);
            }
            inputBuffer.rewind(); // CRITICAL: Reset buffer position for TFLite

            // Dynamically check output shape to avoid crashes
            int[] outputShape = tflite.getOutputTensor(0).shape();
            int outDim = outputShape[outputShape.length - 1];

            float[][] output = new float[1][outDim];
            tflite.run(inputBuffer, output);
            
            cropped.recycle();
            scaled.recycle();
            
            return l2Normalize(output[0]);
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Embedding generation failed!", e);
            return null;
        }
    }

    private float euclideanDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    private float[] l2Normalize(float[] v) {
        float norm = 0;
        for (float f : v) norm += f * f;
        norm = (float) Math.sqrt(norm);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
        return out;
    }
    
    public void reset() {
        intruderConsecutiveFrames = 0;
    }
}
