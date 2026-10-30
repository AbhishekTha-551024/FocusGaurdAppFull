package com.focusguard.app.feature.session.domain.repository

import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val sessionStateFlow: Flow<SessionBlockManager.SessionUiState>
    fun getInstalledApps(): Flow<List<AppInfo>>
    fun getSyncSuitcasePackages(): Set<String>
    fun getSyncSessionTotalMinutes(): Int
    suspend fun saveSuitcasePackages(packages: Set<String>): AppResult<Unit>
    fun isSessionCurrentlyActive(): Boolean
    fun isTamperOrSettingsPackage(packageName: String): Boolean
    fun startSession(durationMinutes: Int, suitcasePackages: Set<String>): AppResult<Unit>
}
