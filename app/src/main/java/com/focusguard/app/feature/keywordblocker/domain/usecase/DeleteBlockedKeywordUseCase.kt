package com.focusguard.app.feature.keywordblocker.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import javax.inject.Inject

/**
 * =====================================================================
 * DeleteBlockedKeywordUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case block list me se kisi specific keyword ya website ko unblock/delete
 * karne ke liye responsible hai jab accountability conditions fulfill ho chuki hon.
 *
 * ## 2. How It Works:
 * - Parameter me unique `id: Long` leta hai jo [com.focusguard.app.core.database.entity.BlockedKeywordEntity]
 *   ki primary key hoti hai.
 * - `repository.unblockKeyword(id)` call karta hai jo Room database se row delete karta hai aur
 *   `cachedBlockedKeywords` set se us keyword ko evict karta hai.
 * - Return type [AppResult] hai, jisse database failures ya exceptions safe tareeqe se handle hote hain.
 *
 * ## 3. Security & Anti-Bypass Note:
 * Direct deletion tabhi allow hoti hai jab accountability challenge disabled ho. Agar challenge
 * enabled hai, toh ViewModel pehle challenge dialog dikhata hai ya time-delay schedule banata hai.
 * Challenge pass hone ke baad hi yeh use case trigger hota hai.
 * =====================================================================
 */
class DeleteBlockedKeywordUseCase @Inject constructor(
    private val repository: KeywordBlockerRepository
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> =
        repository.unblockKeyword(id)
}
