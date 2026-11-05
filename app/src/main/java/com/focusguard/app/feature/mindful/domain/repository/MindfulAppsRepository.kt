package com.focusguard.app.feature.mindful.domain.repository

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.MindfulAppEntity
import com.focusguard.app.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface MindfulAppsRepository {
    fun getMindfulApps(): Flow<List<MindfulAppEntity>>
    fun getInstalledApps(): Flow<List<AppInfo>>
    suspend fun addMindfulApp(packageName: String, appName: String, allowanceMinutes: Int = 5): AppResult<Unit>
    suspend fun removeMindfulApp(packageName: String): AppResult<Unit>
    suspend fun activateChallengeForApp(
        packageName: String,
        appName: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit>
}
