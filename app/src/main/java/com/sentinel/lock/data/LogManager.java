package com.sentinel.lock.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LogManager {
    private static final String TAG = "LogManager";
    private static final String PREF_NAME = "sentinel_logs";
    private static final String KEY_STALKERS = "stalker_list_v2";
    private static final String KEY_FRIENDS = "friendly_list_v2";

    private final SharedPreferences prefs;
    private final Context context;

    public LogManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (getFriendlyUsers().isEmpty()) {
            addFriendlyUser("Owner (You)");
        }
    }

    public void addStalkerEntry(String reason, Bitmap image) {
        long now = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(now));
        String fileName = "stalker_" + timeStamp + "_" + (now % 1000) + ".jpg";
        String imagePath = saveToInternalStorage(image, fileName);
        
        String displayTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(now));
        String entry = reason + "|" + displayTime + "|" + (imagePath != null ? imagePath : "no_image");
        addEntry(KEY_STALKERS, entry);
    }

    public void addFriendlyEntry(String name, Bitmap image) {
        long now = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(now));
        String fileName = "friend_" + timeStamp + "_" + (now % 1000) + ".jpg";
        String imagePath = saveToInternalStorage(image, fileName);

        String displayTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(now));
        String entry = name + "|" + displayTime + "|" + (imagePath != null ? imagePath : "no_image");
        addEntry(KEY_FRIENDS, entry);
    }

    private String saveToInternalStorage(Bitmap bitmapImage, String fileName) {
        if (bitmapImage == null) return null;
        File directory = new File(context.getFilesDir(), "captured_faces");
        if (!directory.exists()) directory.mkdirs();

        File file = new File(directory, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bitmapImage.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    public void addFriendlyUser(String name) {
        Set<String> users = new HashSet<>(prefs.getStringSet("friendly_users_list", new HashSet<>()));
        users.add(name);
        prefs.edit().putStringSet("friendly_users_list", users).apply();
    }

    public List<String> getFriendlyUsers() {
        return new ArrayList<>(prefs.getStringSet("friendly_users_list", new HashSet<>()));
    }

    public List<LogEntry> getStalkerLogs() {
        return parseLogs(KEY_STALKERS, false);
    }

    public List<LogEntry> getFriendlyLogs() {
        return parseLogs(KEY_FRIENDS, true);
    }

    private List<LogEntry> parseLogs(String key, boolean isFriendly) {
        Set<String> rawLogs = prefs.getStringSet(key, new HashSet<>());
        List<LogEntry> entries = new ArrayList<>();
        for (String raw : rawLogs) {
            String[] parts = raw.split("\\|");
            if (isFriendly && parts.length >= 3) {
                // Friendly: name | time | image | uniqueID
                entries.add(new LogEntry(parts[0], parts[1], parts[2]));
            } else if (!isFriendly && parts.length >= 3) {
                // Stalker: reason | time | image | uniqueID
                entries.add(new LogEntry(parts[0], parts[1], parts[2]));
            } else if (!isFriendly && parts.length == 2) {
                // Backward compatibility for old stalker logs
                entries.add(new LogEntry("Intruder", parts[0], parts[1]));
            }
        }
        Collections.sort(entries, (a, b) -> b.timestamp.compareTo(a.timestamp));
        return entries;
    }

    private void addEntry(String key, String value) {
        Set<String> logs = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
        // Add a unique suffix to prevent Set from ignoring entries in the same second
        String uniqueEntry = value + "|" + System.currentTimeMillis();
        logs.add(uniqueEntry);
        prefs.edit().putStringSet(key, logs).commit(); // Use commit for immediate cross-component visibility
        Log.d(TAG, "Log entry added: " + uniqueEntry);
    }

    public void clearLogs() {
        prefs.edit().remove(KEY_STALKERS).remove(KEY_FRIENDS).apply();
        File directory = new File(context.getFilesDir(), "captured_faces");
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }

    public static class LogEntry {
        public String name;
        public String timestamp;
        public String imagePath;

        public LogEntry(String name, String timestamp, String imagePath) {
            this.name = name;
            this.timestamp = timestamp;
            this.imagePath = imagePath;
        }
    }
}
