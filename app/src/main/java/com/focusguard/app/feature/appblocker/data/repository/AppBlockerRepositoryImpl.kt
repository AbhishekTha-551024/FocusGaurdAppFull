package com.focusguard.app.feature.appblocker.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlockerRepositoryImpl @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val focusGuardRepository: FocusGuardRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AppBlockerRepository {

    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getPendingAppChanges(): Flow<List<PendingChangeEntity>> =
        focusGuardRepository.pendingChanges.map { list ->
            list.filter { it.changeType == ChangeType.UNBLOCK_APP }
        }

    override suspend fun blockApp(packageName: String, appName: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                focusGuardRepository.blockApp(packageName = packageName, appName = appName)
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
                focusGuardRepository.unblockApp(packageName)
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
            focusGuardRepository.setChallengeType(type)
            focusGuardRepository.setCommitmentSentence(sentence)
            focusGuardRepository.setChallengeActive(true)
            focusGuardRepository.blockApp(packageName = packageName, appName = appName)
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
                focusGuardRepository.cancelPendingChange(id)
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
