package com.focusguard.app.feature.session.domain.usecase

import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionStateUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    val sessionState: Flow<SessionBlockManager.SessionUiState> = repository.sessionStateFlow

    fun getInstalledApps(): Flow<List<AppInfo>> = repository.getInstalledApps()

    fun getSyncSuitcasePackages(): Set<String> = repository.getSyncSuitcasePackages()

    fun getSyncSessionTotalMinutes(): Int = repository.getSyncSessionTotalMinutes()

    fun isSessionCurrentlyActive(): Boolean = repository.isSessionCurrentlyActive()

    fun isTamperOrSettingsPackage(packageName: String): Boolean =
        repository.isTamperOrSettingsPackage(packageName)
}
