package com.focusguard.app.service.ui

import com.focusguard.app.security.SupportVerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCoverSupportStateTest {

    @Test
    fun `digit buffer appends valid digits up to 6 characters`() {
        var buffer = DigitBuffer()
        assertEquals(0, buffer.length)
        assertFalse(buffer.isComplete)

        "123456".forEach { char ->
            buffer = buffer.append(char)
        }
        assertEquals(6, buffer.length)
        assertEquals("123456", buffer.code)
        assertTrue(buffer.isComplete)

        // Attempting to append beyond 6 is rejected
        val excess = buffer.append('7')
        assertEquals("Buffer capped at 6 digits", "123456", excess.code)
        assertEquals(6, excess.length)
    }

    @Test
    fun `digit buffer rejects non-numeric characters`() {
        var buffer = DigitBuffer()
        buffer = buffer.append('a')
        buffer = buffer.append('!')
        buffer = buffer.append(' ')
        assertEquals("Non-numeric characters must be ignored", "", buffer.code)
        assertEquals(0, buffer.length)

        buffer = buffer.append('9')
        assertEquals("9", buffer.code)
    }

    @Test
    fun `digit buffer backspace removes last digit safely`() {
        var buffer = DigitBuffer("123")
        buffer = buffer.backspace()
        assertEquals("12", buffer.code)

        buffer = buffer.backspace()
        assertEquals("1", buffer.code)

        buffer = buffer.backspace()
        assertEquals("", buffer.code)

        // Backspace on empty buffer should not crash
        buffer = buffer.backspace()
        assertEquals("", buffer.code)
    }

    @Test
    fun `digit buffer clear empties buffer`() {
        val buffer = DigitBuffer("123456").clear()
        assertEquals("", buffer.code)
        assertEquals(0, buffer.length)
        assertFalse(buffer.isComplete)
    }

    @Test
    fun `wrong code never dismisses the shield`() {
        val wrongResult = SupportVerificationResult.InvalidCode

        val shouldDismissWithFailure = evaluateDismissEligibility(
            result = wrongResult,
            appliedSuccessfully = false
        )
        assertFalse(
            "CRITICAL INVARIANT: Wrong code must NEVER dismiss the shield",
            shouldDismissWithFailure
        )

        val shouldDismissWithTrueApplied = evaluateDismissEligibility(
            result = wrongResult,
            appliedSuccessfully = true
        )
        assertFalse(
            "CRITICAL INVARIANT: Wrong code must NEVER dismiss the shield even if applied is erroneously true",
            shouldDismissWithTrueApplied
        )
    }

    @Test
    fun `rate limited result never dismisses the shield`() {
        val rateLimited = SupportVerificationResult.RateLimited(remainingMinutes = 5L)
        assertFalse(
            "Rate limited result must not dismiss shield",
            evaluateDismissEligibility(rateLimited, appliedSuccessfully = false)
        )
    }

    @Test
    fun `already used code never dismisses the shield`() {
        val alreadyUsed = SupportVerificationResult.AlreadyUsed
        assertFalse(
            "Replayed/consumed code must not dismiss shield",
            evaluateDismissEligibility(alreadyUsed, appliedSuccessfully = false)
        )
    }

    @Test
    fun `null result never dismisses the shield`() {
        assertFalse(
            "Null verification result must not dismiss shield",
            evaluateDismissEligibility(null, appliedSuccessfully = false)
        )
    }

    @Test
    fun `valid code dismisses shield only when action applied successfully`() {
        val successResult = SupportVerificationResult.Success(
            actionCode = "101",
            actionName = "Disable Uninstall Protection",
            targetPayload = "",
            description = "Allowed",
            matchedDate = "2026-11-01",
            codeFingerprint = "fingerprint123"
        )

        assertTrue(
            "Valid code with successful execution MUST dismiss the shield",
            evaluateDismissEligibility(successResult, appliedSuccessfully = true)
        )

        assertFalse(
            "Valid code with failed execution must NOT dismiss the shield",
            evaluateDismissEligibility(successResult, appliedSuccessfully = false)
        )
    }
}

