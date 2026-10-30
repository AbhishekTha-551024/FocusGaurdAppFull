package com.focusguard.app.feature.mindful.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import javax.inject.Inject

class ToggleMindfulAppUseCase @Inject constructor(
    private val repository: MindfulAppsRepository
) {
    suspend fun add(app: AppInfo, defaultAllowanceMinutes: Int = 5): AppResult<Unit> =
        repository.addMindfulApp(app.packageName, app.appName, defaultAllowanceMinutes)

    suspend fun remove(packageName: String): AppResult<Unit> =
        repository.removeMindfulApp(packageName)
}
