package com.focusguard.app.data.repository

import android.content.Context
import android.util.Log
import com.focusguard.app.core.database.dao.MindfulDao
import com.focusguard.app.core.database.entity.MindfulAppEntity
import com.focusguard.app.core.database.entity.MindfulIntentLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MindfulRepo"

@Singleton
class MindfulRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mindfulDao: MindfulDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Zero-Latency RAM Caches (<0.01ms Fast Path) ─────────────────────────
    // Thread-safe set of package names configured for Mindful Pause
    val cachedMindfulPackages = CopyOnWriteArraySet<String>()

    // Thread-safe map of active allowance sessions: packageName -> expiryTimestampMs
    private val activeAllowanceTokens = ConcurrentHashMap<String, Long>()

    // Thread-safe map tracking return sessions and frequency: packageName -> MindfulSessionInfo
    private val appSessionStats = ConcurrentHashMap<String, MindfulSessionInfo>()

    data class MindfulSessionInfo(
        val lastClosedTimestamp: Long = 0L,
        val todayOpenCount: Int = 0,
        val lastResetDayTimestamp: Long = 0L
    )

    val allMindfulApps: Flow<List<MindfulAppEntity>> = mindfulDao.getAllMindfulApps()
    val recentIntentLogs: Flow<List<MindfulIntentLogEntity>> = mindfulDao.getRecentLogs(50)

    init {
        // Continuously synchronize RAM cache from Room Database in real-time
        scope.launch {
            mindfulDao.getAllMindfulApps()
                .distinctUntilChanged()
                .collect { apps ->
                    val pkgs = apps.map { it.packageName }
                    cachedMindfulPackages.clear()
                    cachedMindfulPackages.addAll(pkgs)
                    Log.d(TAG, "Synchronized Mindful RAM cache: ${cachedMindfulPackages.size} apps.")
                }
        }
    }

    // ── Zero-Latency Fast Path Methods (<0.01ms for Accessibility Thread) ────

    fun isPackageMindful(packageName: String): Boolean {
        return cachedMindfulPackages.contains(packageName)
    }

    /**
     * Checks if the package has an active, unexpired allowance token.
     * Automatically purges expired tokens in O(1).
     */
    fun isAllowanceActive(packageName: String): Boolean {
        val expiry = activeAllowanceTokens[packageName] ?: return false
        val now = System.currentTimeMillis()
        return if (now < expiry) {
            true
        } else {
            activeAllowanceTokens.remove(packageName)
            false
        }
    }

    fun getRemainingAllowanceSeconds(packageName: String): Long {
        val expiry = activeAllowanceTokens[packageName] ?: return 0L
        val remaining = (expiry - System.currentTimeMillis()) / 1000L
        return if (remaining > 0L) remaining else 0L
    }

    // ── Token Management & Journal Logging ──────────────────────────────────

    fun grantAllowance(
        packageName: String,
        appName: String,
        durationMinutes: Int,
        intentText: String,
        category: String = "CUSTOM"
    ) {
        val expiry = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        activeAllowanceTokens[packageName] = expiry
        Log.i(TAG, "Granted $durationMinutes min allowance for $packageName (expires at $expiry)")

        scope.launch {
            mindfulDao.insertIntentLog(
                MindfulIntentLogEntity(
                    packageName = packageName,
                    appName = appName,
                    intentText = intentText.trim(),
                    intentCategory = category,
                    allowanceMinutes = durationMinutes,
                    timestamp = System.currentTimeMillis(),
                    wasWalkedAway = false
                )
            )
        }
    }

    fun recordWalkedAway(packageName: String, appName: String) {
        // Ensure no active token exists
        activeAllowanceTokens.remove(packageName)
        Log.i(TAG, "Recorded WALLED AWAY victory for $packageName ($appName)!")

        scope.launch {
            mindfulDao.insertIntentLog(
                MindfulIntentLogEntity(
                    packageName = packageName,
                    appName = appName,
                    intentText = "Walked away (Mindful Victory)",
                    intentCategory = "WALK_AWAY",
                    allowanceMinutes = 0,
                    timestamp = System.currentTimeMillis(),
                    wasWalkedAway = true
                )
            )
        }
    }

    fun revokeAllowance(packageName: String) {
        activeAllowanceTokens.remove(packageName)
        Log.i(TAG, "Revoked allowance for $packageName")
    }

    // ── Returning Session & Repetition Tracking ──────────────────────────────

    fun getSessionInfo(packageName: String): MindfulSessionInfo {
        val todayStart = getTodayStartTimestamp()
        val current = appSessionStats[packageName] ?: MindfulSessionInfo(lastResetDayTimestamp = todayStart)
        return if (current.lastResetDayTimestamp < todayStart) {
            // New day, reset todayOpenCount
            val reset = MindfulSessionInfo(
                lastClosedTimestamp = current.lastClosedTimestamp,
                todayOpenCount = 0,
                lastResetDayTimestamp = todayStart
            )
            appSessionStats[packageName] = reset
            reset
        } else {
            current
        }
    }

    fun recordSessionStarted(packageName: String) {
        val current = getSessionInfo(packageName)
        val updated = current.copy(
            todayOpenCount = current.todayOpenCount + 1
        )
        appSessionStats[packageName] = updated
        Log.d(TAG, "Mindful session started for $packageName: Open count today = ${updated.todayOpenCount}")
    }

    fun recordSessionEnded(packageName: String) {
        val current = getSessionInfo(packageName)
        val updated = current.copy(
            lastClosedTimestamp = System.currentTimeMillis()
        )
        appSessionStats[packageName] = updated
        Log.d(TAG, "Mindful session ended for $packageName at ${updated.lastClosedTimestamp}")
    }

    // ── CRUD Operations ─────────────────────────────────────────────────────

    suspend fun addMindfulApp(packageName: String, appName: String, defaultAllowanceMinutes: Int = 5) {
        cachedMindfulPackages.add(packageName)
        mindfulDao.insertMindfulApp(
            MindfulAppEntity(
                packageName = packageName,
                appName = appName,
                defaultAllowanceMinutes = defaultAllowanceMinutes
            )
        )
    }

    suspend fun removeMindfulApp(packageName: String) {
        cachedMindfulPackages.remove(packageName)
        activeAllowanceTokens.remove(packageName)
        mindfulDao.deleteMindfulApp(packageName)
    }

    suspend fun updateAllowanceMinutes(packageName: String, minutes: Int) {
        mindfulDao.updateAllowanceMinutes(packageName, minutes)
    }

    // ── Victory Analytics ───────────────────────────────────────────────────

    fun getTodayStartTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getTodayWalkedAwayCount(): Flow<Int> {
        return mindfulDao.getTodayWalkedAwayCount(getTodayStartTimestamp())
    }

    fun getTodayConsciousOpensCount(): Flow<Int> {
        return mindfulDao.getTodayConsciousOpensCount(getTodayStartTimestamp())
    }
}
