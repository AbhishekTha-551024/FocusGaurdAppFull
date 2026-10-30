package com.focusguard.app.feature.timelimits.domain.usecase

import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * =====================================================================
 * GetTimeLimitsUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case Time Limits UI ko render karne ke liye zaroori saare data streams
 * aur queries ko ek single façade ke roop me provide karta hai.
 *
 * ## 2. Core Streams & Data Access:
 * - `getInstalledApps()`: Device ke installed apps ki list.
 * - `getAllTimeLimits()`: Room database me configured time caps.
 * - `getPendingChanges()`: Delays/cooldowns ke under pending modification requests.
 * - `hasUsagePermission()`: Usage stats access permission status.
 * - `getTodayUsageBatch()`: Sabhi apps ke aaj ke foreground usage minutes.
 * - `getTodayForegroundMinutes(packageName)`: Single package ka live calculated foreground time.
 * =====================================================================
 */
class GetTimeLimitsUseCase @Inject constructor(
    private val repository: TimeLimitRepository
) {
    fun getInstalledApps(): Flow<List<AppInfo>> = repository.getInstalledApps()

    fun getAllTimeLimits(): Flow<List<AppTimeLimitEntity>> = repository.getAllTimeLimits()

    fun getPendingChanges(): Flow<List<PendingChangeEntity>> = repository.getPendingChanges()

    fun hasUsagePermission(): Boolean = repository.hasUsagePermission()

    fun getTodayUsageBatch(): Map<String, Int> = repository.getTodayUsageBatch()

    fun getTodayForegroundMinutes(packageName: String): Int =
        repository.getTodayForegroundMinutes(packageName)
}
