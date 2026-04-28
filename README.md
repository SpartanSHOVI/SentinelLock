# SentinelLock
### Zero-Trust Biometric Gatekeeper for Android

SentinelLock is a proactive security application designed to protect mobile devices from unauthorized access even after a successful PIN/pattern unlock. It leverages on-device AI to continuously verify the owner's identity and deploys a "Troll Screen" if an intruder is detected.

## Key Features

- **Zero-Trust Unlock**: Immediately triggers a face scan upon device unlock.
- **Continuous Persistence**: Resets verification state on every screen cycle.
- **Intruder Trap (Flashbang)**: Deploys a full-screen high-brightness strobe overlay if verification fails.
- **Stealth Logging**: Automatically captures and stores photos of intruders who trigger the trap or fail the pattern.
- **Pattern Bypass**: A secret invisible tap-sequence bypass for the owner in low-light conditions.

---

## Technical Architecture

```
┌───────────────────────────┐          ┌───────────────────────────┐
│      Unlock Event         │─────────▶│    SentinelService        │
│ (ACTION_USER_PRESENT)     │          │  (CameraX + FaceAnalyzer) │
└───────────────────────────┘          └─────────────┬─────────────┘
                                                     │
                                           ┌─────────┴─────────┐
                                   [VERIFIED]           [UNVERIFIED]
                                         │                   │
                                         ▼                   ▼
                               ┌──────────────────┐  ┌───────────────────────┐
                               │   STAND DOWN     │  │   FREEZE ACTIVITY     │
                               │ (Access Granted) │  │ (Troll Screen Active) │
                               └──────────────────┘  └───────────┬───────────┘
                                                                 │
                                                       ┌─────────┴─────────┐
                                                 [BYPASS]           [FAILURE]
                                                     │                   │
                                                     ▼                   ▼
                                             ┌──────────────┐    ┌──────────────┐
                                             │   HOME SCREEN│    │  FLASHBANG + │
                                             │   UNLOCKED   │    │  PHOTO LOG   │
                                             └──────────────┘    └──────────────┘
```

---

## Invisible Pattern Bypass

If biometric verification fails (e.g., in complete darkness), the owner can use a secret tap sequence on the transparent overlay. 

**Default Sequence:** 
1. `TOP-LEFT` (Double Tap)
2. `TOP-RIGHT` (Single Tap)
3. `BOTTOM-LEFT` (Double Tap)
4. `BOTTOM-RIGHT` (Single Tap)

*Note: The sequence is fully configurable in `FreezeActivity.java`.*

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Background Logic | Java `LifecycleService` | Foreground service for persistence |
| Vision | **CameraX** | High-performance frame analysis |
| AI / ML | **Google ML Kit** | Real-time face detection |
| Face Recognition | **TFLite (MobileFaceNet)** | 512-D embedding extraction |
| Data Storage | `SQLite / Room` | Intruder logs and metadata |
| CI/CD | GitHub Actions | Automated build & APK generation |

---

## Installation & Setup

1. **Build the APK**: Use `./gradlew assembleDebug` or the GitHub Actions artifacts.
2. **Grant Permissions**:
   - Camera
   - Display over other apps
   - Notification access
3. **Enroll Face**: Run the setup wizard to capture your "Golden Embedding".
4. **Activate Protection**: Start the service. The app will now monitor every unlock.

---

## Academic Information

- **Project**: SentinelLock (Not Friendly)
- **Course**: Advanced Programming / Android Security
- **Author**: SpartanSHOVI
- **Tech Highlights**: Zero-Trust Implementation, On-Device AI, Stealth Background Operations.
