package com.sentinel.lock.overlay;

import android.content.Context;
import android.util.Log;

/**
 * Placeholder manager for anchor + trap overlays. Real implementation requires
 * SYSTEM_ALERT_WINDOW and careful touch handling.
 */
public class TrapOverlayManager {
    private final Context context;

    public TrapOverlayManager(Context context) {
        this.context = context;
    }

    public void showAnchorOverlay() {
        // create 1x1 transparent window anchored to screen (placeholder)
        Log.i("TrapOverlayManager", "showAnchorOverlay()");
    }

    public void showTrapOverlay() {
        // show full-screen trap (white, absorb touches, vibrate) (placeholder)
        Log.i("TrapOverlayManager", "showTrapOverlay()");
    }

    public void removeOverlays() {
        // remove any overlays shown (placeholder)
        Log.i("TrapOverlayManager", "removeOverlays()");
    }
}
