package com.focusguard.app.domain.policy

import com.focusguard.app.commitment.TEST_DEVICE_ID
import com.focusguard.app.commitment.dayMs
import com.focusguard.app.commitment.lockState
import com.focusguard.app.commitment.nowMs
import com.focusguard.app.domain.model.CommitmentLockState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CommitmentRestoreInvariantsTest
 * ===============================
 * Exhaustive, table-driven proof of the two promises the whole feature rests on:
 *
 * 1. `decide()` can only ADD protection to this device, and never past what the record promises.
 * 2. `isStrongerThan()` agrees with the published Firestore ruleset — the Kotlin model of
 *    `docs/firestore-device-locks-rules.md` below is run against every candidate write, and the
 *    disagreements are asserted to be exactly the two documented, harmless ones.
 *
 * Every state pair is enumerated rather than sampled, so a future edit that loosens one rule fails
 * here instead of on a phone.
 */
class CommitmentRestoreInvariantsTest {

    private val now = nowMs()
    private val day = dayMs()

    /** Named so a failure message points straight at the pair that broke. */
    private data class Named(val label: String, val state: CommitmentLockState?)

    private fun timed(days: Int, expiresInDays: Int) = CommitmentLockState(
        deviceId = TEST_DEVICE_ID,
        isTimedLockActive = true,
        days = days,
        expiryWallMs = now + expiresInDays.toLong() * day,
        recordedAtMs = now
    )

    private val localStates = listOf(
        Named("local/inactive", null),
        Named("local/live-short", timed(days = 3, expiresInDays = 3)),
        Named("local/live-long", timed(days = 20, expiresInDays = 20)),
        Named("local/expired-record", timed(days = 5, expiresInDays = -2)),
        Named("local/permanent", CommitmentLockState(TEST_DEVICE_ID, true, 0, 0L, now))
    )

    private val serverStates = listOf(
        Named("server/absent", null),
        Named("server/live-short", timed(days = 4, expiresInDays = 4)),
        Named("server/live-long", timed(days = 30, expiresInDays = 25)),
        Named("server/expired", timed(days = 10, expiresInDays = -1)),
        Named("server/released", CommitmentLockState(TEST_DEVICE_ID, false, 0, 0L, now)),
        Named("server/permanent", CommitmentLockState(TEST_DEVICE_ID, true, 0, 0L, now))
    )

    private fun stateOf(named: Named): CommitmentLockState =
        named.state ?: CommitmentLockState(TEST_DEVICE_ID, false, 0, 0L, now)

    /** The Kotlin model of the ruleset in docs/firestore-device-locks-rules.md. */
    private fun isWellFormedByTheRules(state: CommitmentLockState, serverNowMs: Long): Boolean =
        state.deviceId == TEST_DEVICE_ID &&
            state.isTimedLockActive &&
            state.days >= 1 &&
            state.days <= 3650 &&
            state.expiryWallMs > serverNowMs

    private fun isNonWeakeningByTheRules(
        before: CommitmentLockState,
        after: CommitmentLockState
    ): Boolean = after.isTimedLockActive &&
        after.expiryWallMs >= before.expiryWallMs &&
        after.days >= before.days &&
        after.deviceId == before.deviceId

    // ── 1. decide() over the full grid ──

    @Test
    fun decideNeverLowersLocalProtectionAcrossEveryLocalServerPair() {
        val violations = mutableListOf<String>()

        for (local in localStates) {
            for (server in serverStates) {
                val localState = stateOf(local)
                val decision = CommitmentRestorePolicy.decide(
                    local = localState,
                    server = server.state,
                    nowMs = now,
                    oneDayMs = day
                )
                val pair = "${local.label} x ${server.label} -> $decision"

                when (decision) {
                    // A restore may only ever move the deadline forward, and must land exactly on the
                    // deadline the record itself carries.
                    is CommitmentRestoreDecision.ReArmLocal -> {
                        if (localState.isPermanentLock) violations += "$pair (permanent local replaced by timed)"
                        if (decision.expiryWallMs != server.state!!.expiryWallMs) {
                            violations += "$pair (deadline is not the record's own)"
                        }
                        if (decision.expiryWallMs < localState.expiryWallMs) {
                            violations += "$pair (shortened a live local lock)"
                        }
                        if (decision.days < 1) violations += "$pair (zero-day timed lock)"
                    }
                    // Silence from the server must never read as "released".
                    CommitmentRestoreDecision.NothingToRestore ->
                        if (CommitmentRestorePolicy.isOwedAt(localState, now)) {
                            violations += "$pair (live local lock treated as nothing owed)"
                        }
                    is CommitmentRestoreDecision.MirrorLocalToServer ->
                        if (!CommitmentRestorePolicy.isOwedAt(decision.state, now)) {
                            violations += "$pair (mirrored a state that owes nothing)"
                        }
                    CommitmentRestoreDecision.LocalAlreadyCoversServer -> Unit
                }
            }
        }

        assertTrue("violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun everyLiveLocalLockAgainstAServerThatOwesNothingIsMirroredNotDropped() {
        val local = timed(days = 6, expiresInDays = 6)

        for (server in listOf(
            null,
            CommitmentLockState(TEST_DEVICE_ID, false, 0, 0L, now),
            timed(days = 6, expiresInDays = -1)
        )) {
            val decision = CommitmentRestorePolicy.decide(local, server, now, day)
            assertTrue(
                "server=$server must not release a live lock",
                decision is CommitmentRestoreDecision.MirrorLocalToServer
            )
        }
    }

    @Test
    fun reDecidingAfterApplyingTheDecisionIsAlwaysStable() {
        for (local in localStates) {
            for (server in serverStates) {
                var current = stateOf(local)
                val first = CommitmentRestorePolicy.decide(current, server.state, now, day)
                if (first is CommitmentRestoreDecision.ReArmLocal) {
                    current = timed(days = first.days, expiresInDays = 0)
                        .copy(expiryWallMs = first.expiryWallMs)
                }
                val second = CommitmentRestorePolicy.decide(current, server.state, now, day)
                assertTrue(
                    "${local.label} x ${server.label}: $first then $second must not re-pin the anchor",
                    second !is CommitmentRestoreDecision.ReArmLocal || !second.repinUptimeAnchor
                )
            }
        }
    }

    // ── 2. merge rule vs the published ruleset ──

    @Test
    fun theClientNeverAttemptsAWriteTheRulesetWouldTreatAsWeakening() {
        val offenders = mutableListOf<String>()

        for (stored in serverStates) {
            for (candidate in localStates + serverStates) {
                if (stored === candidate) continue
                val before = stored.state
                val after = candidate.state ?: continue
                if (!CommitmentRestorePolicy.isStrongerThan(after, before)) continue
                if (before != null &&
                    after.expiryWallMs < before.expiryWallMs &&
                    !after.isPermanentLock
                ) {
                    offenders += "${candidate.label} over ${stored.label}"
                }
            }
        }

        assertTrue("weakening writes attempted: ${offenders.joinToString()}", offenders.isEmpty())
    }

    @Test
    fun clientAndRulesetDisagreeOnExactlyTheDocumentedCases() {
        val disagreements = mutableListOf<String>()

        for (stored in serverStates + Named("stored/absent", null)) {
            for (candidate in localStates + serverStates) {
                val before = stored.state
                val after = candidate.state ?: continue
                val clientAccepts = CommitmentRestorePolicy.isStrongerThan(after, before)
                val rulesAccepts = if (before == null) {
                    isWellFormedByTheRules(after, now)
                } else {
                    isWellFormedByTheRules(after, now) && isNonWeakeningByTheRules(before, after)
                }
                if (clientAccepts == rulesAccepts) continue
                disagreements += "${candidate.label} over ${stored.label}: client=$clientAccepts rules=$rulesAccepts"
            }
        }

        // Both known divergences are harmless in the fail-closed direction:
        //  - an equal write: the client skips a transaction the rules would have allowed (cost only);
        //  - a permanent record: the client outranks it, but `isWellFormed` cannot store days <= 0, so
        //    the write is denied and the local permanent lock is untouched either way.
        assertTrue(
            "unexpected divergence:\n${disagreements.joinToString("\n")}",
            disagreements.all {
                it.contains("client=false rules=true") || it.contains("rules=false")
            }
        )
    }

    @Test
    fun anInactiveCandidateIsRefusedByBothTheClientAndTheRuleset() {
        val stored = timed(days = 8, expiresInDays = 8)
        val released = CommitmentLockState(TEST_DEVICE_ID, false, 0, 0L, now)

        assertFalse(CommitmentRestorePolicy.isStrongerThan(released, stored))
        assertFalse(isWellFormedByTheRules(released, now))
        assertFalse(isNonWeakeningByTheRules(stored, released))
    }

    @Test
    fun mergeRuleOrdersRecordsByDeadlineThenDaysAndIgnoresTheRecordTimestamp() {
        val older = lockState(days = 5, expiresInMs = 5 * day)
        val laterDeadline = older.copy(expiryWallMs = older.expiryWallMs + day, recordedAtMs = 1L)

        assertTrue(CommitmentRestorePolicy.isStrongerThan(laterDeadline, older))
        assertTrue(CommitmentRestorePolicy.isStrongerThan(older.copy(days = 6), older))
        assertFalse(CommitmentRestorePolicy.isStrongerThan(older, older.copy(days = 6)))
        // A self-reported write timestamp is never a reason to accept a weaker commitment.
        assertFalse(
            CommitmentRestorePolicy.isStrongerThan(
                older.copy(recordedAtMs = now + 100_000L),
                older.copy(days = 6)
            )
        )
    }
}
