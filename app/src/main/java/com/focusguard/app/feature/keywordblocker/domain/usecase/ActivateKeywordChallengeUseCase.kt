package com.focusguard.app.feature.keywordblocker.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import javax.inject.Inject

/**
 * =====================================================================
 * ActivateKeywordChallengeUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case tab trigger hota hai jab user pehli baar koi keyword ya website block
 * karne jaata hai aur app me Accountability Challenge pehle se active nahi hota.
 * Yeh ek atomic operation perform karta hai:
 * 1. User dwara chune gaye challenge type (e.g. TYPING ya PARTNER) ko save karta hai.
 * 2. Commitment sentence ko persist karta hai.
 * 3. App ke global challenge status ko active set karta hai.
 * 4. Aur usi waqt us specified keyword/website ko block list me add kar deta hai.
 *
 * ## 2. Architecture Me Iska Role Kya Hai?
 * - Single Responsibility Principle (SRP): ViewModel ko business logic ki complex orchestration
 *   khud nahi karni padti. ViewModel sirf is use case ke `invoke(...)` operator ko call karta hai.
 * - Testability: Is use case ko unit test karna bohot asaan hai kyunki yeh sirf [KeywordBlockerRepository]
 *   ke interface par depend karta hai.
 * =====================================================================
 */
class ActivateKeywordChallengeUseCase @Inject constructor(
    private val repository: KeywordBlockerRepository
) {
    suspend operator fun invoke(
        keyword: String,
        type: ChallengeType,
        sentence: String
    ): AppResult<Unit> = repository.activateChallengeForKeyword(keyword, type, sentence)
}
