package com.focusguard.app.domain.repository

import com.focusguard.app.domain.model.CommitmentLockState
import kotlinx.coroutines.flow.Flow

/**
 * LocalCommitmentStore
 * ====================
 * Domain port for "what does THIS install currently owe, and how do we write a restored lock back?".
 *
 * It exists so the restore engine can be tested without a Context, a DataStore or a device: the
 * engine only ever needs an atomic view of the timed lock, a synchronous cache-backed snapshot, and
 * one re-arm write. [com.focusguard.app.core.datastore.FocusGuardPreferences] satisfies it directly,
 * so binding the port adds no delegating helper of its own.
 *
 * Rule 3 of the project invariants still applies to implementations: [localCommitmentState] must be
 * served from in-memory caches, never from a suspending DataStore read, because callers can be on
 * any thread including the accessibility one.
 */
interface LocalCommitmentStore {

    /**
     * The live timed-lock state as ONE atomic emission per storage write, for the mirror watcher.
     *
     * Deliberately not three separate fields: observing them independently can produce a torn
     * (new deadline, old day count) record, and that torn record would then be pushed to the device.
     */
    fun timedLockState(deviceId: String): Flow<CommitmentLockState>

    /** Cache-backed snapshot in the same shape the device record uses, so the two can be compared. */
    fun localCommitmentState(deviceId: String): CommitmentLockState

    /**
     * Re-arms a timed lock the device record says is still owed, keeping the ORIGINAL deadline.
     *
     * @param repinUptimeAnchor pin the monotonic uptime anchor only when local state had no live lock
     */
    suspend fun reArmRestoredTimedProtection(days: Int, expiryWallMs: Long, repinUptimeAnchor: Boolean)
}
