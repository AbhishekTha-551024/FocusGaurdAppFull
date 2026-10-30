package com.focusguard.app.feature.appblocker.domain.usecase

import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBlockedAppsUseCase @Inject constructor(
    private val repository: AppBlockerRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> =
        repository.getInstalledApps()
}
