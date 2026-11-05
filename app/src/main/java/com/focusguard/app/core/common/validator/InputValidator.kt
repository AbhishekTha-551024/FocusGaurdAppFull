package com.focusguard.app.core.common.validator

/**
 * Result of validating an input field.
 * Pure Kotlin, zero Android framework dependencies.
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val error: ValidationError) : ValidationResult
}

/**
 * Structured validation errors containing a unique diagnostic code and user-friendly message.
 */
sealed class ValidationError(
    val code: String,
    val message: String
) {
    data class Blank(
        val fieldName: String = "Field"
    ) : ValidationError(
        code = "VAL-001",
        message = "$fieldName cannot be empty or blank."
    )

    data class TooShort(
        val fieldName: String = "Field",
        val minLength: Int,
        val actualLength: Int
    ) : ValidationError(
        code = "VAL-002",
        message = "$fieldName must be at least $minLength characters ($actualLength provided)."
    )

    data class TooLong(
        val fieldName: String = "Field",
        val maxLength: Int,
        val actualLength: Int
    ) : ValidationError(
        code = "VAL-003",
        message = "$fieldName cannot exceed $maxLength characters ($actualLength provided)."
    )

    data class InvalidEmail(
        val input: String
    ) : ValidationError(
        code = "VAL-004",
        message = "Please enter a valid email address."
    )

    data class OutOfRange(
        val fieldName: String = "Value",
        val min: Int,
        val max: Int,
        val actual: Int
    ) : ValidationError(
        code = "VAL-005",
        message = "$fieldName must be between $min and $max ($actual provided)."
    )

    data class InvalidDomain(
        val input: String
    ) : ValidationError(
        code = "VAL-006",
        message = "'$input' is not a valid website domain (e.g. reddit.com or youtube.com)."
    )

    data class InvalidSupportCode(
        val input: String
    ) : ValidationError(
        code = "VAL-007",
        message = "Support passcode must be exactly 6 numeric digits."
    )
}

/**
 * Centralized, pure Kotlin input validator for FocusGuard.
 * Completely decoupled from Android UI, Context, and Compose.
 */
object InputValidator {

    private val DOMAIN_REGEX = Regex(
        """^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$"""
    )

    private val EMAIL_REGEX = Regex(
        """^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"""
    )

    /**
     * Validates a search or filter keyword.
     * Trimmed, min 2 characters, max 80 characters.
     */
    fun validateKeyword(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.Blank(fieldName = "Keyword"))
        }
        if (trimmed.length < 2) {
            return ValidationResult.Invalid(
                ValidationError.TooShort(
                    fieldName = "Keyword",
                    minLength = 2,
                    actualLength = trimmed.length
                )
            )
        }
        if (trimmed.length > 80) {
            return ValidationResult.Invalid(
                ValidationError.TooLong(
                    fieldName = "Keyword",
                    maxLength = 80,
                    actualLength = trimmed.length
                )
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a website domain name (e.g. reddit.com, news.bbc.co.uk).
     * Normalizes protocol prefixes and validates RFC domain structure, max 80 characters.
     */
    fun validateDomain(input: String): ValidationResult {
        val trimmed = input.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .removeSuffix("/")
            .trim()

        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.Blank(fieldName = "Website domain"))
        }
        if (trimmed.length > 80) {
            return ValidationResult.Invalid(
                ValidationError.TooLong(
                    fieldName = "Website domain",
                    maxLength = 80,
                    actualLength = trimmed.length
                )
            )
        }
        if (!DOMAIN_REGEX.matches(trimmed)) {
            return ValidationResult.Invalid(ValidationError.InvalidDomain(input = trimmed))
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a custom overlay block message.
     * Non-blank, min 2 characters, max 120 characters.
     */
    fun validateCustomMessage(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.Blank(fieldName = "Custom block message"))
        }
        if (trimmed.length < 2) {
            return ValidationResult.Invalid(
                ValidationError.TooShort(
                    fieldName = "Custom block message",
                    minLength = 2,
                    actualLength = trimmed.length
                )
            )
        }
        if (trimmed.length > 120) {
            return ValidationResult.Invalid(
                ValidationError.TooLong(
                    fieldName = "Custom block message",
                    maxLength = 120,
                    actualLength = trimmed.length
                )
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validates an accountability partner email address without Android framework dependencies.
     */
    fun validateEmail(input: String): ValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.Blank(fieldName = "Email address"))
        }
        if (!EMAIL_REGEX.matches(trimmed)) {
            return ValidationResult.Invalid(ValidationError.InvalidEmail(input = trimmed))
        }
        return ValidationResult.Valid
    }

    /**
     * Validates the duration for timed uninstall protection lock.
     * Allowed range: 1 to 30 days.
     */
    fun validateUninstallDays(days: Int): ValidationResult {
        if (days !in 1..30) {
            return ValidationResult.Invalid(
                ValidationError.OutOfRange(
                    fieldName = "Uninstall protection duration",
                    min = 1,
                    max = 30,
                    actual = days
                )
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a daily screen time quota for restricted applications.
     * Allowed range: 1 to 1440 minutes (max 24 hours).
     */
    fun validateDailyLimitMinutes(minutes: Int): ValidationResult {
        if (minutes !in 1..1440) {
            return ValidationResult.Invalid(
                ValidationError.OutOfRange(
                    fieldName = "Daily screen time limit",
                    min = 1,
                    max = 1440,
                    actual = minutes
                )
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validates the title of a focus schedule.
     * Non-blank, min 2 characters, max 50 characters.
     */
    fun validateScheduleTitle(title: String): ValidationResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.Blank(fieldName = "Schedule title"))
        }
        if (trimmed.length < 2) {
            return ValidationResult.Invalid(
                ValidationError.TooShort(
                    fieldName = "Schedule title",
                    minLength = 2,
                    actualLength = trimmed.length
                )
            )
        }
        if (trimmed.length > 50) {
            return ValidationResult.Invalid(
                ValidationError.TooLong(
                    fieldName = "Schedule title",
                    maxLength = 50,
                    actualLength = trimmed.length
                )
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a 6-digit emergency support unlock code.
     */
    fun validateSupportCode(code: String): ValidationResult {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.Blank(fieldName = "Support passcode"))
        }
        if (trimmed.length != 6 || !trimmed.all { it.isDigit() }) {
            return ValidationResult.Invalid(ValidationError.InvalidSupportCode(input = trimmed))
        }
        return ValidationResult.Valid
    }
}
