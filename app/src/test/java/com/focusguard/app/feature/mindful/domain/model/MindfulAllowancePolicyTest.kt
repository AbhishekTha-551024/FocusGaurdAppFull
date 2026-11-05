package com.focusguard.app.feature.mindful.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindfulAllowancePolicyTest {

    @Test
    fun `expiry uses elapsed duration and reports remaining time`() {
        val expiry = MindfulAllowancePolicy.expiryAt(nowElapsedMs = 12_000L, durationMinutes = 5)

        assertEquals(312_000L, expiry)
        assertTrue(MindfulAllowancePolicy.isActive(expiry, nowElapsedMs = 12_001L))
        assertEquals(299L, MindfulAllowancePolicy.remainingSeconds(expiry, nowElapsedMs = 12_001L))
    }

    @Test
    fun `expired allowance is inactive with no remaining time`() {
        assertFalse(MindfulAllowancePolicy.isActive(expiryElapsedMs = 10_000L, nowElapsedMs = 10_000L))
        assertEquals(0L, MindfulAllowancePolicy.remainingSeconds(expiryElapsedMs = 10_000L, nowElapsedMs = 11_000L))
    }

    @Test
    fun `negative duration is clamped to zero`() {
        assertEquals(10_000L, MindfulAllowancePolicy.expiryAt(nowElapsedMs = 10_000L, durationMinutes = -1))
    }
}
