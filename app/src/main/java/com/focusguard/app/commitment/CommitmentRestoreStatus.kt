package com.focusguard.app.commitment

/**
 * Where the pre-login commitment restore stands, and whether it still owes the first-run UI a gate.
 *
 * The gate exists to close the window between "app process is up after a reinstall" and "the device
 * record has been consulted": without it the user could reach an unrestricted app — and the uninstall
 * path — before the restored lock is re-armed.
 */
enum class CommitmentRestoreStatus {

    /** Cold-start lookup is running. UI must stay gated. */
    CHECKING,

    /** Lookup failed on a transient error and is being retried. UI must stay gated. */
    RETRYING,

    /** Device is protected: the commitment is live locally (restored, already present, or mirrored). */
    PROTECTED,

    /** Nothing is owed: no device record, or a record whose deadline has genuinely passed. */
    NOTHING_OWED,

    /** Attempts exhausted while offline. UI is released, but every later cold start tries again. */
    UNREACHABLE,

    /** The platform gave no reinstall-stable identity, so nothing can be keyed or restored. */
    SKIPPED_NO_IDENTITY;

    /** True while the first-run UI must wait for the restore to resolve. */
    val isGating: Boolean get() = this == CHECKING || this == RETRYING
}
