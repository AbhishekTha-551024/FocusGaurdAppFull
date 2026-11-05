package com.focusguard.app.feature.timelimits.ui

import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.feature.settings.data.repository.ChallengeSettings
import com.focusguard.app.feature.timelimits.domain.usecase.GetTimeLimitsUseCase
import com.focusguard.app.usage.UsageStatsReader
import javax.inject.Inject

class TimeLimitUiStateFactory @Inject constructor(
    private val getTimeLimitsUseCase: GetTimeLimitsUseCase,
    private val usageStatsReader: UsageStatsReader
) {
    fun create(
        installedApps: List<AppInfo>,
        limits: List<AppTimeLimitEntity>,
        query: String,
        isLoading: Boolean,
        activeChallenge: ActiveChallengeState?,
        pendingAccountabilityLimit: PendingAccountabilityLimit?,
        pendingChanges: List<PendingChangeEntity>,
        settings: ChallengeSettings,
        errorMessage: String?
    ): TimeLimitUiState {
        val hasPermission = usageStatsReader.hasUsageAccessPermission()
        val limitMap = limits.associateBy { it.packageName }
        val usageMap = if (hasPermission) {
            usageStatsReader.getTodayForegroundMinutesBatch()
        } else {
            emptyMap()
        }

        val models = installedApps.map { app ->
            val limit = limitMap[app.packageName]
            val batchUsed = usageMap[app.packageName] ?: 0
            val liveMinutes = if (limit != null) {
                getTimeLimitsUseCase.getTodayForegroundMinutes(app.packageName)
            } else {
                0
            }
            AppTimeLimitUiModel(
                appInfo = app,
                dailyLimitMinutes = limit?.dailyLimitMinutes ?: 30,
                usedMinutesToday = maxOf(batchUsed, liveMinutes),
                isLimitConfigured = limit != null,
                isEnabled = limit?.isEnabled ?: true
            )
        }.sortedWith(
            compareByDescending<AppTimeLimitUiModel> { it.isLimitConfigured }
                .thenByDescending { it.usedMinutesToday }
                .thenBy { it.appInfo.appName.lowercase() }
        )

        val filtered = if (query.isBlank()) {
            models
        } else {
            models.filter {
                it.appInfo.appName.contains(query, ignoreCase = true) ||
                    it.appInfo.packageName.contains(query, ignoreCase = true)
            }
        }

        return TimeLimitUiState(
            apps = models,
            filteredApps = filtered,
            searchQuery = query,
            hasUsagePermission = hasPermission,
            isLoading = isLoading,
            pendingChanges = pendingChanges.filter {
                it.changeType == ChangeType.DISABLE_LIMIT || it.changeType == ChangeType.LOWER_LIMIT
            },
            activeChallenge = activeChallenge,
            isChallengeActive = settings.isChallengeActive,
            challengeType = settings.type,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            pendingAccountabilityLimit = pendingAccountabilityLimit,
            errorMessage = errorMessage
        )
    }
}
