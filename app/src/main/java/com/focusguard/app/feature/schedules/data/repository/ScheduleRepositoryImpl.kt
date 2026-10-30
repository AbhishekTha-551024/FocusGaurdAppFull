package com.focusguard.app.feature.schedules.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val focusGuardRepository: FocusGuardRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScheduleRepository {

    override fun getAllSchedules(): Flow<List<ScheduleEntity>> =
        focusGuardRepository.allSchedules

    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getPendingChanges(): Flow<List<PendingChangeEntity>> =
        focusGuardRepository.pendingChanges

    override suspend fun saveSchedule(schedule: ScheduleEntity): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                focusGuardRepository.saveSchedule(schedule)
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
                focusGuardRepository.deleteSchedule(id)
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
                focusGuardRepository.updateScheduleEnabled(id, isEnabled)
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
                focusGuardRepository.cancelPendingChange(id)
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
            focusGuardRepository.setChallengeType(type)
            focusGuardRepository.setCommitmentSentence(sentence)
            focusGuardRepository.setChallengeActive(true)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-044",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge for schedule"
                )
            )
        }
    }
}
