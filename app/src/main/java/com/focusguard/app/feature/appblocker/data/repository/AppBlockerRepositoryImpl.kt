package com.focusguard.app.feature.appblocker.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.dao.BlockedAppDao
import com.focusguard.app.core.database.dao.PendingChangeDao
import com.focusguard.app.core.database.dao.SecurityEventDao
import com.focusguard.app.core.database.entity.BlockedAppEntity
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.SecurityEvent
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppBlockerRepo"

@Singleton
class AppBlockerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedAppDao: BlockedAppDao,
    private val pendingChangeDao: PendingChangeDao,
    private val eventDao: SecurityEventDao,
    private val prefs: FocusGuardPreferences,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AppBlockerRepository {

    // ── Single Owner of In-Memory App Blocking Caches ──
    private val cachedBlockedPackages = ConcurrentHashMap.newKeySet<String>()
    private val cachedAutoBlockNewApps = AtomicBoolean(false)
    private val cachedAutoBlockEnabledTimestamp = AtomicLong(0L)
    private val cachedReviewedPackages = ConcurrentHashMap.newKeySet<String>()

    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        // 1. Synchronize blocked apps set for 0ms lookup & mirror to DE cache
        repoScope.launch {
            blockedAppDao.watchBlockedApps().collect { list ->
                cachedBlockedPackages.clear()
                cachedBlockedPackages.addAll(list.map { it.packageName.lowercase() })
                prefs.updateSyncCounts(apps = list.size)
                Log.d(TAG, "Synchronized blocked apps cache: ${cachedBlockedPackages.size} packages")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val deContext = context.createDeviceProtectedStorageContext()
                        deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                            .edit()
                            .putStringSet("blocked_packages", list.map { it.packageName.lowercase() }.toSet())
                            .apply()
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to cache blocked packages in DE: ${e.message}")
                    }
                }
            }
        }

        // 2. Synchronize auto-block new apps toggle
        repoScope.launch {
            prefs.isAutoBlockNewAppsEnabled.collect { enabled ->
                cachedAutoBlockNewApps.set(enabled)
            }
        }

        // 3. Synchronize auto-block enabled timestamp
        repoScope.launch {
            prefs.autoBlockEnabledTimestamp.collect { ts ->
                cachedAutoBlockEnabledTimestamp.set(ts)
            }
        }

        // 4. Synchronize reviewed packages
        repoScope.launch {
            prefs.reviewedPackages.collect { pkgs ->
                cachedReviewedPackages.clear()
                cachedReviewedPackages.addAll(pkgs.map { it.lowercase() })
            }
        }
    }

    // ── Flows ──
    val blockedApps: Flow<List<BlockedAppEntity>> = blockedAppDao.watchBlockedApps()
    val blockedAppsCount: Flow<Int> = blockedAppDao.count()
    val isAutoBlockNewAppsEnabled: Flow<Boolean> = prefs.isAutoBlockNewAppsEnabled

    val todayImpulsesBlockedCount: Flow<Int> = channelFlow {
        while (isActive) {
            val startOfDay = getStartOfDayMillis()
            val nextMidnight = startOfDay + 24L * 60L * 60L * 1000L
            val remainingMs = (nextMidnight - System.currentTimeMillis()).coerceAtLeast(60_000L)
            try {
                withTimeoutOrNull(remainingMs) {
                    eventDao.observeTodayBlockCount(startOfDay).collect { send(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "todayImpulsesBlockedCount collection interrupted: ${e.message}")
            }
            delay(1_500L)
        }
    }

    // ── Fast In-Memory Cache Reads (0ms Hot Path for Accessibility) ──
    fun isFastBlockedApp(packageName: String): Boolean = cachedBlockedPackages.contains(packageName.lowercase())
    fun getFastBlockedPackages(): Set<String> = cachedBlockedPackages
    fun getFastBlockedAppsCount(): Int = cachedBlockedPackages.size
    fun isFastAppBlocked(packageName: String): Boolean = cachedBlockedPackages.contains(packageName.lowercase())
    fun isFastAutoBlockNewAppsEnabled(): Boolean = cachedAutoBlockNewApps.get()
    fun getFastAutoBlockEnabledTimestamp(): Long = cachedAutoBlockEnabledTimestamp.get()
    fun isFastAppReviewed(packageName: String): Boolean = cachedReviewedPackages.contains(packageName.lowercase())

    private fun getStartOfDayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private suspend fun logEvent(type: EventType, detail: String = "") {
        try {
            eventDao.insert(SecurityEvent(type = type, detail = detail, timestamp = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: $type", e)
        }
    }

    // ── Direct Operations (used by FocusGuardRepository facade & internal) ──
    suspend fun blockAppDirect(
        packageName: String,
        appName: String,
        isAutoBlockedUnsupportedBrowser: Boolean = false,
        isAutoBlockedNewInstall: Boolean = false
    ) = withContext(ioDispatcher) {
        val clean = packageName.trim().lowercase()
        val entity = BlockedAppEntity(
            packageName = clean,
            appName = appName,
            isAutoBlockedUnsupportedBrowser = isAutoBlockedUnsupportedBrowser,
            isAutoBlockedNewInstall = isAutoBlockedNewInstall
        )
        blockedAppDao.insert(entity)
        cachedBlockedPackages.add(clean)
        logEvent(EventType.APP_BLOCKED, "Package: $packageName ($appName)")
    }

    suspend fun unblockAppDirect(packageName: String) = withContext(ioDispatcher) {
        val clean = packageName.trim().lowercase()
        blockedAppDao.deleteByPackageName(clean)
        blockedAppDao.deleteByPackageName(packageName)
        cachedBlockedPackages.remove(clean)
        cachedBlockedPackages.remove(packageName)
        markAppReviewed(clean)
        logEvent(EventType.APP_UNBLOCKED, "Package: $packageName")
    }

    suspend fun unblockAllAutoBlockedNewApps() = withContext(ioDispatcher) {
        val apps = blockedAppDao.getAutoBlockedNewInstallApps()
        for (app in apps) {
            val clean = app.packageName.lowercase()
            cachedBlockedPackages.remove(clean)
            markAppReviewed(clean)
            logEvent(EventType.APP_UNBLOCKED, "Auto-unblocked package on setting disabled: ${app.packageName}")
        }
        blockedAppDao.deleteAutoBlockedNewInstallApps()
    }

    suspend fun markAppReviewed(packageName: String) = withContext(ioDispatcher) {
        val clean = packageName.lowercase()
        cachedReviewedPackages.add(clean)
        prefs.markAppReviewed(clean)
    }

    suspend fun clearAppReviewed(packageName: String) = withContext(ioDispatcher) {
        val clean = packageName.lowercase()
        cachedReviewedPackages.remove(clean)
        prefs.clearAppReviewed(clean)
    }

    suspend fun isAppBlocked(packageName: String): Boolean = withContext(ioDispatcher) {
        cachedBlockedPackages.contains(packageName.lowercase())
    }

    suspend fun setAutoBlockNewAppsEnabled(enabled: Boolean) = withContext(ioDispatcher) {
        cachedAutoBlockNewApps.set(enabled)
        prefs.setAutoBlockNewApps(enabled)
    }

    suspend fun isAutoBlockNewAppsEnabled(): Boolean = prefs.isAutoBlockNewAppsEnabled.first()

    // ── Domain Interface Implementation (AppBlockerRepository) ──
    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getPendingAppChanges(): Flow<List<PendingChangeEntity>> =
        pendingChangeDao.watchPendingChanges().map { list ->
            list.filter { it.changeType == ChangeType.UNBLOCK_APP }
        }

    override suspend fun blockApp(packageName: String, appName: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                blockAppDirect(packageName = packageName, appName = appName)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-010",
                        errorMessage = e.localizedMessage ?: "Failed to block app $appName",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun unblockApp(packageName: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                unblockAppDirect(packageName)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-011",
                        errorMessage = e.localizedMessage ?: "Failed to unblock app $packageName",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun activateChallengeForApp(
        packageName: String,
        appName: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            prefs.setChallengeType(type)
            prefs.setCommitmentSentence(sentence)
            prefs.setChallengeActive(true)
            blockAppDirect(packageName = packageName, appName = appName)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-012",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge and block $appName"
                )
            )
        }
    }

    override suspend fun cancelPendingChange(id: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                pendingChangeDao.deleteById(id)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-012",
                        errorMessage = e.localizedMessage ?: "Failed to cancel change $id",
                        throwable = e
                    )
                )
            }
        }
}
