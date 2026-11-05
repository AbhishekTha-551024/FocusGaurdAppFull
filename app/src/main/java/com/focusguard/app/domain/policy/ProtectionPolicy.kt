package com.focusguard.app.domain.policy

/**
 * Pure Kotlin policy rules for protection state evaluation.
 * Invariant Rule 4: Zero android.* imports in the domain layer.
 */
object ProtectionPolicy {

    /**
     * Evaluates whether uninstall protection is actively armed.
     *
     * Rules:
     * - [enabled] == false -> false (disabled)
     * - [enabled] == true && [days] <= 0 -> true (untimed/permanent protection)
     * - [enabled] == true && [days] >= 1 -> active only if [expiry] > 0L and [now] < [expiry]
     * - [enabled] == true && [days] >= 1 && [expiry] <= 0L -> false (corrupt timed state)
     * - [now] == [expiry] -> false (expired at boundary)
     */
    fun isProtectionActive(
        enabled: Boolean,
        days: Int,
        expiry: Long,
        now: Long
    ): Boolean {
        if (!enabled) return false
        // Untimed / permanent protection: active indefinitely
        if (days <= 0) return true
        // Timed protection: requires valid future expiry
        if (expiry <= 0L) return false
        return now < expiry
    }

    /**
     * True only when a timed lock has provably run its full course on BOTH independent clocks:
     * the user-settable wall clock is past [expiryMs], AND the monotonic uptime ledger shows at
     * least [requiredMs] of real device uptime since the lock was armed ([elapsedUptimeMs]).
     *
     * A wall-clock jump forward (Settings > Date & time > automatic OFF, then a later date) moves
     * only one of the two, so the lock never lapses from a clock change alone. Missing or short
     * uptime never lapses a lock — the rule fails closed.
     *
     * Untimed ([expiryMs] <= 0) and invalid duration inputs are never lapsed.
     */
    fun isTimedLockLapsed(
        expiryMs: Long,
        wallNowMs: Long,
        requiredMs: Long,
        elapsedUptimeMs: Long
    ): Boolean {
        if (expiryMs <= 0L) return false
        if (requiredMs <= 0L) return false
        if (elapsedUptimeMs < requiredMs) return false
        return wallNowMs >= expiryMs
    }

    /**
     * Wall-clock duration a timed lock of [days] must actually run, in monotonic uptime ms.
     * Returns 0 for untimed (days <= 0) so untimed locks stay outside the lapsed check entirely.
     */
    fun timedLockRequiredMs(days: Int, oneDayMs: Long): Long =
        if (days <= 0 || oneDayMs <= 0L) 0L else days * oneDayMs

    /**
     * True when disarming protection must first pass an accountability challenge.
     *
     * A timed lock is a commitment: while it is armed and still really running, the only in-app
     * way out is a challenge (or an owner support code — those callers authorize explicitly).
     * Permanent (untimed) protection and lapsed locks do not add this extra gate.
     */
    fun requiresChallengeToDisarm(
        enabled: Boolean,
        days: Int,
        protectionReallyActive: Boolean
    ): Boolean = enabled && days >= 1 && protectionReallyActive

    /**
     * Uninstall protection state with the monotonic gate applied to timed locks.
     *
     * Same enabled/untimed/corrupt rules as [isProtectionActive]; for timed locks the wall-clock
     * comparison is replaced by [isTimedLockLapsed], which needs verifiable uptime.
     */
    fun isProtectionActive(
        enabled: Boolean,
        days: Int,
        expiry: Long,
        wallNowMs: Long,
        requiredMs: Long,
        elapsedUptimeMs: Long
    ): Boolean {
        if (!enabled) return false
        if (days <= 0) return true
        if (expiry <= 0L) return false
        return !isTimedLockLapsed(
            expiryMs = expiry,
            wallNowMs = wallNowMs,
            requiredMs = requiredMs,
            elapsedUptimeMs = elapsedUptimeMs
        )
    }
}

