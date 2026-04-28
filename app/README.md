# SentinelLock (app module)

This folder contains a minimal Android app skeleton matching the README at /files/README.md. Replace placeholder files with full implementations before building.
lets suppost there are two kinds of unlockers
1. Owner and 2. Spy or stalker.
   if owner is captured pass our criteria and give homescreen directly
   if spy or stalker captured and they try to unlock phone (with right or wrong password) it get freezed the screen on homescreen (if right password) and freezed to lockscreen (if wrong password)



# SentinelLock
### Zero-Trust Biometric Gatekeeper for Android

---lets suppost there are two kinds of unlockers
1. Owner and 2. Spy or stalker.
   if owner is captured pass our criteria and give homescreen directly
   if spy or stalker captured and they try to unlock phone (with right or wrong password) it get freezed the screen on homescreen (if right password) and freezed to lockscreen (if wrong password)



## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        DEVICE UNLOCKED                          │
│                   (PIN / pattern accepted)                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │  Intent.ACTION_USER_PRESENT
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  UnlockReceiver (BroadcastReceiver)                             │
│  Registered in Manifest — fires in <50ms after unlock           │
└───────────────────────────┬─────────────────────────────────────┘
                            │  startForegroundService(UNLOCK_EVENT)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  SentinelService  (StickyForegroundService)                     │
│  Disguised as "Android Core UI Service"                         │
│                                                                 │
│  1. showAnchorOverlay()  — 1×1 transparent SYSTEM_ALERT_WINDOW  │
│  2. bindCamera()         — CameraX front camera, 3 frames       │
└──────────┬────────────────────────────┬────────────────────────┘
           │                            │
           ▼  STAGE A                  ▼
┌──────────────────────┐    ┌──────────────────────────────────────┐
│   ML Kit Detector    │    │      ML Kit Verdict                  │
│  (FaceDetection)     │    │                                      │
│                      │    │  FaceCount == 0  → TRIGGER TRAP      │
│  Ultra-fast on-device│    │  FaceCount  > 1  → TRIGGER TRAP      │
│  detection           │    │  FaceCount == 1  → Stage B ──────┐   │
└──────────────────────┘    └──────────────────────────────────┴───┘
                                                                │
                                                                ▼  STAGE B
                                               ┌────────────────────────────┐
                                               │  MobileFaceNet (TFLite)    │
                                               │                            │
                                               │  Crop + resize → 112×112   │
                                               │  Normalize to [-1, 1]      │
                                               │  Run inference → 512-D vec │
                                               │  L2-normalize embedding    │
                                               │  Euclidean dist vs. owner  │
                                               │                            │
                                               │  dist > 0.90  → TRIGGER    │
                                               │  dist ≤ 0.90  → STAND DOWN │
                                               └────────────┬───────────────┘
                              ┌─────────────────────────────┴───────────┐
                              ▼                                          ▼
               ┌──────────────────────────┐           ┌──────────────────────────────┐
               │        STAND DOWN        │           │        TRIGGER TRAP          │
               │                         │           │                              │
               │  Remove 1×1 overlay     │           │  Deploy full-screen white    │
               │  Camera unbound         │           │  Screen brightness → MAX     │
               │  Device works normally  │           │  Vibration burst             │
               └──────────────────────────┘           │  All touch absorbed         │
                                                      │  Back button absorbed       │
                                                      │  Only exit: Power button    │
                                                      │            or kill-switch   │
                                                      └──────────────────────────────┘
```

---

## Project Structure

```
SentinelLock/
├── app/src/main/
│   ├── AndroidManifest.xml                  — Permissions, receivers, services
│   ├── java/com/sentinel/lock/
│   │   ├── SetupActivity.java               — One-time enrollment wizard
│   │   ├── services/
│   │   │   └── SentinelService.java         — Core sticky service (the brain)
│   │   ├── receivers/
│   │   │   └── UnlockReceiver.java          — USER_PRESENT + BOOT_COMPLETED
│   │   ├── overlay/
│   │   │   └── TrapOverlayManager.java      — Anchor + flashbang overlays
│   │   ├── ml/
│   │   │   └── FaceAnalyzer.java            — ML Kit + TFLite pipeline
│   │   └── admin/
│   │       └── SentinelDeviceAdmin.java     — Device admin (anti-uninstall)
│   ├── res/
│   │   ├── layout/activity_setup.xml
│   │   └── xml/device_admin_policies.xml
│   └── assets/
│       └── mobilefacenet.tflite             — ← You provide this (see ml/)
├── ml/
│   └── model_prep.py                        — Python: convert & calibrate model
├── app/build.gradle                         — Dependencies
├── app/proguard-rules.pro                   — Release obfuscation
└── README.md
```

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Android Service | Java `LifecycleService` | 24/7 background guardian |
| Overlay | `SYSTEM_ALERT_WINDOW` | 1×1 anchor + flashbang trap |
| Camera | **CameraX** `ImageAnalysis` | Frame capture without UI |
| Stage A Detection | **Google ML Kit** `FaceDetection` | Fast face count check |
| Stage B Recognition | **TensorFlow Lite** MobileFaceNet | 512-D embedding comparison |
| Anti-removal | `DeviceAdminReceiver` | Blocks Safe Mode uninstall |
| Boot persistence | `ACTION_BOOT_COMPLETED` | Auto-restart after reboot |
| Model pipeline | **Python** + TensorFlow 2.15 | Quantize, calibrate, export |

---

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android device running API 26+ (Android 8.0)
- Front-facing camera
- MobileFaceNet pre-trained weights (see Step 0)

---

### Step 0 — Obtain the TFLite Model

SentinelLock needs a `mobilefacenet.tflite` file in `app/src/main/assets/`.

**Option A — Use a pre-converted model (fastest)**

Download from the sirius-ai/MobileFaceNet_TF repository or InsightFace project.
Look for a `.tflite` file with 512-dimensional output, 112×112 input.

**Option B — Convert from a SavedModel (recommended for production)**

```bash
# Install Python dependencies
pip install tensorflow==2.15.0 pillow numpy tqdm opencv-python matplotlib

# Convert (adjust model_path to your download)
cd ml/
python model_prep.py \
    --model_path /path/to/mobilefacenet_savedmodel/ \
    --output_dir ../app/src/main/assets/

# Optional: calibrate the threshold against LFW dataset
python model_prep.py \
    --calibrate \
    --lfw_dir ~/datasets/lfw/
```

The calibration step prints a recommended `DISTANCE_THRESHOLD` value.
Update the constant in `FaceAnalyzer.java` accordingly.

---

### Step 1 — Build the App

```bash
# Clone / open in Android Studio, then:
./gradlew assembleDebug          # Debug build
./gradlew assembleRelease        # Release build (requires signing config)
```

---

### Step 2 — Install & Enroll

1. **Install** the APK on your device.
2. **Open** "Android Core UI" from the launcher (only visible during initial setup).
3. **Grant permissions** — the wizard walks through:
   - Draw over other apps (SYSTEM_ALERT_WINDOW)
   - Modify system settings (WRITE_SETTINGS)
   - Device Administrator activation
4. **Enroll your face** — look at the front camera; 10 frames are captured and averaged.
5. **Tap "Start Protection"** — the app sends itself to the background permanently.

After setup, there is no launcher icon. The app runs invisibly as "Android Core UI Service" in the notification tray.

---

### Step 3 — Test the Trap

1. Lock the screen (power button).
2. Unlock with your PIN/pattern.
3. The screen should remain fully functional (owner confirmed).
4. Cover the camera and unlock — you should be flashbanged.
5. Ask someone else to look at your unlocked phone — same result.

---

## Configuration Reference

All tunable constants are in `FaceAnalyzer.java`:

| Constant | Default | Effect |
|----------|---------|--------|
| `DISTANCE_THRESHOLD` | `0.90f` | Lower = stricter (run calibration to tune) |
| `FRAMES_TO_CAPTURE` | `3` | More frames = slower but more reliable |
| `FACE_SIZE` | `112` | MobileFaceNet input size (do not change) |

And in `TrapOverlayManager.java`:

| Constant | Default | Effect |
|----------|---------|--------|
| `TR_TAPS_NEEDED` | `5` | Top-right corner taps for kill-switch phase 1 |
| `BL_TAPS_NEEDED` | `2` | Bottom-left corner taps for kill-switch phase 2 |
| `GESTURE_TIMEOUT` | `5000ms` | Window to complete the full gesture |

Kill-switch pause duration is in `SentinelService.disableTemporarily()`: **5 minutes**.

---

## Security Considerations

### Fail-Closed Design
Every ambiguous result triggers the trap:
- Camera fails to start → TRAP
- Model file missing → TRAP
- Zero faces detected → TRAP
- More than one face → TRAP
- Any single frame fails in a 3-frame session → TRAP

### Stored Embedding Security
The owner embedding is stored in `SharedPreferences` by default.
For production, replace with **EncryptedSharedPreferences** backed by Android Keystore:

```java
// Replace in FaceAnalyzer.saveOwnerEmbedding()
MasterKey masterKey = new MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build();

SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "sl_secure_encrypted",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);
```

Add to `build.gradle`: `implementation 'androidx.security:security-crypto:1.1.0-alpha06'`

### Threshold Tuning
The default threshold of `0.90` is conservative. Run `model_prep.py --calibrate`
against your own face photos in varied lighting conditions to find your personal
optimal value.

---

## Known Limitations & Mitigations

| Limitation | Mitigation |
|-----------|------------|
| Dark room → no face detected → TRAP | Kill-switch gesture (TR×5, BL×2) |
| Sunglasses / mask → TRAP | Same kill-switch |
| Android 10+ background camera restriction | Foreground service with `foregroundServiceType="camera"` |
| System kills sticky service | `START_STICKY` + `BOOT_COMPLETED` receiver |
| Bright outdoor light overexposing front camera | Multi-frame averaging (3 frames); tune `FRAMES_TO_CAPTURE` up |
| Intruder finding and using kill-switch | Gesture is not documented anywhere on the device |

---

## Permissions Explained

| Permission | Why |
|------------|-----|
| `SYSTEM_ALERT_WINDOW` | Both overlays (anchor + trap) require this |
| `CAMERA` | Front-camera access for face capture |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CAMERA` | Android 12+ requires camera type declared |
| `WRITE_SETTINGS` | Set screen brightness to maximum during trap |
| `RECEIVE_BOOT_COMPLETED` | Restart service after device reboot |
| `WAKE_LOCK` | Keep CPU alive during 3-frame analysis (<2s) |
| `BIND_DEVICE_ADMIN` | Activates Device Administrator protection |
| `VIBRATE` | Disorienting vibration pattern when trap fires |

---

*SentinelLock is a personal security tool. Ensure its use complies with applicable privacy laws in your jurisdiction, particularly regarding camera access and device locking.*
