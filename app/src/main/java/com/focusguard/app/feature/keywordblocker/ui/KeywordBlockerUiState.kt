package com.focusguard.app.feature.keywordblocker.ui

import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.core.model.ActiveChallengeState

/**
 * =====================================================================
 * KeywordBlockerUiState.kt - UI State (MVI / StateFlow)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh data class Keyword Blocker screen ka **Single Source of Truth** hai.
 * Compose UI ko draw karne ke liye jitna bhi state data chahiye (loading, list, input text,
 * errors, active challenges, permissions), sab kuch is ek immutable state object ke andar encapsulate rehta hai.
 *
 * ## 2. Key Properties Breakdown:
 * - `isLoading`: Screen par loading spinner dikhana hai ya content.
 * - `keywordInput`: Add website dialog ke text field ki live value.
 * - `searchQuery`: Search bar me user jo keyword/domain filter kar raha hai.
 * - `errorMessage`: Validation error ya duplicate warning message (e.g. "Already blocked").
 * - `blockedKeywords`: User dwara block ki gayi websites aur keywords ki active list.
 * - `pendingChanges`: Time-delayed unblock requests (e.g. 24-hour accountability countdown).
 * - `activeChallenge`: User jab delete karne ki koshish karta hai tab typing/partner dialog ka state.
 * - `isChallengeActive`: Kya app me Accountability Challenge enabled hai.
 * - `challengeType`: Active challenge category (TYPING sentence ya PARTNER approval).
 * - `commitmentSentence`: Wo moral sentence jo user ko type karna padta hai unlock karne ke liye.
 * - `timeDelayHours`: Friction delay in hours (default 24h).
 * - `pendingAccountabilityKeyword`: Pehla keyword add karte waqt challenge setup karne ke liye temporary holder.
 *
 * ## 3. Architecture Benefit (Senior Dev Note):
 * Unidirectional Data Flow (UDF): Compose UI sirf is state ko read karti hai. State me koi bhi
 * direct mutation nahi ho sakti; sirf ViewModel nayee state copy emit kar sakta hai. Isse UI
 * tearing, glitching, aur race conditions completely prevent hoti hain.
 * =====================================================================
 */
data class KeywordBlockerUiState(
    val isLoading: Boolean = false,
    val keywordInput: String = "",
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val blockedKeywords: List<BlockedKeyword> = emptyList(),
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilityKeyword: BlockedKeyword? = null
)
