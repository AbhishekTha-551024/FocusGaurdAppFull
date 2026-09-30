# 🛡️ FocusGuard — Comprehensive System Architecture

> **Master Reference**: This document details the technical implementation, low-level mechanics, and data structures of FocusGuard. For user guides and project context, refer to [README.md](README.md) and [FOCUSGUARD_PROJECT_CONTEXT.md](FOCUSGUARD_PROJECT_CONTEXT.md).

---

## 📑 Table of Contents

- [1. Architectural Paradigm](#1-architectural-paradigm)
- [2. Window Overlay & Interception Architecture](#2-window-overlay--interception-architecture)
  - [The `TYPE_ACCESSIBILITY_OVERLAY` Primitive](#the-type_accessibility_overlay-primitive)
  - [The Blocking Order Invariant (CRITICAL)](#the-blocking-order-invariant-critical)
- [3. The Master Event Pipeline (`FocusGuardAccessibilityService`)](#3-the-master-event-pipeline-focusguardaccessibilityservice)
- [4. Core Security & Interception Engines](#4-core-security--interception-engines)
  - [4.1 Zero-Tolerance Anti-Cloning Engine (`AntiClonerEngine`)](#41-zero-tolerance-anti-cloning-engine-anticlonerengine)
  - [4.2 Split-Screen & Multi-Window Guard (`MultiWindowGuard`)](#42-split-screen--multi-window-guard-multiwindowguard)
  - [4.3 Monotonic Clock Rollback Shield (`TimeTamperGuard`)](#43-monotonic-clock-rollback-shield-timetamperguard)
  - [4.4 Micro-Heuristic Short-Form Video Blocker (`ReelBlocker`)](#44-micro-heuristic-short-form-video-blocker-reelblocker)
  - [4.5 Universal Keyword & URL Traversal (`KeywordBlocker` & `BrowserUrlTracker`)](#45-universal-keyword--url-traversal-keywordblocker--browserurltracker)
  - [4.6 Deep Focus Lockdown (#blockit Engine & `SessionBlockManager`)](#46-deep-focus-lockdown-blockit-engine--sessionblockmanager)
- [5. Zero-Latency Fast Path (< 0.01ms)](#5-zero-latency-fast-path--001ms)
- [6. Data Layer & Local Persistence](#6-data-layer--local-persistence)
  - [Room Database Schema (v4)](#room-database-schema-v4)
  - [Jetpack DataStore Preferences](#jetpack-datastore-preferences)
- [7. Background Persistence & Watchdog System](#7-background-persistence--watchdog-system)
- [8. Two-Tier Accountability Friction Hierarchy](#8-two-tier-accountability-friction-hierarchy)
- [9. Device Owner & DPC Tamper Protection](#9-device-owner--dpc-tamper-protection)
- [10. Emergency Support Code & Rescue Cryptography](#10-emergency-support-code--rescue-cryptography)

---

## 1. Architectural Paradigm

FocusGuard follows **Clean Architecture** with **MVVM (Model-View-ViewModel)** and unidirectional data flow (UDF):

```
┌─────────────────────────────────────────────────────────────────┐
│                    Presentation Layer (UI)                      │
│      Jetpack Compose (Material 3 Dark Forest) • ViewModels      │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Observes UIState / Sends Intents
┌───────────────────────────────▼─────────────────────────────────┐
│                       Domain Layer (Engines)                    │
│   AntiCloner • MultiWindow • TimeTamper • Reel • SessionBlock  │
└───────────────────────────────┬─────────────────────────────────┘
                                │ Calls Repositories / Atomic Caches
┌───────────────────────────────▼─────────────────────────────────┐
│                         Data Layer                              │
│   Room Database (v4) • Jetpack DataStore • UsageStatsManager    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Window Overlay & Interception Architecture

### The `TYPE_ACCESSIBILITY_OVERLAY` Primitive
FocusGuard mounts full-screen block overlays using `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` (API 22+).

#### Why this is superior to standard `TYPE_APPLICATION_OVERLAY`:
1. **Zero Permission Overhead:** When the user enables the Accessibility Service, the system implicitly grants overlay rights. No separate `ACTION_MANAGE_OVERLAY_PERMISSION` prompt is required.
2. **Trusted Tapjacking Immunity:** In Android 12+, accessibility overlays are classified as "trusted" windows, immune to `FLAG_SLIPPERY` touch filtering.
3. **Absolute Z-Order Priority:** System window managers render accessibility overlays strictly above Package Installer, App Info, and Settings dialogues.

### The Blocking Order Invariant (CRITICAL)
- **Rule:** Always call `BlockOverlayManager.showOverlay(...)` first, and dispatch `GLOBAL_ACTION_HOME` / close target apps only inside the `onAttached` callback.
- **Why:** If `GLOBAL_ACTION_HOME` is dispatched before the overlay is physically attached to the window manager:
  - Target apps (like YouTube) immediately trigger Android Picture-in-Picture (PiP) mode.
  - OEM window managers (MIUI, ColorOS) delay or drop overlay window attachment when the foreground task switches to the launcher.

---

## 3. The Master Event Pipeline (`FocusGuardAccessibilityService`)

Accessibility events arrive at 60–120Hz. All interception is consolidated inside a single event handler:

```
[AccessibilityEvent Arrives]
  │
  ├─► Guard 0: Window Hierarchy Changed? (TYPE_WINDOWS_CHANGED)
  │     └── MultiWindowGuard.checkAndInterceptMultiWindow()
  │
  ├─► Guard 1: Self-Package Exclusion
  │     └── if (pkg == packageName || pkg.startsWith("com.focusguard.app")) return
  │
  ├─► Guard 1.05: Fast-discard Keyboards & Input Methods (IME)
  │     └── if (isInputMethod(pkg)) return
  │
  ├─► Guard 1.1: Notification Shade Pulldown
  │     └── if (statusBarOpenDuringDeepFocus) collapseStatusBar()
  │
  ├─► Guard 1.15: Transient System Dialogs (Volume Slider, Toasts)
  │     └── if (isTransientDialog(pkg)) return
  │
  ├─► Guard 1.155: ZERO-TOLERANCE ANTI-CLONING (Layer 1 Cloner Package Blacklist)
  │     └── if (isClonerPackage(pkg)) triggerBlock(pkg, "APP_CLONING_BLOCKED")
  │
  ├─► Guard 1.16: MONOTONIC CLOCK ROLLBACK & TIME TRAVEL SHIELD
  │     └── if (TimeTamperGuard.isTampered()) triggerBlock(pkg, "CLOCK_TAMPERED")
  │
  ├─► Guard 1.17: MultiWindowGuard (Package switch traversal)
  │     └── MultiWindowGuard.checkAndInterceptMultiWindow()
  │
  ├─► Guard 1.2: Root Node Anti-Cloning (Layer 2 UI Text Scan & Layer 3 OEM Cloner Settings)
  │     └── if (containsCloneKeywords(rootNode)) triggerBlock(pkg, "CLONER_UI_BLOCKED")
  │
  ├─► Guard 1.8: Ultra-Fast Short-Form Video Traversal (< 1ms)
  │     └── ReelBlocker.intercept(event, rootNode)
  │
  ├─► Guard 2: Schedules & Daily Time Limits
  │     └── if (isDailyLimitExceeded(pkg) || isInActiveSchedule(pkg)) triggerBlock(pkg)
  │
  ├─► Guard 3: Package Installer / Uninstaller Intercept
  │     └── if (isUninstallAttempt(pkg, rootNode)) triggerBlock(pkg, "UNINSTALL_BLOCKED")
  │
  ├─► Guard 4: Settings Tamper Intercept (Device Admin, Date/Time, Dev Options)
  │     └── if (isTamperSettingsPage(pkg, rootNode)) triggerBlock(pkg, "SETTINGS_BLOCKED")
  │
  └─► Guard 5: Launcher Long-Press & Drag-to-Uninstall Shield
        └── if (isLauncherAction(pkg, rootNode)) triggerBlock(pkg, "LAUNCHER_TAMPER")
```

---

## 4. Core Security & Interception Engines

### 4.1 Zero-Tolerance Anti-Cloning Engine (`AntiClonerEngine`)
Provides unbypassable protection against OEM dual spaces (Xiaomi Second Space, Realme App Cloner, Samsung Dual Messenger, Island, Shelter):
1. **Process UID Self-Termination:**
   - Android assigns `UID = (userId * 100000) + appId`. User 0 is primary owner; secondary users run with `userId >= 95` (e.g. `999` for Xiaomi Dual Apps).
   - In `FocusGuardApplication.onCreate()`:
     ```kotlin
     if (Process.myUid() / 100000 != 0) {
         exitProcess(1) // Kills clone process instantly
     }
     ```
2. **OEM Cloner Package Blacklist:** Hard-blocks all known cloner packages on frame 0:
   - Realme / Oppo: `com.oplus.multiapp`, `com.coloros.oppomultiapp`
   - Xiaomi / Poco: `com.miui.securityadd`, `com.miui.cloner`, `com.miui.dualapp`
   - Vivo / iQOO: `com.vivo.doubleinstance`, `com.vivo.appclone`
   - Samsung: `com.samsung.android.da.daagent`, `com.samsung.android.settings.dualmessenger`
   - Virtual spaces: `com.lbe.parallel.intl`, `com.polestar.super.clone`, etc.
3. **Permanent Shield:** No UI toggle; this protection is an intrinsic security invariant of FocusGuard.

### 4.2 Split-Screen & Multi-Window Guard (`MultiWindowGuard`)
- Subscribes to `AccessibilityEvent.TYPE_WINDOWS_CHANGED`.
- Traverses all active `AccessibilityWindowInfo` objects.
- When two or more application windows are visible simultaneously (split-screen mode, pop-up floating windows, or PiP), `MultiWindowGuard` inspects their package roots.
- If any window contains a blocked app, an unauthorized app during Deep Focus, or an OEM cloner, it triggers the block overlay first and dismisses the split session to Home.

### 4.3 Monotonic Clock Rollback Shield (`TimeTamperGuard`)
- Tracks CPU uptime via `SystemClock.elapsedRealtime()` (which ticks continuously and is immune to user clock modifications).
- Calculates:
  $$\Delta T_{real} = \text{elapsedRealtime}() - \text{lastBootRealtime}$$
  $$\Delta T_{wall} = \text{System.currentTimeMillis}() - \text{lastWallClockTime}$$
- **Triggers:**
  - $\Delta T_{wall} < -30\text{s} \Rightarrow$ Clock Rollback detected.
  - $(\Delta T_{wall} - \Delta T_{real}) > 60\text{s} \Rightarrow$ Artificial Time Jump detected.
- Puts app into security lock until legitimate network time is restored.

### 4.4 Micro-Heuristic Short-Form Video Blocker (`ReelBlocker`)
- Runs in $< 1\text{ms}$ using in-memory target view IDs:
  - **YouTube Shorts:** Blocks `com.google.android.youtube:id/reel_recycler` and `shorts_player`.
  - **Instagram Reels:** Blocks `com.instagram.android:id/clips_tab` and `clips_viewer`.
  - **Facebook Reels:** Blocks `reels_video_container`.
- Preserves standard videos, searches, profiles, feeds, and direct messages.

### 4.5 Universal Keyword & URL Traversal (`KeywordBlocker` & `BrowserUrlTracker`)
- Recursively searches for editable address bars across major browsers: Chrome, Firefox, Edge, Brave, Opera, Samsung Internet, UC Browser, DuckDuckGo.
- Also inspects YouTube search fields and in-app search bars.
- Matched keywords trigger immediate URL overwrite and Home kick.

### 4.6 Deep Focus Lockdown (#blockit Engine & `SessionBlockManager`)
- **Compact Mechanical Dial:** 360-degree circular slider with 1-minute precision, haptic tick sound, and dual hour/minute step buttons.
- **The Suitcase (Strict 3-App Cap):** Allows up to 3 essential productivity tools (e.g., Phone, Notes, Calculator). All other apps are strictly blocked.
- **Zero-Scroll Viewport:** Optimized layout (`Arrangement.SpaceBetween`) fits entirely within one screen without scrolling.
- **Monotonic Hardware Countdown:** Uses `sessionEndElapsedRealtime = SystemClock.elapsedRealtime() + durationMs`. Unaffected by system clock manipulation.

---

## 5. Zero-Latency Fast Path (< 0.01ms)

Because accessibility events fire continuously on the main thread, synchronous Room queries or DataStore lookups cause dropped frames. FocusGuard uses atomic in-memory mirrors:

```kotlin
val cachedBlockedPackages = CopyOnWriteArraySet<String>()
val cachedBlockedKeywords = AtomicReference<List<String>>(emptyList())
val cachedTimeLimits = AtomicReference<Map<String, TimeLimitEntity>>(emptyMap())
val cachedActiveSchedules = AtomicReference<List<ScheduleEntity>>(emptyList())
val cachedUninstallProtection = AtomicBoolean(true)
val cachedAutoBlockNewApps = AtomicBoolean(false)
```

Room database updates emit asynchronously via Kotlin `Flow` and update these atomic caches in the background.

---

## 6. Data Layer & Local Persistence

### Room Database Schema (v4)
- **`BlockedAppEntity`:** `packageName` (PK), `appName`, `isBlocked`, `addedTimestamp`.
- **`BlockedKeywordEntity`:** `id` (PK, auto), `keyword`, `isRegex`, `addedTimestamp`.
- **`TimeLimitEntity`:** `packageName` (PK), `dailyLimitMinutes`, `consumedMinutesToday`, `lastResetDate`.
- **`ScheduleEntity`:** `id` (PK), `name`, `startTime`, `endTime`, `daysOfWeekBitmask`, `assignedPackages`, `isActive`.
- **`InterceptionLogEntity`:** `id` (PK), `packageName`, `reason`, `timestamp`.

### Jetpack DataStore Preferences
Stores user preferences, challenge settings, master protection switch, wallpaper dimming parameters, and emergency recovery credentials.

---

## 7. Background Persistence & Watchdog System

1. **`ProtectionWatchdogService`:** Foreground service maintaining sticky execution priority (`START_STICKY`) with a persistent ongoing notification.
2. **`BootReceiver`:** Automatically re-arms accessibility monitoring and foreground services when device boots (`ACTION_BOOT_COMPLETED`).
3. **`NewAppInstallReceiver`:** Intercepts `PACKAGE_ADDED` broadcasts. If "Auto-block newly installed apps" is enabled, adds the new package to the blocklist immediately.
4. **WorkManager Workers:**
   - `UsagePollingWorker`: Runs every 15 minutes to synchronize `UsageStatsManager` deltas.
   - `AccessibilityHeartbeatWorker`: Checks whether the accessibility service has been killed by aggressive OEM task managers.

---

## 8. Two-Tier Accountability Friction Hierarchy

```
                      ┌─────────────────────────────────┐
                      │    FocusGuard Shield Settings   │
                      └────────────────┬────────────────┘
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
      ┌───────────────────────────┐         ┌───────────────────────────┐
      │          TIER 1           │         │          TIER 2           │
      │   Self-Commitment Mode    │         │      True Hard Mode       │
      ├───────────────────────────┤         ├───────────────────────────┤
      │ • Sentence Typing Barrier │         │ • External Partner Gated  │
      │ • Copy-Paste Disabled     │         │ • Self-Unlock Disabled    │
      │ • Time Delay Countdown    │         │ • Firebase Firestore Link │
      │ • 100% Offline & Private  │         │ • Partner Approval Remote │
      └───────────────────────────┘         └───────────────────────────┘
```

---

## 9. Device Owner & DPC Tamper Protection

FocusGuard implements `DeviceAdminReceiver` and Device Policy Controller (DPC) capabilities:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dpm set-device-owner com.focusguard.app/.receiver.FocusGuardDeviceAdminReceiver
```

When provisioned as Device Owner:
- **`setUninstallBlocked(true)`:** `pm uninstall` fails with `DELETE_FAILED_DEVICE_POLICY_MANAGER`.
- **`DISALLOW_CONFIG_DATE_TIME`:** Date/Time settings are grayed out at the OS level.
- **`DISALLOW_SAFE_BOOT`:** Safe mode reboot bypass is blocked.
- **`DISALLOW_ADD_USER`:** Secondary multi-user bypass is blocked.

---

## 10. Emergency Support Code & Rescue Cryptography

- **Algorithm:** HMAC-SHA256
- **Payload:** `"{DEVICE_ID}|{YYYY-MM-DD}|{ACTION_CODE}|{TARGET_PAYLOAD}"`
- **PIN Formulation:**
  $$\text{PIN} = (\text{uint32}(\text{digest}[0..3]) \pmod{900000}) + 100000$$
- **Validation:** Computed on-device by `SupportCodeEngine.kt` and validated against admin inputs from Python CLI, HTML Portal, or React Dashboard.
