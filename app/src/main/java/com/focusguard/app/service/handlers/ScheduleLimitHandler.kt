package com.focusguard.app.service.handlers

import android.util.Log
import com.focusguard.app.data.repository.AccessDecision
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FocusGuardService"

@Singleton
class ScheduleLimitHandler @Inject constructor(
    private val repository: FocusGuardRepository
) {
    suspend fun handleScheduleOrLimit(
        pkg: String,
        appLabel: String,
        liveSessionMs: Long,
        isOverlayShowing: () -> Boolean,
        getLastBlockTimestamp: () -> Long,
        clock: () -> Long,
        onUpdateLastBlockTimestamp: (Long) -> Unit,
        onTriggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit,
        onStartForegroundPolling: (pkg: String, appLabel: String) -> Unit
    ) {
        when (val decision = repository.evaluateAccess(pkg, appLabel, liveSessionMs)) {
            is AccessDecision.BlockedBySchedule -> {
                if (isOverlayShowing() || (clock() - getLastBlockTimestamp() < 1500L)) return
                onUpdateLastBlockTimestamp(clock())
                Log.i(TAG, "Focus Schedule block triggered for $pkg: '${decision.scheduleTitle}' until ${decision.endTimeFormatted}")
                onTriggerBlock(
                    pkg,
                    "Focus Schedule Blocked: $appLabel (${decision.scheduleTitle})",
                    OverlayBlockType.SCHEDULE_BLOCK,
                    appLabel,
                    "Blocked by Focus Schedule '${decision.scheduleTitle}' until ${decision.endTimeFormatted}."
                )
            }
            is AccessDecision.BlockedByTimeLimit -> {
                repository.markTimeLimitExceeded(pkg)
                if (isOverlayShowing() || (clock() - getLastBlockTimestamp() < 1500L)) return
                onUpdateLastBlockTimestamp(clock())
                Log.i(TAG, "Time Limit block triggered for $pkg: ${decision.usedMinutes}/${decision.limitMinutes}m")
                onTriggerBlock(
                    pkg,
                    "Daily Time Limit Exceeded: $appLabel (${decision.usedMinutes}/${decision.limitMinutes}m)",
                    OverlayBlockType.TIME_LIMIT_BLOCK,
                    appLabel,
                    "Daily cap of ${decision.limitMinutes} min reached (${decision.usedMinutes} min used). Resets at midnight."
                )
            }
            is AccessDecision.BlockedByAppBlocker -> {
                if (isOverlayShowing() || (clock() - getLastBlockTimestamp() < 1500L)) return
                onUpdateLastBlockTimestamp(clock())
                Log.i(TAG, "Hard App Blocker triggered for $pkg")
                onTriggerBlock(
                    pkg,
                    "App Blocked: $appLabel",
                    OverlayBlockType.APP_BLOCK,
                    appLabel,
                    "This app is in your FocusGuard blocklist. Take a breath and stay on track!"
                )
            }
            AccessDecision.Allowed -> {
                onStartForegroundPolling(pkg, appLabel)
            }
        }
    }
}

