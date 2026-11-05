package com.focusguard.app.feature.schedules.domain.repository

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<ScheduleEntity>>
    fun getInstalledApps(): Flow<List<AppInfo>>
    fun getPendingChanges(): Flow<List<PendingChangeEntity>>
    suspend fun saveSchedule(schedule: ScheduleEntity): AppResult<Unit>
    suspend fun deleteSchedule(id: Long): AppResult<Unit>
    suspend fun updateScheduleEnabled(id: Long, isEnabled: Boolean): AppResult<Unit>
    suspend fun cancelPendingChange(id: Long): AppResult<Unit>
    suspend fun activateChallengeForSchedule(type: ChallengeType, sentence: String): AppResult<Unit>
}
