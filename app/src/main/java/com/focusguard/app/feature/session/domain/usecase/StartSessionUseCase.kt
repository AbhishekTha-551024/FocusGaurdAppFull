package com.focusguard.app.feature.session.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.session.domain.repository.SessionRepository
import javax.inject.Inject

class StartSessionUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    operator fun invoke(durationMinutes: Int, suitcasePackages: Set<String>): AppResult<Unit> =
        repository.startSession(durationMinutes, suitcasePackages)
}
