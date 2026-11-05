package com.focusguard.app.feature.appblocker.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import javax.inject.Inject

class ToggleAppBlockUseCase @Inject constructor(
    private val repository: AppBlockerRepository
) {
    suspend fun block(app: AppInfo): AppResult<Unit> =
        repository.blockApp(packageName = app.packageName, appName = app.appName)

    suspend fun unblock(packageName: String): AppResult<Unit> =
        repository.unblockApp(packageName = packageName)
}
