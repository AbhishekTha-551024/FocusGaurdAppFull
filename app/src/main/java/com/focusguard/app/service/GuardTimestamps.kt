package com.focusguard.app.service

/**
 * Mutable state holder for monotonic timestamps and transient cooldown trackers
 * used by [FocusGuardAccessibilityService].
 *
 * Scoped to the service instance (resets with a new service instance; not a singleton).
 * Uses @JvmField for zero-overhead direct property access on the accessibility hot path.
 */
class GuardTimestamps {
    @JvmField var lastBlockTimestamp: Long = 0L
    @JvmField var lastShadeCollapseTimestamp: Long = 0L
    @JvmField var lastDeepFocusReassertTimestamp: Long = 0L
    @JvmField var lastOverlayDismissTimestamp: Long = 0L
    @JvmField var lastDismissedPackage: String = ""
    @JvmField var lastAliveTimestamp: Long = 0L
    @JvmField var lastAdminTamperLogTimestamp: Long = 0L
}

