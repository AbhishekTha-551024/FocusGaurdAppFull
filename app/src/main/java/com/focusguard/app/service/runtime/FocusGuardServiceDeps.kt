package com.focusguard.app.service.runtime

import com.focusguard.app.blockers.KeywordBlocker
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.startup.ProtectionStartupReadiness
import com.focusguard.app.core.engine.BlockDecisionEngine
import com.focusguard.app.core.engine.RulesSnapshotHolder
import com.focusguard.app.core.engine.RulesSnapshotSynchronizer
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.MindfulRepository
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.recovery.AccessibilityLivenessWatchdog
import com.focusguard.app.service.handlers.AutoBlockHandler
import com.focusguard.app.service.handlers.BrowserGuardHandler
import com.focusguard.app.service.handlers.ClonedAppScanHandler
import com.focusguard.app.service.handlers.HardBlockHandler
import com.focusguard.app.service.handlers.HeartbeatCoordinator
import com.focusguard.app.service.handlers.KeyEventHandler
import com.focusguard.app.service.handlers.KeywordDetectionHandler
import com.focusguard.app.service.handlers.LauncherEventHandler
import com.focusguard.app.service.handlers.LauncherTargetResolver
import com.focusguard.app.service.handlers.MindfulPauseHandler
import com.focusguard.app.service.handlers.OemScanDetector
import com.focusguard.app.service.handlers.PackageInstallerHandler
import com.focusguard.app.service.handlers.ReelDetectionHandler
import com.focusguard.app.service.handlers.ScheduleLimitHandler
import com.focusguard.app.service.handlers.ServiceReceiverManager
import com.focusguard.app.service.handlers.SettingsEventHandler
import com.focusguard.app.service.handlers.TamperResponseHandler
import com.focusguard.app.service.utils.DeviceAdminManager
import com.focusguard.app.service.utils.GlobalActionGate
import com.focusguard.app.tracker.BrowserHeartbeatTracker
import com.focusguard.app.usage.ScheduleEvaluator
import com.focusguard.app.usage.TimeLimitEvaluator
import com.focusguard.app.utils.LauncherDetector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single field-injection point for every process-wide dependency of
 * [com.focusguard.app.service.FocusGuardAccessibilityService].
 *
 * Phase 5 split: the service used to declare ~30 individual `@Inject lateinit var` fields.
 * They now live here so the service file stays inside the 300-line rule without changing
 * what gets injected or when Hilt injects it (one field injection on the service).
 */
@Singleton
class FocusGuardServiceDeps @Inject constructor(
    val repository: FocusGuardRepository,
    val overlayManager: BlockOverlayManager,
    val keywordBlocker: KeywordBlocker,
    val browserHeartbeatTracker: BrowserHeartbeatTracker,
    val timeLimitEvaluator: TimeLimitEvaluator,
    val scheduleEvaluator: ScheduleEvaluator,
    val serviceReceiverManager: ServiceReceiverManager,
    val heartbeatCoordinator: HeartbeatCoordinator,
    val launcherDetector: LauncherDetector,
    val mindfulRepository: MindfulRepository,
    val sessionBlockManager: SessionBlockManager,
    val blockDecisionEngine: BlockDecisionEngine,
    val rulesSnapshotHolder: RulesSnapshotHolder,
    val rulesSnapshotSynchronizer: RulesSnapshotSynchronizer,
    val livenessWatchdog: AccessibilityLivenessWatchdog,
    val globalActionGate: GlobalActionGate,
    val launcherEventHandler: LauncherEventHandler,
    val launcherTargetResolver: LauncherTargetResolver,
    val settingsEventHandler: SettingsEventHandler,
    val packageInstallerHandler: PackageInstallerHandler,
    val keyEventHandler: KeyEventHandler,
    val deviceAdminManager: DeviceAdminManager,
    val mindfulPauseHandler: MindfulPauseHandler,
    val reelDetectionHandler: ReelDetectionHandler,
    val keywordDetectionHandler: KeywordDetectionHandler,
    val autoBlockHandler: AutoBlockHandler,
    val browserGuardHandler: BrowserGuardHandler,
    val clonedAppScanHandler: ClonedAppScanHandler,
    val hardBlockHandler: HardBlockHandler,
    val scheduleLimitHandler: ScheduleLimitHandler,
    val oemScanDetector: OemScanDetector,
    val tamperResponseHandler: TamperResponseHandler,
    val startupReadiness: ProtectionStartupReadiness
)
