package com.focusguard.app.feature.appblocker.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import javax.inject.Inject

class ActivateAppChallengeUseCase @Inject constructor(
    private val repository: AppBlockerRepository
) {
    suspend operator fun invoke(
        packageName: String,
        appName: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> =
        repository.activateChallengeForApp(packageName, appName, type, sentence)
}
