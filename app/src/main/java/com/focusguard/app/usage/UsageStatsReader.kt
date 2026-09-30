package com.focusguard.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UsageStatsReader"

/**
 * UsageStatsReader
 * ================
 * High-precision reader wrapping Android's UsageStatsManager.
 *
 * Uses event-based aggregation (MOVE_TO_FOREGROUND -> MOVE_TO_BACKGROUND)
 * from start-of-today (00:00:00) to current time for zero midnight-reset drift.
 */
@Singleton
class UsageStatsReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Checks if the PACKAGE_USAGE_STATS permission has been granted by the user.
     */
    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Computes the total foreground duration (in milliseconds) of [packageName]
     * since midnight (00:00) of the current calendar day.
     */
    @Suppress("DEPRECATION")
    fun getTodayForegroundMillis(packageName: String): Long {
        if (!hasUsageAccessPermission() || usageStatsManager == null) {
            return 0L
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()

        try {
            // Fast-path: queryAndAggregateUsageStats computes total foreground time in C++ inside system_server (1-2ms)
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfToday, now)
            val stats = aggregated[packageName]
            if (stats != null && stats.totalTimeInForeground > 0) {
                return stats.totalTimeInForeground
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryAndAggregateUsageStats failed, falling back to event scan", e)
        }

        try {
            val events = usageStatsManager.queryEvents(startOfToday, now)
            val event = UsageEvents.Event()

            var totalForegroundMs = 0L
            var lastForegroundStart = 0L
            var isCurrentlyInForeground = false

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            lastForegroundStart = event.timeStamp
                            isCurrentlyInForeground = true
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (isCurrentlyInForeground && lastForegroundStart > 0) {
                                totalForegroundMs += (event.timeStamp - lastForegroundStart).coerceAtLeast(0L)
                                isCurrentlyInForeground = false
                            }
                        }
                    }
                }
            }

            // If the app is currently in foreground right now
            if (isCurrentlyInForeground && lastForegroundStart > 0) {
                totalForegroundMs += (now - lastForegroundStart).coerceAtLeast(0L)
            }

            return totalForegroundMs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query usage events for $packageName", e)
            return 0L
        }
    }

    /**
     * Returns total foreground duration in whole minutes for today.
     */
    fun getTodayForegroundMinutes(packageName: String): Int {
        return (getTodayForegroundMillis(packageName) / 60_000L).toInt()
    }

    /**
     * Batch queries all app usage events since midnight in a single O(N) pass.
     * Reduces Binder IPC transactions from N to 1 when displaying app usage lists.
     */
    @Suppress("DEPRECATION")
    fun getTodayForegroundMinutesBatch(): Map<String, Int> {
        if (!hasUsageAccessPermission() || usageStatsManager == null) {
            return emptyMap()
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()

        try {
            val events = usageStatsManager.queryEvents(startOfToday, now)
            val event = UsageEvents.Event()

            val totals = HashMap<String, Long>()
            val starts = HashMap<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        starts[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = starts.remove(pkg)
                        if (start != null && start > 0) {
                            val duration = (event.timeStamp - start).coerceAtLeast(0L)
                            totals[pkg] = (totals[pkg] ?: 0L) + duration
                        }
                    }
                }
            }

            // Handle apps currently in foreground
            for ((pkg, start) in starts) {
                if (start > 0) {
                    val duration = (now - start).coerceAtLeast(0L)
                    totals[pkg] = (totals[pkg] ?: 0L) + duration
                }
            }

            return totals.mapValues { (it.value / 60_000L).toInt() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch query usage events", e)
            return emptyMap()
        }
    }

    /**
     * Fast batch query of all today's foreground durations in milliseconds.
     * Uses queryAndAggregateUsageStats which takes 1-2ms for all apps.
     */
    fun getAllTodayForegroundMillis(): Map<String, Long> {
        if (!hasUsageAccessPermission() || usageStatsManager == null) {
            return emptyMap()
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()

        return try {
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfToday, now)
            val result = HashMap<String, Long>(aggregated.size)
            for ((k, v) in aggregated) {
                if (v.totalTimeInForeground > 0) {
                    result[k.lowercase()] = v.totalTimeInForeground
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getAllTodayForegroundMillis failed", e)
            emptyMap()
        }
    }
}
