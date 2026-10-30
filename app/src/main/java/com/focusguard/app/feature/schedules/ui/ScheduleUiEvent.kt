package com.focusguard.app.feature.schedules.ui

import android.content.Context
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ScheduleEntity

sealed interface ScheduleUiEvent {
    data object OpenAddDialog : ScheduleUiEvent
    data object CloseAddDialog : ScheduleUiEvent
    data class AddSchedule(
        val title: String,
        val packageName: String?,
        val appName: String?,
        val daysOfWeekMask: Int,
        val startMinute: Int,
        val endMinute: Int
    ) : ScheduleUiEvent
    data class DeleteSchedule(val id: Long, val title: String) : ScheduleUiEvent
    data class ToggleSchedule(val schedule: ScheduleEntity, val isEnabled: Boolean) : ScheduleUiEvent
    data class OnActivateChallenge(val type: ChallengeType, val sentence: String) : ScheduleUiEvent
    data object OnDismissAccountability : ScheduleUiEvent
    data class OnChallengeSuccess(val context: Context) : ScheduleUiEvent
    data object OnCancelChallenge : ScheduleUiEvent
    data class CancelPendingChange(val id: Long) : ScheduleUiEvent
}
