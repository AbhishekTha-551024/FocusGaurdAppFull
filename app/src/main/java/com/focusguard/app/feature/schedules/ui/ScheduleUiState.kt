package com.focusguard.app.feature.schedules.ui

import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo

data class ScheduleUiState(
    val schedules: List<ScheduleEntity> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilitySchedule: PendingAccountabilitySchedule? = null
)
