package com.focusguard.app.service.runtime

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.blockers.MultiWindowGuard
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.utils.PackageClassifier
import com.focusguard.app.utils.SafeNodeUtils
import kotlinx.coroutines.launch

/**
 * Guard chain for [FocusGuardAccessibilityService.onAccessibilityEvent].
 *
 * Phase 5 split: this is the former `processAccessibilityEvent` body, moved verbatim.
 * Guard order, early returns, coalescing and every callback are unchanged; the service
 * only forwards the event here.
 */
internal class AccessibilityEventRouter(
    private val service: FocusGuardAccessibilityService,
    private val runtime: ServiceRuntime
) {

    fun process(event: AccessibilityEvent?) {
        val e = event ?: return
        val rawPkg = e.packageName?.toString() ?: ""
        val className = e.className?.toString() ?: ""

        // ── GUARD 0: Handle TYPE_WINDOWS_CHANGED even if event.packageName is null ─
        if (e.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Our own overlay attach/detach emits windows-changed. Re-entering the multi-window
            // guard here remounts the shield in a loop (dismiss -> add -> windows-changed -> ...).
            if (runtime.deps.overlayManager.isOverlayShowing) {
                // While a held shield is up this event is also how Home / app switches surface:
                // the blocked app's window disappears from the accessibility window list. Our own
                // attach/detach also emits this event, but there the blocked window is still
                // present, so nothing is released and the shield keeps ownership of the screen.
                if (!runtime.deps.overlayManager.releaseHeldShieldIfForegroundLeft()) return
            }
            val isIntercepted = MultiWindowGuard.checkAndInterceptMultiWindow(
                service = service,
                repository = runtime.deps.repository,
                sessionBlockManager = runtime.deps.sessionBlockManager,
                onIntercept = { sourcePkg, reason, targetName, desc ->
                    val appLabel = service.cachedAppLabel(sourcePkg)
                    service.triggerBlock(
                        sourcePackage = sourcePkg,
                        reason = reason,
                        blockType = OverlayBlockType.APP_BLOCK,
                        targetName = if (targetName == sourcePkg) appLabel else targetName,
                        description = desc
                    )
                }
            )
            if (isIntercepted) return
            if (rawPkg.isBlank()) return
        }

        if (rawPkg.isBlank()) return
        val pkg = rawPkg

        // ── GUARD 1: Self-exclusion (never react to FocusGuard itself) ───────
        if (pkg == service.packageName || pkg.startsWith("com.focusguard.app")) {
            // NOTE: do not release a held shield here. The shield's own ComposeView emits
            // accessibility window events under our package, so releasing on self-events would
            // dismiss and remount the shield in a loop. The shield is released when the user
            // actually leaves the blocked app (launcher / recents / another package).
            runtime.foregroundSessionTracker.onSelfPackage(pkg)
            return
        }

        // ── GUARD 1.05: Fast-discard software keyboards & IME (Zero typing CPU lag) ─
        if (PackageClassifier.isKeyboardPackage(pkg)) {
            return
        }

        // ── GUARD 1.1: Active Overlay Protection & Interception ─
        if (runtime.activeOverlayGuard.process(e = e, pkg = pkg, className = className)) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        if (runtime.systemGuardPipeline.process(pkg = pkg, className = className, nowRealtime = nowRealtime)) {
            return
        }

        val isPackageChanged = pkg != runtime.foregroundSessionTracker.currentPackage
        val isWindowTransition = e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (runtime.windowGuardPipeline.process(
                pkg = pkg,
                isPackageChanged = isPackageChanged,
                isWindowTransition = isWindowTransition,
                nowRealtime = nowRealtime
            )) {
            return
        }

        // ── PROTECTION STATE RESOLUTION (<0.001ms cached atomic lookup) ───────
        val isUninstallProtectionOn = runtime.deps.repository.isFastUninstallProtectionEnabled()
        val isOnboardingDone = runtime.deps.repository.isFastOnboardingComplete()
        val isDeviceAdminCurrentlyActive = service.isDeviceAdminActive()
        val protectionWanted = isDeviceAdminCurrentlyActive && isUninstallProtectionOn

        // Admin can be revoked while Uninstall Protection is still ON (adb revoke, OEM reset, a failed
        // re-grant). `protectionWanted` already ANDs the admin flag in, so it can never be the trigger
        // here — the tamper is exactly "protection wanted, admin gone".
        if (isUninstallProtectionOn && !isDeviceAdminCurrentlyActive) {
            val nowRt = SystemClock.elapsedRealtime()
            if (nowRt - runtime.timestamps.lastAdminTamperLogTimestamp > Timings.ONE_MINUTE_MS) {
                runtime.timestamps.lastAdminTamperLogTimestamp = nowRt
                runtime.scope.launch {
                    runtime.deps.repository.logEvent(
                        EventType.ADMIN_REMOVED_WHILE_PROTECTED,
                        "Device Admin is inactive while Uninstall Protection is wanted! Tamper protection remains enforced."
                    )
                }
            }
        }

        // ── 1. LAUNCHER FAST PATH (< 0.05ms) ──────────────────────────────────
        if (service.isLauncherPackage(pkg)) {
            val rootNode = try { service.rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (runtime.deps.launcherEventHandler.processLauncherEvent(
                        service = service,
                        rootNode = rootNode,
                        e = e,
                        pkg = pkg,
                        className = className,
                        protectionWanted = protectionWanted,
                        nowRealtime = nowRealtime,
                        onTriggerBlock = { sourcePkg, reason, blockType, targetName, desc ->
                            service.triggerBlock(sourcePkg, reason, blockType, targetName, desc)
                        }
                    )) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 2. PACKAGE INSTALLER & STORE UNINSTALL PATH (< 0.1ms) ─────────────
        if (PackageClassifier.isInstallerPackage(pkg, className)) {
            val rootNode = try { service.rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (runtime.deps.packageInstallerHandler.processInstallerEvent(
                        rootNode = rootNode,
                        e = e,
                        pkg = pkg,
                        className = className,
                        protectionWanted = protectionWanted,
                        onTamperTriggered = { sourcePkg, reason, targetName, toastMsg ->
                            // handleSecurityTamper already sends BACK then HOME; sending HOME here
                            // too put three navigation actions on one installer event, which is the
                            // burst Vivo's watchdog punishes by unbinding this service.
                            service.handleSecurityTamper(
                                sourcePackage = sourcePkg,
                                reason = reason,
                                targetName = targetName,
                                blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
                                customToastMessage = toastMsg
                            )
                        }
                    )) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 3. SYSTEM SETTINGS & OEM SECURITY CENTERS PATH (< 0.2ms) ──────────
        val lowerPkg = pkg.lowercase()
        if (PackageClassifier.isSettingsPackage(lowerPkg)) {
            val rootNode = try { service.rootInActiveWindow ?: e.source } catch (_: Exception) { null }
            try {
                if (runtime.deps.settingsEventHandler.processSettingsEvent(
                        service = service,
                        rootNode = rootNode,
                        e = e,
                        pkg = pkg,
                        className = className,
                        protectionWanted = protectionWanted,
                        isDeviceAdminCurrentlyActive = isDeviceAdminCurrentlyActive,
                        nowRealtime = nowRealtime,
                        getCachedAppLabel = { service.cachedAppLabel(it) },
                        onHandleSecurityTamper = { sourcePkg, reason, targetName, blockType, toastMsg ->
                            service.handleSecurityTamper(sourcePkg, reason, targetName, blockType, toastMsg)
                        }
                    )) {
                    return
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
            return
        }

        // ── 4. SYSTEM TOOLS FILTER (Dialer, Clock, SystemUI) ──────────────────
        if (PackageClassifier.isSystemOrLauncherPackage(
                pkg = pkg,
                launcherDetector = runtime.deps.launcherDetector,
                isProtectionActive = isDeviceAdminCurrentlyActive && protectionWanted,
                isSessionActive = runtime.deps.sessionBlockManager.isSessionCurrentlyActive()
            )) {
            return
        }

        // ── 5. TARGET APPLICATIONS (Blocked Apps, Limits, Reels, Browsers, Keywords) ─
        // WINDOW-SCAN COALESCING: everything below walks the window tree. A scrolling feed
        // emits TYPE_WINDOW_CONTENT_CHANGED dozens of times per second, so sample it per package.
        // Window transitions, clicks, long-presses and text changes bypass this gate. Security
        // paths above (launcher long-press, installer, Settings) are never sampled.
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            !runtime.windowScanCoalescer.shouldProcess(pkg, nowRealtime)
        ) {
            return
        }

        val rootNode = try { service.rootInActiveWindow ?: e.source } catch (_: Exception) { null }
        try {
            runtime.targetAppPipeline.processEvent(
                rootNode = rootNode,
                e = e,
                pkg = pkg,
                className = className,
                nowRealtime = nowRealtime,
                isPackageChanged = isPackageChanged,
                isWindowTransition = isWindowTransition,
                protectionWanted = protectionWanted
            )
        } finally {
            SafeNodeUtils.safeRecycle(rootNode)
        }
    }
}
