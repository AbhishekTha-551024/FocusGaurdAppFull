package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import javax.inject.Inject

class ActivateScheduleChallengeUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(type: ChallengeType, sentence: String): AppResult<Unit> =
        repository.activateChallengeForSchedule(type, sentence)
}
