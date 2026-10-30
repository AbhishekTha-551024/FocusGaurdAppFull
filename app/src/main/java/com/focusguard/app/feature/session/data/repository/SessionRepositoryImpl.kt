package com.focusguard.app.feature.session.data.repository

import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.feature.session.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionBlockManager: SessionBlockManager,
    private val preferences: FocusGuardPreferences,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SessionRepository {

    override val sessionStateFlow: Flow<SessionBlockManager.SessionUiState> =
        sessionBlockManager.sessionStateFlow

    override fun getInstalledApps(): Flow<List<AppInfo>> =
        getInstalledAppsUseCase.execute()

    override fun getSyncSuitcasePackages(): Set<String> =
        preferences.syncSuitcasePackages

    override fun getSyncSessionTotalMinutes(): Int =
        preferences.syncSessionTotalMinutes

    override suspend fun saveSuitcasePackages(packages: Set<String>): AppResult<Unit> =
        withContext(ioDispatcher) {
            try {
                preferences.saveSuitcasePackages(packages)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Failure(
                    AppError.DatabaseError(
                        errorCode = "DB-050",
                        errorMessage = e.localizedMessage ?: "Failed to save suitcase packages",
                        throwable = e
                    )
                )
            }
        }

    override fun isSessionCurrentlyActive(): Boolean =
        sessionBlockManager.isSessionCurrentlyActive()

    override fun isTamperOrSettingsPackage(packageName: String): Boolean =
        sessionBlockManager.isTamperOrSettingsPackage(packageName)

    override fun startSession(
        durationMinutes: Int,
        suitcasePackages: Set<String>
    ): AppResult<Unit> {
        return try {
            sessionBlockManager.startSession(durationMinutes, suitcasePackages)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(
                AppError.SecurityError(
                    errorCode = "SEC-051",
                    errorMessage = e.localizedMessage ?: "Failed to start session",
                    throwable = e
                )
            )
        }
    }
}
