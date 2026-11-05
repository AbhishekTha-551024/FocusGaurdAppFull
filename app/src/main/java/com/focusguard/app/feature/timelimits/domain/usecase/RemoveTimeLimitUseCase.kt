package com.focusguard.app.feature.timelimits.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import javax.inject.Inject

/**
 * =====================================================================
 * RemoveTimeLimitUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case target application se daily screen time limit ko unblock/delete
 * karne ke liye responsible hai jab accountability conditions satisfy ho jaati hain.
 *
 * ## 2. How It Works:
 * - Parameter me app ka `packageName: String` leta hai.
 * - `repository.removeTimeLimit(packageName)` call karke Room DB se entity delete karta hai
 *   aur in-memory active cache + exceeded limits set se us app ko remove kar deta hai.
 * =====================================================================
 */
class RemoveTimeLimitUseCase @Inject constructor(
    private val repository: TimeLimitRepository
) {
    suspend operator fun invoke(packageName: String): AppResult<Unit> =
        repository.removeTimeLimit(packageName)
}
