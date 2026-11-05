package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.GuardTimestamps
import com.focusguard.app.service.utils.ClassNameMatchers
import com.focusguard.app.service.utils.PackageClassifier
import com.focusguard.app.utils.LauncherDetector

private const val TAG = "FocusGuardService"

/**
 * Executes the contiguous system guard checks (1.14 to 1.16) for [FocusGuardAccessibilityService].
 *
 * Checks in order:
 * - 1.14: Post-overlay dismiss cooldown (drops event if inside cooldown window)
 * - 1.145: Deep Focus SystemUI shade & recents collapse
 * - 1.15: Transient system events filter (status bar, volume slider, toasts, IME)
 * - 1.15a: Preemptive OEM security scan detection (arms static suppression window, falls through)
 * - 1.155: Zero-tolerance anti-cloning & dual apps shield
 * - 1.16: System clock rollback & time travel tamper shield
 *
 * Returns true if an intercept occurred or the event was handled (service must return).
 * Returns false to continue downstream processing.
 */
class SystemGuardPipeline(
    private val host: SystemGuardHost,
    private val repository: FocusGuardRepository,
    private val sessionBlockManager: SessionBlockManager,
    private val launcherDetector: LauncherDetector,
    private val oemScanDetector: OemScanDetector,
    private val timestamps: GuardTimestamps
) {

    fun process(
        pkg: String,
        className: String,
        nowRealtime: Long
    ): Boolean {
        // ── GUARD 1.14: Post-Overlay Dismiss Cooldown ────────────────────────
        // Prevents rapid back-to-back re-blocking of the same package when an overlay just closed.
        // NEVER drop launcher, system packages, settings packages, or cloners!
        val isProtectionActiveForCooldown = host.isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        val isSessionActiveForCooldown = sessionBlockManager.isSessionCurrentlyActive()
        if (nowRealtime - timestamps.lastOverlayDismissTimestamp < Timings.OVERLAY_DISMISS_COOLDOWN_MS &&
            pkg == timestamps.lastDismissedPackage &&
            !PackageClassifier.isSystemOrLauncherPackage(pkg, launcherDetector, isProtectionActiveForCooldown, isSessionActiveForCooldown) &&
            !PackageClassifier.isSettingsPackage(pkg.lowercase()) &&
            !AntiClonerEngine.isClonerPackage(pkg)) {
            return true
        }

        // ── GUARD 1.145: DEEP FOCUS SYSTEMUI SHADE & RECENTS COLLAPSE ───────
        // Prevent escaping Deep Focus Kiosk via notification shade, quick settings tiles, or overview
        if (sessionBlockManager.isSessionCurrentlyActive() && pkg == "com.android.systemui") {
            val isShadeOrRecents = ClassNameMatchers.isShadeOrRecentsClass(className)
            if (isShadeOrRecents) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - timestamps.lastShadeCollapseTimestamp > Timings.SHADE_COLLAPSE_THROTTLE_MS) {
                    timestamps.lastShadeCollapseTimestamp = now
                    Log.w(TAG, "Notification shade / quick settings / recents intercepted during Deep Focus -> Collapsing!")
                    host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    com.focusguard.app.feature.session.ui.SessionLockActivity.start(host.context())
                }
                return true
            }
        }

        // ── GUARD 1.15: Transient system events (status bar, volume slider, toasts, keyboard) ─
        // These events are transient overlays and must NOT disrupt the active foreground app's session timer!
        val isProtectionActiveForTransient = host.isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        if (PackageClassifier.isTransientSystemPackage(pkg, isProtectionActiveForTransient)) {
            return true
        }

        // ── GUARD 1.15a: PREEMPTIVE OEM SECURITY SCAN DETECTION ─────────────
        // When events arrive from known OEM security managers (Vivo iManager, Vivo SafeCenter,
        // Vivo PermissionManager) we check whether our accessibility service entry is temporarily
        // absent from ENABLED_ACCESSIBILITY_SERVICES. If so, it's an OEM scan window -- arm the
        // suppression flag immediately so handleSecurityTamper() won't fire HOME during the scan.
        if (oemScanDetector.isOemSecurityPackage(pkg) && !host.isOemScanWindowActive()) {
            val ctx = host.context()
            if (oemScanDetector.isAccessibilityTransientlyOff(ctx.contentResolver, ctx.packageName)) {
                FocusGuardAccessibilityService.markOemScanActive()
                Log.i(TAG, "GUARD 1.15a: OEM security manager ($pkg) detected with transient accessibility clear -- scan window armed")
            }
        }

        // ── GUARD 1.155: ZERO-TOLERANCE ANTI-CLONING & DUAL APPS SHIELD (Layer 1: Cloner Blacklist) ─
        // Catches OEM Cloners (com.oplus.multiapp, com.miui.securityadd, com.vivo.doubleinstance, etc.)
        // and 3rd-party virtual spaces (Parallel Space, Island, Shelter, etc.).
        // Only active when anti-cloning is enabled AND protection or an active focus session is running.
        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = host.isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if ((isProtectionActive || isSessionActive) && AntiClonerEngine.isClonerPackage(pkg)) {
                val appLabel = host.cachedAppLabel(pkg)
                Log.w(TAG, "ANTI-CLONING INTERCEPT: Cloner / Virtual Container blocked: $pkg ($appLabel)")
                host.handleSecurityTamper(
                    sourcePackage = pkg,
                    reason = "App Cloner Blocked: $appLabel",
                    targetName = "App Cloning Prohibited",
                    customToastMessage = "🚫 App cloning and dual apps are prohibited while FocusGuard protection is active."
                )
                return true
            }
        }

        // ── GUARD 1.16: SYSTEM CLOCK ROLLBACK & TIME TRAVEL TAMPER SHIELD ─────
        if (TimeTamperGuard.checkAndValidateTime()) {
            val isProtectionActive = host.isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
            if (isProtectionActive || sessionBlockManager.isSessionCurrentlyActive()) {
                Log.e(TAG, "Clock tampering detected! Triggering security tamper block.")
                host.triggerBlock(
                    sourcePackage = pkg,
                    reason = "System Clock Tampering Detected",
                    blockType = OverlayBlockType.SETTINGS_TAMPER,
                    targetName = "Time Manipulation Blocked",
                    description = "FocusGuard detected an unauthorized change to the system clock. Clock rollbacks and artificial time jumps are blocked to protect your focus integrity."
                )
                return true
            }
        }

        return false
    }
}

