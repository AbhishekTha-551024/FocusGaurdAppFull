package com.focusguard.app.service.utils

import com.focusguard.app.core.common.constants.Timings

/**
 * Outcome of asking the navigation-action budget for one more HOME/BACK.
 *
 * A refusal is not "the attempt was ignored": the block shield, the heal paths and the plain
 * `startActivity(HOME intent)` fallback all still run. Only the accessibility navigation gesture is
 * withheld, and only while a burst is in progress.
 */
enum class GlobalActionVerdict {
    ALLOW,
    DENY_BUDGET
}

/**
 * Monotonic snapshot of the current navigation-action window. All values are
 * `SystemClock.elapsedRealtime()` readings (AGENTS invariant 4) — never wall clock, which the user
 * can move in Settings > Date & time.
 */
data class GlobalActionWindow(
    val windowStartElapsed: Long,
    val actionsInWindow: Int
) {
    companion object {
        /** Nothing sent since this process bound. */
        val EMPTY = GlobalActionWindow(windowStartElapsed = 0L, actionsInWindow = 0)
    }
}

/**
 * GlobalActionBudget
 * ==================
 * Pure rolling-window ceiling for the global accessibility actions that Vivo's anti-tapjacking
 * watchdog punishes. The watchdog does not distinguish "useful" HOME from "flooded" HOME, and its
 * response — unbinding FocusGuard — costs more protection than any single blocked attempt is worth.
 *
 * There is deliberately **no minimum gap between two actions**: the legitimate response to one
 * incident is `BACK` then `HOME` issued synchronously in the same block, and any gap would refuse the
 * second half of that pair. The ceiling is therefore counted per window, sized above a whole
 * incident (2-3 actions), so real blocking is never weakened — only repeated re-detection of the same
 * surface (Settings streaming window events under a shield, shade pull-down spam, launcher popup
 * re-entry) gets cut.
 *
 * Pure Kotlin, no android.* imports (AGENTS Rule 4), so the whole rule is unit-testable without a
 * device. State is owned by [GlobalActionGate]; a denied request must never advance the state.
 */
object GlobalActionBudget {

    fun decide(
        state: GlobalActionWindow,
        nowElapsed: Long,
        isNavigationAction: Boolean
    ): GlobalActionVerdict {
        if (!isNavigationAction) return GlobalActionVerdict.ALLOW
        val window = rollWindow(state, nowElapsed)
        return if (window.actionsInWindow >= Timings.GLOBAL_ACTION_MAX_PER_WINDOW) {
            GlobalActionVerdict.DENY_BUDGET
        } else {
            GlobalActionVerdict.ALLOW
        }
    }

    /**
     * State to hold after an ALLOW. Re-rolls first so the caller may pass the pre-decision snapshot:
     * the action being counted belongs to the window it is charged against.
     */
    fun advance(state: GlobalActionWindow, nowElapsed: Long): GlobalActionWindow =
        rollWindow(state, nowElapsed).let { it.copy(actionsInWindow = it.actionsInWindow + 1) }

    /**
     * Opens a fresh window when none exists, when the window has expired, or when [nowElapsed] went
     * backwards — elapsedRealtime restarts at zero on reboot, and a snapshot from before the reboot
     * must not spend the new boot's budget.
     */
    private fun rollWindow(state: GlobalActionWindow, nowElapsed: Long): GlobalActionWindow {
        val isExpired = state.windowStartElapsed <= 0L ||
                nowElapsed < state.windowStartElapsed ||
                nowElapsed - state.windowStartElapsed >= Timings.GLOBAL_ACTION_WINDOW_MS
        return if (isExpired) {
            state.copy(windowStartElapsed = nowElapsed, actionsInWindow = 0)
        } else {
            state
        }
    }
}
