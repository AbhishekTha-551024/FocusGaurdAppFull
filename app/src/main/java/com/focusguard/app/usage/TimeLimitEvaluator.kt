package com.focusguard.app.usage

import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LimitStatus {
    object Ok : LimitStatus
    data class Warning(val remainingMinutes: Int, val usedMinutes: Int, val limitMinutes: Int) : LimitStatus
    data class Exceeded(val usedMinutes: Int, val limitMinutes: Int) : LimitStatus
}

internal fun trackingDateChanged(
    previousYear: Int,
    previousDayOfYear: Int,
    previousTimeZone: String,
    currentYear: Int,
    currentDayOfYear: Int,
    currentTimeZone: String
): Boolean = previousYear != currentYear ||
    previousDayOfYear != currentDayOfYear ||
    previousTimeZone != currentTimeZone

@Singleton
class TimeLimitEvaluator @Inject constructor(
    private val usageStatsReader: UsageStatsReader
) {
    // In-memory accumulated session tracking to provide 0ms real-time precision
    // and bypass Android UsageStatsManager event-buffering lag.
    private val accumulatedSessionMsToday = ConcurrentHashMap<String, Long>()
    private val trackingDayLock = Any()
    private val initialCalendar = Calendar.getInstance()
    private var trackingYear = initialCalendar.get(Calendar.YEAR)
    private var trackingDayOfYear = initialCalendar.get(Calendar.DAY_OF_YEAR)
    private var trackingTimeZone = initialCalendar.timeZone.id

    private fun checkDayReset() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
        val currentTimeZone = calendar.timeZone.id
        synchronized(trackingDayLock) {
            if (trackingDateChanged(
                    trackingYear,
                    trackingDayOfYear,
                    trackingTimeZone,
                    currentYear,
                    currentDay,
                    currentTimeZone
                )
            ) {
                accumulatedSessionMsToday.clear()
                trackingYear = currentYear
                trackingDayOfYear = currentDay
                trackingTimeZone = currentTimeZone
            }
        }
    }

    /**
     * Records elapsed foreground duration from an active session into the in-memory cache.
     */
    fun recordSessionUsage(packageName: String, elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        checkDayReset()
        val pkg = packageName.lowercase()
        accumulatedSessionMsToday.merge(pkg, elapsedMs, Long::plus)
    }

    /**
     * Returns total foreground duration in milliseconds today, combining
     * Android UsageStatsManager historical data with in-memory active session tracking.
     */
    fun getTodayUsageMillis(packageName: String, liveSessionMs: Long = 0L): Long {
        checkDayReset()
        val pkg = packageName.lowercase()
        val statsMs = usageStatsReader.getTodayForegroundMillis(pkg)
        val accumulatedMs = accumulatedSessionMsToday[pkg] ?: 0L
        // Take the max to ensure we never lose time if UsageStatsManager is delayed,
        // and add any active in-flight session time.
        val baseMs = maxOf(statsMs, accumulatedMs)
        return baseMs + liveSessionMs.coerceAtLeast(0L)
    }

    /**
     * Returns total foreground duration in whole minutes for today.
     */
    fun getTodayForegroundMinutes(packageName: String, liveSessionMs: Long = 0L): Int {
        return (getTodayUsageMillis(packageName, liveSessionMs) / 60_000L).toInt()
    }

    /**
     * Evaluates whether [packageName] has exceeded its configured [AppTimeLimitEntity]
     * with millisecond precision to eliminate late blocking.
     */
    fun evaluate(packageName: String, limitEntity: AppTimeLimitEntity, liveSessionMs: Long = 0L): LimitStatus {
        if (!limitEntity.isEnabled || limitEntity.dailyLimitMinutes <= 0) {
            return LimitStatus.Ok
        }

        val totalUsedMs = getTodayUsageMillis(packageName, liveSessionMs)
        val limitMs = limitEntity.dailyLimitMinutes * 60_000L
        val limitMinutes = limitEntity.dailyLimitMinutes
        val usedMinutes = (totalUsedMs / 60_000L).toInt()

        return when {
            totalUsedMs >= limitMs -> {
                LimitStatus.Exceeded(
                    usedMinutes = maxOf(usedMinutes, limitMinutes),
                    limitMinutes = limitMinutes
                )
            }
            totalUsedMs >= (limitMs - 5 * 60_000L).coerceAtLeast(60_000L) -> {
                val remainingMinutes = ((limitMs - totalUsedMs) / 60_000L).toInt().coerceAtLeast(0)
                LimitStatus.Warning(
                    remainingMinutes = remainingMinutes,
                    usedMinutes = usedMinutes,
                    limitMinutes = limitMinutes
                )
            }
            else -> LimitStatus.Ok
        }
    }

    /**
     * Returns remaining milliseconds before [packageName] hits its daily limit.
     */
    fun getRemainingMillis(packageName: String, limitEntity: AppTimeLimitEntity, liveSessionMs: Long = 0L): Long {
        if (!limitEntity.isEnabled) return Long.MAX_VALUE
        val totalUsedMs = getTodayUsageMillis(packageName, liveSessionMs)
        val limitMs = limitEntity.dailyLimitMinutes * 60_000L
        return (limitMs - totalUsedMs).coerceAtLeast(0L)
    }

    /**
     * Batch evaluates all enabled time limits against today's usage in a single 1ms pass.
     * Returns the set of package names that have exceeded their daily limit.
     */
    fun getExceededPackages(limits: Collection<AppTimeLimitEntity>): Set<String> {
        checkDayReset()
        val usageMap = usageStatsReader.getAllTodayForegroundMillis()
        val exceeded = mutableSetOf<String>()

        for (limit in limits) {
            if (!limit.isEnabled) continue
            val pkg = limit.packageName.lowercase()
            val statsMs = usageMap[pkg] ?: 0L
            val accumMs = accumulatedSessionMsToday[pkg] ?: 0L
            val totalMs = maxOf(statsMs, accumMs)
            val limitMs = limit.dailyLimitMinutes * 60_000L

            if (totalMs >= limitMs) {
                exceeded.add(pkg)
            }
        }
        return exceeded
    }
}
