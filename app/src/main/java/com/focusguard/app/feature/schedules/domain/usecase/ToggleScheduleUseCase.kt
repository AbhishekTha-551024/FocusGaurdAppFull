package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import javax.inject.Inject

class ToggleScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(id: Long, isEnabled: Boolean): AppResult<Unit> =
        repository.updateScheduleEnabled(id, isEnabled)
}
