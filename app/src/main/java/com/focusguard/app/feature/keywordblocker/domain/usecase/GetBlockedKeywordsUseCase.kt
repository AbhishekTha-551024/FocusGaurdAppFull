package com.focusguard.app.feature.keywordblocker.domain.usecase

import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * =====================================================================
 * GetBlockedKeywordsUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case UI aur ViewModels ko blocked keywords aur websites ki list deliver
 * karta hai. Isme do modes hain:
 * 1. `invoke(): Flow<List<BlockedKeyword>>`: Reactive real-time stream jo Room Database
 *    se connect hoti hai. Jab bhi koi naya keyword add ya delete hota hai, yeh automatically
 *    nayee list emit karta hai.
 * 2. `getFastBlocked(): List<BlockedKeyword>`: Synchronous in-memory snapshot jo instant initial UI
 *    render ke liye use hota hai (Cold-start flash ko eliminate karne ke liye).
 *
 * ## 2. Architecture & Performance Benefit:
 * - **Cold Start UX Optimization:** Agar direct database query ka wait karenge toh UI me spinner
 *   ya empty state aayegi. `getFastBlocked()` ki madad se `stateIn` initial value instant populate ho jaati hai.
 * - **Reactive Single Source of Truth:** Flow Room Database se bind rehta hai, isliye UI kabhi
 *   stale ya out-of-sync nahi hota.
 * =====================================================================
 */
class GetBlockedKeywordsUseCase @Inject constructor(
    private val repository: KeywordBlockerRepository
) {
    operator fun invoke(): Flow<List<BlockedKeyword>> =
        repository.getBlockedKeywords()

    fun getFastBlocked(): List<BlockedKeyword> =
        repository.getFastBlockedKeywords()
}
