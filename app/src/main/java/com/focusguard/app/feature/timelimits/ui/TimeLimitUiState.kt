package com.focusguard.app.feature.timelimits.ui

import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.model.ActiveChallengeState

/**
 * =====================================================================
 * TimeLimitUiState.kt - UI State (MVI / StateFlow)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh data class Time Limits screen ka **Single Source of Truth** hai.
 * Compose UI ko render karne ke liye jo bhi information chahiye—installed apps, active limits,
 * usage permissions, search text, loading spinners, aur challenge state—sab is immutable class me encapsulated hai.
 *
 * ## 2. Key Properties Breakdown:
 * - `apps`: Installed apps aur unki configured daily limits ki poori sorted list.
 * - `filteredApps`: Search bar query ke hisab se filtered apps list.
 * - `searchQuery`: App picker dialog me user dwara type kiya gaya search filter.
 * - `hasUsagePermission`: Android Usage Access permission di gayi hai ya nahi.
 * - `isLoading`: Initial cold-start data load ho raha hai ya complete ho gaya.
 * - `pendingChanges`: Time-delay cooldowns ke under pending modification requests.
 * - `activeChallenge`: Deletion ya limit increase karte waqt ka active typing challenge.
 * - `isChallengeActive`: Global accountability protection enabled status.
 * - `challengeType`: Active challenge type (TYPING sentence ya PARTNER approval).
 * - `commitmentSentence`: Wo sentence jo user ko unlock karne ke liye type karna padta hai.
 * - `timeDelayHours`: Friction cooldown delay in hours.
 * - `pendingAccountabilityLimit`: Pehli baar limit lagate waqt challenge configure karne ka payload.
 * =====================================================================
 */
data class TimeLimitUiState(
    val apps: List<AppTimeLimitUiModel> = emptyList(),
    val filteredApps: List<AppTimeLimitUiModel> = emptyList(),
    val searchQuery: String = "",
    val hasUsagePermission: Boolean = false,
    val isLoading: Boolean = false,
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilityLimit: PendingAccountabilityLimit? = null
)
