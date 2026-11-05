package com.focusguard.app.service.runtime

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.utils.ClassNameMatchers
import com.focusguard.app.utils.SafeNodeUtils

private const val TAG = "FocusGuardService"

/**
 * Delayed reconnect sweep: feeds the currently open window through the guard chain once,
 * three seconds after connect, so an app that was already open before the service started
 * still gets evaluated.
 *
 * Phase 5 split: former `sweepActiveWindowOnConnect` body, moved verbatim.
 */
internal class ActiveWindowSweep(
    private val service: FocusGuardAccessibilityService,
    private val runtime: ServiceRuntime
) {
    fun sweepActiveWindowOnConnect() {
        try {
            val root = service.rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: ""

            // ── CRITICAL FIX: Never sweep Settings/Accessibility screens on reconnect ──
            // When Vivo force-unbinds our service (while the user is on Accessibility Settings)
            // and then rebinds us, rootInActiveWindow still points to the Accessibility Settings
            // screen. Sweeping it immediately calls processAccessibilityEvent() which fires
            // handleSecurityTamper() -> GLOBAL_ACTION_HOME -> Vivo watchdog unbinds us again.
            // This was the infinite disconnect loop.
            val isSettingsOrA11y = ClassNameMatchers.isSettingsOrA11yPackage(pkg)

            if (pkg.isBlank() || pkg == service.packageName || pkg.startsWith("com.focusguard.app") || isSettingsOrA11y) {
                SafeNodeUtils.safeRecycle(root)
                Log.i(TAG, "sweepActiveWindowOnConnect: SKIPPED settings/a11y screen on reconnect (pkg=$pkg) -- avoids false tamper block")
                return
            }
            Log.i(TAG, "sweepActiveWindowOnConnect: active window snapshot pkg=$pkg")
            val dummyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
                    packageName = pkg
                    className = root.className?.toString() ?: ""
                }
            } else {
                @Suppress("DEPRECATION")
                AccessibilityEvent.obtain().apply {
                    eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    packageName = pkg
                    className = root.className?.toString() ?: ""
                }
            }
            runtime.router.process(dummyEvent)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                dummyEvent.recycle()
            }
            SafeNodeUtils.safeRecycle(root)
        } catch (t: Throwable) {
            Log.w(TAG, "Error in sweepActiveWindowOnConnect", t)
        }
    }
}
