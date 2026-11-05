package com.focusguard.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the user-visible auto-recovery rule:
 *
 *   - Uninstall Protection ON  -> recover, every time.
 *   - Uninstall Protection OFF -> never recover, never enable Accessibility.
 *
 * Any future edit that breaks this contract fails here before it reaches a device.
 */
class AutoRecoveryPolicyTest {

    @Test
    fun `protection on allows auto recovery`() {
        assertTrue(AutoRecoveryPolicy.shouldAutoRecover(isProtectionActive = true))
    }

    @Test
    fun `protection off forbids auto recovery`() {
        assertFalse(AutoRecoveryPolicy.shouldAutoRecover(isProtectionActive = false))
    }

    @Test
    fun `probe is faster while protection is armed`() {
        assertEquals(
            AutoRecoveryPolicy.PROTECTED_PROBE_INTERVAL_MS,
            AutoRecoveryPolicy.probeIntervalMs(isProtectionActive = true)
        )
        assertEquals(
            AutoRecoveryPolicy.IDLE_PROBE_INTERVAL_MS,
            AutoRecoveryPolicy.probeIntervalMs(isProtectionActive = false)
        )
        assertTrue(
            AutoRecoveryPolicy.probeIntervalMs(true) < AutoRecoveryPolicy.probeIntervalMs(false)
        )
    }

    @Test
    fun `first recovery attempt is not throttled`() {
        assertEquals(0L, AutoRecoveryPolicy.recoveryGapMs(consecutiveFailures = 0))
    }

    @Test
    fun `recovery gap doubles per failure`() {
        assertEquals(30_000L, AutoRecoveryPolicy.recoveryGapMs(1))
        assertEquals(60_000L, AutoRecoveryPolicy.recoveryGapMs(2))
        assertEquals(120_000L, AutoRecoveryPolicy.recoveryGapMs(3))
        assertEquals(240_000L, AutoRecoveryPolicy.recoveryGapMs(4))
    }

    @Test
    fun `recovery gap is capped so a blocked OEM never spins`() {
        val cap = 5 * 60_000L
        assertEquals(cap, AutoRecoveryPolicy.recoveryGapMs(5))
        assertEquals(cap, AutoRecoveryPolicy.recoveryGapMs(50))
        assertEquals(cap, AutoRecoveryPolicy.recoveryGapMs(Int.MAX_VALUE))
    }

    @Test
    fun `negative failure count is treated as a fresh start`() {
        assertEquals(0L, AutoRecoveryPolicy.recoveryGapMs(-1))
    }

    @Test
    fun `escalation happens only after the threshold`() {
        assertFalse(AutoRecoveryPolicy.shouldEscalate(0))
        assertFalse(AutoRecoveryPolicy.shouldEscalate(AutoRecoveryPolicy.ESCALATION_THRESHOLD - 1))
        assertTrue(AutoRecoveryPolicy.shouldEscalate(AutoRecoveryPolicy.ESCALATION_THRESHOLD))
        assertTrue(AutoRecoveryPolicy.shouldEscalate(AutoRecoveryPolicy.ESCALATION_THRESHOLD + 1))
    }

    @Test
    fun `first ever attempt is never treated as a duplicate`() {
        assertFalse(AutoRecoveryPolicy.shouldSuppressDuplicate(lastAttemptElapsed = 0L, nowElapsed = 50_000L))
    }

    @Test
    fun `overlapping triggers inside the window are suppressed`() {
        val last = 100_000L
        assertTrue(AutoRecoveryPolicy.shouldSuppressDuplicate(last, last))
        assertTrue(AutoRecoveryPolicy.shouldSuppressDuplicate(last, last + 1L))
        assertTrue(
            AutoRecoveryPolicy.shouldSuppressDuplicate(
                last, last + AutoRecoveryPolicy.DUPLICATE_SUPPRESSION_MS - 1L
            )
        )
    }

    @Test
    fun `trigger exactly at the window edge is allowed`() {
        val last = 100_000L
        assertFalse(
            AutoRecoveryPolicy.shouldSuppressDuplicate(
                last, last + AutoRecoveryPolicy.DUPLICATE_SUPPRESSION_MS
            )
        )
    }

    @Test
    fun `stale timestamp from before a reboot never blocks recovery`() {
        // elapsedRealtime restarts at zero on reboot: an old value is larger than "now".
        assertFalse(AutoRecoveryPolicy.shouldSuppressDuplicate(lastAttemptElapsed = 900_000L, nowElapsed = 5_000L))
    }

    @Test
    fun `duplicate window stays below the binding verification delay`() {
        // AccessibilityRecoveryScheduler verifies the binding after 20 s. If the duplicate gate
        // were wider, that verification would be swallowed by the gate meant to protect it.
        assertTrue(AutoRecoveryPolicy.DUPLICATE_SUPPRESSION_MS < 20_000L)
    }
}
