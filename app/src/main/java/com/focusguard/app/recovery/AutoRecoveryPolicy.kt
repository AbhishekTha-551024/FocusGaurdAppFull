package com.focusguard.app.recovery

/**
 * AutoRecoveryPolicy
 * ==================
 * Single source of truth for the user-visible recovery rule:
 *
 *   - Uninstall protection ON  + Accessibility OFF -> auto-recover, always.
 *   - Uninstall protection OFF                     -> never auto-recover, never auto-enable.
 *
 * Pure Kotlin on purpose (no android.* imports) so the rule and the backoff maths can be
 * unit-tested without a device. Every recovery caller must go through this class so the
 * rule cannot drift between call sites (Rule 6: no duplicate helpers).
 */
object AutoRecoveryPolicy {

    /** Probe cadence while Uninstall Protection is armed. */
    const val PROTECTED_PROBE_INTERVAL_MS: Long = 15_000L

    /** Idle cadence while Uninstall Protection is off — cheap no-op checks only. */
    const val IDLE_PROBE_INTERVAL_MS: Long = 60_000L

    /** Minimum gap before a failed silent restore may be retried (grows per failure). */
    private const val BASE_RECOVERY_GAP_MS: Long = 30_000L

    /** Upper bound for the retry gap so a permanent OEM block never spins the CPU. */
    private const val MAX_RECOVERY_GAP_MS: Long = 5 * 60_000L

    /** Number of failed silent attempts before the full-screen recovery gate is escalated. */
    const val ESCALATION_THRESHOLD: Int = 3

    /**
     * Window in which every recovery trigger but the first is treated as a duplicate of the
     * same failure episode (observer + probe + unbind can all fire within a second).
     *
     * Deliberately shorter than [AccessibilityRecoveryScheduler]'s 20 s binding verification:
     * that pass must reach the restore path, or a failed bind would never be re-checked.
     */
    const val DUPLICATE_SUPPRESSION_MS: Long = 10_000L

    /**
     * The only decision that matters: recovery is allowed if and only if Uninstall
     * Protection is strictly active (Device Admin + unexpired timed lock).
     */
    fun shouldAutoRecover(isProtectionActive: Boolean): Boolean = isProtectionActive

    /** How long the probe sleeps before the next check. */
    fun probeIntervalMs(isProtectionActive: Boolean): Long =
        if (isProtectionActive) PROTECTED_PROBE_INTERVAL_MS else IDLE_PROBE_INTERVAL_MS

    /**
     * Throttle between recovery attempts: 0 for the first attempt, then
     * 30s, 60s, 120s, 240s, capped at 5 minutes.
     */
    fun recoveryGapMs(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        val shift = (consecutiveFailures - 1).coerceAtMost(4)
        return (BASE_RECOVERY_GAP_MS shl shift).coerceAtMost(MAX_RECOVERY_GAP_MS)
    }

    /** True once the probe should stop relying on silent restore and show the gate. */
    fun shouldEscalate(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= ESCALATION_THRESHOLD

    /**
     * The single duplicate gate every recovery source passes through.
     *
     * [lastAttemptElapsed] and [nowElapsed] are monotonic (`SystemClock.elapsedRealtime()`), so
     * values from before a reboot are detected as stale (`now < last`) and never block the first
     * post-boot attempt.
     */
    fun shouldSuppressDuplicate(lastAttemptElapsed: Long, nowElapsed: Long): Boolean {
        if (lastAttemptElapsed <= 0L) return false
        if (nowElapsed < lastAttemptElapsed) return false
        return nowElapsed - lastAttemptElapsed < DUPLICATE_SUPPRESSION_MS
    }
}
