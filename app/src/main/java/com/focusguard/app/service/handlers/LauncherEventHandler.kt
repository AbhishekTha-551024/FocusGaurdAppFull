package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.BuildConfig
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Launcher"

internal fun shouldBlockLauncherHideApps(target: LauncherTarget): Boolean =
    target == LauncherTarget.FOCUSGUARD

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
 * - Dedicated launcher block flow mounts the overlay before navigating HOME and retries attachment once.
 */
@Singleton
class LauncherEventHandler @Inject constructor(
    private val targetResolver: LauncherTargetResolver,
    private val popupDetector: LauncherPopupDetector,
    private val attemptCoordinator: LauncherAttemptCoordinator
) {
    var focusGuardLatchUntil: Long = 0L
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

        // Resolve target identity
        val target = targetResolver.resolve(e, rootNode, screenWidth, screenHeight, nowRealtime)

        // A. Hide Apps is protected only when the launcher interaction is confirmed as FocusGuard.
        if (popupDetector.isLauncherHideApps(className, rootNode) &&
            shouldBlockLauncherHideApps(target)
        ) {
            Log.w(TAG, "Decision: BLOCKED (Hide Apps tamper attempt targeting FocusGuard: $pkg - $className)")
            onTriggerBlock(
                pkg,
                "Hide Apps Access Blocked in Launcher",
                OverlayBlockType.SETTINGS_TAMPER,
                "Hide Apps Protection",
                "Hiding FocusGuard is restricted to prevent bypassing protection."
            )
            return true
        }

        // B. Long-Press Evaluation
        if (e.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            when (target) {
                LauncherTarget.FOCUSGUARD -> {
                    focusGuardLatchUntil = nowRealtime + 2500L
                    Log.i(TAG, "Decision: BLOCKED (Launcher Long-Press on FocusGuard, latch set for 2500ms)")
                    attemptCoordinator.handle(service, pkg, "Launcher Long-Press on FocusGuard", e.eventTime)
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
                    attemptCoordinator.handle(service, pkg, "Launcher Preemptive Clone Block", e.eventTime)
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
                    attemptCoordinator.handle(service, pkg, "Launcher Preemptive Uninstall Block", e.eventTime)
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
                    attemptCoordinator.handle(service, pkg, "Launcher Hold Reactive Block", e.eventTime)
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
                attemptCoordinator.handle(service, pkg, "Launcher Drop-to-Uninstall FocusGuard", e.eventTime)
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

}
