package com.focusguard.app.feature.mindful.ui

import android.content.Context
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.model.AppInfo

sealed interface MindfulAppsUiEvent {
    data class OnSearchQueryChange(val query: String) : MindfulAppsUiEvent
    data class ToggleMindfulApp(val app: AppInfo, val enable: Boolean) : MindfulAppsUiEvent
    data class ConfirmAddMindfulApp(val app: AppInfo) : MindfulAppsUiEvent
    data object DismissAddMindfulApp : MindfulAppsUiEvent
    data class OnActivateChallenge(val type: ChallengeType, val sentence: String) : MindfulAppsUiEvent
    data object OnDismissAccountability : MindfulAppsUiEvent
    data class OnChallengeSuccess(val context: Context) : MindfulAppsUiEvent
    data object OnCancelChallenge : MindfulAppsUiEvent
    data class UpdateDefaultAllowance(val packageName: String, val minutes: Int) : MindfulAppsUiEvent
    data object OpenJournalDialog : MindfulAppsUiEvent
    data object DismissJournalDialog : MindfulAppsUiEvent
}
