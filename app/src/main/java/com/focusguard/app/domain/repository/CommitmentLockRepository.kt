package com.focusguard.app.domain.repository

import com.focusguard.app.domain.model.CommitmentLockState

/** Why a server read produced no verdict. Keeps raw exception text (and any service URL) out of logs. */
enum class CommitmentReadFailure { NETWORK, PERMISSION, TIMEOUT, UNKNOWN }

/** Result of asking the server what this device owes. */
sealed interface CommitmentRead {
    data class Found(val state: CommitmentLockState) : CommitmentRead

    /** The server answered definitively: no commitment was ever recorded for this device. */
    data object NoRecord : CommitmentRead

    /**
     * The server could not be consulted. Distinct from [NoRecord] on purpose: absence of proof must
     * never be mistaken for proof of absence, or an offline boot would look like a released lock.
     */
    data class Unavailable(val failure: CommitmentReadFailure) : CommitmentRead
}

/** Result of mirroring local state upward. */
sealed interface CommitmentWrite {
    data object Written : CommitmentWrite

    /** The stored record already covers what we tried to write; nothing changed. */
    data object AlreadyCurrent : CommitmentWrite

    data class Failed(val failure: CommitmentReadFailure) : CommitmentWrite
}

/**
 * CommitmentLockRepository
 * ========================
 * Domain port for the durable copy of a device's timed uninstall lock — the only state that can
 * outlive an uninstall.
 */
interface CommitmentLockRepository {

    suspend fun read(deviceId: String): CommitmentRead

    /**
     * Writes [state] only if it is stronger than what the server already holds.
     * The merge is enforced server-side inside the transaction, so a tampered or stale client can
     * never lower an existing commitment.
     */
    suspend fun publishStronger(state: CommitmentLockState): CommitmentWrite
}
