package com.focusguard.app.service.runtime

import android.content.Intent
import android.widget.Toast
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.FocusGuardAccessibilityService

/**
 * Handles system tampering attempts (Settings deactivation, Device Admin removal,
 * Package Installer uninstall attempts, launcher icon manipulations).
 *
 * Phase 5 split: former [FocusGuardAccessibilityService.handleSecurityTamper] body, moved
 * verbatim. The service override stays as the [com.focusguard.app.service.handlers.SystemGuardHost]
 * entry point and forwards here.
 *
 * CRITICAL SECURITY ARCHITECTURE:
 * NEVER attach a WindowManager TYPE_ACCESSIBILITY_OVERLAY directly over system settings or
 * package installer screens! Doing so triggers Android 12+ and OEM (Vivo FuntouchOS/OriginOS/ColorOS)
 * anti-tapjacking security watchdogs, which forcibly unbind and disable the AccessibilityService.
 *
 * Instead, we immediately execute GLOBAL_ACTION_HOME + launch the Home launcher intent to
 * boot the user to the Home screen in <2ms, display an informative HUD Toast, and then
 * mount the rich Block Overlay over the Home screen after 150ms.
 *
 * FIX C5 -- Double-HOME Prevention:
 * We send HOME here (step 2) to exit the dangerous settings/installer screen.
 * triggerBlock() -> forceCloseApp() would send a SECOND HOME via its onAttached callback,
 * causing launcher flicker on MIUI/ColorOS/FuntouchOS. We use
 * triggerBlockWithHomeAlreadySent() to suppress the redundant HOME inside forceCloseApp().
 */
internal class SecurityTamperDelegate(
    private val service: FocusGuardAccessibilityService,
    private val runtime: ServiceRuntime
) {
    fun handleSecurityTamper(
        sourcePackage: String,
        reason: String,
        targetName: String,
        blockType: OverlayBlockType,
        customToastMessage: String?
    ) {
        runtime.deps.tamperResponseHandler.handleSecurityTamper(
            sourcePackage = sourcePackage,
            reason = reason,
            targetName = targetName,
            blockType = blockType,
            customToastMessage = customToastMessage,
            isOemScanWindowActive = { service.isOemScanWindowActive() },
            onUpdateLastBlockTimestamp = { runtime.timestamps.lastBlockTimestamp = it },
            triggerBlockHaptic = { runtime.windowActions.triggerBlockHaptic() },
            showHudToast = { msg ->
                runtime.mainHandler.post {
                    Toast.makeText(service.applicationContext, msg, Toast.LENGTH_LONG).show()
                }
            },
            sendGlobalAction = { service.sendGlobalAction(it) },
            launchHomeIntent = {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                service.startActivity(homeIntent)
            },
            onHealAccessibilityInPlace = {
                // Re-assert ENABLED_ACCESSIBILITY_SERVICES from the still-bound service instead of
                // navigating. Verified read-back is what decides whether the user also needs the
                // full-screen gate.
                com.focusguard.app.recovery.ProgrammaticAccessibilityEnabler
                    .tryEnableAccessibilityProgrammatically(service.applicationContext)
            },
            onShowRecoveryCover = {
                // Self-gated on isProtectionActive(): with Uninstall Protection OFF this is a no-op,
                // which is the same rule the recovery path enforces (AGENTS invariant 3 / Rule 7).
                com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity.start(
                    context = service,
                    reason = "FocusGuard accessibility was just switched off (toggle contested)."
                )
            },
            postDelayed = { delayMs, action -> runtime.mainHandler.postDelayed(action, delayMs) },
            onTriggerBlockWithHomeAlreadySent = { srcPkg, r, bt, tn, desc ->
                runtime.blockTrigger.triggerBlockWithHomeAlreadySent(
                    sourcePackage = srcPkg,
                    reason = r,
                    blockType = bt,
                    targetName = tn,
                    description = desc
                )
            },
            scope = runtime.scope
        )
    }
}
