package com.focusguard.app.service.utils

import com.focusguard.app.core.common.constants.Timings

/**
 * WindowScanCoalescer
 * ===================
 * Samples the expensive window-tree scan of [TYPE_WINDOW_CONTENT_CHANGED] events to at most
 * one per [windowMs] for the current foreground package.
 *
 * Why: a live feed (YouTube, Instagram, Chrome) emits dozens of content-change events per
 * second. Each processed event runs the full inspection pipeline, whose extractors perform
 * dozens of view-ID lookups in the window tree. On the Vivo V2355 test device, a deterministic
 * 50 events/s flood cost the app ~54% of one core without sampling and ~37% with it.
 *
 * Safety rules encoded here:
 * - A switch to a different package is always processed (a new screen must never be skipped).
 * - Only content-change events pass through this gate. Window transitions, clicks, long-presses
 *   and text changes are handled outside it, so tap interception and typing stay instant, and a
 *   reel/short player is still detected within one sample.
 *
 * Not thread-safe by design: it is only mutated on the accessibility service thread.
 */
class WindowScanCoalescer(
    private val windowMs: Long = Timings.A11Y_WINDOW_SCAN_COALESCE_MS
) {

    private var lastPackage: String? = null
    private var lastProcessedAt: Long = 0L

    /**
     * Returns true when this event should be processed.
     * Records the sample when it returns true, so the next call inside the window is skipped.
     *
     * @param pkg foreground package of the event
     * @param nowRealtime monotonic timestamp (SystemClock.elapsedRealtime)
     */
    fun shouldProcess(pkg: String, nowRealtime: Long): Boolean {
        if (pkg == lastPackage && nowRealtime - lastProcessedAt < windowMs) {
            return false
        }
        lastPackage = pkg
        lastProcessedAt = nowRealtime
        return true
    }

    /** Forgets the last sample (service reconnect, package reset). */
    fun reset() {
        lastPackage = null
        lastProcessedAt = 0L
    }
}
