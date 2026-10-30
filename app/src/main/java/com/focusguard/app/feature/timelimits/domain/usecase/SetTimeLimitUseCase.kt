package com.focusguard.app.feature.timelimits.domain.usecase

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.common.validator.InputValidator
import com.focusguard.app.core.common.validator.ValidationResult
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import javax.inject.Inject

/**
 * =====================================================================
 * SetTimeLimitUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case user dwara configure kiye gaye daily limit minutes ko validate
 * karta hai aur safe tareeqe se repository me save karta hai.
 *
 * ## 2. Domain Validation Logic:
 * - [InputValidator.validateDailyLimitMinutes] ko execute karta hai.
 * - Daily screen time limit 1 minute se lekar 1440 minutes (24 hours) ke beech hona zaroori hai.
 * - Negative, 0, ya 24 ghante se zyada value aane par fail hoke [AppError.ValidationError] return karta hai.
 *
 * ## 3. Success Flow:
 * - Validation pass hone par `repository.setTimeLimit(packageName, appName, dailyLimitMinutes, isEnabled)`
 *   call karta hai jo Room DB upsert aur evaluator cache refresh trigger karta hai.
 * =====================================================================
 */
class SetTimeLimitUseCase @Inject constructor(
    private val repository: TimeLimitRepository
) {
    suspend operator fun invoke(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean = true
    ): AppResult<Unit> {
        when (val validation = InputValidator.validateDailyLimitMinutes(dailyLimitMinutes)) {
            is ValidationResult.Invalid -> {
                return AppResult.Failure(
                    AppError.ValidationError(
                        errorMessage = validation.error.message,
                        errorCode = validation.error.code
                    )
                )
            }
            ValidationResult.Valid -> { /* proceed */ }
        }
        return repository.setTimeLimit(packageName, appName, dailyLimitMinutes, isEnabled)
    }
}
