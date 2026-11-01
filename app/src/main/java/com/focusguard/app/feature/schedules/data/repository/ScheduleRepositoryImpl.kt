package com.focusguard.app.feature.schedules.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.dao.PendingChangeDao
import com.focusguard.app.core.database.dao.ScheduleDao
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val pendingChangeDao: PendingChangeDao,
    private val prefs: FocusGuardPreferences,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScheduleRepository {

    // ── Single Owner of In-Memory Active Schedules Cache ──
    private val cachedActiveSchedules = CopyOnWriteArrayList<ScheduleEntity>()
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        repoScope.launch {
            try {
                scheduleDao.watchEnabledSchedules().collect { list ->
                    cachedActiveSchedules.clear()
                    cachedActiveSchedules.addAll(list)
                    prefs.updateSyncCounts(schedules = list.size)
                }
            } catch (_: Exception) {}
        }
    }

    // ── Raw & Entity Flows (used by FocusGuardRepository facade) ──
    val activeSchedulesCount: Flow<Int> = scheduleDao.countActive()

    // ── Fast In-Memory Cache Reads (0ms for accessibility & UI) ──
    fun getFastActiveSchedules(): List<ScheduleEntity> = cachedActiveSchedules.toList()

    // ── Core Operations ──
    suspend fun saveScheduleDirect(schedule: ScheduleEntity): Long = withContext(ioDispatcher) {
        scheduleDao.insert(schedule)
    }

    suspend fun deleteScheduleDirect(id: Long) = withContext(ioDispatcher) {
        scheduleDao.deleteById(id)
    }

    suspend fun updateScheduleEnabledDirect(id: Long, enabled: Boolean) = withContext(ioDispatcher) {
        scheduleDao.updateEnabled(id, enabled)
    }

    suspend fun clearAllSchedules() = withContext(ioDispatcher) {
        val all = scheduleDao.watchAllSchedules().first()
        all.forEach { scheduleDao.deleteById(it.id) }
        cachedActiveSchedules.clear()
    }

    // ── Domain Interface Implementation (ScheduleRepository) ──
    override fun getAllSchedules(): Flow<List<ScheduleEntity>> = scheduleDao.watchAllSchedules()

    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getPendingChanges(): Flow<List<PendingChangeEntity>> =
        pendingChangeDao.watchPendingChanges()

    override suspend fun saveSchedule(schedule: ScheduleEntity): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                saveScheduleDirect(schedule)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-040",
                        errorMessage = e.localizedMessage ?: "Failed to save schedule ${schedule.title}",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun deleteSchedule(id: Long): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                deleteScheduleDirect(id)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-041",
                        errorMessage = e.localizedMessage ?: "Failed to delete schedule $id",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun updateScheduleEnabled(id: Long, isEnabled: Boolean): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                updateScheduleEnabledDirect(id, isEnabled)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-042",
                        errorMessage = e.localizedMessage ?: "Failed to update schedule status $id",
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
                        errorCode = "DB-043",
                        errorMessage = e.localizedMessage ?: "Failed to cancel pending change $id",
                        throwable = e
                    )
                )
            }
        }

    override suspend fun activateChallengeForSchedule(
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
                    errorCode = "SEC-040",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge for schedules"
                )
            )
        }
    }
}
