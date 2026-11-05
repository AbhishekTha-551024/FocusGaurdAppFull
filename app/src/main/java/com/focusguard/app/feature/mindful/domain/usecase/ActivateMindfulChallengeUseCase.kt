package com.focusguard.app.feature.mindful.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import javax.inject.Inject

class ActivateMindfulChallengeUseCase @Inject constructor(
    private val repository: MindfulAppsRepository
) {
    suspend operator fun invoke(
        packageName: String,
        appName: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> =
        repository.activateChallengeForApp(packageName, appName, type, sentence)
}
