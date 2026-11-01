package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.BuildConfig
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.utils.SafeNodeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Launcher"

/**
 * LauncherEventHandler
 * ====================
 * Specialized fast-path handler for Launcher and Home screen events.
 *
 * Core Policy:
 * - Every long-press target is classified as FOCUSGUARD, OTHER, or UNKNOWN.
 * - Only FOCUSGUARD may trigger a block.
 * - OTHER and UNKNOWN are ignored without touching the popup or showing an overlay.
 * - Latch: Active for 2500ms after verified FocusGuard long-press; cleared immediately on OTHER.
 * - Dedicated launcher block flow: BACK on frame 0, overlay mounted with sendHomeOnDismiss=false,
 *   no startActivity(homeIntent), and 300ms Vivo retry.
 */
@Singleton
class LauncherEventHandler @Inject constructor(
    private val repository: FocusGuardRepository,
    private val targetResolver: LauncherTargetResolver,
    private val popupDetector: LauncherPopupDetector,
    private val overlayManager: BlockOverlayManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    var focusGuardLatchUntil: Long = 0L
        private set
    var lastFocusGuardBlockTimestamp: Long = 0L
        private set

    fun processLauncherEvent(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        protectionWanted: Boolean,
        nowRealtime: Long,
        onTriggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit
    ): Boolean {
        if (!protectionWanted) {
            return false
        }

        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        if (BuildConfig.DEBUG) {
            val sourceNode = try { e.source } catch (_: Exception) { null }
            val srcBounds = android.graphics.Rect().also { sourceNode?.getBoundsInScreen(it) }
            val isIcon = targetResolver.isIconSized(srcBounds, screenWidth, screenHeight)
            Log.d(TAG, "Event: type=${AccessibilityEvent.eventTypeToString(e.eventType)} cls=$className pkg=$pkg text=${e.text} desc=${e.contentDescription} bounds=$srcBounds isIcon=$isIcon")
            SafeNodeUtils.safeRecycle(sourceNode)
        }

        // A. Launcher Hide Apps Intercept
        if (popupDetector.isLauncherHideApps(className, rootNode)) {
            Log.w(TAG, "Decision: BLOCKED (Hide Apps tamper attempt in Launcher: $pkg - $className)")
            onTriggerBlock(
                pkg,
                "Hide Apps Access Blocked in Launcher",
                OverlayBlockType.SETTINGS_TAMPER,
                "Hide Apps Protection",
                "Hiding apps is restricted to prevent bypassing FocusGuard protection."
            )
            return true
        }

        // Resolve target identity
        val target = targetResolver.resolve(e, rootNode, screenWidth, screenHeight, nowRealtime)

        // B. Long-Press Evaluation
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            when (target) {
                LauncherTarget.FOCUSGUARD -> {
                    focusGuardLatchUntil = nowRealtime + 2500L
                    Log.i(TAG, "Decision: BLOCKED (Launcher Long-Press on FocusGuard, latch set for 2500ms)")
                    handleLauncherFocusGuardAttempt(service, pkg, "Launcher Long-Press on FocusGuard")
                    return true
                }
                LauncherTarget.OTHER -> {
                    focusGuardLatchUntil = 0L // Clear latch immediately
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Decision: IGNORED (Long-press on non-FocusGuard app, latch cleared)")
                    }
                    return true
                }
                LauncherTarget.UNKNOWN -> {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Decision: IGNORED (Long-press target UNKNOWN, evidence-based policy)")
                    }
                    return true
                }
            }
        }

        // C. Click Evaluation (App Clone / Uninstall Click Intercept)
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (target == LauncherTarget.OTHER) {
                focusGuardLatchUntil = 0L // Clear latch immediately
            }

            val clickedNode = try { e.source } catch (_: Exception) { null }
            val clickedText = (e.text.joinToString(" ") + " " + (e.contentDescription ?: "") + " " +
                    (clickedNode?.text ?: "") + " " + (clickedNode?.contentDescription ?: "")).lowercase()

            val isLatchActive = nowRealtime < focusGuardLatchUntil
            val isTargetingFg = isLatchActive || popupDetector.isNodeAncestorFocusGuard(clickedNode)

            // Clone Click Check
            if (popupDetector.isCloneClick(clickedText)) {
                SafeNodeUtils.safeRecycle(clickedNode)
                if (isTargetingFg) {
                    Log.i(TAG, "Decision: BLOCKED (Clone click specifically targeting FocusGuard)")
                    handleLauncherFocusGuardAttempt(service, pkg, "Launcher Preemptive Clone Block")
                    return true
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Decision: IGNORED (Clone click on non-FocusGuard app)")
                    }
                    return true
                }
            }

            // Uninstall Click Check
            if (popupDetector.isUninstallClick(clickedText)) {
                SafeNodeUtils.safeRecycle(clickedNode)
                if (isTargetingFg) {
                    Log.i(TAG, "Decision: BLOCKED (Uninstall click specifically targeting FocusGuard)")
                    focusGuardLatchUntil = 0L
                    handleLauncherFocusGuardAttempt(service, pkg, "Launcher Preemptive Uninstall Block")
                    return true
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Decision: IGNORED (Uninstall click on non-FocusGuard app)")
                    }
                    return true
                }
            }
            SafeNodeUtils.safeRecycle(clickedNode)
        }

        // D. Launcher Popup / Shortcut Menu Interception
        if (rootNode != null) {
            val popupAnchor = popupDetector.findActivePopupAnchor(rootNode)
            if (popupAnchor != null) {
                val isLatchActive = nowRealtime < focusGuardLatchUntil
                val isFgPopup = popupDetector.isPopupForFocusGuard(rootNode, popupAnchor, isLatchActive)
                SafeNodeUtils.safeRecycle(popupAnchor)

                if (isFgPopup) {
                    focusGuardLatchUntil = 0L
                    Log.i(TAG, "Decision: BLOCKED (FocusGuard popup menu reactive intercept)")
                    handleLauncherFocusGuardAttempt(service, pkg, "Launcher Hold Reactive Block")
                    return true
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Decision: IGNORED (Popup menu belongs to another app)")
                    }
                    return true
                }
            }
        }

        // E. Dragging to Uninstall / Drop Target
        if (popupDetector.isDragDropTarget(className, rootNode)) {
            val isLatchActive = nowRealtime < focusGuardLatchUntil
            if (isLatchActive || target == LauncherTarget.FOCUSGUARD) {
                focusGuardLatchUntil = 0L
                Log.i(TAG, "Decision: BLOCKED (Drag-to-uninstall FocusGuard)")
                handleLauncherFocusGuardAttempt(service, pkg, "Launcher Drop-to-Uninstall FocusGuard")
                return true
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Decision: IGNORED (Drag-to-uninstall on non-FocusGuard app)")
                }
                return true
            }
        }

        return true
    }

    /**
     * Dedicated flow for launcher long-press / uninstall / clone on FocusGuard.
     * 1. Frame 0: GLOBAL_ACTION_BACK kills the launcher popup before render.
     * 2. Post-delay 100ms: Mounts overlay with sendHomeOnDismiss=false (no startActivity(homeIntent)).
     * 3. 1500ms cooldown so duplicate launcher events don't re-trigger.
     * 4. 300ms Vivo retry if overlay failed to attach.
     */
    fun handleLauncherFocusGuardAttempt(
        service: AccessibilityService,
        pkg: String,
        reason: String
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFocusGuardBlockTimestamp < 1500L) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Debounce: Launcher FocusGuard block skipped (within 1500ms cooldown)")
            }
            return
        }
        lastFocusGuardBlockTimestamp = now

        // 1. Frame 0: Immediately kill the launcher popup
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.i(TAG, "Frame 0: GLOBAL_ACTION_BACK sent to dismiss launcher popup immediately ($pkg)")

        // 2. Post-delay 100ms: Mount full-screen shield overlay without resetting launcher via HOME
        val duration = repository.getFastBlockCountdownSeconds().coerceAtLeast(3)
        val customMessage = repository.getFastCustomBlockMessage().takeIf { it.isNotBlank() }
        overlayManager.currentPresetId = repository.getFastOverlayPresetId()
        overlayManager.currentDimAlpha = repository.getFastOverlayDimAlpha()

        mainHandler.postDelayed({
            overlayManager.showOverlay(
                durationSeconds = duration,
                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                targetName = "FocusGuard Protection",
                sourcePackage = pkg,
                description = "FocusGuard is protected from uninstall & cloning.",
                customMessage = customMessage,
                sendHomeOnDismiss = false,
                onAttached = {
                    Log.i(TAG, "Launcher FocusGuard overlay mounted successfully.")
                }
            )
        }, 100L)

        // 3. Vivo 300ms retry: if overlay failed to attach, retry showOverlay once
        mainHandler.postDelayed({
            if (!overlayManager.isOverlayShowing) {
                Log.w(TAG, "Vivo retry: Overlay was not attached after 300ms -> Retrying showOverlay once")
                overlayManager.showOverlay(
                    durationSeconds = duration,
                    blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                    targetName = "FocusGuard Protection",
                    sourcePackage = pkg,
                    description = "FocusGuard is protected from uninstall & cloning.",
                    customMessage = customMessage,
                    sendHomeOnDismiss = false,
                    onAttached = {
                        Log.i(TAG, "Vivo retry: Launcher FocusGuard overlay mounted successfully.")
                    }
                )
            }
        }, 300L)

        // 4. Log event ONLY for FocusGuard
        scope.launch {
            repository.logEvent(
                EventType.UNINSTALL_ATTEMPT,
                "Target=FocusGuard Launcher | Pkg=$pkg | $reason"
            )
        }
    }
}
