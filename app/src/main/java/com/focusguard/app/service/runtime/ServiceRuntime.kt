package com.focusguard.app.service.runtime

import android.os.Handler
import android.os.Looper
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.GuardTimestamps
import com.focusguard.app.service.handlers.ActiveOverlayGuard
import com.focusguard.app.service.handlers.BlockTrigger
import com.focusguard.app.service.handlers.ForegroundSessionTracker
import com.focusguard.app.service.handlers.SystemGuardPipeline
import com.focusguard.app.service.handlers.TargetAppPipeline
import com.focusguard.app.service.handlers.WindowGuardPipeline
import com.focusguard.app.service.utils.PackageClassifier
import com.focusguard.app.service.utils.ServiceWindowActions
import com.focusguard.app.service.utils.WindowScanCoalescer
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Per-service-instance component graph for [FocusGuardAccessibilityService].
 *
 * Owns the exact same `by lazy` wiring that used to live inside the service class, so the
 * hot path keeps initializing each collaborator on first use (and only then). Every
 * construction argument is identical to the pre-split service; only the receiver changed.
 */
internal class ServiceRuntime(
    private val service: FocusGuardAccessibilityService,
    internal val deps: FocusGuardServiceDeps,
    internal val scope: CoroutineScope
) {
    internal val mainHandler = Handler(Looper.getMainLooper())

    internal val blockedPackages = CopyOnWriteArraySet<String>()

    internal val timestamps = GuardTimestamps()

    /** Samples repeated content-change events so the window-tree scan does not run per frame. */
    internal val windowScanCoalescer = WindowScanCoalescer()

    internal val windowActions by lazy {
        ServiceWindowActions(
            service = service,
            sendGlobalAction = { action -> service.sendGlobalAction(action) },
            isSystemPackage = { pkg ->
                val isProtectionActive = service.isDeviceAdminActive() && deps.repository.isFastUninstallProtectionEnabled()
                val isSessionActive = deps.sessionBlockManager.isSessionCurrentlyActive()
                PackageClassifier.isSystemOrLauncherPackage(pkg, deps.launcherDetector, isProtectionActive, isSessionActive)
            },
            isLauncherPackage = { pkg -> service.isLauncherPackage(pkg) }
        )
    }

    internal val foregroundSessionTracker by lazy {
        ForegroundSessionTracker(
            repository = deps.repository,
            timeLimitEvaluator = deps.timeLimitEvaluator,
            scheduleEvaluator = deps.scheduleEvaluator,
            mindfulRepository = deps.mindfulRepository,
            scope = scope,
            isSystemOrLauncherPackage = { pkg ->
                val isProtectionActive = service.isDeviceAdminActive() && deps.repository.isFastUninstallProtectionEnabled()
                val isSessionActive = deps.sessionBlockManager.isSessionCurrentlyActive()
                PackageClassifier.isSystemOrLauncherPackage(pkg, deps.launcherDetector, isProtectionActive, isSessionActive)
            },
            onTriggerBlock = { sourcePkg, reason, blockType, targetName, description ->
                service.triggerBlock(
                    sourcePackage = sourcePkg,
                    reason = reason,
                    blockType = blockType,
                    targetName = targetName,
                    description = description
                )
            },
            onStopBrowserHeartbeat = {
                deps.browserHeartbeatTracker.stopHeartbeat()
            },
            isCriticalCallSurface = { pkg -> deps.sessionBlockManager.isCriticalCallSurface(pkg) },
            isPermanentCallSurface = { pkg -> deps.sessionBlockManager.isPermanentCallSurface(pkg) }
        )
    }

    internal val blockTrigger by lazy {
        BlockTrigger(
            context = service.applicationContext,
            repository = deps.repository,
            overlayManager = deps.overlayManager,
            windowActions = windowActions,
            foregroundSessionTracker = foregroundSessionTracker,
            launcherDetector = deps.launcherDetector,
            sessionBlockManager = deps.sessionBlockManager,
            scope = scope,
            isDeviceAdminActive = { service.isDeviceAdminActive() },
            isLauncherPackage = { pkg -> service.isLauncherPackage(pkg) },
            onUpdateLastBlockTimestamp = { timestamps.lastBlockTimestamp = it },
            onUpdateLastOverlayDismiss = { timestamp, pkg ->
                timestamps.lastOverlayDismissTimestamp = timestamp
                timestamps.lastDismissedPackage = pkg
            }
        )
    }

    internal val targetAppPipeline by lazy {
        TargetAppPipeline(
            host = service,
            hardBlockHandler = deps.hardBlockHandler,
            mindfulPauseHandler = deps.mindfulPauseHandler,
            autoBlockHandler = deps.autoBlockHandler,
            browserGuardHandler = deps.browserGuardHandler,
            clonedAppScanHandler = deps.clonedAppScanHandler,
            reelDetectionHandler = deps.reelDetectionHandler,
            scheduleLimitHandler = deps.scheduleLimitHandler,
            keywordDetectionHandler = deps.keywordDetectionHandler,
            heartbeatCoordinator = deps.heartbeatCoordinator,
            repository = deps.repository,
            overlayManager = deps.overlayManager,
            foregroundSessionTracker = foregroundSessionTracker,
            timestamps = timestamps,
            blockedPackages = blockedPackages,
            scope = scope
        )
    }

    internal val systemGuardPipeline by lazy {
        SystemGuardPipeline(
            host = service,
            repository = deps.repository,
            sessionBlockManager = deps.sessionBlockManager,
            launcherDetector = deps.launcherDetector,
            oemScanDetector = deps.oemScanDetector,
            timestamps = timestamps
        )
    }

    internal val activeOverlayGuard by lazy {
        ActiveOverlayGuard(
            host = service,
            repository = deps.repository,
            sessionBlockManager = deps.sessionBlockManager,
            overlayManager = deps.overlayManager,
            packageInstallerHandler = deps.packageInstallerHandler,
            launcherTargetResolver = deps.launcherTargetResolver,
            timestamps = timestamps
        )
    }

    internal val windowGuardPipeline by lazy {
        WindowGuardPipeline(
            host = service,
            windowActions = windowActions,
            repository = deps.repository,
            sessionBlockManager = deps.sessionBlockManager,
            timestamps = timestamps
        )
    }

    internal val router by lazy { AccessibilityEventRouter(service, this) }

    internal val lifecycle by lazy { AccessibilityServiceLifecycle(service, this) }

    internal val sweep by lazy { ActiveWindowSweep(service, this) }

    internal val tamperDelegate by lazy { SecurityTamperDelegate(service, this) }
}
