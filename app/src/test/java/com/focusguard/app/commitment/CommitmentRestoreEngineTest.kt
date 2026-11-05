package com.focusguard.app.commitment

import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.domain.repository.CommitmentRead
import com.focusguard.app.domain.repository.CommitmentReadFailure
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CommitmentRestoreEngineTest
 * ===========================
 * The cold-start half of the restore flow: what the engine does to local state, which statuses it
 * publishes (the UI gate reads them), and how many times it asks the server.
 *
 * Everything runs on virtual time, so the retry budget is proven exactly rather than waited out.
 */
class CommitmentRestoreEngineTest {

    /** Builds an engine over the doubles and returns it, with nothing started yet. */
    private fun TestScope.engineFor(
        locks: FakeCommitmentLockRepository,
        local: FakeLocalCommitmentStore = FakeLocalCommitmentStore(),
        identity: String = TEST_DEVICE_ID
    ) = CommitmentRestoreEngine(
        deviceIdentity = FakeDeviceIdentity(identity),
        commitmentLocks = locks,
        prefs = local,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    @Test
    fun blankIdentityDisablesBothTheLookupAndTheMirror() = runTest {
        val locks = FakeCommitmentLockRepository(CommitmentRead.NoRecord)
        val local = FakeLocalCommitmentStore()
        val engine = engineFor(locks, local, identity = "")

        engine.start()
        advanceUntilIdle()
        local.armLocally(days = 7, expiresInMs = 7 * dayMs())
        advanceUntilIdle()

        assertEquals(CommitmentRestoreStatus.SKIPPED_NO_IDENTITY, engine.status.value)
        assertFalse("A blank identity must not gate the UI forever", engine.status.value.isGating)
        assertEquals("no key means no lookup", 0, locks.readIds.size)
        assertTrue("no key means no mirror", locks.writes.isEmpty())
    }

    @Test
    fun serverRecordOwedOnAFreshReinstallReArmsWithTheServersOwnDeadline() = runTest {
        val owed = lockState(days = 7, expiresInMs = 5 * dayMs())
        val locks = FakeCommitmentLockRepository(CommitmentRead.Found(owed))
        val local = FakeLocalCommitmentStore()

        val engine = engineFor(locks, local)
        engine.start()
        advanceUntilIdle()

        // The re-armed duration is the time LEFT (5d), not the days the user originally promised (7),
        // so a restore never demands uptime that has already been served.
        assertEquals(listOf(Triple(5, owed.expiryWallMs, true)), local.reArms)
        assertEquals(CommitmentRestoreStatus.PROTECTED, engine.status.value)
        assertEquals(1, locks.readIds.size)
    }

    @Test
    fun shorterLocalLockIsExtendedWithoutRePinningItsUptimeAnchor() = runTest {
        val owed = lockState(days = 30, expiresInMs = 25 * dayMs())
        val local = FakeLocalCommitmentStore(initial = lockState(days = 4, expiresInMs = 4 * dayMs()))
        val engine = engineFor(FakeCommitmentLockRepository(CommitmentRead.Found(owed)), local)

        engine.start()
        advanceUntilIdle()

        assertEquals(listOf(Triple(25, owed.expiryWallMs, false)), local.reArms)
        assertEquals(CommitmentRestoreStatus.PROTECTED, engine.status.value)
    }

    @Test
    fun aLocalLockThatAlreadyCoversTheRecordNeitherWritesNorRePins() = runTest {
        val server = lockState(days = 10, expiresInMs = 5 * dayMs())
        val local = FakeLocalCommitmentStore(initial = lockState(days = 10, expiresInMs = 10 * dayMs()))
        val locks = FakeCommitmentLockRepository(CommitmentRead.Found(server))

        val engine = engineFor(locks, local)
        engine.start()
        advanceUntilIdle()

        assertTrue("idempotent cold start must not touch local state", local.reArms.isEmpty())
        assertTrue("and must not re-mirror what the record already has", locks.writes.isEmpty())
        assertEquals(CommitmentRestoreStatus.PROTECTED, engine.status.value)
    }

    @Test
    fun permanentLocalProtectionIsNeverDowngradedByATimedRecord() = runTest {
        val server = lockState(days = 20, expiresInMs = 20 * dayMs())
        val local = FakeLocalCommitmentStore(
            initial = lockState(active = true, days = 0, expiresInMs = 0L)
        )
        val engine = engineFor(FakeCommitmentLockRepository(CommitmentRead.Found(server)), local)

        engine.start()
        advanceUntilIdle()

        assertTrue("a timed record cannot replace permanent protection", local.reArms.isEmpty())
        assertEquals(CommitmentRestoreStatus.PROTECTED, engine.status.value)
    }

    @Test
    fun anExpiredRecordNeverDisarmsALiveLocalLock() = runTest {
        val expired = lockState(days = 3, expiresInMs = -1 * dayMs())
        val local = FakeLocalCommitmentStore(initial = lockState(days = 3, expiresInMs = 3 * dayMs()))
        val locks = FakeCommitmentLockRepository(CommitmentRead.Found(expired))

        val engine = engineFor(locks, local)
        engine.start()
        advanceUntilIdle()

        assertTrue("absence of proof is not proof of release", local.reArms.isEmpty())
        assertEquals(CommitmentRestoreStatus.PROTECTED, engine.status.value)
        assertEquals(1, locks.writes.size)
    }

    @Test
    fun nothingArmedAnywhereIsNothingOwed() = runTest {
        val locks = FakeCommitmentLockRepository(CommitmentRead.NoRecord)
        val local = FakeLocalCommitmentStore()
        val engine = engineFor(locks, local)

        engine.start()
        advanceUntilIdle()

        assertEquals(CommitmentRestoreStatus.NOTHING_OWED, engine.status.value)
        assertTrue(local.reArms.isEmpty())
        assertTrue(locks.writes.isEmpty())
    }

    @Test
    fun transientFailureRetriesExactlyUpToTheBudgetThenReleasesTheGate() = runTest {
        val locks = FakeCommitmentLockRepository(
            CommitmentRead.Unavailable(CommitmentReadFailure.NETWORK)
        )
        val engine = engineFor(locks)

        engine.start()
        runCurrent()
        assertEquals(CommitmentRestoreStatus.RETRYING, engine.status.value)
        assertEquals(1, locks.readIds.size)

        advanceTimeBy(Timings.COMMITMENT_RESTORE_RETRY_DELAY_MS * 2)
        runCurrent()
        assertEquals(3, locks.readIds.size)
        assertEquals(CommitmentRestoreStatus.RETRYING, engine.status.value)

        advanceUntilIdle()
        assertEquals(CommitmentRestoreStatus.UNREACHABLE, engine.status.value)
        assertEquals(Timings.COMMITMENT_RESTORE_MAX_ATTEMPTS, locks.readIds.size)
    }

    @Test
    fun deniedLookupFailsFastInsteadOfHoldingTheGateForFiveAttempts() = runTest {
        val locks = FakeCommitmentLockRepository(
            CommitmentRead.Unavailable(CommitmentReadFailure.PERMISSION)
        )
        val engine = engineFor(locks)

        engine.start()
        advanceUntilIdle()

        assertEquals(CommitmentRestoreStatus.UNREACHABLE, engine.status.value)
        assertEquals("a denial does not heal by asking again", 1, locks.readIds.size)
    }

    @Test
    fun aLookupThatHealsAfterOneTimeoutStillReArmsTheCommitment() = runTest {
        val owed = lockState(days = 12, expiresInMs = 11 * dayMs())
        val locks = FakeCommitmentLockRepository(
            CommitmentRead.Unavailable(CommitmentReadFailure.TIMEOUT),
            CommitmentRead.Found(owed)
        )
        val local = FakeLocalCommitmentStore()
        val engine = engineFor(locks, local)

        engine.start()
        runCurrent()
        assertEquals(CommitmentRestoreStatus.RETRYING, engine.status.value)
        assertEquals(1, locks.readIds.size)

        advanceTimeBy(Timings.COMMITMENT_RESTORE_RETRY_DELAY_MS)
        runCurrent()

        advanceTimeBy(Timings.COMMITMENT_RESTORE_RETRY_DELAY_MS)
        advanceUntilIdle()

        assertEquals(listOf(Triple(11, owed.expiryWallMs, true)), local.reArms)
        assertEquals(CommitmentRestoreStatus.PROTECTED, engine.status.value)
        assertEquals(2, locks.readIds.size)
    }

    @Test
    fun anUnexpectedThrowLeavesATerminalStatusInsteadOfStuckChecking() = runTest {
        val locks = FakeCommitmentLockRepository(CommitmentRead.NoRecord)
        locks.throwOnRead = IllegalStateException("boom")
        val engine = engineFor(locks)

        engine.start()
        advanceUntilIdle()

        assertEquals(CommitmentRestoreStatus.UNREACHABLE, engine.status.value)
        assertFalse("the gate must not depend on its own timeout here", engine.status.value.isGating)
    }

    @Test
    fun aSecondStartDoesNotStackASecondLookup() = runTest {
        val locks = FakeCommitmentLockRepository(CommitmentRead.NoRecord)
        val engine = engineFor(locks)

        engine.start()
        engine.start()
        advanceUntilIdle()

        assertEquals(1, locks.readIds.size)
    }

    /**
     * The device record keeps the days the user ORIGINALLY promised (30) while local state carries
     * what is LEFT (25), so the "local already covers server" shortcut cannot fire after a restore.
     * That is fine as long as the price is only a same-value re-write: the uptime anchor must not be
     * re-pinned, the lock must not drift, and nothing new may be pushed to the record.
     */
    @Test
    fun aSecondColdStartAfterARestoreNeitherRePinsNorExtendsTheLock() = runTest {
        val owed = lockState(days = 30, expiresInMs = 25 * dayMs())
        val local = FakeLocalCommitmentStore()

        engineFor(FakeCommitmentLockRepository(CommitmentRead.Found(owed)), local).start()
        advanceUntilIdle()
        val afterFirst = local.localCommitmentState(TEST_DEVICE_ID)

        val secondLookup = FakeCommitmentLockRepository(CommitmentRead.Found(owed))
        engineFor(secondLookup, local).start()
        advanceUntilIdle()

        assertEquals(2, local.reArms.size)
        assertEquals(Triple(25, owed.expiryWallMs, false), local.reArms.last())
        assertEquals(afterFirst.days, local.localCommitmentState(TEST_DEVICE_ID).days)
        assertEquals(afterFirst.expiryWallMs, local.localCommitmentState(TEST_DEVICE_ID).expiryWallMs)
        assertTrue("the record already holds the longer promise", secondLookup.writes.isEmpty())
    }
}
