package com.focusguard.app.feature.timelimits.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import javax.inject.Inject

/**
 * =====================================================================
 * ActivateTimeLimitChallengeUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case tab call hota hai jab user pehli baar kisi app par daily screen time limit
 * lagata hai aur app me pehle se Accountability Challenge active nahi hota.
 * Yeh user ka chuna hua challenge type (TYPING ya PARTNER) aur commitment sentence
 * save karta hai aur app-wide accountability guard ko activate kar deta hai.
 *
 * ## 2. Business Flow:
 * - Pehli limit lagne ke baad user bina commitment sentence type kiye limit ko delete ya
 *   increase (loose) nahi kar sakega (Anti-Bypass Protection).
 * =====================================================================
 */
class ActivateTimeLimitChallengeUseCase @Inject constructor(
    private val repository: TimeLimitRepository
) {
    suspend operator fun invoke(type: ChallengeType, sentence: String): AppResult<Unit> =
        repository.activateChallengeForLimit(type, sentence)
}
