package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.GuardTimestamps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pipeline coordinating all target application inspections, time limit checks,
 * mindful pauses, reels detection, keyword matching, and heartbeat tracking.
 *
 * Scoped to the service instance via lazy delegate (not @Singleton).
 * Common-path lambda references are initialized once to avoid allocations on the accessibility thread.
 */
class TargetAppPipeline(
    private val host: TargetAppHost,
    private val hardBlockHandler: HardBlockHandler,
    private val mindfulPauseHandler: MindfulPauseHandler,
    private val autoBlockHandler: AutoBlockHandler,
    private val browserGuardHandler: BrowserGuardHandler,
    private val clonedAppScanHandler: ClonedAppScanHandler,
    private val reelDetectionHandler: ReelDetectionHandler,
    private val scheduleLimitHandler: ScheduleLimitHandler,
    private val keywordDetectionHandler: KeywordDetectionHandler,
    private val heartbeatCoordinator: HeartbeatCoordinator,
    private val repository: FocusGuardRepository,
    private val overlayManager: BlockOverlayManager,
    private val foregroundSessionTracker: ForegroundSessionTracker,
    private val timestamps: GuardTimestamps,
    private val blockedPackages: Set<String>,
    private val scope: CoroutineScope
) {
    private companion object {
        private const val TAG = "FocusGuardService"
    }

    // Pre-allocated functional references to eliminate hot-path allocations
    private val labelProvider: (String) -> String = { host.cachedAppLabel(it) }
    private val triggerFn: (String, String, OverlayBlockType, String, String) -> Unit = { sourcePkg, reason, blockType, targetName, desc ->
        host.triggerBlock(sourcePkg, reason, blockType, targetName, desc)
    }
    private val rootProvider: () -> AccessibilityNodeInfo? = { host.currentRoot() }

    fun processEvent(
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        nowRealtime: Long,
        isPackageChanged: Boolean,
        isWindowTransition: Boolean,
        protectionWanted: Boolean
    ) {
        // Monotonic Debounce to avoid multi-triggering overlay in same transition
        if (!isPackageChanged && (nowRealtime - timestamps.lastBlockTimestamp < Timings.TRANSITION_DEBOUNCE_MS)) return

        // Manage In-Foreground package transitions
        if (isPackageChanged) {
            foregroundSessionTracker.onPackageChanged(pkg, nowRealtime)
            heartbeatCoordinator.updateHeartbeat(
                pkg = pkg,
                scope = scope,
                isOverlayShowing = { host.isOverlayShowing() },
                rootProvider = rootProvider,
                getCachedAppLabel = labelProvider,
                onTriggerBlock = triggerFn
            )
        }

        // ── 1. FAST-PATH: INSTANT HARD APP BLOCKER, EXCEEDED TIME LIMITS & SCHEDULES (0ms latency) ─
        val hardBlockResult = hardBlockHandler.checkHardBlock(
            pkg = pkg,
            className = className,
            eventType = e.eventType,
            nowRealtime = nowRealtime,
            blockedPackages = blockedPackages,
            getCachedAppLabel = labelProvider
        )
        val isHardBlocked = when (hardBlockResult) {
            HardBlockResult.CallSurfaceAllowed -> {
                foregroundSessionTracker.startForegroundPollingIfTracked(pkg, host.cachedAppLabel(pkg))
                return
            }
            is HardBlockResult.Block -> {
                timestamps.lastBlockTimestamp = nowRealtime
                Log.i(TAG, "Instant 0ms Block triggered for $pkg (${hardBlockResult.targetName}) [isLimit=${hardBlockResult.isLimit}, isSched=${hardBlockResult.isSched}]")
                host.triggerBlock(
                    sourcePackage = pkg,
                    reason = hardBlockResult.reason,
                    blockType = hardBlockResult.blockType,
                    targetName = hardBlockResult.targetName,
                    description = hardBlockResult.description
                )
                return
            }
            is HardBlockResult.HardBlockedButAllowed -> true
            is HardBlockResult.Clear -> false
        }

        // ── 1.2 MINDFUL PAUSE INTERCEPT (Conscious Breathing & Intent Delay) ─
        if (mindfulPauseHandler.shouldIntercept(pkg, isHardBlocked, isPackageChanged, isWindowTransition)) {
            val appLabel = host.cachedAppLabel(pkg)
            timestamps.lastBlockTimestamp = nowRealtime
            mindfulPauseHandler.showMindfulPause(
                pkg = pkg,
                appLabel = appLabel,
                isForegroundPackage = { foregroundSessionTracker.currentPackage == pkg },
                onTriggerBlock = triggerFn,
                onForceClose = { host.forceCloseApp(it) }
            )
            return
        }

        // ── 1.5 AUTO-BLOCK NEW APPS JIT INTERCEPT (Catches recently installed apps on open) ─
        val autoBlockAction = autoBlockHandler.checkAutoBlock(
            pkg = pkg,
            isHardBlocked = isHardBlocked,
            getCachedAppLabel = labelProvider,
            scope = scope
        )
        if (autoBlockAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            host.triggerBlock(
                sourcePackage = pkg,
                reason = "Auto-Blocked New App: ${autoBlockAction.appLabel}",
                blockType = OverlayBlockType.APP_BLOCK,
                targetName = autoBlockAction.appLabel,
                description = "This newly installed app was automatically blocked by FocusGuard. You can review and unblock it in the app."
            )
            return
        }

        // ── 1.6 UNSUPPORTED BROWSER GUARD (Kills Unknown Browser Loophole) ─
        // Attribution rule: the registry quarantines only packages with their own browser
        // surface. Custom Tabs hosts and apps that merely resolve http(s) links are allowed
        // through, so a link opened inside a chat/mail app is never mistaken for a browser.
        val unsupportedBrowserAction = browserGuardHandler.checkUnsupportedBrowser(
            pkg = pkg,
            getCachedAppLabel = labelProvider
        )
        if (unsupportedBrowserAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            host.triggerBlock(
                sourcePackage = pkg,
                reason = "Unsupported Browser Blocked: ${unsupportedBrowserAction.appLabel}",
                blockType = OverlayBlockType.APP_BLOCK,
                targetName = unsupportedBrowserAction.appLabel,
                description = "Unsupported browser detected. FocusGuard only permits its verified browser list for safe filtering."
            )
            return
        }

        // ── ANTI-CLONING: STRUCTURAL CLONED TARGET VIEW ID SCAN (Layer 2) ─
        val clonedAppAction = clonedAppScanHandler.checkClonedTarget(
            rootNode = rootNode,
            pkg = pkg,
            isProtectionWanted = protectionWanted,
            getCachedAppLabel = labelProvider
        )
        if (clonedAppAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            host.triggerBlock(
                sourcePackage = pkg,
                reason = "Cloned Distraction App Blocked: ${clonedAppAction.appLabel}",
                blockType = OverlayBlockType.CLONER_BLOCK,
                targetName = "Cloned App (${clonedAppAction.appLabel})",
                description = "Target app views were detected running inside an unverified container or modified APK clone."
            )
            return
        }

        // ── 1.8 ULTRA-FAST REELS INTERCEPT (< 1ms Fast Path, Zero Latency) ─
        val reelAction = reelDetectionHandler.detectReelOrShort(
            e = e,
            rootNode = rootNode,
            pkg = pkg,
            isDebounceElapsed = (nowRealtime - timestamps.lastBlockTimestamp >= 800L),
            isOverlayShowing = host.isOverlayShowing()
        )
        if (reelAction != null) {
            timestamps.lastBlockTimestamp = nowRealtime
            Log.i(TAG, "Ultra-fast ${reelAction.targetLabel} block triggered for $pkg")
            host.triggerBlock(
                sourcePackage = pkg,
                reason = "${reelAction.targetLabel} Blocked",
                blockType = OverlayBlockType.SHORT_OR_REEL_BLOCK,
                targetName = reelAction.targetLabel,
                description = reelAction.targetDesc
            )
            return
        }

        // ── 2. FOCUS SCHEDULES & TIME LIMITS (Evaluated only on window transition) ─
        if ((isPackageChanged || isWindowTransition) && repository.hasActiveSchedulesOrLimits(pkg)) {
            val appLabel = host.cachedAppLabel(pkg)

            scope.launch {
                val liveSessionMs = foregroundSessionTracker.liveSessionMs(android.os.SystemClock.elapsedRealtime())
                scheduleLimitHandler.handleScheduleOrLimit(
                    pkg = pkg,
                    appLabel = appLabel,
                    liveSessionMs = liveSessionMs,
                    isOverlayShowing = { host.isOverlayShowing() },
                    getLastBlockTimestamp = { timestamps.lastBlockTimestamp },
                    clock = { android.os.SystemClock.elapsedRealtime() },
                    onUpdateLastBlockTimestamp = { timestamps.lastBlockTimestamp = it },
                    onTriggerBlock = triggerFn,
                    onStartForegroundPolling = { p, label ->
                        foregroundSessionTracker.startForegroundPollingIfTracked(p, label)
                    }
                )
            }
        }

        // ── 3. KEYWORD & CONTENT BLOCKING (Browsers, YouTube On-Screen Metadata, and Universal Apps) ─
        val keywordHandled = keywordDetectionHandler.detectAndHandle(
            e = e,
            rootNode = rootNode,
            pkg = pkg,
            getCachedAppLabel = labelProvider,
            triggerBlock = triggerFn
        )
        if (keywordHandled) return
    }
}
