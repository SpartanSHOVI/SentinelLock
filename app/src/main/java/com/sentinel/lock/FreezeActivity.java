package com.sentinel.lock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FreezeActivity extends AppCompatActivity {
    private boolean isVerified = false;
    private boolean isIntruder = false;
    private View root;
    private TextView statusText;
    private ProgressBar loadingBar;
    private final Handler flashHandler = new Handler(Looper.getMainLooper());
    private boolean flashState = false;

    // Pattern: LU, LU, RU, LD, LD, RD (Left Up 2x, Right Up 1x, Left Down 2x, Right Down 1x)
    private final List<Integer> secretPattern = Arrays.asList(
            R.id.tap_left_up, R.id.tap_left_up,
            R.id.tap_right_up,
            R.id.tap_left_down, R.id.tap_left_down,
            R.id.tap_right_down
    );
    private final List<Integer> currentAttempt = new ArrayList<>();

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.sentinel.lock.VERIFIED".equals(action)) {
                isVerified = true;
                finish();
            } else if ("com.sentinel.lock.INTRUDER".equals(action)) {
                triggerFlashbang();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make activity transparent to show homescreen underneath
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_freeze);
        
        root = findViewById(R.id.freezeRoot);
        statusText = findViewById(R.id.statusText);
        loadingBar = findViewById(R.id.loadingBar);

        setupPatternTaps();
        hideSystemUI();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.sentinel.lock.VERIFIED");
        filter.addAction("com.sentinel.lock.INTRUDER");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    private void setupPatternTaps() {
        View.OnClickListener listener = v -> {
            // Collect pattern
            currentAttempt.add(v.getId());
            if (currentAttempt.size() > secretPattern.size()) {
                currentAttempt.remove(0);
            }

            if (currentAttempt.equals(secretPattern)) {
                isVerified = true;
                finish();
            } else if (!isIntruder) {
                // If not already in flashbang mode, any tap that doesn't complete the pattern triggers it
                triggerFlashbang();
            }
        };

        findViewById(R.id.tap_left_up).setOnClickListener(listener);
        findViewById(R.id.tap_right_up).setOnClickListener(listener);
        findViewById(R.id.tap_left_down).setOnClickListener(listener);
        findViewById(R.id.tap_right_down).setOnClickListener(listener);

        // Also catch touches on the root to trigger flashbang if not on a button
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && !isIntruder) {
                // If they touch anywhere but the invisible corner buttons, trigger flashbang
                triggerFlashbang();
            }
            return false; // Let it pass to children
        });
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void triggerFlashbang() {
        if (isIntruder) return;
        isIntruder = true;

        boolean flashbangEnabled = getSharedPreferences("sentinel_settings", MODE_PRIVATE)
                .getBoolean("enable_flashbang", true);
        
        if (flashbangEnabled) {
            flashHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isVerified) return;
                    flashState = !flashState;
                    root.setBackgroundColor(flashState ? Color.WHITE : Color.argb(128, 0, 0, 0));
                    flashHandler.postDelayed(this, 125);
                }
            });
        } else {
            root.setBackgroundColor(Color.argb(200, 0, 0, 0));
        }
    }

    @Override
    public void onBackPressed() {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true; 
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {}
        flashHandler.removeCallbacksAndMessages(null);
    }
}
