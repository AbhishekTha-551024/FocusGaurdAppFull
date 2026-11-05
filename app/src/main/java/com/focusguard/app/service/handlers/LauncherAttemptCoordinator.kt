package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.utils.GlobalActionGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Launcher"
private const val VIVO_OVERLAY_RETRY_DELAY_MS = 300L

@Singleton
class LauncherAttemptCoordinator @Inject constructor(
    private val repository: FocusGuardRepository,
    private val overlayManager: BlockOverlayManager,
    private val globalActionGate: GlobalActionGate
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val attemptPolicy = LauncherAttemptPolicy()

    fun handle(
        service: AccessibilityService,
        pkg: String,
        reason: String,
        eventTime: Long
    ) {
        val duration = repository.getFastBlockCountdownSeconds().coerceAtLeast(3)
        val customMessage = repository.getFastCustomBlockMessage().takeIf { it.isNotBlank() }
        val presetId = repository.getFastOverlayPresetId()
        val dimAlpha = repository.getFastOverlayDimAlpha()

        if (!attemptPolicy.begin(eventTime, overlayManager.isOverlayShowing)) {
            Log.d(TAG, "Duplicate launcher attempt skipped")
            return
        }

        overlayManager.currentPresetId = presetId
        overlayManager.currentDimAlpha = dimAlpha

        val backSent = globalActionGate.dispatch(AccessibilityService.GLOBAL_ACTION_BACK) {
            service.performGlobalAction(it)
        }
        if (!backSent) {
            Log.w(TAG, "Launcher BACK was refused; continuing with the FocusGuard protection overlay")
        }

        val homeSent = AtomicBoolean(false)
        showLauncherOverlay(service, pkg, duration, customMessage, homeSent)

        mainHandler.postDelayed({
            if (attemptPolicy.shouldRetry(overlayManager.isOverlayShowing)) {
                Log.w(TAG, "Launcher overlay was not attached; retrying once")
                showLauncherOverlay(service, pkg, duration, customMessage, homeSent)
                mainHandler.postDelayed({
                    if (attemptPolicy.shouldRetry(overlayManager.isOverlayShowing)) {
                        attemptPolicy.onAttachFailed()
                        Log.e(TAG, "Launcher overlay retry failed; allowing a later attempt")
                    }
                }, VIVO_OVERLAY_RETRY_DELAY_MS)
            }
        }, VIVO_OVERLAY_RETRY_DELAY_MS)

        scope.launch {
            repository.logEvent(
                EventType.UNINSTALL_ATTEMPT,
                "Target=FocusGuard Launcher | Pkg=$pkg | $reason"
            )
        }
    }

    private fun showLauncherOverlay(
        service: AccessibilityService,
        pkg: String,
        duration: Int,
        customMessage: String?,
        homeSent: AtomicBoolean
    ) {
        overlayManager.showOverlay(
            durationSeconds = duration,
            blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
            targetName = "FocusGuard Protection",
            sourcePackage = pkg,
            description = "FocusGuard is protected from uninstall & cloning.",
            customMessage = customMessage,
            sendHomeOnDismiss = false,
            onAttached = {
                if (overlayManager.isOverlayShowing) {
                    attemptPolicy.onOverlayAttached()
                    sendHomeAfterOverlayAttached(service, homeSent)
                }
            },
            onDismissed = { attemptPolicy.onOverlayDismissed() }
        )
    }

    private fun sendHomeAfterOverlayAttached(
        service: AccessibilityService,
        homeSent: AtomicBoolean
    ) {
        if (!homeSent.compareAndSet(false, true)) return

        val sent = globalActionGate.dispatch(AccessibilityService.GLOBAL_ACTION_HOME) {
            service.performGlobalAction(it)
        }
        if (sent) return

        try {
            service.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            Log.w(TAG, "Global HOME action was refused; launched the HOME intent after overlay attachment")
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No HOME activity was available after the launcher overlay attached", e)
        }
    }
}
