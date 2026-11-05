package com.focusguard.app.service.utils

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.common.constants.Timings
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Service"

/**
 * GlobalActionGate
 * ================
 * The single choke point for every global accessibility action this app sends.
 *
 * [AccessibilityService.performGlobalAction] is final, so the gate cannot sit in front of it by
 * override — [com.focusguard.app.service.FocusGuardAccessibilityService] exposes
 * `sendGlobalAction(action)` and every guard, overlay and handler routes through it. Adding a new
 * navigation path means calling that method, never the framework one.
 *
 * Why capping is safe for protection: every path that sends HOME also brings the launcher forward
 * with a plain `startActivity(HOME intent)` (an Activity start is not an accessibility global action,
 * so the OEM watchdog ignores it) — [com.focusguard.app.service.utils.ServiceWindowActions.forceCloseApp],
 * [com.focusguard.app.service.handlers.TamperResponseHandler],
 * [com.focusguard.app.overlay.manager.BlockOverlayManager]. A denied HOME therefore still leaves the
 * user off the dangerous surface; what it removes is the burst that gets FocusGuard unbound.
 *
 * Non-navigation actions ([AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_BUTTON],
 * …_PANEL, …_LOCK_SCREEN, …) are never capped: the watchdog does not punish them and no guard
 * depends on them today.
 */
@Singleton
class GlobalActionGate @Inject constructor() {

    private val lock = Any()

    private var window: GlobalActionWindow = GlobalActionWindow.EMPTY

    private var lastDenialLogElapsed: Long = 0L

    /** Navigation actions actually handed to the framework, since this instance was created. */
    @Volatile
    var sentCount: Int = 0
        private set

    /** Navigation actions the budget refused. */
    @Volatile
    var deniedCount: Int = 0
        private set

    /**
     * Monotonic stamp of the last navigation action really handed to the framework, or 0 while this
     * instance has sent none. Diagnostics ask the gate instead of keeping their own copy: the gate is
     * the only place that knows whether a HOME/BACK left the process.
     */
    @Volatile
    var lastNavigationSentElapsed: Long = 0L
        private set

    /**
     * Sends [action] through [send] when the budget allows it.
     *
     * @return the framework's result, or false when the action was refused.
     */
    fun dispatch(action: Int, send: (Int) -> Boolean): Boolean {
        val isNavigation = isNavigationAction(action)
        val now = SystemClock.elapsedRealtime()

        val verdict = synchronized(lock) {
            val decision = GlobalActionBudget.decide(window, now, isNavigation)
            if (decision == GlobalActionVerdict.ALLOW) window = GlobalActionBudget.advance(window, now)
            decision
        }

        if (verdict == GlobalActionVerdict.ALLOW) {
            val sent = send(action)
            if (sent && isNavigation) {
                sentCount++
                lastNavigationSentElapsed = now
            }
            return sent
        }

        if (isNavigation) deniedCount++
        logDenial(action, verdict, now)
        return false
    }

    private fun logDenial(action: Int, verdict: GlobalActionVerdict, now: Long) {
        if (now - lastDenialLogElapsed < Timings.GLOBAL_ACTION_LOG_GAP_MS) return
        lastDenialLogElapsed = now
        Log.w(
            TAG,
            "Navigation action ${describe(action)} refused ($verdict) — " +
                "sent=$sentCount denied=$deniedCount since bind. OEM tapjacking watchdog stays calm."
        )
    }

    private fun describe(action: Int): String = when (action) {
        AccessibilityService.GLOBAL_ACTION_HOME -> "HOME"
        AccessibilityService.GLOBAL_ACTION_BACK -> "BACK"
        AccessibilityService.GLOBAL_ACTION_RECENTS -> "RECENTS"
        else -> "action#$action"
    }

    companion object {
        /** The three actions that navigate the whole task stack, and that Vivo's watchdog counts. */
        fun isNavigationAction(action: Int): Boolean =
            action == AccessibilityService.GLOBAL_ACTION_HOME ||
                    action == AccessibilityService.GLOBAL_ACTION_BACK ||
                    action == AccessibilityService.GLOBAL_ACTION_RECENTS
    }
}
