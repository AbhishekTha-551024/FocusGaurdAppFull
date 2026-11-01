package com.focusguard.app.service.handlers

import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.engine.BlockDecisionEngine
import com.focusguard.app.core.engine.RulesSnapshotHolder
import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.usage.ScheduleEvaluator
import javax.inject.Inject
import javax.inject.Singleton

sealed interface HardBlockResult {
    data class Block(
        val blockType: OverlayBlockType,
        val reason: String,
        val targetName: String,
        val description: String,
        val isLimit: Boolean,
        val isSched: Boolean
    ) : HardBlockResult

    data object Clear : HardBlockResult
    data object HardBlockedButAllowed : HardBlockResult
}

@Singleton
class HardBlockHandler @Inject constructor(
    private val repository: FocusGuardRepository,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val blockDecisionEngine: BlockDecisionEngine,
    private val rulesSnapshotHolder: RulesSnapshotHolder,
    private val sessionBlockManager: SessionBlockManager
) {
    fun checkHardBlock(
        pkg: String,
        className: String,
        eventType: Int,
        nowRealtime: Long,
        blockedPackages: Set<String>,
        getCachedAppLabel: (String) -> String
    ): HardBlockResult {
        val eventContext = EventContext(
            packageName = pkg,
            className = className,
            eventType = eventType,
            monotonicTimeMs = nowRealtime
        )
        val engineDecision = blockDecisionEngine.evaluate(eventContext, rulesSnapshotHolder.current)
        val isEngineBlocked = engineDecision is BlockDecision.Block
        val isHardBlocked = isEngineBlocked || blockedPackages.contains(pkg) || blockedPackages.contains(pkg.lowercase()) || repository.isFastBlockedApp(pkg)
        val isTimeLimitExceeded = repository.isFastTimeLimitExceeded(pkg)
        val activeSchedules = repository.getFastActiveSchedules()
        val scheduleBlock = if (activeSchedules.isNotEmpty()) {
            scheduleEvaluator.isBlockedBySchedule(
                packageName = pkg,
                schedules = activeSchedules,
                isAppBlockedInBlocklist = isHardBlocked
            )
        } else null

        if ((isHardBlocked || isTimeLimitExceeded || scheduleBlock != null) && !sessionBlockManager.isAlwaysAllowedPackage(pkg)) {
            val appLabel = getCachedAppLabel(pkg)
            val isLimit = !isHardBlocked && isTimeLimitExceeded && scheduleBlock == null
            val isSched = scheduleBlock != null && !isHardBlocked
            val limitEntity = if (isLimit) repository.getFastTimeLimits()[pkg.lowercase()] else null
            val limitMins = limitEntity?.dailyLimitMinutes ?: 0

            val blockType = when {
                isSched -> OverlayBlockType.SCHEDULE_BLOCK
                isLimit -> OverlayBlockType.TIME_LIMIT_BLOCK
                else -> OverlayBlockType.APP_BLOCK
            }
            val reason = when {
                isSched -> "Focus Schedule Active: ${scheduleBlock?.scheduleTitle ?: "Scheduled"}"
                isLimit -> "Daily Time Limit Exceeded: $appLabel"
                else -> "App Blocked: $appLabel"
            }
            val description = when {
                isSched -> "Blocked by Focus Schedule '${scheduleBlock?.scheduleTitle}' until ${scheduleBlock?.endTimeFormatted}."
                isLimit -> "Daily cap of $limitMins min reached. Resets at midnight."
                else -> "This app is in your FocusGuard blocklist. Take a breath and stay on track!"
            }

            return HardBlockResult.Block(
                blockType = blockType,
                reason = reason,
                targetName = appLabel,
                description = description,
                isLimit = isLimit,
                isSched = isSched
            )
        }

        return if (isHardBlocked) HardBlockResult.HardBlockedButAllowed else HardBlockResult.Clear
    }
}

