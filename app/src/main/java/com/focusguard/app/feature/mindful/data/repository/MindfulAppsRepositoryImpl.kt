package com.focusguard.app.feature.mindful.data.repository

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.MindfulAppEntity
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.MindfulRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MindfulAppsRepositoryImpl @Inject constructor(
    private val mindfulRepository: MindfulRepository,
    private val prefs: FocusGuardPreferences,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MindfulAppsRepository {

    override fun getMindfulApps(): Flow<List<MindfulAppEntity>> =
        mindfulRepository.allMindfulApps

    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override suspend fun addMindfulApp(
        packageName: String,
        appName: String,
        allowanceMinutes: Int
    ): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            mindfulRepository.addMindfulApp(packageName, appName, allowanceMinutes)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.DatabaseError(
                    errorCode = "DB-020",
                    errorMessage = e.localizedMessage ?: "Failed to add mindful app $appName",
                    throwable = e
                )
            )
        }
    }

    override suspend fun removeMindfulApp(packageName: String): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                mindfulRepository.removeMindfulApp(packageName)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-021",
                        errorMessage = e.localizedMessage ?: "Failed to remove mindful app $packageName",
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
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-023",
                    errorMessage = e.localizedMessage ?: "Failed to activate challenge for $appName"
                )
            )
        }
    }
}
