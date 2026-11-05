package com.focusguard.app.feature.mindful.domain.usecase

import com.focusguard.app.core.database.entity.MindfulAppEntity
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMindfulAppsUseCase @Inject constructor(
    private val repository: MindfulAppsRepository
) {
    fun getInstalledApps(): Flow<List<AppInfo>> = repository.getInstalledApps()

    fun getMindfulApps(): Flow<List<MindfulAppEntity>> = repository.getMindfulApps()
}
