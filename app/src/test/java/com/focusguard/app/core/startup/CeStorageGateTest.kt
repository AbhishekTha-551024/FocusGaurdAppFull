package com.focusguard.app.core.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision table for the Direct Boot CE-storage gate.
 *
 * The invariant under test: while storage is locked the poller must keep waiting. Releasing the
 * waiters early is what re-opens the post-reboot crash (Room query on a non-existent CE database
 * directory kills the process during Hilt graph construction).
 */
class CeStorageGateTest {

    @Test
    fun testUnlockedAtFirstProbe_StopsImmediately() {
        assertEquals(GateAction.STOP_POLLING, nextGateAction(storageReady = true, lockedPollsSinceLog = 0))
    }

    @Test
    fun testUnlockedAfterLongWait_StopsRegardlessOfPollCount() {
        assertEquals(GateAction.STOP_POLLING, nextGateAction(storageReady = true, lockedPollsSinceLog = 12))
        assertEquals(GateAction.STOP_POLLING, nextGateAction(storageReady = true, lockedPollsSinceLog = 9_999))
    }

    @Test
    fun testLockedBelowLogThreshold_KeepsPollingQuietly() {
        assertEquals(GateAction.POLL, nextGateAction(storageReady = false, lockedPollsSinceLog = 0))
        assertEquals(GateAction.POLL, nextGateAction(storageReady = false, lockedPollsSinceLog = 11))
    }

    @Test
    fun testLockedAtLogThreshold_PollsAndLogs() {
        assertEquals(GateAction.POLL_AND_LOG, nextGateAction(storageReady = false, lockedPollsSinceLog = 12))
        assertEquals(GateAction.POLL_AND_LOG, nextGateAction(storageReady = false, lockedPollsSinceLog = 500))
    }

    @Test
    fun testLockedState_NeverYieldsStopPolling() {
        // Fail-closed proof: no poll count may release the gate while storage is locked.
        for (polls in 0..2_000) {
            val action = nextGateAction(storageReady = false, lockedPollsSinceLog = polls)
            assertTrue("Locked storage must never release the gate (poll $polls)", action != GateAction.STOP_POLLING)
        }
    }
}
