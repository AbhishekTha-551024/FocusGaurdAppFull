package com.focusguard.app.feature.mindful.ui

import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.MindfulIntentLogEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo

data class MindfulAppsUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val mindfulApps: List<AppInfo> = emptyList(),
    val availableApps: List<AppInfo> = emptyList(),
    val appAllowanceMap: Map<String, Int> = emptyMap(),
    val walkedAwayCount: Int = 0,
    val consciousOpensCount: Int = 0,
    val recentLogs: List<MindfulIntentLogEntity> = emptyList(),
    val showJournalDialog: Boolean = false,
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val pendingAccountabilityApp: AppInfo? = null,
    val pendingConfirmAddApp: AppInfo? = null
)
