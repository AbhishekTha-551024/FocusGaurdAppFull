package com.focusguard.app.feature.appblocker.domain.repository

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface AppBlockerRepository {
    fun getInstalledApps(): Flow<List<AppInfo>>
    fun getPendingAppChanges(): Flow<List<PendingChangeEntity>>
    suspend fun blockApp(packageName: String, appName: String): AppResult<Unit>
    suspend fun unblockApp(packageName: String): AppResult<Unit>
    suspend fun activateChallengeForApp(
        packageName: String,
        appName: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit>
    suspend fun cancelPendingChange(id: Long): AppResult<Unit>
}
