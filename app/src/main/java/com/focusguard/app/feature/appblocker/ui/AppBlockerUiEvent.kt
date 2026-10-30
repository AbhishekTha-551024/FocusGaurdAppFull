package com.focusguard.app.feature.appblocker.ui

import android.content.Context
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.model.AppInfo

sealed interface AppBlockerUiEvent {
    data class OnSearchQueryChange(val query: String) : AppBlockerUiEvent
    data class ToggleAppBlock(val app: AppInfo, val shouldBlock: Boolean) : AppBlockerUiEvent
    data class OnActivateChallenge(val type: ChallengeType, val sentence: String) : AppBlockerUiEvent
    data object OnDismissAccountability : AppBlockerUiEvent
    data class OnChallengeSuccess(val context: Context) : AppBlockerUiEvent
    data object OnCancelChallenge : AppBlockerUiEvent
    data class OnCancelPendingChange(val id: Long) : AppBlockerUiEvent
}
