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

    /**
     * Ceiling on the global navigation actions (HOME / BACK / RECENTS) this service may hand to the
     * framework inside [GLOBAL_ACTION_WINDOW_MS].
     *
     * Vivo's anti-tapjacking watchdog force-unbinds the service when it observes a burst of global
     * actions during its security scan, and an unbound service protects nothing. One legitimate
     * incident costs 2-3 actions (pre-overlay BACK, BACK + HOME, launcher-popup BACK), so the ceiling
     * sits above that while cutting an event-driven flood (Settings streams window events continuously
     * under a shield) down to a handful per second.
     */
    const val GLOBAL_ACTION_MAX_PER_WINDOW = 6

    /** Rolling window the navigation-action ceiling is measured over (2,000ms). */
    const val GLOBAL_ACTION_WINDOW_MS = 2_000L

    /** Throttle for the "navigation action refused" log line so a flood cannot flood logcat. */
    const val GLOBAL_ACTION_LOG_GAP_MS = 5_000L

    /**
     * Delay before the tamper block overlay is mounted over the Home screen (150ms), so the HOME
     * transition has settled and the shield attaches onto Home instead of onto the page being closed.
     */
    const val OVERLAY_MOUNT_AFTER_HOME_DELAY_MS = 150L

    /** Debounce for repeated notification-shade collapse attempts while a block overlay is showing (1s). */
    const val OVERLAY_SHADE_COLLAPSE_DEBOUNCE_MS = 1_000L

    /**
     * Debounce for HOME dismissals of a Settings screen that is streaming window events while a block
     * shield is up (1,000ms). Settings fires state/content changes continuously, so an unpaced
     * dismissal is a global-action burst rather than one navigation.
     */
    const val SETTINGS_DISMISS_DEBOUNCE_MS = 1_000L

    /**
     * Length of the OEM "security scan" window (3,500ms).
     *
     * Vivo blanks ENABLED_ACCESSIBILITY_SERVICES for 2-5 s when Settings opens, and performing a
     * global action inside that window trips its anti-tapjacking watchdog. The window exists only
     * to keep the *response* safe; it must never decide whether an attempt is noticed at all
     * (see [com.focusguard.app.service.utils.TamperResponsePolicy]).
     *
     * It used to be 10-12 s, armed on every service connect — and entering Accessibility Settings
     * is exactly what causes a connect, so the attacker got a fresh blindfold with each attempt.
     */
    const val OEM_SCAN_SUPPRESS_WINDOW_MS = 3_500L

    /**
     * Length of the SPECULATIVE window armed by a service connect (1,500ms).
     *
     * A connect is not evidence of a scan: it happens on every boot, every app update and after
     * every programmatic heal. Only a connect that lands on a Settings/OEM screen, or that looks
     * like Vivo's own force-unbind/rebind, plausibly sits mid-scan — see
     * [com.focusguard.app.service.utils.OemScanWindowPolicy]. The real fingerprints (the setting
     * observed blank, an OEM security package in the foreground) still arm the full
     * [OEM_SCAN_SUPPRESS_WINDOW_MS] above.
     */
    const val POST_CONNECT_OEM_SCAN_WINDOW_MS = 1_500L

    /** Accessibility state observer health check cooldown — the window the observer arms. */
    const val A11Y_OBSERVER_COOLDOWN_MS = OEM_SCAN_SUPPRESS_WINDOW_MS

    /**
     * Minimum gap between two accessibility self-protection contests (3,000ms).
     *
     * The details-page check re-fires on every TYPE_WINDOW_CONTENT_CHANGED of the Accessibility
     * list (~4 per second while it scrolls). The OEM-window response no longer navigates away, so
     * the user stays on that screen and every one of those events would be a Toast and a haptic.
     */
    const val SELF_A11Y_CONTEST_DEBOUNCE_MS = 3_000L

    /**
     * Batching window for accessibility event delivery (70ms).
     * The OS coalesces events within this window before handing them to the service.
     * 10ms produced effectively no batching (up to ~100 deliveries/sec on content changes),
     * which forced a window-tree traversal on almost every emitted frame. 70ms is still far
     * below the threshold where click / long-press interception feels delayed.
     * Keep in sync with res/xml/accessibility_service_config.xml.
     */
    const val A11Y_EVENT_BATCH_WINDOW_MS = 70L

    /**
     * Minimum spacing between two expensive window-tree scans of the same package (150ms).
     * TYPE_WINDOW_CONTENT_CHANGED can fire dozens of times per second inside one scrolling
     * feed, and every scan performs dozens of focus / view-ID lookups. Sampling keeps detection
     * latency imperceptible (typing still arrives through TYPE_VIEW_TEXT_CHANGED, which is never
     * sampled). Measured on Vivo V2355 with a deterministic 50 events/s flood: ~54% -> ~37% of
     * one core for the app process.
     */
    const val A11Y_WINDOW_SCAN_COALESCE_MS = 150L

    /** Heartbeat interval for time tamper monitor (30,000ms / 30s) */
    const val TAMPER_CHECK_INTERVAL_MS = 30_000L

    /** Standard one-minute interval (60,000ms) */
    const val ONE_MINUTE_MS = 60_000L

    /** Standard one-second interval (1,000ms) */
    const val ONE_SECOND_MS = 1000L

    /** Standard one-day interval (86,400,000ms) — timed uninstall lock granularity */
    const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * Largest uptime step the monotonic ledger may credit when its chain is broken
     * (device reboot, or a wall-clock/NTP epoch change). One real reboot costs at least this
     * much wall time, so capping the step here makes the ledger impossible to accelerate.
     */
    const val UPTIME_LEDGER_MAX_STEP_MS = ONE_MINUTE_MS

    /** Minimum uptime gap between two monotonic ledger persists (avoids hot-path storage writes) */
    const val UPTIME_LEDGER_PERSIST_STEP_MS = ONE_MINUTE_MS

    /**
     * Delay between attempts of the pre-login commitment lookup after a cold start (3,000ms).
     * Five attempts hold the first-run gate for ~15s — long enough to survive a slow network, short
     * enough that a genuinely offline fresh install is not bricked.
     */
    const val COMMITMENT_RESTORE_RETRY_DELAY_MS = 3_000L

    /** Maximum pre-login commitment lookup attempts while the first-run gate is closed. */
    const val COMMITMENT_RESTORE_MAX_ATTEMPTS = 5

    /**
     * Hard ceiling on the first-run commitment gate (20,000ms). The lookup is best-effort: if the
     * engine never starts (DI failure) or never resolves, the user must still reach their own app.
     * Releasing the gate does not release protection — a late restore still re-arms the lock.
     */
    const val COMMITMENT_GATE_HARD_TIMEOUT_MS = 20_000L

    /**
     * How long this process must survive after the system uninstaller opens before the privilege
     * teardown that preceded it is treated as a cancellation (45,000ms).
     *
     * A completed uninstall kills the process when the package is removed, so the check never runs
     * and nothing is re-armed. The window only has to outlast a user who reads the system dialog
     * slowly and then presses Cancel, while staying short enough that a cancelled uninstall does
     * not leave the app defenceless for minutes.
     */
    const val UNINSTALL_CANCEL_GRACE_MS = 45_000L
}

