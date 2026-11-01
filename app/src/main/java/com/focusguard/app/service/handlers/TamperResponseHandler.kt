package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles security tamper interceptions (settings tamper, uninstaller protection, cloner bypass).
 *
 * Coordinates OEM security scan suppression, friction HUD Toast, navigation actions (BACK then HOME),
 * fallback home intent dispatch, delayed block overlay presentation (150ms), and tamper audit logging.
 *
 * Stateless @Singleton: does not store any Handler, Runnable, Context, or lambda in fields.
 */
@Singleton
class TamperResponseHandler @Inject constructor(
    private val repository: FocusGuardRepository
) {

    fun isExemptFromSuppression(
        targetName: String,
        blockType: OverlayBlockType,
        reason: String
    ): Boolean = Companion.isExemptFromSuppression(targetName, blockType, reason)

    fun buildToastMessage(targetName: String, customToastMessage: String?): String =
        Companion.buildToastMessage(targetName, customToastMessage)

    fun handleSecurityTamper(
        sourcePackage: String,
        reason: String,
        targetName: String,
        blockType: OverlayBlockType = OverlayBlockType.SETTINGS_TAMPER,
        customToastMessage: String? = null,
        isOemScanWindowActive: () -> Boolean,
        onUpdateLastBlockTimestamp: (Long) -> Unit,
        triggerBlockHaptic: () -> Unit,
        showHudToast: (String) -> Unit,
        performGlobalAction: (Int) -> Boolean,
        launchHomeIntent: () -> Unit,
        postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
        onTriggerBlockWithHomeAlreadySent: (
            sourcePackage: String,
            reason: String,
            blockType: OverlayBlockType,
            targetName: String,
            description: String
        ) -> Unit,
        scope: CoroutineScope
    ) {
        // ── OEM SCAN SUPPRESSION GUARD ────────────────────────────────
        // Vivo iManager temporarily clears ENABLED_ACCESSIBILITY_SERVICES during
        // its security scan (2-5 s). During this window, ANY Settings/iManager event
        // we receive is from the OEM scanner -- not a real user tamper attempt.
        // Calling performGlobalAction(HOME) here during the scan triggers Vivo’s
        // anti-tapjacking watchdog, which force-unbinds us. Skip the block entirely.
        // CRITICAL EXEMPTION: Device Admin, Uninstall, and Settings tamper must NEVER be suppressed!
        val isExemptFromSuppression = isExemptFromSuppression(targetName, blockType, reason)

        if (isOemScanWindowActive() && !isExemptFromSuppression) {
            Log.i(TAG, "handleSecurityTamper SUPPRESSED during OEM security scan window [$targetName] pkg=$sourcePackage")
            return
        }
        val nowRealtime = SystemClock.elapsedRealtime()
        onUpdateLastBlockTimestamp(nowRealtime)
        triggerBlockHaptic()
        Log.w(TAG, "SECURITY TAMPER INTERCEPTED [$targetName]: $reason (pkg=$sourcePackage)")

        val message = buildToastMessage(targetName, customToastMessage)

        // 1. Post a clean native HUD Toast explaining the protection
        showHudToast(message)

        // 2. First pop the dangerous settings page off the back stack
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

        // 3. Return to Home screen immediately in < 2ms to exit dangerous settings/installer page.
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        try {
            launchHomeIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch home intent on security tamper", e)
        }

        // 3. Mount rich Block Overlay over the Home screen after the 150ms transition.
        //    homeAlreadySent=true tells triggerBlockWithHomeAlreadySent to skip the second
        //    HOME that forceCloseApp() would otherwise fire inside onAttached.
        postDelayed(150L) {
            onTriggerBlockWithHomeAlreadySent(
                sourcePackage,
                reason,
                blockType,
                targetName,
                message
            )
        }

        scope.launch {
            repository.logEvent(
                EventType.APP_BLOCKED,
                "[SETTINGS_TAMPER] Target=$targetName | Pkg=$sourcePackage | $reason"
            )
        }
    }

    companion object {
        private const val TAG = "FocusGuardService"

        fun isExemptFromSuppression(
            targetName: String,
            blockType: OverlayBlockType,
            reason: String
        ): Boolean {
            return targetName == "Device Administrator" ||
                    targetName == "Hide Apps Protection" ||
                    blockType == OverlayBlockType.UNINSTALL_ATTEMPT ||
                    reason.contains("Device Admin", ignoreCase = true)
        }

        fun buildToastMessage(targetName: String, customToastMessage: String?): String {
            return customToastMessage
                ?: "🚫 $targetName is locked by FocusGuard. Complete the friction challenge in the app to modify."
        }
    }
}
