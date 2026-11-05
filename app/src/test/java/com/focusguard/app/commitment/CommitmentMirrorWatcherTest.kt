package com.focusguard.app.commitment

import com.focusguard.app.domain.model.CommitmentLockState
import com.focusguard.app.domain.repository.CommitmentRead
import com.focusguard.app.domain.repository.CommitmentReadFailure
import com.focusguard.app.domain.repository.CommitmentWrite
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CommitmentMirrorWatcherTest
 * ===========================
 * The upward half of the flow: an armed lock must reach the device record exactly once per real
 * change, a DataStore replay must not re-write it, and a release must never be mirrored — that last
 * one is what makes a reinstall re-arm a commitment the user tried to walk back.
 */
class CommitmentMirrorWatcherTest {

    private fun TestScope.engineWith(
        locks: FakeCommitmentLockRepository,
        local: FakeLocalCommitmentStore
    ): CommitmentRestoreEngine = CommitmentRestoreEngine(
        deviceIdentity = FakeDeviceIdentity(TEST_DEVICE_ID),
        commitmentLocks = locks,
        prefs = local,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    /** Restores against an empty record so only the watcher can produce writes. */
    private fun TestScope.watchingOver(
        local: FakeLocalCommitmentStore,
        writeOutcome: CommitmentWrite = CommitmentWrite.Written
    ): Pair<CommitmentRestoreEngine, FakeCommitmentLockRepository> {
        val locks = FakeCommitmentLockRepository(CommitmentRead.NoRecord, writeOutcome = writeOutcome)
        return engineWith(locks, local).also { it.start() } to locks
    }

    @Test
    fun anArmedLockIsMirroredExactlyOnceAndAReplayDoesNotReWriteIt() = runTest {
        val local = FakeLocalCommitmentStore()
        val (engine, locks) = watchingOver(local)
        advanceUntilIdle()
        assertEquals(CommitmentRestoreStatus.NOTHING_OWED, engine.status.value)
        assertTrue(locks.writes.isEmpty())

        local.armLocally(days = 7, expiresInMs = 7 * dayMs())
        advanceUntilIdle()
        assertEquals(listOf(7), locks.writes.map { it.days })

        local.replayIdentical()
        local.replayIdentical()
        advanceUntilIdle()
        assertEquals("DataStore replays must not stack transactions", 1, locks.writes.size)

        local.armLocally(days = 30, expiresInMs = 30 * dayMs())
        advanceUntilIdle()
        assertEquals(listOf(7, 30), locks.writes.map { it.days })
    }

    @Test
    fun everyMirroredRecordIsAnActiveCommitmentWithADeadline() = runTest {
        val local = FakeLocalCommitmentStore()
        val (_, locks) = watchingOver(local)

        local.armLocally(days = 3, expiresInMs = 3 * dayMs())
        advanceUntilIdle()
        local.releaseLocally()
        advanceUntilIdle()
        local.armLocally(days = 9, expiresInMs = 9 * dayMs())
        advanceUntilIdle()

        assertEquals(listOf(3, 9), locks.writes.map { it.days })
        assertTrue(
            "a release is honored locally and never told to the server",
            locks.writes.all { it.isTimedLockActive && it.expiryWallMs > nowMs() }
        )
    }

    @Test
    fun theMirroredRecordCarriesThisDevicesKeyAndItsOwnDeadline() = runTest {
        val local = FakeLocalCommitmentStore()
        val (_, locks) = watchingOver(local)

        local.armLocally(days = 5, expiresInMs = 6 * dayMs())
        advanceUntilIdle()

        val pushed: CommitmentLockState = locks.writes.single()
        assertEquals(TEST_DEVICE_ID, pushed.deviceId)
        assertEquals(5, pushed.days)
        assertTrue(pushed.expiryWallMs > nowMs() + 5 * dayMs())
    }

    @Test
    fun anAlreadyCurrentRecordIsRememberedInsteadOfRetried() = runTest {
        val local = FakeLocalCommitmentStore()
        val (_, locks) = watchingOver(local, writeOutcome = CommitmentWrite.AlreadyCurrent)

        local.armLocally(days = 14, expiresInMs = 14 * dayMs())
        advanceUntilIdle()
        local.armLocally(days = 14, expiresInMs = 14 * dayMs())
        advanceUntilIdle()

        assertEquals(1, locks.writes.size)
    }

    @Test
    fun aFailedMirrorStaysQuietAndLeavesProtectionAndStatusAlone() = runTest {
        val local = FakeLocalCommitmentStore()
        val (engine, locks) = watchingOver(local, writeOutcome = CommitmentWrite.Failed(CommitmentReadFailure.NETWORK))

        local.armLocally(days = 6, expiresInMs = 6 * dayMs())
        advanceUntilIdle()

        assertEquals(1, locks.writes.size)
        assertEquals(CommitmentRestoreStatus.NOTHING_OWED, engine.status.value)
        assertTrue("the local lock stays armed whatever the server says", local.reArms.isEmpty())
    }

    @Test
    fun aRestoreThatReArmedLocallyDoesNotImmediatelyMirrorItselfBackUp() = runTest {
        val owed = lockState(days = 8, expiresInMs = 7 * dayMs())
        val local = FakeLocalCommitmentStore()
        val locks = FakeCommitmentLockRepository(CommitmentRead.Found(owed))
        val engine = engineWith(locks, local)

        engine.start()
        advanceUntilIdle()

        assertEquals(1, local.reArms.size)
        assertTrue("re-armed state is already what the record holds", locks.writes.isEmpty())
    }
}
