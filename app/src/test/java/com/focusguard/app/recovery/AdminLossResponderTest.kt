package com.focusguard.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decision table for contesting a Device Admin deactivation.
 *
 * Two invariants hold this table together:
 *  - a user who genuinely turned Uninstall Protection OFF must never be chased (declared
 *    no-auto-recovery rule), and that outranks every other branch except "admin is still active";
 *  - a device that has just booted must still be able to contest, so "never contested" is not
 *    modelled as "0ms ago", which would fall inside the cooldown.
 */
class AdminLossResponderTest {

    private val cooldown = 60_000L

    private fun decide(
        protected: Boolean,
        adminActive: Boolean,
        sinceLastMs: Long
    ) = decideAdminLoss(
        protectionEnabled = protected,
        adminStillActive = adminActive,
        msSinceLastContest = sinceLastMs,
        cooldownMs = cooldown
    )

    @Test
    fun testAdminStillActive_NothingToDoEvenWithProtectionOn() {
        assertEquals(AdminLossDecision.SKIP_ADMIN_STILL_ACTIVE, decide(protected = true, adminActive = true, sinceLastMs = -1L))
        assertEquals(AdminLossDecision.SKIP_ADMIN_STILL_ACTIVE, decide(protected = true, adminActive = true, sinceLastMs = 0L))
    }

    @Test
    fun testProtectionOff_HonouredAndNeverContested() {
        assertEquals(AdminLossDecision.SKIP_NOT_PROTECTED, decide(protected = false, adminActive = false, sinceLastMs = -1L))
        assertEquals(AdminLossDecision.SKIP_NOT_PROTECTED, decide(protected = false, adminActive = false, sinceLastMs = 0L))
        assertEquals(AdminLossDecision.SKIP_NOT_PROTECTED, decide(protected = false, adminActive = false, sinceLastMs = 999_999L))
    }

    @Test
    fun testProtectionOff_TakesPrecedenceOverCooldown() {
        assertEquals(AdminLossDecision.SKIP_NOT_PROTECTED, decide(protected = false, adminActive = false, sinceLastMs = 5L))
    }

    @Test
    fun testFirstContest_Ever_HappensImmediatelyEvenRightAfterBoot() {
        // NEVER_CONTESTED (-1) must not be confused with "contested 0ms ago".
        assertEquals(AdminLossDecision.REACTIVATE_AND_SHIELD, decide(protected = true, adminActive = false, sinceLastMs = -1L))
    }

    @Test
    fun testContestedRecently_CooldownBlocksTheSecondAttempt() {
        assertEquals(AdminLossDecision.SKIP_COOLDOWN, decide(protected = true, adminActive = false, sinceLastMs = 0L))
        assertEquals(AdminLossDecision.SKIP_COOLDOWN, decide(protected = true, adminActive = false, sinceLastMs = 59_999L))
    }

    @Test
    fun testCooldownExpired_ContestsAgain() {
        assertEquals(AdminLossDecision.REACTIVATE_AND_SHIELD, decide(protected = true, adminActive = false, sinceLastMs = 60_000L))
        assertEquals(AdminLossDecision.REACTIVATE_AND_SHIELD, decide(protected = true, adminActive = false, sinceLastMs = 60_001L))
    }

    @Test
    fun testFullTable_EveryCombinationMapsToExactlyOneBranch() {
        val expected = mapOf(
            Triple(true, true, -1L) to AdminLossDecision.SKIP_ADMIN_STILL_ACTIVE,
            Triple(false, true, -1L) to AdminLossDecision.SKIP_ADMIN_STILL_ACTIVE,
            Triple(false, false, -1L) to AdminLossDecision.SKIP_NOT_PROTECTED,
            Triple(true, false, -1L) to AdminLossDecision.REACTIVATE_AND_SHIELD,
            Triple(false, false, 0L) to AdminLossDecision.SKIP_NOT_PROTECTED,
            Triple(true, false, 0L) to AdminLossDecision.SKIP_COOLDOWN,
            Triple(true, false, 59_999L) to AdminLossDecision.SKIP_COOLDOWN,
            Triple(true, false, 60_000L) to AdminLossDecision.REACTIVATE_AND_SHIELD
        )
        for ((input, want) in expected) {
            val got = decide(protected = input.first, adminActive = input.second, sinceLastMs = input.third)
            assertEquals("For input $input", want, got)
        }
    }
}
