package com.focusguard.app.feature.mindful.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import javax.inject.Inject

class UpdateMindfulAllowanceUseCase @Inject constructor(
    private val repository: MindfulAppsRepository
) {
    suspend operator fun invoke(packageName: String, minutes: Int): AppResult<Unit> =
        repository.updateAllowanceMinutes(packageName, minutes)
}
