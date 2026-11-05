package com.focusguard.app.service.utils

import com.focusguard.app.core.common.constants.Timings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the navigation-action ceiling that keeps Vivo's anti-tapjacking watchdog from unbinding
 * the accessibility service, and — just as important — proves the ceiling never refuses a real
 * blocking incident.
 */
class GlobalActionBudgetTest {

    private fun allowed(state: GlobalActionWindow, now: Long, isNavigation: Boolean = true): Boolean =
        GlobalActionBudget.decide(state, now, isNavigation) == GlobalActionVerdict.ALLOW

    @Test
    fun `first navigation action on a fresh process is allowed`() {
        assertTrue(allowed(GlobalActionWindow.EMPTY, now = 1_000L))
    }

    @Test
    fun `non-navigation actions are never budgeted`() {
        val exhausted = GlobalActionWindow(
            windowStartElapsed = 1_000L,
            actionsInWindow = Timings.GLOBAL_ACTION_MAX_PER_WINDOW
        )
        assertEquals(
            GlobalActionVerdict.ALLOW,
            GlobalActionBudget.decide(exhausted, nowElapsed = 1_001L, isNavigationAction = false)
        )
    }

    @Test
    fun `a whole incident is never refused`() {
        // The worst real sequence, sent back to back inside one millisecond:
        // pre-overlay BACK -> BACK -> HOME -> launcher-popup BACK.
        var window = GlobalActionWindow.EMPTY
        var now = 10_000L
        repeat(4) {
            assertEquals(
                "Action #$it of a single incident must reach the framework",
                GlobalActionVerdict.ALLOW,
                GlobalActionBudget.decide(window, now, isNavigationAction = true)
            )
            window = GlobalActionBudget.advance(window, now)
            now += 1L
        }
    }

    @Test
    fun `a burst is cut at the ceiling`() {
        var window = GlobalActionWindow.EMPTY
        var allowedCount = 0
        // 50 events in the same window = the Settings-shield flood this gate exists to stop.
        repeat(50) {
            if (allowed(window, now = 1_000L, isNavigation = true)) {
                allowedCount++
                window = GlobalActionBudget.advance(window, 1_000L)
            }
        }
        assertEquals(Timings.GLOBAL_ACTION_MAX_PER_WINDOW, allowedCount)
    }

    @Test
    fun `refusal does not spend the budget`() {
        val window = GlobalActionWindow(
            windowStartElapsed = 1_000L,
            actionsInWindow = Timings.GLOBAL_ACTION_MAX_PER_WINDOW
        )
        assertEquals(GlobalActionVerdict.DENY_BUDGET, GlobalActionBudget.decide(window, 1_500L, true))
        assertEquals(
            "A refused request must leave the stored state untouched",
            Timings.GLOBAL_ACTION_MAX_PER_WINDOW,
            window.actionsInWindow
        )
    }

    @Test
    fun `window rolls exactly at its length and refills`() {
        val full = GlobalActionWindow(
            windowStartElapsed = 1_000L,
            actionsInWindow = Timings.GLOBAL_ACTION_MAX_PER_WINDOW
        )
        assertEquals(
            GlobalActionVerdict.DENY_BUDGET,
            GlobalActionBudget.decide(full, 1_000L + Timings.GLOBAL_ACTION_WINDOW_MS - 1L, true)
        )
        assertTrue(
            "One ms past the window the ceiling restarts from zero",
            allowed(full, now = 1_000L + Timings.GLOBAL_ACTION_WINDOW_MS)
        )
    }

    @Test
    fun `advance charges the rolled window not the stale one`() {
        val stale = GlobalActionWindow(
            windowStartElapsed = 1_000L,
            actionsInWindow = Timings.GLOBAL_ACTION_MAX_PER_WINDOW
        )
        val now = 1_000L + Timings.GLOBAL_ACTION_WINDOW_MS + 500L
        val after = GlobalActionBudget.advance(stale, now)
        assertEquals(now, after.windowStartElapsed)
        assertEquals(1, after.actionsInWindow)
    }

    @Test
    fun `monotonic clock going backwards opens a fresh window`() {
        // elapsedRealtime restarts at zero on reboot; a snapshot from the previous boot may not
        // spend this boot's budget, or a reboot would leave the shield permanently unable to navigate.
        val fromBeforeReboot = GlobalActionWindow(windowStartElapsed = 9_000_000L, actionsInWindow = 6)
        assertTrue(
            "Pre-reboot count must not block the first post-boot action",
            allowed(fromBeforeReboot, now = 2_000L)
        )
        val after = GlobalActionBudget.advance(fromBeforeReboot, 2_000L)
        assertEquals(2_000L, after.windowStartElapsed)
        assertEquals(1, after.actionsInWindow)
    }

    @Test
    fun `ceiling is wider than one incident so blocking is never weakened`() {
        assertTrue(
            "An incident costs BACK + HOME plus the pre-overlay and launcher-popup BACKs (4)",
            Timings.GLOBAL_ACTION_MAX_PER_WINDOW > 4
        )
    }

    @Test
    fun `window is shorter than the settings dismiss debounce so a flood still drains`() {
        // The per-path debounces pace each surface; the window must be short enough that a refusal is
        // forgotten before the next legitimate dismissal of the same surface.
        assertTrue(
            Timings.GLOBAL_ACTION_WINDOW_MS <= Timings.SETTINGS_DISMISS_DEBOUNCE_MS * 2
        )
    }
}
