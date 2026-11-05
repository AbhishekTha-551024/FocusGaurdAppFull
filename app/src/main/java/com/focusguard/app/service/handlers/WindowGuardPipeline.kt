package com.focusguard.app.service.handlers

import android.util.Log
import com.focusguard.app.blockers.MultiWindowGuard
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.GuardTimestamps
import com.focusguard.app.service.utils.ServiceWindowActions

private const val TAG = "FocusGuardService"

/**
 * Executes window transitions and kiosk lockdown guard checks for [com.focusguard.app.service.FocusGuardAccessibilityService].
 *
 * Checks in order:
 * - 1.17: Multi-Window & Split-Screen Bypass Guard
 * - 1.18: Deep Focus Lockdown Strict Kiosk Enforcement
 *
 * Returns true if an intercept occurred (service must return).
 * Returns false to continue downstream processing.
 */
class WindowGuardPipeline(
    private val host: SystemGuardHost,
    private val windowActions: ServiceWindowActions,
    private val repository: FocusGuardRepository,
    private val sessionBlockManager: SessionBlockManager,
    private val timestamps: GuardTimestamps
) {

    // Pre-allocated intercept handler to avoid per-event lambda allocations
    private val multiWindowInterceptHandler: (String, String, String, String) -> Unit =
        { sourcePkg, reason, targetName, desc ->
            if (sessionBlockManager.isSessionCurrentlyActive()) {
                windowActions.triggerBlockHaptic()
                com.focusguard.app.feature.session.ui.SessionLockActivity.start(host.context())
                windowActions.dismissUnauthorizedAppInDeepFocus(sourcePkg)
            } else {
                val appLabel = host.cachedAppLabel(sourcePkg)
                host.triggerBlock(
                    sourcePackage = sourcePkg,
                    reason = reason,
                    blockType = OverlayBlockType.APP_BLOCK,
                    targetName = if (targetName == sourcePkg) appLabel else targetName,
                    description = desc
                )
            }
        }

    fun process(
        pkg: String,
        isPackageChanged: Boolean,
        isWindowTransition: Boolean,
        nowRealtime: Long
    ): Boolean {
        // ── GUARD 1.17: MULTI-WINDOW & SPLIT-SCREEN BYPASS GUARD ──────────────
        if (isWindowTransition || isPackageChanged) {
            val isIntercepted = MultiWindowGuard.checkAndInterceptMultiWindow(
                service = host.service(),
                repository = repository,
                sessionBlockManager = sessionBlockManager,
                onIntercept = multiWindowInterceptHandler
            )
            if (isIntercepted) return true
        }

        // ── GUARD 1.18: DEEP FOCUS LOCKDOWN (#blockit Engine) STRICT KIOSK ENFORCEMENT ──
        // When Deep Focus session is active, the device operates as a zero-bypass kiosk.
        // All Home Launchers, App Drawers, System Settings, App Stores, and non-suitcase apps
        // are intercepted instantly (< 0.01ms) and redirected directly to SessionLockActivity.
        if (sessionBlockManager.isSessionCurrentlyActive()) {
            val isBlockedInSession = sessionBlockManager.isPackageBlockedInSession(pkg)
            if (isBlockedInSession) {
                val nowRt = android.os.SystemClock.elapsedRealtime()
                if (nowRt - timestamps.lastDeepFocusReassertTimestamp > 40L) {
                    timestamps.lastDeepFocusReassertTimestamp = nowRt
                    Log.w(TAG, "DEEP FOCUS INTERCEPT: Unauthorized app/launcher blocked: $pkg -> Reasserting Kiosk Lock Screen")
                    windowActions.triggerBlockHaptic()
                    com.focusguard.app.feature.session.ui.SessionLockActivity.start(host.context())
                    windowActions.dismissUnauthorizedAppInDeepFocus(pkg)
                }
                return true
            }
        }

        return false
    }
}

