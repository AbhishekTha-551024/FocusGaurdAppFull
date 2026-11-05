package com.focusguard.app.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionPolicyTest {

    @Test
    fun `untimed protection is permanently active when enabled`() {
        val now = 1_700_000_000_000L
        val active = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = 0,
            expiry = 0L,
            now = now
        )
        assertTrue("Untimed protection (days=0, expiry=0L) must be active", active)
    }

    @Test
    fun `timed protection is active before expiry`() {
        val now = 1_700_000_000_000L
        val futureExpiry = now + 7 * 24 * 60 * 60 * 1000L
        val active = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = 7,
            expiry = futureExpiry,
            now = now
        )
        assertTrue("Timed protection before expiry must be active", active)
    }

    @Test
    fun `timed protection is inactive after expiry`() {
        val now = 1_700_000_000_000L
        val pastExpiry = now - 1_000L
        val active = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = 7,
            expiry = pastExpiry,
            now = now
        )
        assertFalse("Timed protection after expiry must be inactive", active)
    }

    @Test
    fun `timed protection boundary at exact expiry is inactive`() {
        val now = 1_700_000_000_000L
        val active = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = 7,
            expiry = now,
            now = now
        )
        assertFalse("Timed protection exactly at expiry (now == expiry) must be inactive", active)
    }

    @Test
    fun `protection is inactive when disabled regardless of days and expiry`() {
        val now = 1_700_000_000_000L
        val activeUntimed = ProtectionPolicy.isProtectionActive(
            enabled = false,
            days = 0,
            expiry = 0L,
            now = now
        )
        assertFalse("Disabled untimed protection must be inactive", activeUntimed)

        val activeTimed = ProtectionPolicy.isProtectionActive(
            enabled = false,
            days = 7,
            expiry = now + 100_000L,
            now = now
        )
        assertFalse("Disabled timed protection must be inactive", activeTimed)
    }

    @Test
    fun `timed protection with corrupt expiry is inactive`() {
        val now = 1_700_000_000_000L
        val activeZeroExpiry = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = 7,
            expiry = 0L,
            now = now
        )
        assertFalse("Timed protection with 0L expiry is corrupt and must be inactive", activeZeroExpiry)

        val activeNegativeExpiry = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = 7,
            expiry = -100L,
            now = now
        )
        assertFalse("Timed protection with negative expiry must be inactive", activeNegativeExpiry)
    }

    @Test
    fun `negative days treated as untimed permanent protection`() {
        val now = 1_700_000_000_000L
        val activeNegativeDaysZeroExpiry = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = -1,
            expiry = 0L,
            now = now
        )
        assertTrue("Negative days with 0L expiry must be treated as untimed permanent", activeNegativeDaysZeroExpiry)

        val activeNegativeDaysWithExpiry = ProtectionPolicy.isProtectionActive(
            enabled = true,
            days = -5,
            expiry = now + 100_000L,
            now = now
        )
        assertTrue("Negative days with expiry must be treated as untimed permanent", activeNegativeDaysWithExpiry)
    }

    @Test
    fun `running timed lock requires a challenge to disarm`() {
        assertTrue(
            ProtectionPolicy.requiresChallengeToDisarm(
                enabled = true,
                days = 30,
                protectionReallyActive = true
            )
        )
    }

    @Test
    fun `untimed protection does not add the timed-lock challenge gate`() {
        assertFalse(
            ProtectionPolicy.requiresChallengeToDisarm(
                enabled = true,
                days = 0,
                protectionReallyActive = true
            )
        )
    }

    @Test
    fun `lapsed timed lock does not require a challenge`() {
        assertFalse(
            ProtectionPolicy.requiresChallengeToDisarm(
                enabled = true,
                days = 30,
                protectionReallyActive = false
            )
        )
    }

    @Test
    fun `disabled protection never requires a disarm challenge`() {
        assertFalse(
            ProtectionPolicy.requiresChallengeToDisarm(
                enabled = false,
                days = 30,
                protectionReallyActive = false
            )
        )
    }
}

