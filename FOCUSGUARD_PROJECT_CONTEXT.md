# 🛡️ FocusGuard — Master Project Context Document
> **Agent Instruction**: READ THIS FILE IN FULL AT THE START OF EVERY SESSION.
> This is the single source of truth for the FocusGuard project.
> Never contradict or deviate from anything in this document without explicit user approval.

---

## 1. WHAT WE ARE BUILDING

**FocusGuard** is an unbypassable Android **digital wellbeing & friction-lock app** designed to prevent compulsive smartphone use, block uninstallation, enforce daily app time limits, automate focus schedules, block custom keywords/website domains in real time, and eliminate short-form video addiction (Reels/Shorts).

### Core Architecture & UI/UX Structure
1. **Consolidated 4-Tab Navigation**:
   - 🏠 **Home**: Live protection shield status, live 2x2 metric cards (Apps, Keywords, Limits, Today's Interceptions), Distraction Feed quick-controls, and the Accountability Challenge barrier card.
   - 🚫 **Block**: Sub-tabs for **"Apps"** (with pinned blocklist and fast search) and **"Websites"** (keyword/domain blocks with universal browser tracking).
   - ⏱️ **Limits**: Sub-tabs for **"Daily Limits"** (preset chips + custom minute slider + midnight-reset usage bars) and **"Focus Schedules"** (day-of-week bitmask windows).
   - ⚙️ **Settings**: Permission status checklist, Wallpaper/Overlay customization, Tamper & Uninstall Protection controls, Emergency Support Code rescue, and Accountability Partner pairing.
2. **High-Stakes Block Overlay (`TYPE_ACCESSIBILITY_OVERLAY`)**:
   - Dominant countdown progress ring with bold numeric timer.
   - Immediate Home-kick inside `onAttached` callback (Overlay First, Then Home invariant).
   - Custom motivational block messages and wallpaper dimming.
3. **Resilience & Watchdogs**:
   - Crash-proof accessibility event loop (`processAccessibilityEvent`).
   - Settings `ContentObserver` for real-time toggle monitoring.
   - 15-min periodic `ProtectionHeartbeatWorker` and `UsagePollingWorker`.

---

## 2. TWO-TIER ACCOUNTABILITY MODEL

FocusGuard operates on a strict two-tier friction hierarchy:

| Tier | Name | Nature | How it Unlocks | Connectivity / Account |
|---|---|---|---|---|
| **Tier 1** | **Self-Commitment Mode** | Local Friction Barrier | User must type an exact, non-bypassable commitment statement word-for-word. | **100% Offline & Login-Free** |
| **Tier 2** | **True Hard Mode** | External Partner Gated | User **cannot** self-unlock. Override request is sent to the linked partner's device. | **Requires Google Sign-In** + Internet |

> [!IMPORTANT]
> **Hard Mode Invariant:** If `isPartnerConfigured == false`, True Hard Mode is strictly **locked/unavailable** (grayed out). The app never allows a silent fallback to self-completion under Hard Mode.

---

## 3. FEATURE MATRIX: OFFLINE (NO LOGIN) VS CLOUD (LOGIN REQUIRED)

To protect user privacy and guarantee rock-solid offline reliability, FocusGuard enforces a strict architectural boundary:

| Feature | Login Required? | Data Scope | Offline Capable? |
|---|---|---|---|
| **App Blocker** | ❌ No | Local Room DB (`BlockedAppEntity`) | ✅ 100% Offline |
| **Website & Keyword Blocker** | ❌ No | Local Room DB (`BlockedKeywordEntity`) | ✅ 100% Offline |
| **Daily Time Limits & Schedules** | ❌ No | Local Room DB + `UsageStatsManager` | ✅ 100% Offline |
| **Reel / Short-form Video Blocker** | ❌ No | In-memory Accessibility heuristics | ✅ 100% Offline |
| **Self-Commitment Sentence Challenge** | ❌ No | Local DataStore preferences | ✅ 100% Offline |
| **Uninstall & Settings Tamper Shield** | ❌ No | Device Admin + Accessibility Service | ✅ 100% Offline |
| **Accountability Partner System** | ✅ Yes | Firebase Auth + Firestore (`partnerships`) | 🌐 Cloud Required |
| **Emergency Support Code (Production)** | ✅ Yes | Firebase Cloud Function (`verifyEmergencyCode`) | 🌐 Cloud Required |

---

## 4. INVARIANT SECURITY & ANTI-TAMPER RULES

1. **BLOCKING ORDER INVARIANT: Overlay First, Then Home (CRITICAL)**
   - **NEVER** call `performGlobalAction(GLOBAL_ACTION_HOME)` before attaching the block overlay.
   - Target apps (like YouTube) trigger Android PiP mode if Home is pressed before overlay attachment.
   - Always use `triggerBlock(sourcePackage, reason, ...)` which enforces overlay attachment first, then Home.

2. **SELF-PACKAGE EXCLUSION (GUARD 1)**
   - The very first check in `processAccessibilityEvent` must always be:
     `if (pkg == packageName || pkg.startsWith("com.focusguard.app")) return`
   - FocusGuard must never inspect or block its own UI activities, dialogs, or overlays.

3. **UNINSTALL & TAMPER PROTECTION (GUARD 2)**
   - Intercepting Settings, Device Admin deactivation, or Launcher uninstall actions occurs when:
     `isProtectionActive = isDeviceAdminActive() && isUninstallProtectionEnabled()`
   - Turning off Uninstall Protection **must always trigger the mandatory commitment typing challenge**, even if general app friction is inactive. Zero-friction bypass is mathematically impossible.

4. **ZERO-LATENCY FAST PATH (< 0.01ms)**
   - Accessibility events run at 60-120Hz. Never execute Room queries or DataStore lookups on the main accessibility thread.
   - Use in-memory atomic caches (`cachedBlockedPackages`, `cachedActiveSchedules`, `cachedTimeLimits`, `cachedUninstallProtection`, `cachedAutoBlockNewApps`).

5. **EMERGENCY RESCUE ARCHITECTURE**
   - Client APK must never store a static, reversible master secret in production.
   - Emergency support codes are validated against server-side authorization (Cloud Function / Firestore) to prevent APK decompiler exploits.

---

## 5. FEATURE ROADMAP & STATUS

| Priority | Feature | Status |
|---|---|---|
| **P0** | **Uninstall & Tamper Protection** (Device Admin + Accessibility + Countdown Overlay) | ✅ **DONE** |
| **P1** | **App Blocker** (One-page UI + Pinned Blocklist + Real-time Interception) | ✅ **DONE** |
| **P2** | **Website & Keyword Blocker** (Fast URL inspection + Universal Input tracking) | ✅ **DONE** |
| **P3** | **Crash Resilience & Heartbeat Service** (Crash-proof + ContentObserver + WorkManager) | ✅ **DONE** |
| **P4** | **Usage Time Limits & Focus Schedules** (UsageStatsManager + Polling + Midnight reset) | ✅ **DONE** |
| **P5** | **Distraction Feed Blocker** (YouTube Shorts, Instagram Reels, Facebook Reels) | ✅ **DONE** |
| **P6** | **Self-Commitment Challenge Barrier** (Sentence Typing + Copy-Paste Prevention) | ✅ **DONE** |
| **P7** | **Google Auth & Single Keystore** (Dedicated `app/debug.keystore` + Credential Manager) | ✅ **DONE** |
| **P8** | **Deep Focus Lockdown (#blockit Engine)** (360° Dial + 3-App Suitcase + Zero Scroll) | ✅ **DONE** |
| **P9** | **Zero-Tolerance Anti-Cloning & Dual Apps Shield** (Process UID Self-Kill + OEM Cloner Blacklist) | ✅ **DONE** |
| **P10** | **Split-Screen & Multi-Window Bypass Prevention** (`MultiWindowGuard` + Window Tree Scan) | ✅ **DONE** |
| **P11** | **Monotonic Clock Rollback Shield** (`TimeTamperGuard` + `SystemClock.elapsedRealtime`) | ✅ **DONE** |
| **P12** | **Accountability Partner System** (Firestore pairing + Owner/Partner override flow) | 🔄 **IN PROGRESS** |
| **P13** | **Cloud Function Emergency Support Code** (Server-side validation) | 🔄 **IN PROGRESS** |

---

## 6. TECHNOLOGY STACK

| Layer | Technology |
|---|---|
| Language | **Kotlin 2.0.21** |
| UI | **Jetpack Compose (Material 3 Dark Theme)** |
| Architecture | **MVVM + Clean Architecture** |
| DI | **Dagger Hilt 2.52** |
| Local DB | **Room 2.6.1 (Database v4)** |
| Preferences | **Jetpack DataStore** |
| Usage Tracking | **`android.app.usage.UsageStatsManager`** (Midnight-based aggregation) |
| Cloud / Auth | **Firebase Auth (Google Sign-In via Credential Manager) + Firestore** |
| Overlay | **`TYPE_ACCESSIBILITY_OVERLAY`** |
| Background Work | **WorkManager 2.9.1** |
