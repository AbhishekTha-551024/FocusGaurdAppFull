package com.focusguard.app.feature.session.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.session.domain.repository.SessionRepository
import javax.inject.Inject

class UpdateSuitcaseUseCase @Inject constructor(
    private val repository: SessionRepository
) {
    suspend operator fun invoke(packages: Set<String>): AppResult<Unit> =
        repository.saveSuitcasePackages(packages)
}
