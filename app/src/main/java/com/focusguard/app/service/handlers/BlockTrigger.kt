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
     * [handleSecurityTamper] already sent GLOBAL_ACTION_HOME before mounting the overlay, so
     * nothing here dispatches another HOME. The overlay only cleans up the source package's
     * background process (if non-system) and never redirects the user a second time.
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
            // HOME was already dispatched by the tamper path. Do not dispatch a second one
            // when the countdown ends -- the user must not be redirected again.
            sendHomeOnDismiss = false,
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
     * Main overlay block entry point. For eligible block types, BACK is sent before mounting the
     * shield and HOME is sent only after attachment, so the blocked surface cannot immediately
     * retrigger when reopened. System packages retain their dedicated safety handling.
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

        // HOME is dispatched after the shield attaches for eligible block types. The overlay
        // never dispatches another HOME when its countdown ends.
        val shouldSendHomeOnDismiss = false

        // BACK must reach the triggering surface before the overlay takes input focus.
        val steppedBackBeforeOverlay =
            shouldStepBackBeforeOverlaySurface(blockType) && windowActions.stepBackBeforeOverlay(sourcePackage)
        if (steppedBackBeforeOverlay) {
            Log.i(TAG, "Pre-overlay BACK sent so $sourcePackage will not resume the blocked screen")
        }

        val exitsToHome = shouldExitToHomeAfterAttach(blockType) && !isSystemPkg && !isLauncher
        // A HOME redirect changes the foreground to the launcher. In that case the shield must
        // finish its countdown over Home instead of the hold watcher releasing it early.
        val holdWhileForeground =
            shouldHoldWhileForeground(blockType, exitsToHome) && sourcePackage.isNotBlank()

        overlayManager.showOverlay(
            durationSeconds = duration,
            blockType = blockType,
            targetName = targetName,
            sourcePackage = sourcePackage,
            description = description,
            customMessage = customMessage,
            sendHomeOnDismiss = shouldSendHomeOnDismiss,
            holdUntilForegroundChanges = holdWhileForeground,
            onAttached = {
                if (exitsToHome) {
                    windowActions.forceCloseApp(sourcePackage, sendBack = false)
                } else if (!isSystemPkg) {
                    try {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(sourcePackage)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error killing bg process for $sourcePackage after overlay attach", e)
                    }
                }
            },
            onDismissed = {
                val dismissTime = SystemClock.elapsedRealtime()
                // Held shields are released when the user leaves the app, not by a countdown.
                // Writing the dismiss cooldown there would let a fast re-open slip through.
                if (!holdWhileForeground) {
                    onUpdateLastOverlayDismiss(dismissTime, sourcePackage)
                }
                Log.i(TAG, "Overlay countdown ended -- $sourcePackage [isSystem=$isSystemPkg, sendHome=$shouldSendHomeOnDismiss, held=$holdWhileForeground]")
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

    internal companion object {
        internal fun shouldStepBackBeforeOverlaySurface(blockType: OverlayBlockType): Boolean = when (blockType) {
            OverlayBlockType.SESSION_LOCK_BLOCK -> false
            else -> true
        }

        internal fun shouldExitToHomeAfterAttach(blockType: OverlayBlockType): Boolean =
            shouldStepBackBeforeOverlaySurface(blockType)

        /**
         * Block types whose shield must stay mounted while the blocked package is still in the
         * foreground. Transient content/tamper shields keep their old timed auto-dismiss.
         */
        internal fun shouldHoldWhileForeground(
            blockType: OverlayBlockType,
            exitsToHome: Boolean
        ): Boolean = !exitsToHome && when (blockType) {
            OverlayBlockType.APP_BLOCK,
            OverlayBlockType.TIME_LIMIT_BLOCK,
            OverlayBlockType.SCHEDULE_BLOCK,
            OverlayBlockType.CLONER_BLOCK -> true
            else -> false
        }
    }
}
