package com.focusguard.app.core.common.validator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    // ── 1. validateKeyword Tests ──────────────────────────────────────────

    @Test
    fun `keyword that is empty returns Blank VAL-001`() {
        val result = InputValidator.validateKeyword("")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    @Test
    fun `keyword with only spaces returns Blank VAL-001`() {
        val result = InputValidator.validateKeyword("    ")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    @Test
    fun `keyword with 1 char returns TooShort VAL-002`() {
        val result = InputValidator.validateKeyword("a")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooShort)
        assertEquals("VAL-002", error.code)
    }

    @Test
    fun `keyword with 2 chars returns Valid`() {
        val result = InputValidator.validateKeyword("ab")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `keyword with 80 chars returns Valid`() {
        val result = InputValidator.validateKeyword("a".repeat(80))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `keyword with 81 chars returns TooLong VAL-003`() {
        val result = InputValidator.validateKeyword("a".repeat(81))
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooLong)
        assertEquals("VAL-003", error.code)
    }

    @Test
    fun `keyword with emoji input returns Valid`() {
        val result = InputValidator.validateKeyword("focus 🛡️")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `keyword with leading and trailing spaces is trimmed and returns Valid`() {
        val result = InputValidator.validateKeyword("   focus   ")
        assertEquals(ValidationResult.Valid, result)
    }

    // ── 2. validateDomain Tests ───────────────────────────────────────────

    @Test
    fun `domain example dot com returns Valid`() {
        val result = InputValidator.validateDomain("example.com")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `domain with https and www prefix returns Valid`() {
        val result = InputValidator.validateDomain("https://www.example.com/")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `domain without dot returns InvalidDomain VAL-006`() {
        val result = InputValidator.validateDomain("abc")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidDomain)
        assertEquals("VAL-006", error.code)
    }

    @Test
    fun `domain with invalid special chars returns InvalidDomain VAL-006`() {
        val result = InputValidator.validateDomain("??***")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidDomain)
        assertEquals("VAL-006", error.code)
    }

    @Test
    fun `domain with consecutive dots returns InvalidDomain VAL-006`() {
        val result = InputValidator.validateDomain("a..com")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidDomain)
        assertEquals("VAL-006", error.code)
    }

    @Test
    fun `domain with 81 or more chars returns TooLong VAL-003`() {
        val result = InputValidator.validateDomain("a".repeat(77) + ".com")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooLong)
        assertEquals("VAL-003", error.code)
    }

    // ── 3. validateCustomMessage Tests ────────────────────────────────────

    @Test
    fun `custom message that is empty returns Blank VAL-001`() {
        val result = InputValidator.validateCustomMessage("")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    @Test
    fun `custom message with only spaces returns Blank VAL-001`() {
        val result = InputValidator.validateCustomMessage("   ")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    @Test
    fun `custom message with 1 char returns TooShort VAL-002`() {
        val result = InputValidator.validateCustomMessage("a")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooShort)
        assertEquals("VAL-002", error.code)
    }

    @Test
    fun `custom message with 2 chars returns Valid`() {
        val result = InputValidator.validateCustomMessage("Go")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `custom message with 120 chars returns Valid`() {
        val result = InputValidator.validateCustomMessage("m".repeat(120))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `custom message with 121 chars returns TooLong VAL-003`() {
        val result = InputValidator.validateCustomMessage("m".repeat(121))
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooLong)
        assertEquals("VAL-003", error.code)
    }

    // ── 4. validateEmail Tests ────────────────────────────────────────────

    @Test
    fun `valid email returns Valid`() {
        val result = InputValidator.validateEmail("a@b.com")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `email missing at sign returns InvalidEmail VAL-004`() {
        val result = InputValidator.validateEmail("ab.com")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidEmail)
        assertEquals("VAL-004", error.code)
    }

    @Test
    fun `email missing domain returns InvalidEmail VAL-004`() {
        val result = InputValidator.validateEmail("a@")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidEmail)
        assertEquals("VAL-004", error.code)
    }

    @Test
    fun `email with spaces inside returns InvalidEmail VAL-004`() {
        val result = InputValidator.validateEmail("a @b.com")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidEmail)
        assertEquals("VAL-004", error.code)
    }

    @Test
    fun `email missing tld returns InvalidEmail VAL-004`() {
        val result = InputValidator.validateEmail("a@b")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidEmail)
        assertEquals("VAL-004", error.code)
    }

    @Test
    fun `empty email returns Blank VAL-001`() {
        val result = InputValidator.validateEmail("")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    // ── 5. validateUninstallDays Tests ────────────────────────────────────

    @Test
    fun `uninstall days 0 returns OutOfRange VAL-005`() {
        val result = InputValidator.validateUninstallDays(0)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.OutOfRange)
        assertEquals("VAL-005", error.code)
    }

    @Test
    fun `uninstall days 1 returns Valid`() {
        val result = InputValidator.validateUninstallDays(1)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `uninstall days 30 returns Valid`() {
        val result = InputValidator.validateUninstallDays(30)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `uninstall days 31 returns OutOfRange VAL-005`() {
        val result = InputValidator.validateUninstallDays(31)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.OutOfRange)
        assertEquals("VAL-005", error.code)
    }

    @Test
    fun `uninstall days negative returns OutOfRange VAL-005`() {
        val result = InputValidator.validateUninstallDays(-5)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.OutOfRange)
        assertEquals("VAL-005", error.code)
    }

    // ── 6. validateDailyLimitMinutes Tests ────────────────────────────────

    @Test
    fun `daily limit 0 minutes returns OutOfRange VAL-005`() {
        val result = InputValidator.validateDailyLimitMinutes(0)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.OutOfRange)
        assertEquals("VAL-005", error.code)
    }

    @Test
    fun `daily limit 1 minute returns Valid`() {
        val result = InputValidator.validateDailyLimitMinutes(1)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `daily limit 1440 minutes returns Valid`() {
        val result = InputValidator.validateDailyLimitMinutes(1440)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `daily limit 1441 minutes returns OutOfRange VAL-005`() {
        val result = InputValidator.validateDailyLimitMinutes(1441)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.OutOfRange)
        assertEquals("VAL-005", error.code)
    }

    @Test
    fun `daily limit negative minutes returns OutOfRange VAL-005`() {
        val result = InputValidator.validateDailyLimitMinutes(-1)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.OutOfRange)
        assertEquals("VAL-005", error.code)
    }
}
