package com.focusguard.app.service.utils

/**
 * OemScanWindowPolicy
 * ===================
 * When the OEM "security scan" blindfold may be opened, and by what evidence.
 *
 * The blindfold is a cost, not a benefit: while it is open, every tamper class except the
 * self-protection ones is answered with [TamperResponse.SILENT] (see
 * [TamperResponsePolicy.decide]). Its only job is to keep a response from issuing a global
 * accessibility action while Vivo's anti-tapjacking watchdog is listening.
 *
 * Two kinds of arm exist:
 *
 * - EVIDENCE-BASED. GUARD 1.15a (an OEM security package owns the foreground and the secure
 *   setting was just observed blank) and [com.focusguard.app.observer.AccessibilityStateObserver]
 *   (the setting changed under us) are the scanner's own fingerprints, so they arm the full
 *   [com.focusguard.app.core.common.constants.Timings.OEM_SCAN_SUPPRESS_WINDOW_MS].
 * - SPECULATIVE. "The service just connected" is not evidence of a scan. A connect also happens on
 *   every boot, every app update and after every programmatic heal — and each of those used to
 *   blind the guards for the full window, the boot connect included. Only a connect that lands on
 *   a Settings/OEM screen, or that looks like Vivo's own force-unbind/rebind, is plausibly
 *   mid-scan, and it arms the shorter
 *   [com.focusguard.app.core.common.constants.Timings.POST_CONNECT_OEM_SCAN_WINDOW_MS].
 *
 * No android.* imports (AGENTS Rule 4) so the rule is unit-testable without a device.
 */
object OemScanWindowPolicy {

    /**
     * True when a service connect may open the blindfold.
     *
     * [foregroundPackage] is the window the OS reports as active at connect time; null or blank
     * means the framework had no window yet, which is exactly the state Vivo leaves us in when it
     * rebinds us over its own Settings screen — so an unknown foreground keeps the historical
     * hedge and arms. [isUnexpectedRestart] is the service's own "rebound within 45 s of the last
     * live event" signal, i.e. an OEM-forced unbind rather than a deliberate enable.
     */
    fun shouldArmOnConnect(foregroundPackage: String?, isUnexpectedRestart: Boolean): Boolean {
        if (foregroundPackage.isNullOrBlank()) return true
        if (isUnexpectedRestart) return true
        return ClassNameMatchers.isSettingsOrA11yPackage(foregroundPackage)
    }

    /**
     * Monotonic window arithmetic: an arm may extend the blindfold, never shorten one already open.
     *
     * The three arms are independent observers of the same OEM scan, so the widest evidence wins.
     * Without this, the shorter speculative post-connect arm would cut the full evidence-based arm
     * that [com.focusguard.app.observer.AccessibilityStateObserver] opened seconds earlier — the
     * rebind it causes lands exactly when the observer has just seen the setting blanked.
     *
     * All three values are `SystemClock.elapsedRealtime()` based (AGENTS invariant 4).
     */
    fun extendedUntilElapsed(nowElapsed: Long, currentUntilElapsed: Long, windowMs: Long): Long =
        maxOf(currentUntilElapsed, nowElapsed + windowMs)
}
