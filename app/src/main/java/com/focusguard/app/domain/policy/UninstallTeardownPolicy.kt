package com.focusguard.app.domain.policy

/**
 * The protection state an uninstall teardown is about to destroy, captured while it is still
 * readable, so a cancelled uninstall can put it back.
 *
 * [requestedAtElapsedMs] stamps the moment the system uninstaller was opened. Invariant Rule 4:
 * a monotonic elapsedRealtime reading supplied by the caller, never the wall clock, so moving the
 * date cannot make the cancellation check fire early.
 */
data class OwedTeardown(
    val protectionWasEnabled: Boolean,
    val days: Int,
    val expiryWallMs: Long,
    val requestedAtElapsedMs: Long
) {
    /**
     * A timed lock carries its own deadline; untimed protection is only a flag, so it has no
     * expiry to restore.
     */
    val isTimedLock: Boolean
        get() = protectionWasEnabled && days >= 1 && expiryWallMs > 0L
}

/** What the survival check should do with a pending teardown marker. */
enum class TeardownVerdict {
    NOTHING_PENDING,
    WAIT_FOR_UNINSTALLER,
    RESTORE
}

/**
 * Pure rules behind "the uninstaller closed but we are still installed".
 *
 * Uninstalling FocusGuard is only possible after its Device Administrator and Accessibility
 * Service have been stripped, so those privileges are always destroyed BEFORE the outcome is known.
 * If the user then presses Cancel, the app is still installed and completely defenceless — and the
 * disarm that made that possible was authorised for one purpose only: letting the uninstall happen.
 * These rules decide when that teardown has to be undone.
 */
object UninstallTeardownPolicy {

    /**
     * Only a user who actually had protection armed gets it back. With protection OFF the stripped
     * privileges are the declared end state, and re-arming them would break the invariant that
     * nothing recovers while Uninstall Protection is OFF.
     */
    fun shouldArm(protectionWasEnabled: Boolean): Boolean = protectionWasEnabled

    /**
     * Decides what to do with the marker [pending] (null when no teardown is outstanding).
     *
     * A marker survives reboot because it lives in device-protected storage, so it is compared
     * against [nowElapsedMs] with [armedInEarlierBoot] rather than assumed to be from this boot.
     */
    fun decide(
        pending: OwedTeardown?,
        nowElapsedMs: Long,
        graceMs: Long
    ): TeardownVerdict {
        if (pending == null) return TeardownVerdict.NOTHING_PENDING
        if (!shouldArm(pending.protectionWasEnabled)) return TeardownVerdict.NOTHING_PENDING
        if (armedInEarlierBoot(pending.requestedAtElapsedMs, nowElapsedMs)) {
            return TeardownVerdict.RESTORE
        }
        val waitedMs = nowElapsedMs - pending.requestedAtElapsedMs
        return if (graceMs > 0L && waitedMs < graceMs) {
            TeardownVerdict.WAIT_FOR_UNINSTALLER
        } else {
            TeardownVerdict.RESTORE
        }
    }

    /**
     * Monotonic time restarts at zero on every boot, so a stamp larger than the current reading
     * cannot come from this boot. A reboot is itself proof that the uninstall never completed —
     * the package would be gone — so the grace window does not apply to a previous boot's marker.
     */
    fun armedInEarlierBoot(requestedAtElapsedMs: Long, nowElapsedMs: Long): Boolean =
        requestedAtElapsedMs > nowElapsedMs
}
