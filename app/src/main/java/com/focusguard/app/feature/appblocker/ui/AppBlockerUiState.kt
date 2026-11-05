package com.focusguard.app.feature.appblocker.ui

import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo

data class AppBlockerUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val blockedApps: List<AppInfo> = emptyList(),
    val pendingReviewApps: List<AppInfo> = emptyList(),
    val unblockedApps: List<AppInfo> = emptyList(),
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilityApp: AppInfo? = null
)
