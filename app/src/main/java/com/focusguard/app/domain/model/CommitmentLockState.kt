package com.focusguard.app.domain.model

/**
 * CommitmentLockState
 * ===================
 * The one shape that describes "a timed uninstall lock is owed on this device", used for BOTH the
 * local DataStore state and the server-side record, so the two can be compared without mapping.
 *
 * Pure Kotlin (Invariant Rule 4: no android.* in the domain layer).
 *
 * @param deviceId          reinstall-stable device key (Settings.Secure.ANDROID_ID), never a person.
 * @param isTimedLockActive true when protection is armed on this device: a timed lock when [days] is
 *                          >= 1, permanent protection when it is <= 0 — the same reading as
 *                          [com.focusguard.app.domain.policy.ProtectionPolicy.isProtectionActive].
 * @param days              committed days. Untimed/permanent protection is represented as days <= 0
 *                          with [expiryWallMs] <= 0, mirroring [com.focusguard.app.domain.policy.ProtectionPolicy].
 * @param expiryWallMs      wall-clock instant the timed lock is owed until (0/absent when untimed).
 * @param recordedAtMs      wall clock of the write, used only for logging/ordering — never trusted
 *                          as proof, since either side of the pair can be edited by its owner.
 *
 * Deliberately absent: the monotonic uptime anchor. Uptime proof is device-local by construction
 * ([com.focusguard.app.core.datastore.UptimeLedger] is the only valid source), so a restored lock
 * re-proves its remaining duration on this device's clock instead of importing a remote number.
 */
data class CommitmentLockState(
    val deviceId: String,
    val isTimedLockActive: Boolean,
    val days: Int,
    val expiryWallMs: Long,
    val recordedAtMs: Long
) {
    /** A state that can enforce nothing: no identity to key a server record with. */
    val hasUsableDeviceId: Boolean get() = deviceId.isNotBlank()

    /** Timed lock with a real future deadline (untimed protection is handled separately). */
    val isTimedWithDeadline: Boolean get() = isTimedLockActive && days >= 1 && expiryWallMs > 0L

    /**
     * Armed with no deadline at all. Strictly stronger than any timed record, so a timed server
     * record must never overwrite it.
     */
    val isPermanentLock: Boolean get() = isTimedLockActive && days <= 0
}
