package com.focusguard.app.service.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WindowScanCoalescerTest
 * ==========================
 * Verifies the sampling gate that keeps TYPE_WINDOW_CONTENT_CHANGED from running the
 * expensive window-tree scan on every emitted frame.
 *
 * Required properties:
 * - the first event after a package switch is ALWAYS processed,
 * - repeated events from the same package inside the window are dropped,
 * - the window boundary re-opens processing,
 * - a different package is never blocked by the previous package's sample.
 */
class WindowScanCoalescerTest {

    private val windowMs = 150L

    @Test
    fun firstEventIsProcessed() {
        val coalescer = WindowScanCoalescer(windowMs)
        assertTrue(
            "The first content change of a package must always be scanned",
            coalescer.shouldProcess("com.instagram.android", 1_000L)
        )
    }

    @Test
    fun repeatedEventInsideWindowIsDropped() {
        val coalescer = WindowScanCoalescer(windowMs)
        assertTrue(coalescer.shouldProcess("com.instagram.android", 1_000L))
        assertFalse(
            "A second content change 149ms later is redundant",
            coalescer.shouldProcess("com.instagram.android", 1_149L)
        )
        assertFalse(
            "Still inside the window",
            coalescer.shouldProcess("com.instagram.android", 1_150L - 1L)
        )
    }

    @Test
    fun windowBoundaryReopensProcessing() {
        val coalescer = WindowScanCoalescer(windowMs)
        assertTrue(coalescer.shouldProcess("com.google.android.youtube", 1_000L))
        assertTrue(
            "At exactly the window length the sample must run again",
            coalescer.shouldProcess("com.google.android.youtube", 1_150L)
        )
    }

    @Test
    fun packageSwitchIsNeverDropped() {
        val coalescer = WindowScanCoalescer(windowMs)
        assertTrue(coalescer.shouldProcess("com.instagram.android", 1_000L))
        assertTrue(
            "A new package must be scanned immediately even inside the previous window",
            coalescer.shouldProcess("com.google.android.chrome", 1_010L)
        )
    }

    @Test
    fun resetAllowsImmediateProcessing() {
        val coalescer = WindowScanCoalescer(windowMs)
        assertTrue(coalescer.shouldProcess("com.instagram.android", 1_000L))
        coalescer.reset()
        assertTrue(
            "After a service reconnect / reset the next event must not be delayed",
            coalescer.shouldProcess("com.instagram.android", 1_001L)
        )
    }

    @Test
    fun droppingDoesNotSlideTheWindow() {
        val coalescer = WindowScanCoalescer(windowMs)
        assertTrue(coalescer.shouldProcess("com.instagram.android", 1_000L))
        // Dropped samples must not extend the deadline: 1_150 is still the open point.
        assertFalse(coalescer.shouldProcess("com.instagram.android", 1_050L))
        assertFalse(coalescer.shouldProcess("com.instagram.android", 1_100L))
        assertTrue(coalescer.shouldProcess("com.instagram.android", 1_150L))
    }
}
