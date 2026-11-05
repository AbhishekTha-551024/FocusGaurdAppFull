package com.focusguard.app.feature.keywordblocker.ui

import android.content.Context
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword

/**
 * =====================================================================
 * KeywordBlockerUiEvent.kt - User Intent / UI Events (MVI Pattern)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh sealed interface MVI (Model-View-Intent) pattern ke "Intent" ya "Events"
 * ko define karta hai. UI screen par user dwara kiye gaye har interaction (button click,
 * text input, dialog dismiss, challenge completion) ko ek strongly-typed event class me represent karta hai.
 *
 * ## 2. Event Types & Usage:
 * - `OnKeywordInputChange(input)`: User jab dialog me website ya keyword type karta hai.
 * - `OnSearchQueryChange(query)`: User jab blocked list me search/filter karta hai.
 * - `AddKeyword`: "Block" button click par validation aur addition initiate karta hai.
 * - `RemoveKeyword(keyword)`: Trash icon click par unblock ya challenge prompt open karta hai.
 * - `OnActivateChallenge(type, sentence)`: Pehli baar challenge setup confirm karne par.
 * - `OnDismissAccountability`: Challenge setup dialog cancel karne par.
 * - `OnChallengeSuccess(context)`: Commitment sentence correctly type karne ke baad deletion trigger karne ke liye.
 * - `OnCancelChallenge`: Challenge dialog cancel karne par.
 * - `OnCancelPendingChange(id)`: Time delay countdown cancel karne par.
 * - `ClearError`: Error banner ya snackbar dismiss karne par.
 *
 * ## 3. Architecture Benefit (Senior Dev Note):
 * Sealed interface hone ki wajah se ViewModel ke `when (event)` block me exhaustiveness
 * compile-time verify hoti hai. Agar future me koi naya event add hota hai toh compiler
 * warning dega ki handle nahi hua, jisse zero runtime bugs bachte hain.
 * =====================================================================
 */
sealed interface KeywordBlockerUiEvent {
    data class OnKeywordInputChange(val input: String) : KeywordBlockerUiEvent
    data class OnSearchQueryChange(val query: String) : KeywordBlockerUiEvent
    data object AddKeyword : KeywordBlockerUiEvent
    data class RemoveKeyword(val keyword: BlockedKeyword) : KeywordBlockerUiEvent
    data class OnActivateChallenge(val type: ChallengeType, val sentence: String) : KeywordBlockerUiEvent
    data object OnDismissAccountability : KeywordBlockerUiEvent
    data class OnChallengeSuccess(val context: Context) : KeywordBlockerUiEvent
    data object OnCancelChallenge : KeywordBlockerUiEvent
    data class OnCancelPendingChange(val id: Long) : KeywordBlockerUiEvent
    data object ClearError : KeywordBlockerUiEvent
}
