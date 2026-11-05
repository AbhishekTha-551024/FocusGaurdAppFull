package com.focusguard.app.feature.keywordblocker.domain.usecase

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.common.validator.InputValidator
import com.focusguard.app.core.common.validator.ValidationResult
import com.focusguard.app.feature.keywordblocker.domain.repository.KeywordBlockerRepository
import javax.inject.Inject

/**
 * =====================================================================
 * AddBlockedKeywordUseCase.kt - Domain Use Case
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Use Case user dwara enter kiye gaye naye keyword ya website domain ko validate
 * aur sanitize karke block list me persist karne ke liye responsible hai.
 *
 * ## 2. Step-by-Step Logic Flow:
 * 1. **Sanitization:** Raw string se leading/trailing spaces, `http://`, `https://`, `www.`,
 *    aur trailing slashes/symbols ko remove karta hai via `repository.sanitizeKeyword(rawKeyword)`.
 * 2. **Domain Validation:** [InputValidator.validateKeyword] run karta hai:
 *    - Check karta hai ki input blank toh nahi hai.
 *    - Minimum length 2 characters hona zaroori hai (single-character blocking jaise 'a' ya 'i' pure phone ko block kar degi!).
 *    - Maximum length 80 characters limit check karta hai.
 * 3. **Persistence:** Agar validation pass hoti hai, toh `repository.blockKeyword(sanitized)`
 *    call karke Room database aur in-memory fast cache dono me save kar deta hai.
 *
 * ## 3. Senior Developer Perspective:
 * Validation ko Domain Layer me rakhna Clean Architecture ka best practice hai. Agar UI se koi
 * malicious ya invalid input aata hai, toh database tak jaane se pehle hi gracefully fail
 * hoke [AppError.ValidationError] return ho jata hai.
 * =====================================================================
 */
class AddBlockedKeywordUseCase @Inject constructor(
    private val repository: KeywordBlockerRepository
) {
    suspend operator fun invoke(rawKeyword: String): AppResult<Unit> {
        val sanitized = repository.sanitizeKeyword(rawKeyword)
        val validation = if (sanitized.contains('.') && !sanitized.contains(' ')) {
            InputValidator.validateDomain(sanitized)
        } else {
            InputValidator.validateKeyword(sanitized)
        }
        when (validation) {
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
        return repository.blockKeyword(sanitized)
    }

    fun sanitize(raw: String): String = repository.sanitizeKeyword(raw)
}
