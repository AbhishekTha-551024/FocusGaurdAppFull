package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSchedulesUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    fun getAllSchedules(): Flow<List<ScheduleEntity>> = repository.getAllSchedules()

    fun getInstalledApps(): Flow<List<AppInfo>> = repository.getInstalledApps()

    fun getPendingChanges(): Flow<List<PendingChangeEntity>> = repository.getPendingChanges()
}
