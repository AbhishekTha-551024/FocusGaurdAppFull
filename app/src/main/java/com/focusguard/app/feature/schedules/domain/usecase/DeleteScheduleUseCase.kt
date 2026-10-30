package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import javax.inject.Inject

class DeleteScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> =
        repository.deleteSchedule(id)
}
