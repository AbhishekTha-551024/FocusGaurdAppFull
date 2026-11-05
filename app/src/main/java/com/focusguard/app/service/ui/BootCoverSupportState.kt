package com.focusguard.app.service.ui

import com.focusguard.app.security.SupportVerificationResult

private const val SUPPORT_CODE_LENGTH = 6

/**
 * Immutable digit buffer for 6-digit numeric support code entry.
 * Pure Kotlin logic with zero android.* dependencies.
 */
data class DigitBuffer(val code: String = "") {

    val isComplete: Boolean get() = code.length == SUPPORT_CODE_LENGTH

    val length: Int get() = code.length

    fun append(digit: Char): DigitBuffer {
        if (!digit.isDigit()) return this
        if (code.length >= SUPPORT_CODE_LENGTH) return this
        return DigitBuffer(code + digit)
    }

    fun backspace(): DigitBuffer {
        if (code.isEmpty()) return this
        return DigitBuffer(code.dropLast(1))
    }

    fun clear(): DigitBuffer = DigitBuffer("")
}

/**
 * UI display states for support code verification on the boot shield.
 */
sealed class SupportUiStatus {
    object Idle : SupportUiStatus()
    object Verifying : SupportUiStatus()
    object Success : SupportUiStatus()
    data class InvalidCode(val message: String = "Invalid support code. Please check and try again.") : SupportUiStatus()
    data class RateLimited(val remainingMinutes: Long) : SupportUiStatus()
    data class AlreadyUsed(val message: String = "This support code has already been used.") : SupportUiStatus()
    data class DeviceEncrypted(val message: String = "Support code verification is available after unlocking your phone for the first time.") : SupportUiStatus()
    data class ExecutionError(val message: String = "Failed to deactivate protection. Please try again.") : SupportUiStatus()
}

/**
 * Pure evaluation rule: determines whether a support code verification result
 * permits dismissing the boot shield.
 *
 * Invariant: A wrong or unexecuted code must NEVER dismiss the shield.
 */
fun evaluateDismissEligibility(
    result: SupportVerificationResult?,
    appliedSuccessfully: Boolean
): Boolean = (result is SupportVerificationResult.Success) && appliedSuccessfully

