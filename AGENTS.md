# FocusGuard Development Instructions & Invariant Rules

## 1. BLOCKING ORDER INVARIANT: Overlay First, Then Home (CRITICAL)
- **NEVER** call `performGlobalAction(GLOBAL_ACTION_HOME)` before attaching the block overlay.
- When `GLOBAL_ACTION_HOME` is executed before the overlay is mounted, target apps (like YouTube) trigger Android's Picture-in-Picture (PiP) mode, and the system window manager may delay or drop the overlay window.
- **Rule**: Always call `overlayManager.showOverlay(...)` first, and trigger `GLOBAL_ACTION_HOME` / `forceCloseApp()` only inside the `onAttached` callback.
- Always use `triggerBlock(sourcePackage, reason, ...)` in `FocusGuardAccessibilityService.kt` which strictly enforces this order.

## 2. SELF-PACKAGE EXCLUSION (GUARD 1)
- The very first check in `processAccessibilityEvent` must always be:
  `if (pkg == packageName || pkg.startsWith("com.focusguard.app")) return`
- FocusGuard must never inspect or block its own UI activities, dialogs, or overlays.

## 3. UNINSTALL & TAMPER PROTECTION (GUARD 2)
- Intercepting Settings, Device Admin deactivation, or Launcher uninstall actions must only occur when:
  `isProtectionActive = isDeviceAdminActive() && isUninstallProtectionEnabled()`
- If the user completed the friction challenge to disable uninstall protection, do not block uninstallation.

## 4. CONSOLIDATED PIPELINE (No Parallel Handlers)
- Keep all accessibility event interception consolidated inside `FocusGuardAccessibilityService.kt:processAccessibilityEvent()`.
- Never create a parallel or secondary accessibility service.

## 5. ZERO-LATENCY FAST PATH (< 0.01ms)
- Accessibility events run on high frequency (60–120Hz). Never execute synchronous Room queries or DataStore lookups on the main accessibility thread.
- Use in-memory atomic caches (`cachedBlockedPackages`, `cachedActiveSchedules`, `cachedTimeLimits`, `cachedUninstallProtection`, `cachedAutoBlockNewApps`).

## 6. HARD MODE ACCOUNTABILITY CHALLENGE
- Unblocking an app, modifying a time limit, or turning off uninstall protection must always trigger `ActiveChallengeState` (Sentence Typing Challenge or Time Delay Challenge).
- Never allow a silent self-review or instant unblock bypass.

## 7. ZERO-TOLERANCE ANTI-CLONING INVARIANT
- Anti-Cloning & Dual Apps Shield is a permanent, non-negotiable core property of FocusGuard.
- Never add a user-facing toggle to disable anti-cloning.
- In `FocusGuardApplication.onCreate()`, always enforce:
  `if (Process.myUid() / 100000 != 0) exitProcess(1)`
- Always maintain OEM cloner packages (`com.oplus.multiapp`, `com.miui.securityadd`, `com.vivo.doubleinstance`, `com.samsung.android.da.daagent`, etc.) at the top of the event filter (`GUARD 1.155`).

## 8. SPLIT-SCREEN & MULTI-WINDOW TRAVERSAL
- Accessibility service must subscribe to `AccessibilityEvent.TYPE_WINDOWS_CHANGED`.
- All multi-window and split-screen traversal must evaluate active application windows via `MultiWindowGuard`.
- Invariant Rule 1 (Overlay First, Then Home) applies equally to split-screen and PiP dismissals.

## 9. MONOTONIC HARDWARE CLOCK INVARIANT
- Any focus session countdown or time limit tamper check must incorporate `SystemClock.elapsedRealtime()`.
- Never rely exclusively on `System.currentTimeMillis()`, as wall-clock time can be manipulated in device settings.

## 10. MINDFUL TRACKING & CONSCIOUS ALLOWANCE INVARIANT
- Mindful pause intercept must present breathing pause, conscious intention reason, and time allowance selection (1m, 2m, 5m, 10m, 15m).
- Automatic app closure upon allowance expiry must enforce Invariant Rule 1 (Overlay First, Then Home) and force close the target app.
- Re-opening a recently closed app must detect the return session and prompt "Why again this time?".
- Adding an app to Mindful tracking requires explicit user confirmation.
- Removing an app from Mindful tracking must strictly enforce Invariant Rule 6 (Sentence Typing Challenge / Partner Approval or prompt to activate accountability if inactive). Never allow silent removal.

