package com.focusguard.app.service.handlers

import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.MindfulRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.usage.ScheduleEvaluator
import com.focusguard.app.usage.TimeLimitEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "FocusGuardService"
private const val ACTIVE_CALL_RECHECK_INTERVAL_MS = 1000L

/**
 * Pure delay computation for in-foreground daily time limit monitoring.
 * Plain JVM compatible for unit testing.
 */
internal fun computeAdaptiveDelay(
    hasLimit: Boolean,
    sessionBaselineMs: Long,
    limitMs: Long,
    liveElapsedMs: Long
): Long {
    if (!hasLimit) return 5000L
    val totalMs = sessionBaselineMs + liveElapsedMs
    val remaining = limitMs - totalMs
    return if (remaining > 1000L) 1000L else remaining.coerceIn(100L, 1000L)
}

/**
 * Pure live session duration computation.
 * Plain JVM compatible for unit testing.
 */
internal fun calculateLiveSessionMs(startTimeMs: Long, nowRealtimeMs: Long): Long {
    return if (startTimeMs > 0L) {
        (nowRealtimeMs - startTimeMs).coerceAtLeast(0L)
    } else 0L
}

/**
 * Manages foreground package session timing, transition recording, and real-time polling.
 *
 * Owned and instantiated per service instance (NOT @Singleton).
 */
class ForegroundSessionTracker(
    private val repository: FocusGuardRepository,
    private val timeLimitEvaluator: TimeLimitEvaluator,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val mindfulRepository: MindfulRepository,
    private val scope: CoroutineScope,
    private val isSystemOrLauncherPackage: (String) -> Boolean,
    private val onTriggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit,
    private val onStopBrowserHeartbeat: () -> Unit,
    private val isCriticalCallSurface: (String) -> Boolean = { false },
    private val isPermanentCallSurface: (String) -> Boolean = { false }
) {
    var currentPackage: String? = null
        private set

    var currentStartTime: Long = 0L
        private set

    private var foregroundPollingJob: Job? = null

    fun liveSessionMs(nowRealtime: Long = SystemClock.elapsedRealtime()): Long {
        return calculateLiveSessionMs(currentStartTime, nowRealtime)
    }

    /**
     * Handles self-exclusion in GUARD 1: updates package to self and cancels foreground polling.
     */
    fun onSelfPackage(pkg: String) {
        currentPackage = pkg
        cancelForegroundPolling()
    }

    /**
     * Manages In-Foreground package transitions: records previous session usage and mindful session end,
     * cancels previous polling, and updates current package and start timestamp.
     */
    fun onPackageChanged(pkg: String, nowRealtime: Long) {
        val prevPkg = currentPackage
        val prevStart = currentStartTime
        if (prevPkg != null && prevStart > 0L && !isSystemOrLauncherPackage(prevPkg)) {
            val sessionDuration = nowRealtime - prevStart
            if (sessionDuration > Timings.MIN_RECORDABLE_SESSION_MS) {
                timeLimitEvaluator.recordSessionUsage(prevPkg, sessionDuration)
            }
            if (mindfulRepository.isPackageMindful(prevPkg)) {
                mindfulRepository.recordSessionEnded(prevPkg)
            }
        }

        cancelForegroundPolling()
        currentPackage = pkg
        currentStartTime = nowRealtime
    }

    /**
     * If blocking the currently active foreground package, immediately records its session duration,
     * resets the session start timestamp, and cancels polling.
     */
    fun onAppBlocked(sourcePackage: String, nowRealtime: Long) {
        if (sourcePackage == currentPackage && currentStartTime > 0L) {
            val sessionElapsed = nowRealtime - currentStartTime
            if (sessionElapsed > 0L) {
                timeLimitEvaluator.recordSessionUsage(sourcePackage, sessionElapsed)
            }
            currentStartTime = 0L
            cancelForegroundPolling()
        }
    }

    /**
     * Launches real-time in-foreground polling for tracked apps with daily time limits or active schedules.
     */
    fun startForegroundPollingIfTracked(pkg: String, appLabel: String) {
        if (isSystemOrLauncherPackage(pkg) || isPermanentCallSurface(pkg)) return
        if (foregroundPollingJob?.isActive == true && currentPackage == pkg) return

        foregroundPollingJob?.cancel()

        val limitEntity = repository.getFastTimeLimits()[pkg.lowercase()]
        val hasLimit = limitEntity != null && limitEntity.isEnabled
        val hasSchedules = repository.getFastActiveSchedules().isNotEmpty()

        if (!hasLimit && !hasSchedules) return

        // Snapshot the exact baseline usage at the start of this continuous foreground session
        val sessionBaselineMs = timeLimitEvaluator.getTodayUsageMillis(pkg)
        val limitMs = (limitEntity?.dailyLimitMinutes ?: 0) * 60_000L

        foregroundPollingJob = scope.launch {
            Log.d(TAG, "Started in-foreground real-time monitoring for $pkg ($appLabel) [Base=$sessionBaselineMs ms, Limit=$limitMs ms]")
            val sessionStartTime = if (currentStartTime > 0L) currentStartTime else SystemClock.elapsedRealtime()

            while (isActive && currentPackage == pkg) {
                if (isCriticalCallSurface(pkg)) {
                    delay(ACTIVE_CALL_RECHECK_INTERVAL_MS)
                    continue
                }

                val liveSessionMs = (SystemClock.elapsedRealtime() - sessionStartTime).coerceAtLeast(0L)

                // 1. Millisecond-accurate daily time limit evaluation (Zero Binder IPC in loop!)
                if (hasLimit) {
                    val totalUsedMs = sessionBaselineMs + liveSessionMs
                    val remainingMs = limitMs - totalUsedMs

                    if (remainingMs <= 0L) {
                        repository.markTimeLimitExceeded(pkg)
                        Log.i(TAG, "In-foreground real-time monitoring caught time limit reached for $pkg: $totalUsedMs ms >= $limitMs ms")
                        val usedMinutes = (totalUsedMs / 60_000L).toInt().coerceAtLeast(limitEntity.dailyLimitMinutes)
                        onTriggerBlock(
                            pkg,
                            "Daily Time Limit Exceeded (In-Foreground): $appLabel",
                            OverlayBlockType.TIME_LIMIT_BLOCK,
                            appLabel,
                            "Daily cap of ${limitEntity.dailyLimitMinutes} min reached ($usedMinutes min used). Resets at midnight."
                        )
                        break
                    }
                }

                // 2. Schedule boundary evaluation (0ms fast-path check)
                if (hasSchedules) {
                    val scheduleBlock = scheduleEvaluator.isBlockedBySchedule(
                        packageName = pkg,
                        schedules = repository.getFastActiveSchedules()
                    )
                    if (scheduleBlock != null) {
                        Log.i(TAG, "In-foreground real-time monitoring caught schedule boundary for $pkg")
                        onTriggerBlock(
                            pkg,
                            "Focus Schedule Active (In-Foreground): $appLabel",
                            OverlayBlockType.SCHEDULE_BLOCK,
                            appLabel,
                            "Blocked by Focus Schedule '${scheduleBlock.scheduleTitle}' until ${scheduleBlock.endTimeFormatted}."
                        )
                        break
                    }
                }

                // 3. Adaptive delay: checks every 1000ms, or exact milliseconds if less than 1s remaining.
                val liveElapsed = (SystemClock.elapsedRealtime() - sessionStartTime).coerceAtLeast(0L)
                val delayTime = computeAdaptiveDelay(
                    hasLimit = hasLimit,
                    sessionBaselineMs = sessionBaselineMs,
                    limitMs = limitMs,
                    liveElapsedMs = liveElapsed
                )

                delay(delayTime)
            }
        }
    }

    fun cancelForegroundPolling() {
        onStopBrowserHeartbeat()
        foregroundPollingJob?.cancel()
        foregroundPollingJob = null
    }

    /**
     * Cleans up all resources. Service onDestroy must call this.
     */
    fun cancelAll() {
        cancelForegroundPolling()
        currentPackage = null
        currentStartTime = 0L
    }
}
