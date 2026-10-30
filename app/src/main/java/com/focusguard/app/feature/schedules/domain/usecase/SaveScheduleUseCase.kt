package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import javax.inject.Inject

class SaveScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(schedule: ScheduleEntity): AppResult<Unit> =
        repository.saveSchedule(schedule)
}
