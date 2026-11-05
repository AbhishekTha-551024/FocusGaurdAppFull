package com.focusguard.app.data.repository

import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.database.dao.MindfulDao
import com.focusguard.app.core.database.entity.MindfulAppEntity
import com.focusguard.app.core.startup.CeStorageGate
import com.focusguard.app.feature.mindful.domain.model.MindfulAllowancePolicy
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
    private val mindfulDao: MindfulDao,
    private val ceStorageGate: CeStorageGate
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

    init {
        // Continuously synchronize RAM cache from Room Database in real-time
        scope.launch {
            ceStorageGate.awaitUnlocked()
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
        return if (MindfulAllowancePolicy.isActive(expiry, SystemClock.elapsedRealtime())) {
            true
        } else {
            activeAllowanceTokens.remove(packageName)
            false
        }
    }

    fun getRemainingAllowanceSeconds(packageName: String): Long {
        val expiry = activeAllowanceTokens[packageName] ?: return 0L
        return MindfulAllowancePolicy.remainingSeconds(expiry, SystemClock.elapsedRealtime())
    }

    // ── Allowance token management ────────────────────────────────────────────

    fun grantAllowance(
        packageName: String,
        durationMinutes: Int
    ) {
        val expiry = MindfulAllowancePolicy.expiryAt(SystemClock.elapsedRealtime(), durationMinutes)
        activeAllowanceTokens[packageName] = expiry
        Log.i(TAG, "Granted $durationMinutes min allowance for $packageName")
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

    private fun getTodayStartTimestamp(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ── CRUD Operations ─────────────────────────────────────────────────────

    suspend fun addMindfulApp(packageName: String, appName: String, defaultAllowanceMinutes: Int = 5) {
        mindfulDao.insertMindfulApp(
            MindfulAppEntity(
                packageName = packageName,
                appName = appName,
                defaultAllowanceMinutes = defaultAllowanceMinutes
            )
        )
        cachedMindfulPackages.add(packageName)
    }

    suspend fun removeMindfulApp(packageName: String) {
        mindfulDao.deleteMindfulApp(packageName)
        cachedMindfulPackages.remove(packageName)
        activeAllowanceTokens.remove(packageName)
    }

    suspend fun updateAllowanceMinutes(packageName: String, minutes: Int) {
        mindfulDao.updateAllowanceMinutes(packageName, minutes)
    }

}
