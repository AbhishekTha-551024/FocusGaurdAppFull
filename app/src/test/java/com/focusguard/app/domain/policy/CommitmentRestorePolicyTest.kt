package com.focusguard.app.domain.policy

import com.focusguard.app.domain.model.CommitmentLockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CommitmentRestorePolicyTest
 * ===========================
 * Locks the reinstall-restore rules that decide whether a device still owes a timed uninstall lock.
 * The cases that matter are the ones a bypass would exploit: a server record must only ever ADD
 * protection, a restore must keep the original deadline, and repeated cold starts must not extend a
 * lock that is already counting down.
 */
class CommitmentRestorePolicyTest {

    private val day = 86_400_000L
    private val now = 1_730_000_000_000L
    private val deviceId = "abcdef0123456789"

    private fun armed(days: Int, expiryOffsetMs: Long) = CommitmentLockState(
        deviceId = deviceId,
        isTimedLockActive = true,
        days = days,
        expiryWallMs = now + expiryOffsetMs,
        recordedAtMs = now
    )

    /** Neutral state: an install that owes nothing (a fresh reinstall reads exactly like this). */
    private fun idle() = CommitmentLockState(
        deviceId = deviceId,
        isTimedLockActive = false,
        days = 0,
        expiryWallMs = 0L,
        recordedAtMs = now
    )

    // ── decide(): the restore decision ──

    @Test
    fun freshInstallWithActiveServerRecordReArmsAndPinsUptimeAnchor() {
        val server = armed(days = 30, expiryOffsetMs = 5 * day)

        val decision = CommitmentRestorePolicy.decide(
            local = idle(), server = server, nowMs = now, oneDayMs = day
        )

        assertTrue(decision is CommitmentRestoreDecision.ReArmLocal)
        decision as CommitmentRestoreDecision.ReArmLocal
        // The server's own deadline is reused, never "now + 30 days".
        assertEquals(server.expiryWallMs, decision.expiryWallMs)
        assertEquals(5, decision.days)
        assertTrue("No live local lock -> monotonic anchor must be re-pinned", decision.repinUptimeAnchor)
    }

    @Test
    fun repeatedColdStartDoesNotRePinAnAlreadyCoveredLock() {
        val server = armed(days = 30, expiryOffsetMs = 5 * day)
        val local = armed(days = 30, expiryOffsetMs = 5 * day)

        val decision = CommitmentRestorePolicy.decide(
            local = local, server = server, nowMs = now, oneDayMs = day
        )

        assertEquals(CommitmentRestoreDecision.LocalAlreadyCoversServer, decision)
    }

    @Test
    fun shorterLocalLockIsExtendedWithoutLosingEarnedUptime() {
        val server = armed(days = 30, expiryOffsetMs = 20 * day)
        val local = armed(days = 30, expiryOffsetMs = 2 * day)

        val decision = CommitmentRestorePolicy.decide(
            local = local, server = server, nowMs = now, oneDayMs = day
        )

        assertTrue(decision is CommitmentRestoreDecision.ReArmLocal)
        decision as CommitmentRestoreDecision.ReArmLocal
        assertEquals(server.expiryWallMs, decision.expiryWallMs)
        assertEquals(20, decision.days)
        assertFalse("In-progress lock keeps its anchor so uptime is not thrown away", decision.repinUptimeAnchor)
    }

    @Test
    fun expiredServerRecordNeverReArmsAndNeverDisarmsLocalProtection() {
        val expiredServer = armed(days = 30, expiryOffsetMs = -1 * day)
        val local = armed(days = 30, expiryOffsetMs = 10 * day)

        val decision = CommitmentRestorePolicy.decide(
            local = local, server = expiredServer, nowMs = now, oneDayMs = day
        )

        // Absence of proof is not proof of absence: the live local lock is pushed back upward.
        assertTrue(decision is CommitmentRestoreDecision.MirrorLocalToServer)
        assertEquals(local, (decision as CommitmentRestoreDecision.MirrorLocalToServer).state)
    }

    @Test
    fun permanentLocalProtectionIsNeverDowngradedByATimedServerRecord() {
        val permanent = CommitmentLockState(
            deviceId = deviceId,
            isTimedLockActive = true,
            days = 0,
            expiryWallMs = 0L,
            recordedAtMs = now
        )
        val server = armed(days = 30, expiryOffsetMs = 5 * day)

        val decision = CommitmentRestorePolicy.decide(
            local = permanent, server = server, nowMs = now, oneDayMs = day
        )

        assertEquals(CommitmentRestoreDecision.LocalAlreadyCoversServer, decision)
    }

    @Test
    fun liveLocalLockWithoutAnyServerRecordIsMirroredUpward() {
        val local = armed(days = 7, expiryOffsetMs = 6 * day)

        val decision = CommitmentRestorePolicy.decide(
            local = local, server = null, nowMs = now, oneDayMs = day
        )

        assertTrue(decision is CommitmentRestoreDecision.MirrorLocalToServer)
    }

    @Test
    fun nothingIsRestoredWhenNeitherSideOwesALock() {
        val decision = CommitmentRestorePolicy.decide(
            local = idle(), server = null, nowMs = now, oneDayMs = day
        )

        assertEquals(CommitmentRestoreDecision.NothingToRestore, decision)
    }

    // ── isOwedAt() ──

    @Test
    fun onlyAFutureTimedDeadlineCountsAsOwed() {
        assertTrue(CommitmentRestorePolicy.isOwedAt(armed(days = 3, expiryOffsetMs = day), now))
        assertFalse("Expired deadline is finished", CommitmentRestorePolicy.isOwedAt(armed(days = 3, expiryOffsetMs = -day), now))
        assertFalse("Permanent protection is not a timed debt", CommitmentRestorePolicy.isOwedAt(idle().copy(isTimedLockActive = true, days = 0), now))
        assertFalse(CommitmentRestorePolicy.isOwedAt(null, now))
        assertFalse(CommitmentRestorePolicy.isOwedAt(idle(), now))
    }

    // ── localCoversServer() ──

    @Test
    fun coveringTheServerRequiresBothDeadlineAndDays() {
        val server = armed(days = 30, expiryOffsetMs = 10 * day)

        assertTrue(CommitmentRestorePolicy.localCoversServer(armed(days = 30, expiryOffsetMs = 10 * day), server))
        assertTrue(CommitmentRestorePolicy.localCoversServer(armed(days = 31, expiryOffsetMs = 12 * day), server))
        assertFalse(CommitmentRestorePolicy.localCoversServer(armed(days = 40, expiryOffsetMs = 9 * day), server))
        assertFalse(CommitmentRestorePolicy.localCoversServer(armed(days = 29, expiryOffsetMs = 11 * day), server))
    }

    // ── reArmDays() ──

    @Test
    fun partialDaysRoundUpSoNoCommitmentIsShortened() {
        assertEquals(1, CommitmentRestorePolicy.reArmDays(remainingMs = 1L, oneDayMs = day))
        assertEquals(1, CommitmentRestorePolicy.reArmDays(remainingMs = day, oneDayMs = day))
        assertEquals(2, CommitmentRestorePolicy.reArmDays(remainingMs = day + 1L, oneDayMs = day))
        assertEquals(5, CommitmentRestorePolicy.reArmDays(remainingMs = 4 * day + 1000L, oneDayMs = day))
        assertEquals(1, CommitmentRestorePolicy.reArmDays(remainingMs = 0L, oneDayMs = day))
        assertEquals(0, CommitmentRestorePolicy.reArmDays(remainingMs = 5 * day, oneDayMs = 0L))
    }

    @Test
    fun absurdServerDurationIsCappedInsteadOfOverflowing() {
        val days = CommitmentRestorePolicy.reArmDays(remainingMs = Long.MAX_VALUE, oneDayMs = day)

        assertTrue("Capped value must stay inside the cap: $days", days in 1..3650)
        assertTrue(
            "Capped days must not overflow the uptime requirement",
            days.toLong() * day > 0L
        )
    }

    // ── isStrongerThan(): the device-record merge rule ──

    private fun permanent() = CommitmentLockState(
        deviceId = deviceId,
        isTimedLockActive = true,
        days = 0,
        expiryWallMs = 0L,
        recordedAtMs = now
    )

    @Test
    fun firstRecordForADeviceIsAlwaysWritten() {
        assertTrue(
            CommitmentRestorePolicy.isStrongerThan(armed(days = 1, expiryOffsetMs = day), stored = null)
        )
        assertFalse(
            "An inactive candidate must not create a record",
            CommitmentRestorePolicy.isStrongerThan(idle(), stored = null)
        )
    }

    @Test
    fun onlyALongerOrEqualDeadlineCountsAsStrongerAndEqualIsANoOp() {
        val stored = armed(days = 10, expiryOffsetMs = 10 * day)

        assertTrue(CommitmentRestorePolicy.isStrongerThan(armed(days = 10, expiryOffsetMs = 11 * day), stored))
        assertTrue(CommitmentRestorePolicy.isStrongerThan(armed(days = 11, expiryOffsetMs = 10 * day), stored))
        assertFalse("Same record twice must not re-write", CommitmentRestorePolicy.isStrongerThan(stored, stored))
        assertFalse(CommitmentRestorePolicy.isStrongerThan(armed(days = 30, expiryOffsetMs = 9 * day), stored))
    }

    @Test
    fun aLiveOrInactiveCandidateCanNeverDeactivateAStoredCommitment() {
        val stored = armed(days = 10, expiryOffsetMs = 10 * day)

        assertFalse(CommitmentRestorePolicy.isStrongerThan(idle(), stored))
        assertFalse(
            "days=0 with a deadline is not active protection under the timed rule",
            CommitmentRestorePolicy.isStrongerThan(
                CommitmentLockState(deviceId, isTimedLockActive = false, days = 0, expiryWallMs = 0L, recordedAtMs = now),
                stored
            )
        )
    }

    @Test
    fun permanentProtectionIsTheStrongestRecordOnBothSides() {
        val storedTimed = armed(days = 30, expiryOffsetMs = 30 * day)

        assertTrue(CommitmentRestorePolicy.isStrongerThan(permanent(), storedTimed))
        assertFalse(
            "A timed write must not talk the server out of a permanent commitment",
            CommitmentRestorePolicy.isStrongerThan(storedTimed, stored = permanent())
        )
        assertFalse(
            "Even a longer timed record cannot displace permanent protection",
            CommitmentRestorePolicy.isStrongerThan(armed(days = 30, expiryOffsetMs = 3000 * day), permanent())
        )
    }

    @Test
    fun storedInactiveRecordIsReplacedByLiveProtection() {
        val storedReleased = CommitmentLockState(
            deviceId = deviceId,
            isTimedLockActive = false,
            days = 0,
            expiryWallMs = 0L,
            recordedAtMs = now
        )

        assertTrue(CommitmentRestorePolicy.isStrongerThan(armed(days = 1, expiryOffsetMs = day), storedReleased))
    }
}
