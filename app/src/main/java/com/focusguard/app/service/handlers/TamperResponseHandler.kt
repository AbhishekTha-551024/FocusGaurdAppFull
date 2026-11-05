package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.utils.TamperResponse
import com.focusguard.app.service.utils.TamperResponsePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles security tamper interceptions (settings tamper, uninstaller protection, cloner bypass).
 *
 * Selects the response with [TamperResponsePolicy], then executes it: friction HUD Toast,
 * navigation actions (BACK then HOME), fallback home intent dispatch, delayed block overlay
 * presentation (150ms), the tapjacking-safe heal-and-cover contest, and tamper audit logging.
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
        sendGlobalAction: (Int) -> Boolean,
        launchHomeIntent: () -> Unit,
        onHealAccessibilityInPlace: () -> Boolean,
        onShowRecoveryCover: () -> Unit,
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
        // its security scan (2-5 s). Calling performGlobalAction(HOME) inside that window triggers
        // Vivo's anti-tapjacking watchdog, which force-unbinds us. The scan window therefore
        // changes HOW we respond — never WHETHER an attempt on our own accessibility service is
        // noticed: blanking the setting is itself triggered by the user entering Settings, so
        // suppressing the attempt used to hand over the exact window in which the toggle flips.
        // See TamperResponsePolicy for the full decision table.
        val response = TamperResponsePolicy.decide(
            isOemScanWindowActive = isOemScanWindowActive(),
            isExemptFromSuppression = isExemptFromSuppression(targetName, blockType, reason),
            isSelfA11yProtection = TamperResponsePolicy.isSelfA11yProtectionTamper(targetName, blockType)
        )

        if (response == TamperResponse.SILENT) {
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

        if (response == TamperResponse.HEAL_AND_COVER) {
            contestWithoutNavigating(sourcePackage, targetName, onHealAccessibilityInPlace, onShowRecoveryCover)
            logTamper(scope, targetName, sourcePackage, reason)
            return
        }

        // 2. First pop the dangerous settings page off the back stack
        sendGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

        // 3. Return to Home screen immediately in < 2ms to exit dangerous settings/installer page.
        val homeSent = sendGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        if (!homeSent) {
            Log.w(TAG, "HOME refused by the navigation budget; the home intent below still leaves the dangerous page.")
        }
        try {
            launchHomeIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch home intent on security tamper", e)
        }

        // 3. Mount rich Block Overlay over the Home screen after the 150ms transition.
        //    homeAlreadySent=true tells triggerBlockWithHomeAlreadySent to skip the second
        //    HOME that forceCloseApp() would otherwise fire inside onAttached.
        postDelayed(Timings.OVERLAY_MOUNT_AFTER_HOME_DELAY_MS) {
            onTriggerBlockWithHomeAlreadySent(
                sourcePackage,
                reason,
                blockType,
                targetName,
                message
            )
        }

        logTamper(scope, targetName, sourcePackage, reason)
    }

    /**
     * Tapjacking-safe contest of an attempt on FocusGuard's own Accessibility Service.
     *
     * No global action is performed. The secure setting is re-asserted instead, which the OS
     * honours in well under 100 ms (measured on Vivo V2355: unbind -> re-bind in 69 ms). The
     * full-screen recovery gate is shown only when that silent heal cannot be verified — i.e.
     * WRITE_SECURE_SETTINGS is missing or the OEM re-blanked the value — because then the Toast
     * alone would leave the toggle genuinely off.
     */
    private fun contestWithoutNavigating(
        sourcePackage: String,
        targetName: String,
        onHealAccessibilityInPlace: () -> Boolean,
        onShowRecoveryCover: () -> Unit
    ) {
        val isHealed = try {
            onHealAccessibilityInPlace()
        } catch (e: Exception) {
            Log.e(TAG, "In-place accessibility re-assert failed", e)
            false
        }
        Log.w(
            TAG,
            "OEM scan window active: contested [$targetName] without global action (pkg=$sourcePackage) | silentHealVerified=$isHealed"
        )
        if (!isHealed) onShowRecoveryCover()
    }

    private fun logTamper(
        scope: CoroutineScope,
        targetName: String,
        sourcePackage: String,
        reason: String
    ) {
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
        ): Boolean = TamperResponsePolicy.isExemptFromSuppression(targetName, blockType, reason)

        fun buildToastMessage(targetName: String, customToastMessage: String?): String {
            return customToastMessage
                ?: "🚫 $targetName is locked by FocusGuard. Complete the friction challenge in the app to modify."
        }
    }
}
