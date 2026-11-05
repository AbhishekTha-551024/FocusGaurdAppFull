package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.ActiveWindowProbe
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.overlay.manager.OverlayHoldPolicy
import com.focusguard.app.service.GuardTimestamps
import com.focusguard.app.service.utils.ClassNameMatchers
import com.focusguard.app.service.utils.PackageClassifier
import com.focusguard.app.utils.SafeNodeUtils

private const val TAG = "FocusGuardService"

/**
 * Handles protection and event interception while a block overlay is actively showing (GUARD 1.1).
 *
 * Responsibilities:
 * 1. Dismiss dangerous Settings screens immediately if switched to during active overlay.
 * 2. Crush OEM cloners / virtual spaces launched during active overlay.
 * 3. Dismiss uninstaller dialogs targeting FocusGuard during active overlay.
 * 4. Intercept launcher interactions specifically targeting FocusGuard popup menu.
 * 5. Collapse notification shade / quick settings pull-down attempts during active overlay.
 *
 * Returns true if the block overlay is showing (event consumed/handled; service must return).
 * Returns false if no overlay is showing (service continues downstream processing).
 */
class ActiveOverlayGuard(
    private val host: SystemGuardHost,
    private val repository: FocusGuardRepository,
    private val sessionBlockManager: SessionBlockManager,
    private val overlayManager: BlockOverlayManager,
    private val packageInstallerHandler: PackageInstallerHandler,
    private val launcherTargetResolver: LauncherTargetResolver,
    private val timestamps: GuardTimestamps
) {

    fun process(
        e: AccessibilityEvent,
        pkg: String,
        className: String
    ): Boolean {
        if (!overlayManager.isOverlayShowing) return false

        // ── HELD SHIELD RELEASE: user moved to another app / home / recents ──────
        // A block shield that is holding a blocked app must follow the foreground. Without this
        // it floats over the launcher, recents and other apps after the user switches away.
        val heldPackage = overlayManager.holdPackage
        if (heldPackage != null) {
            val shouldRelease = OverlayHoldPolicy.shouldReleaseForForeground(
                holdPackage = heldPackage,
                isWindowStateChanged = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                foregroundPackage = pkg,
                isKeyboard = PackageClassifier.isKeyboardPackage(pkg),
                isSettings = PackageClassifier.isSettingsPackage(pkg.lowercase()),
                isInstaller = PackageClassifier.isInstallerPackage(pkg, className),
                isCloner = AntiClonerEngine.isClonerPackage(pkg),
                isRecents = pkg == "com.android.systemui" && ClassNameMatchers.isRecentsClass(className),
                heldPackageIsActive = ActiveWindowProbe.isPackageInForeground(host.service(), heldPackage)
            )
            if (shouldRelease) {
                // Route through the manager so the mount grace + consecutive-miss debounce applies.
                // A raw dismissOverlay() here was releasing the shield seconds after it appeared,
                // because launcher / OEM-card events fire while the blocked app's window is still
                // coming up. If the debounce declines, keep holding and consume this event.
                if (overlayManager.releaseHeldShieldIfForegroundLeft()) {
                    Log.i(TAG, "Foreground changed to $pkg -> released held block shield for $heldPackage")
                    // Continue processing this event for the new foreground package so a blocked
                    // app opened right after switching is blocked again immediately.
                    return false
                }
            }
        }

        val isProtectionActive = host.isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        // Dismiss dangerous Settings screens immediately if switched to while overlay is active AND uninstall protection is on
        if (PackageClassifier.isSettingsPackage(pkg.lowercase()) && isProtectionActive) {
            // Paced, and BACK deliberately dropped: Settings streams window events continuously, and an
            // unpaced BACK+HOME per event is the burst that makes Vivo unbind the very service that is
            // trying to hold the shield up. BACK inside Settings also only navigates to another Settings
            // page (the same rule ServiceWindowActions.forceCloseApp documents), while HOME leaves
            // Settings outright — one action, same protection.
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - timestamps.lastSettingsDismissTimestamp > Timings.SETTINGS_DISMISS_DEBOUNCE_MS) {
                timestamps.lastSettingsDismissTimestamp = now
                host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
            return true
        }

        // 1. Immediately crush any OEM cloner or virtual space launched while overlay is active (ONLY when protection or session is active)
        if ((isProtectionActive || sessionBlockManager.isSessionCurrentlyActive()) && AntiClonerEngine.isClonerPackage(pkg)) {
            Log.w(TAG, "Cloner launched during overlay -> killing immediately ($pkg)")
            host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            try {
                val am = host.context().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(pkg)
            } catch (e: Exception) {
                Log.d(TAG, "killBackgroundProcesses error: ${e.message}")
            }
            host.triggerBlock(
                sourcePackage = pkg,
                reason = "App Cloner Blocked",
                blockType = OverlayBlockType.CLONER_BLOCK,
                targetName = "App Cloner Blocked",
                description = "App cloning is strictly prohibited."
            )
            return true
        }

        val overlayRootNode = try { host.currentRoot() ?: e.source } catch (_: Exception) { null }
        try {
            if (isProtectionActive) {
                // 2. Immediately dismiss uninstaller dialogs targeting FocusGuard
                val isPotentialUninstallHost = pkg == "android" ||
                        pkg == "com.google.android.packageinstaller" ||
                        pkg == "com.android.packageinstaller" ||
                        pkg == "com.android.permissioncontroller" ||
                        pkg == "com.google.android.permissioncontroller"
                if (isPotentialUninstallHost && overlayRootNode != null && packageInstallerHandler.isUninstallConfirmationForFocusGuard(overlayRootNode)) {
                    Log.w(TAG, "Uninstall dialog detected during overlay -> dismissing immediately ($pkg)")
                    host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    host.triggerBlock(
                        sourcePackage = pkg,
                        reason = "Uninstall Attempt Blocked",
                        blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                        targetName = "FocusGuard Uninstall",
                        description = "FocusGuard is protected from uninstallation."
                    )
                    return true
                }

                // 3. While block overlay is showing, ignore launcher events entirely
                // UNLESS target resolver confirms FOCUSGUARD and previous overlay was shown > 1500ms ago.
                if (host.isLauncherPackage(pkg)) {
                    val displayMetrics = host.context().resources.displayMetrics
                    val target = launcherTargetResolver.resolve(
                        e = e,
                        rootNode = overlayRootNode,
                        screenWidth = displayMetrics.widthPixels,
                        screenHeight = displayMetrics.heightPixels,
                        nowRealtime = android.os.SystemClock.elapsedRealtime()
                    )
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (target == com.focusguard.app.service.handlers.LauncherTarget.FOCUSGUARD && (now - timestamps.lastBlockTimestamp > Timings.TRANSITION_DEBOUNCE_MS)) {
                        Log.w(TAG, "Launcher interaction specifically targeting FocusGuard detected during active overlay -> dismissing popup immediately ($pkg)")
                        timestamps.lastBlockTimestamp = now
                        host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    }
                    return true
                }
            }
        } finally {
            SafeNodeUtils.safeRecycle(overlayRootNode)
        }

        // 4. SystemUI Notification Shade Pull-down Guard during active overlay
        if (pkg == "com.android.systemui") {
            val isShade = ClassNameMatchers.isShadeClass(className)
            if (isShade) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - timestamps.lastShadeCollapseTimestamp > Timings.OVERLAY_SHADE_COLLAPSE_DEBOUNCE_MS) {
                    timestamps.lastShadeCollapseTimestamp = now
                    Log.w(TAG, "Notification shade pull-down intercepted during block overlay -> Collapsing immediately!")
                    host.sendGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }
        }
        return true
    }
}

