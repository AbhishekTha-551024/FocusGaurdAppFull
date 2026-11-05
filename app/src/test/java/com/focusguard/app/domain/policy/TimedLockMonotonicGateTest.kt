package com.focusguard.app.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Monotonic gate rules for timed uninstall protection: the lock is over only when the user-settable
 * wall clock AND verifiable device uptime both cover its full duration.
 *
 * Scenario under test is the real bypass: Settings > Date & time > automatic OFF, clock moved past
 * the lock end. Without the uptime half of the rule that alone cancelled the lock.
 */
class TimedLockMonotonicGateTest {

    private val oneDayMs = 24L * 60L * 60L * 1000L
    private val armWallMs = 1_700_000_000_000L
    private val days = 3
    private val expiryMs = armWallMs + days * oneDayMs
    private val requiredMs = ProtectionPolicy.timedLockRequiredMs(days, oneDayMs)

    @Test
    fun `wall clock jumped past expiry alone does not lapse the lock`() {
        val rolledForwardWallNow = expiryMs + 60 * 60 * 1000L
        val onlyOneDayOfUptime = oneDayMs

        assertFalse(
            "A clock move must not lapse a lock that has only run 1 of 3 required days",
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = expiryMs,
                wallNowMs = rolledForwardWallNow,
                requiredMs = requiredMs,
                elapsedUptimeMs = onlyOneDayOfUptime
            )
        )
        assertTrue(
            "Protection stays active while the monotonic ledger has not covered the duration",
            ProtectionPolicy.isProtectionActive(
                enabled = true, days = days, expiry = expiryMs,
                wallNowMs = rolledForwardWallNow, requiredMs = requiredMs,
                elapsedUptimeMs = onlyOneDayOfUptime
            )
        )
    }

    @Test
    fun `lock lapses when both the wall clock and the ledger cover the duration`() {
        val pastExpiry = expiryMs + 1_000L
        val fullUptime = requiredMs

        assertTrue(
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = expiryMs, wallNowMs = pastExpiry,
                requiredMs = requiredMs, elapsedUptimeMs = fullUptime
            )
        )
        assertFalse(
            ProtectionPolicy.isProtectionActive(
                enabled = true, days = days, expiry = expiryMs,
                wallNowMs = pastExpiry, requiredMs = requiredMs, elapsedUptimeMs = fullUptime
            )
        )
    }

    @Test
    fun `ledger past expiry while wall clock is still inside the window keeps protection active`() {
        val beforeExpiry = expiryMs - 1_000L

        assertFalse(
            "Neither clock may lapse the lock on its own",
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = expiryMs, wallNowMs = beforeExpiry,
                requiredMs = requiredMs, elapsedUptimeMs = requiredMs + oneDayMs
            )
        )
    }

    @Test
    fun `clock rolled back behind expiry never lapses the lock`() {
        val rolledBackWallNow = armWallMs - 7 * oneDayMs

        assertFalse(
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = expiryMs, wallNowMs = rolledBackWallNow,
                requiredMs = requiredMs, elapsedUptimeMs = requiredMs
            )
        )
        assertTrue(
            ProtectionPolicy.isProtectionActive(
                enabled = true, days = days, expiry = expiryMs,
                wallNowMs = rolledBackWallNow, requiredMs = requiredMs, elapsedUptimeMs = requiredMs
            )
        )
    }

    @Test
    fun `boundary where both clocks exactly reach the duration lapses the lock`() {
        assertTrue(
            "now == expiry with the full uptime accrued is expired, matching the wall-clock rule",
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = expiryMs, wallNowMs = expiryMs,
                requiredMs = requiredMs, elapsedUptimeMs = requiredMs
            )
        )
        assertFalse(
            ProtectionPolicy.isProtectionActive(
                enabled = true, days = days, expiry = expiryMs,
                wallNowMs = expiryMs, requiredMs = requiredMs, elapsedUptimeMs = requiredMs
            )
        )
    }

    @Test
    fun `missing uptime proof fails closed`() {
        val pastExpiry = expiryMs + oneDayMs

        for (elapsed in listOf(-1L, 0L)) {
            assertFalse(
                "Unknown or absent ledger elapsed ($elapsed ms) must not lapse the lock",
                ProtectionPolicy.isTimedLockLapsed(
                    expiryMs = expiryMs, wallNowMs = pastExpiry,
                    requiredMs = requiredMs, elapsedUptimeMs = elapsed
                )
            )
        }
    }

    @Test
    fun `untimed and corrupt inputs are never lapsed`() {
        val pastExpiry = expiryMs + oneDayMs

        assertEquals(0L, ProtectionPolicy.timedLockRequiredMs(days = 0, oneDayMs = oneDayMs))
        assertEquals(0L, ProtectionPolicy.timedLockRequiredMs(days = -5, oneDayMs = oneDayMs))

        assertFalse(
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = 0L, wallNowMs = pastExpiry, requiredMs = requiredMs, elapsedUptimeMs = requiredMs
            )
        )
        assertFalse(
            ProtectionPolicy.isTimedLockLapsed(
                expiryMs = expiryMs, wallNowMs = pastExpiry, requiredMs = 0L, elapsedUptimeMs = requiredMs
            )
        )
    }

    @Test
    fun `untimed protection stays active regardless of the ledger`() {
        assertTrue(
            ProtectionPolicy.isProtectionActive(
                enabled = true, days = 0, expiry = 0L,
                wallNowMs = armWallMs, requiredMs = 0L, elapsedUptimeMs = 0L
            )
        )
    }

    @Test
    fun `disabled protection stays inactive even with no uptime accrued`() {
        assertFalse(
            ProtectionPolicy.isProtectionActive(
                enabled = false, days = days, expiry = expiryMs,
                wallNowMs = armWallMs, requiredMs = requiredMs, elapsedUptimeMs = 0L
            )
        )
    }

    @Test
    fun `corrupt timed state matches the existing wall-clock rule`() {
        assertFalse(
            ProtectionPolicy.isProtectionActive(
                enabled = true, days = days, expiry = 0L,
                wallNowMs = armWallMs + oneDayMs, requiredMs = requiredMs, elapsedUptimeMs = 0L
            )
        )
    }
}
