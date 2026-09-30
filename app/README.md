# 📱 FocusGuard — Android Application Module (`app`)

> Core Android application module for **FocusGuard**, implementing the high-stakes accessibility blocker, Compose Material 3 dark UI, Room database, and hardware-level anti-tamper shields.

---

## 📂 Architecture & Package Organization

```
app/src/main/java/com/focusguard/app/
├── blockers/                # Real-Time Interception Engines
│   ├── AntiClonerEngine.kt  # OEM & virtual space cloner package blacklist
│   ├── MultiWindowGuard.kt  # Split-screen & pop-up floating window protection
│   ├── TimeTamperGuard.kt   # Monotonic hardware clock rollback detector
│   ├── ReelBlocker.kt       # Micro-heuristic YouTube Shorts & Instagram Reels blocker
│   ├── KeywordBlocker.kt    # In-browser address bar & in-app search field blocker
│   └── SessionBlockManager.kt # Deep Focus Lockdown & 3-app Suitcase manager
│
├── data/                    # Data Layer (Room, DataStore, Repositories)
│   ├── db/                  # Room Database v4 (Entities & DAOs)
│   ├── datastore/           # Jetpack DataStore (Fast synchronous mirrors)
│   ├── model/               # Data Transfer & UI State models
│   └── repository/          # Repository implementations
│
├── overlay/                 # Full-Screen Interception Overlays
│   ├── BlockOverlayManager.kt # WindowManager TYPE_ACCESSIBILITY_OVERLAY manager
│   ├── BlockOverlayContent.kt # Compose Overlay (Countdown ring, custom messages)
│   ├── DeepFocusLockOverlayContent.kt # Lockdown session overlay
│   └── MindfulPauseOverlayContent.kt # Breathing circle & intention prompt overlay
│
├── receiver/                # Broadcast Receivers
│   ├── FocusGuardDeviceAdminReceiver.kt # Device Admin & DPC policy controller
│   ├── BootReceiver.kt      # Re-arms services after device reboot
│   └── NewAppInstallReceiver.kt # Auto-blocks new apps on download
│
├── security/                # Cryptography & Validation
│   ├── SupportCodeValidator.kt # Local HMAC token computation
│   └── AuthCryptoManager.kt # Token hashing utilities
│
├── service/                 # System & Foreground Services
│   ├── FocusGuardAccessibilityService.kt # Consolidated Master Interception Pipeline
│   ├── ProtectionWatchdogService.kt # Sticky foreground service keeping app alive
│   └── FocusSessionService.kt # Deep Focus foreground countdown service
│
├── tracker/                 # URL & Address Bar Traversal
│   ├── BrowserUrlTracker.kt # Browser node address bar scraper
│   └── BrowserHeartbeatTracker.kt # Foreground polling loop
│
├── ui/                      # Jetpack Compose UI (Material 3 Dark)
│   ├── home/                # Home Screen (Metrics, Shields, Distraction Feed)
│   ├── block/               # Block Screen (Apps & Websites sub-tabs)
│   ├── limits/              # Limits Screen (Daily Limits & Schedules)
│   ├── settings/            # Settings Screen (Permissions, Themes, Partner, Admin)
│   ├── session/             # Deep Focus Screen (360° Dial, 3-App Suitcase)
│   ├── mindful/             # Mindful Pause UI (Breathing circle, Intent)
│   └── onboarding/          # Guided Permission Setup Checklist
│
└── worker/                  # Background Maintenance Workers
    ├── UsagePollingWorker.kt # 15-minute UsageStatsManager delta worker
    └── AccessibilityHeartbeatWorker.kt # Service liveness check worker
```

---

## 🛠️ Build & Development Commands

From the project root:

### 1. Compile Debug APK
```powershell
.\gradlew.bat :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Run Unit Tests
```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### 3. Install & Launch via ADB
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.focusguard.app/.ui.onboarding.OnboardingActivity
```

---

## 🔐 Critical Invariants for Developers

1. **Blocking Order Invariant:** Always call `BlockOverlayManager.showOverlay(...)` first, and trigger `GLOBAL_ACTION_HOME` only inside `onAttached`.
2. **Self-Package Exclusion:** Never inspect or block `com.focusguard.app`.
3. **No UI Toggle for Anti-Cloning:** Cloner protection is permanent and must never be disabled.
4. **Zero-Latency Fast Path:** Never run Room or DataStore queries on the accessibility thread. Always read from atomic in-memory mirrors.
