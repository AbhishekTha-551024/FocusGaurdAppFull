package com.focusguard.app.feature.timelimits.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.dao.AppTimeLimitDao
import com.focusguard.app.core.database.dao.PendingChangeDao
import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.startup.CeStorageGate
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import com.focusguard.app.usage.LimitStatus
import com.focusguard.app.usage.TimeLimitEvaluator
import com.focusguard.app.usage.UsageStatsReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeLimitRepositoryImpl @Inject constructor(
    private val appTimeLimitDao: AppTimeLimitDao,
    private val pendingChangeDao: PendingChangeDao,
    private val prefs: FocusGuardPreferences,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val usageStatsReader: UsageStatsReader,
    private val timeLimitEvaluator: TimeLimitEvaluator,
    private val ceStorageGate: CeStorageGate,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TimeLimitRepository {

    // ── Single Owner of In-Memory Time Limit Caches ──
    private val cachedTimeLimits = ConcurrentHashMap<String, AppTimeLimitEntity>()
    private val cachedExceededTimeLimits = ConcurrentHashMap.newKeySet<String>()
    private val cachedExceededDateCode = AtomicInteger(
        Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    )
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        repoScope.launch {
            ceStorageGate.awaitUnlocked()
            try {
                appTimeLimitDao.watchAllTimeLimits().collect { list ->
                    cachedTimeLimits.clear()
                    for (item in list) {
                        if (item.isEnabled) {
                            cachedTimeLimits[item.packageName.lowercase()] = item
                        }
                    }
                    prefs.updateSyncCounts(limits = cachedTimeLimits.size)
                    refreshExceededTimeLimits()
                }
            } catch (_: Exception) {}
        }
    }

    // ── Entity Flows (used by FocusGuardRepository facade) ──
    val activeTimeLimitsCount: Flow<Int> = appTimeLimitDao.countActive()

    // ── Fast In-Memory Cache Reads (0ms for accessibility & UI) ──
    fun getFastTimeLimits(): Map<String, AppTimeLimitEntity> = cachedTimeLimits
    fun hasFastTimeLimit(packageName: String): Boolean = cachedTimeLimits.containsKey(packageName.lowercase())

    private fun getTodayDateCode(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    fun isFastTimeLimitExceeded(packageName: String): Boolean {
        val todayCode = getTodayDateCode()
        if (cachedExceededDateCode.get() != todayCode) {
            if (cachedExceededDateCode.getAndSet(todayCode) != todayCode) {
                cachedExceededTimeLimits.clear()
                refreshExceededTimeLimits()
            }
            return false
        }
        return cachedExceededTimeLimits.contains(packageName.lowercase())
    }

    fun markTimeLimitExceeded(packageName: String) {
        cachedExceededTimeLimits.add(packageName.lowercase())
    }

    fun clearTimeLimitExceeded(packageName: String) {
        cachedExceededTimeLimits.remove(packageName.lowercase())
    }

    fun refreshExceededTimeLimits() {
        for ((pkg, entity) in cachedTimeLimits) {
            if (entity.isEnabled) {
                val status = timeLimitEvaluator.evaluate(pkg, entity)
                if (status is LimitStatus.Exceeded) {
                    cachedExceededTimeLimits.add(pkg)
                } else {
                    cachedExceededTimeLimits.remove(pkg)
                }
            }
        }
    }

    // ── Direct Database Operations ──
    suspend fun setTimeLimitDirect(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean = true
    ) = withContext(ioDispatcher) {
        val entity = AppTimeLimitEntity(
            packageName = packageName,
            appName = appName,
            dailyLimitMinutes = dailyLimitMinutes,
            isEnabled = isEnabled
        )
        appTimeLimitDao.upsert(entity)
        if (isEnabled) {
            cachedTimeLimits[packageName.lowercase()] = entity
        } else {
            cachedTimeLimits.remove(packageName.lowercase())
        }
        refreshExceededTimeLimits()
    }

    suspend fun removeTimeLimitDirect(packageName: String) = withContext(ioDispatcher) {
        appTimeLimitDao.delete(packageName)
        cachedTimeLimits.remove(packageName.lowercase())
        cachedExceededTimeLimits.remove(packageName.lowercase())
    }

    // ── Domain Interface Implementation (TimeLimitRepository) ──
    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getAllTimeLimits(): Flow<List<AppTimeLimitEntity>> =
        appTimeLimitDao.watchAllTimeLimits()

    override fun getPendingChanges(): Flow<List<PendingChangeEntity>> =
        pendingChangeDao.watchPendingChanges().map { list ->
            list.filter { it.changeType == ChangeType.DISABLE_LIMIT || it.changeType == ChangeType.LOWER_LIMIT }
        }

    override fun hasUsagePermission(): Boolean =
        usageStatsReader.hasUsageAccessPermission()

    override fun getTodayUsageBatch(): Map<String, Int> =
        if (hasUsagePermission()) usageStatsReader.getTodayForegroundMinutesBatch() else emptyMap()

    override fun getTodayForegroundMinutes(packageName: String): Int =
        timeLimitEvaluator.getTodayForegroundMinutes(packageName)

    override suspend fun setTimeLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            setTimeLimitDirect(packageName, appName, dailyLimitMinutes, isEnabled)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.DatabaseError(
                    errorCode = "DB-030",
                    errorMessage = e.localizedMessage ?: "Failed to set time limit for $appName",
                    throwable = e
                )
            )
        }
    }

    override suspend fun removeTimeLimit(packageName: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                removeTimeLimitDirect(packageName)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-031",
                        errorMessage = e.localizedMessage ?: "Failed to remove time limit for $packageName",
                        throwable = e
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
                        errorCode = "DB-032",
                        errorMessage = e.localizedMessage ?: "Failed to cancel pending change $id",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun activateChallengeForLimit(
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            prefs.setChallengeType(type)
            prefs.setCommitmentSentence(sentence)
            prefs.setChallengeActive(true)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-033",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge",
                    throwable = e
                )
            )
        }
    }
}
