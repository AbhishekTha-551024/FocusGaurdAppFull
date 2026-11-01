package com.focusguard.app.core.common.constants

/**
 * Centralized registry of operational timing values, cooldowns, debounces, and timeouts.
 */
object Timings {
    /** Cooldown period after an overlay is dismissed to prevent immediate re-blocking (2500ms) */
    const val OVERLAY_DISMISS_COOLDOWN_MS = 2500L

    /** Monotonic debounce window for package and window state transitions (1500ms) */
    const val TRANSITION_DEBOUNCE_MS = 1500L

    /** Minimal duration before an in-foreground session is recorded (200ms) */
    const val MIN_RECORDABLE_SESSION_MS = 200L

    /** Window settle delay before re-evaluating window transitions (100ms) */
    const val WINDOW_SETTLE_DELAY_MS = 100L

    /** Delay between back actions during settings/accessibility tampering dismissal (150ms) */
    const val TAMPER_DISMISS_STEP_DELAY_MS = 150L

    /** Notification shade collapse rate limiter (40ms) */
    const val SHADE_COLLAPSE_THROTTLE_MS = 40L

    /** Accessibility state observer health check cooldown (12,000ms / 12s) */
    const val A11Y_OBSERVER_COOLDOWN_MS = 12_000L

    /** Heartbeat interval for time tamper monitor (30,000ms / 30s) */
    const val TAMPER_CHECK_INTERVAL_MS = 30_000L

    /** Standard one-minute interval (60,000ms) */
    const val ONE_MINUTE_MS = 60_000L

    /** Standard one-second interval (1,000ms) */
    const val ONE_SECOND_MS = 1000L
}

