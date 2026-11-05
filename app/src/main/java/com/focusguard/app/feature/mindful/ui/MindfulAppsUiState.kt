package com.focusguard.app.feature.mindful.ui

import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo

data class MindfulAppsUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val mindfulApps: List<AppInfo> = emptyList(),
    val availableApps: List<AppInfo> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val pendingAccountabilityApp: AppInfo? = null,
    val pendingConfirmAddApp: AppInfo? = null
)
