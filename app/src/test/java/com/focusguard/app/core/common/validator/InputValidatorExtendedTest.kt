package com.focusguard.app.core.common.validator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extended unit tests for InputValidator:
 * - validateScheduleTitle (blank, too short, valid, too long)
 * - validateSupportCode (blank, non-digit, wrong length, valid 6 digits)
 */
class InputValidatorExtendedTest {

    // ── Schedule Title Validation ─────────────────────────────────────────

    @Test
    fun `schedule title blank returns Blank VAL-001`() {
        val result = InputValidator.validateScheduleTitle("   ")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    @Test
    fun `schedule title 1 char returns TooShort VAL-002`() {
        val result = InputValidator.validateScheduleTitle("A")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooShort)
        assertEquals("VAL-002", error.code)
    }

    @Test
    fun `schedule title 2 chars returns Valid`() {
        val result = InputValidator.validateScheduleTitle("Work")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `schedule title 50 chars returns Valid`() {
        val fiftyChars = "A".repeat(50)
        val result = InputValidator.validateScheduleTitle(fiftyChars)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `schedule title 51 chars returns TooLong VAL-003`() {
        val fiftyOneChars = "A".repeat(51)
        val result = InputValidator.validateScheduleTitle(fiftyOneChars)
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.TooLong)
        assertEquals("VAL-003", error.code)
    }

    // ── Support Code Validation ───────────────────────────────────────────

    @Test
    fun `support code blank returns Blank VAL-001`() {
        val result = InputValidator.validateSupportCode("   ")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.Blank)
        assertEquals("VAL-001", error.code)
    }

    @Test
    fun `support code with non-digits returns InvalidSupportCode VAL-007`() {
        val result = InputValidator.validateSupportCode("12a456")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidSupportCode)
        assertEquals("VAL-007", error.code)
    }

    @Test
    fun `support code with 5 digits returns InvalidSupportCode VAL-007`() {
        val result = InputValidator.validateSupportCode("12345")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidSupportCode)
        assertEquals("VAL-007", error.code)
    }

    @Test
    fun `support code with 7 digits returns InvalidSupportCode VAL-007`() {
        val result = InputValidator.validateSupportCode("1234567")
        assertTrue(result is ValidationResult.Invalid)
        val error = (result as ValidationResult.Invalid).error
        assertTrue(error is ValidationError.InvalidSupportCode)
        assertEquals("VAL-007", error.code)
    }

    @Test
    fun `support code with valid 6 digits returns Valid`() {
        val result = InputValidator.validateSupportCode("582910")
        assertEquals(ValidationResult.Valid, result)
    }
}
