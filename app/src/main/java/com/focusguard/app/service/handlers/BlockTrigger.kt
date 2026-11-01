package com.focusguard.app.service.handlers

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.utils.LauncherDetector
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.utils.PackageClassifier
import com.focusguard.app.service.utils.ServiceWindowActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "FocusGuardService"

/**
 * Encapsulates the triggerBlock family of functions responsible for overlay presentation,
 * haptic feedback, app force close (or home dispatch), background process termination,
 * and block audit event logging.
 *
 * Owned and instantiated per service instance (NOT @Singleton).
 */
class BlockTrigger(
    private val context: Context,
    private val repository: FocusGuardRepository,
    private val overlayManager: BlockOverlayManager,
    private val windowActions: ServiceWindowActions,
    private val foregroundSessionTracker: ForegroundSessionTracker,
    private val launcherDetector: LauncherDetector,
    private val sessionBlockManager: SessionBlockManager,
    private val scope: CoroutineScope,
    private val isDeviceAdminActive: () -> Boolean,
    private val isLauncherPackage: (String) -> Boolean,
    private val onUpdateLastBlockTimestamp: (Long) -> Unit,
    private val onUpdateLastOverlayDismiss: (timestamp: Long, pkg: String) -> Unit
) {

    /**
     * Variant of [triggerBlock] used exclusively by [handleSecurityTamper].
     *
     * Because [handleSecurityTamper] already sent GLOBAL_ACTION_HOME before mounting the
     * overlay, the normal [forceCloseApp] inside [triggerBlock]'s onAttached callback must
     * NOT send another HOME. This variant's onAttached callback only kills the background
     * process of the source package (if non-system) -- no redundant HOME dispatch.
     *
     * FIX C5: Eliminates the double-HOME (flicker) on MIUI / ColorOS / FuntouchOS launchers.
     */
    fun triggerBlockWithHomeAlreadySent(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType = OverlayBlockType.SETTINGS_TAMPER,
        targetName: String = "FocusGuard Protection",
        description: String = "Action is protected by friction delay."
    ) {
        val nowRealtime = SystemClock.elapsedRealtime()
        onUpdateLastBlockTimestamp(nowRealtime)
        Log.i(TAG, "TAMPER BLOCK (home pre-sent) [$blockType: $targetName] Reason: $reason (pkg=$sourcePackage)")

        val duration = repository.getFastBlockCountdownSeconds().coerceAtLeast(3)
        val customMessage = repository.getFastCustomBlockMessage().takeIf { it.isNotBlank() }
        overlayManager.currentPresetId = repository.getFastOverlayPresetId()
        overlayManager.currentDimAlpha = repository.getFastOverlayDimAlpha()

        val isProtectionActiveTb = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        val isSessionActiveTb = sessionBlockManager.isSessionCurrentlyActive()
        val isSystemPkg = PackageClassifier.isSystemOrLauncherPackage(sourcePackage, launcherDetector, isProtectionActiveTb, isSessionActiveTb)

        overlayManager.showOverlay(
            durationSeconds = duration,
            blockType = blockType,
            targetName = targetName,
            sourcePackage = sourcePackage,
            description = description,
            customMessage = customMessage,
            // HOME was already sent before overlay mount, but we still want to send it
            // again on dismiss so the user always lands on home when they click Close.
            sendHomeOnDismiss = true,
            onAttached = {
                // HOME is already live. Only clean up the background process for non-system pkgs
                // to prevent a PiP window from the settings/installer appearing behind the overlay.
                if (!isSystemPkg) {
                    try {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(sourcePackage)
                        Log.d(TAG, "triggerBlockWithHomeAlreadySent: killed bg processes for $sourcePackage")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error killing bg process for $sourcePackage in tamper block", e)
                    }
                }
            },
            onDismissed = {
                val dismissTime = SystemClock.elapsedRealtime()
                onUpdateLastOverlayDismiss(dismissTime, sourcePackage)
                Log.i(TAG, "Tamper overlay countdown ended -- $sourcePackage")
            }
        )

        scope.launch {
            repository.logEvent(EventType.APP_BLOCKED, "[$blockType] Target=$targetName | Pkg=$sourcePackage | $reason")
        }
    }

    /**
     * Main overlay block entry point.
     *
     * Invariant 1: Mount overlay first, send HOME only after overlay is attached.
     */
    fun triggerBlock(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType = OverlayBlockType.UNINSTALL_ATTEMPT,
        targetName: String = "FocusGuard Protection",
        description: String = "Action is protected by friction delay."
    ) {
        val nowRealtime = SystemClock.elapsedRealtime()
        onUpdateLastBlockTimestamp(nowRealtime)
        windowActions.triggerBlockHaptic()
        Log.i(TAG, "BLOCK triggered! [$blockType: $targetName] Reason: $reason (pkg=$sourcePackage)")

        // If blocking the currently active foreground package, immediately capture its session duration
        foregroundSessionTracker.onAppBlocked(sourcePackage, nowRealtime)

        // Duration: User-configured countdown duration for all block types uniformly (Settings, Tamper, Shorts, etc.)
        val duration = repository.getFastBlockCountdownSeconds().coerceAtLeast(3)

        val customMessage = repository.getFastCustomBlockMessage().takeIf { it.isNotBlank() }

        // Synchronize selected overlay style from user settings
        overlayManager.currentPresetId = repository.getFastOverlayPresetId()
        overlayManager.currentDimAlpha = repository.getFastOverlayDimAlpha()

        val isLauncher = isLauncherPackage(sourcePackage)
        val isProtectionActiveSo = isDeviceAdminActive() && repository.isFastUninstallProtectionEnabled()
        val isSessionActiveSo = sessionBlockManager.isSessionCurrentlyActive()
        val isSystemPkg = PackageClassifier.isSystemOrLauncherPackage(sourcePackage, launcherDetector, isProtectionActiveSo, isSessionActiveSo)

        // ── SEND HOME ON DISMISS: Always true for ALL block types ────────────────
        val shouldSendHomeOnDismiss = true

        overlayManager.showOverlay(
            durationSeconds = duration,
            blockType = blockType,
            targetName = targetName,
            sourcePackage = sourcePackage,
            description = description,
            customMessage = customMessage,
            sendHomeOnDismiss = shouldSendHomeOnDismiss,
            onAttached = {
                // Invariant Rule 1: Attach overlay FIRST. forceCloseApp sends HOME while overlay is live on screen.
                windowActions.forceCloseApp(sourcePackage)
            },
            onDismissed = {
                val dismissTime = SystemClock.elapsedRealtime()
                onUpdateLastOverlayDismiss(dismissTime, sourcePackage)
                Log.i(TAG, "Overlay countdown ended -- $sourcePackage [isSystem=$isSystemPkg, sendHome=$shouldSendHomeOnDismiss]")
                try {
                    // NEVER kill system settings or system packages! Only kill 3rd party apps!
                    if (!isSystemPkg) {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(sourcePackage)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up $sourcePackage after overlay dismiss", e)
                }
            }
        )

        scope.launch {
            repository.logEvent(EventType.APP_BLOCKED, "[$blockType] Target=$targetName | Pkg=$sourcePackage | $reason")
        }
    }
}
