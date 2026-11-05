package com.focusguard.app.feature.timelimits.ui

import android.content.Context
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.AppInfo

/**
 * =====================================================================
 * TimeLimitUiEvent.kt - User Intent / UI Events (MVI Pattern)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh sealed interface MVI pattern ke **Intents** ko define karta hai.
 * Time Limits screen par user jitne bhi actions perform kar sakta hai (searching, setting limit,
 * deleting limit, granting permission, typing challenge verify karna, countdown cancel karna),
 * sab ko strongly-typed event classes me represent karta hai.
 *
 * ## 2. Core Events Breakdown:
 * - `OnSearchQueryChange(query)`: App search bar me text typing.
 * - `ToggleTimeLimit(app, defaultMinutes, isEnabled)`: Active limits list me se limit delete ya toggle karna.
 * - `SetTimeLimit(app, newMinutes)`: App picker dialog me specific minutes confirm karna.
 * - `RefreshPermissions`: Android settings se wapas aane par usage permission re-check karna.
 * - `OnActivateChallenge(type, sentence)`: Pehli baar limit lagane par challenge setup confirm karna.
 * - `OnDismissAccountability`: Setup dialog cancel karna.
 * - `OnChallengeSuccess(context)`: Commitment sentence type karne ke baad pending action execute karna.
 * - `OnCancelChallenge`: Unblock challenge popup cancel karna.
 * - `CancelPendingChange(id)`: Time delay cooldown cancel karna.
 * =====================================================================
 */
sealed interface TimeLimitUiEvent {
    data class OnSearchQueryChange(val query: String) : TimeLimitUiEvent
    data class ToggleTimeLimit(val app: AppInfo, val defaultMinutes: Int, val isEnabled: Boolean) : TimeLimitUiEvent
    data class SetTimeLimit(val app: AppInfo, val newMinutes: Int) : TimeLimitUiEvent
    data object RefreshPermissions : TimeLimitUiEvent
    data class OnActivateChallenge(val type: ChallengeType, val sentence: String) : TimeLimitUiEvent
    data object OnDismissAccountability : TimeLimitUiEvent
    data class OnChallengeSuccess(val context: Context) : TimeLimitUiEvent
    data object OnCancelChallenge : TimeLimitUiEvent
    data class CancelPendingChange(val id: Long) : TimeLimitUiEvent
}
