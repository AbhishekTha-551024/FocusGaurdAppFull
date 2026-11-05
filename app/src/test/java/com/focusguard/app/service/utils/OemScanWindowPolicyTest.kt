package com.focusguard.app.service.utils

import com.focusguard.app.core.common.constants.Timings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a service connect is allowed to open the OEM scan blindfold.
 *
 * The blindfold silences every non-self tamper class while it is open, so an arm that is not
 * backed by evidence of a scan is pure cost. The case that matters most: the connect right after a
 * reboot lands on the launcher, not on Settings, and used to hand the attacker a blindfold exactly
 * when the boot recovery is running.
 */
class OemScanWindowPolicyTest {

    @Test
    fun shouldArmOnConnect_unknownForeground_keepsTheHistoricalHedge() {
        // The framework may not have a window yet at connect -- Vivo leaves us rebound over its
        // own Settings screen in precisely that state, so "unknown" must arm.
        assertTrue(OemScanWindowPolicy.shouldArmOnConnect(foregroundPackage = null, isUnexpectedRestart = false))
        assertTrue(OemScanWindowPolicy.shouldArmOnConnect(foregroundPackage = "", isUnexpectedRestart = false))
        assertTrue(OemScanWindowPolicy.shouldArmOnConnect(foregroundPackage = "   ", isUnexpectedRestart = false))
    }

    @Test
    fun shouldArmOnConnect_oemForcedRebind_armsEvenAwayFromSettings() {
        assertTrue(
            OemScanWindowPolicy.shouldArmOnConnect(
                foregroundPackage = "com.android.launcher3",
                isUnexpectedRestart = true
            )
        )
    }

    @Test
    fun shouldArmOnConnect_settingsAndOemScreens_arm() {
        for (pkg in listOf(
            "com.android.settings",
            "com.android.settings.SubSettings",
            "com.vivo.imanager",
            "com.vivo.permissionmanager",
            "com.vivo.safecenter",
            "com.miui.securitycenter",
            "com.google.android.permissioncontroller"
        )) {
            assertTrue(
                "connect over $pkg may be mid-scan",
                OemScanWindowPolicy.shouldArmOnConnect(pkg, isUnexpectedRestart = false)
            )
        }
    }

    @Test
    fun shouldArmOnConnect_cleanConnectOnANonSettingsScreen_doesNotBlindTheGuards() {
        for (pkg in listOf(
            "com.android.launcher3",
            "com.bbk.launcher2",
            "com.google.android.apps.nexuslauncher",
            "com.focusguard.app",          // BootCover overlay / our own screen
            "com.google.android.youtube",
            "com.android.systemui"
        )) {
            assertFalse(
                "connect over $pkg is not evidence of an OEM scan",
                OemScanWindowPolicy.shouldArmOnConnect(pkg, isUnexpectedRestart = false)
            )
        }
    }

    @Test
    fun postConnectWindow_isShorterThanTheEvidenceBasedWindow() {
        // Evidence-based arms (GUARD 1.15a, AccessibilityStateObserver) keep the full scanner
        // window; the speculative post-connect arm must be the shorter one it replaced.
        assertTrue(Timings.POST_CONNECT_OEM_SCAN_WINDOW_MS < Timings.OEM_SCAN_SUPPRESS_WINDOW_MS)
        assertTrue(Timings.A11Y_OBSERVER_COOLDOWN_MS == Timings.OEM_SCAN_SUPPRESS_WINDOW_MS)
    }

    // ── arming arithmetic: an arm may extend, never shorten ──────────────────────────────────

    @Test
    fun extendedUntilElapsed_noWindowOpen_armsTheRequestedLength() {
        assertEquals(
            10_000L + Timings.POST_CONNECT_OEM_SCAN_WINDOW_MS,
            OemScanWindowPolicy.extendedUntilElapsed(
                nowElapsed = 10_000L,
                currentUntilElapsed = 0L,
                windowMs = Timings.POST_CONNECT_OEM_SCAN_WINDOW_MS
            )
        )
    }

    @Test
    fun extendedUntilElapsed_shortArmOverAnEvidenceBasedArm_keepsTheLongerDeadline() {
        // The rebind caused by the observer's heal lands over the Settings screen seconds after the
        // observer armed the full window. The speculative 1,500 ms arm must not cut that down --
        // HOME is still dangerous for as long as the scan evidence said it was.
        val observerArmedAt = 100_000L
        val observerUntil = observerArmedAt + Timings.OEM_SCAN_SUPPRESS_WINDOW_MS
        val connectAt = observerArmedAt + 1_200L

        val until = OemScanWindowPolicy.extendedUntilElapsed(
            nowElapsed = connectAt,
            currentUntilElapsed = observerUntil,
            windowMs = Timings.POST_CONNECT_OEM_SCAN_WINDOW_MS
        )
        assertEquals(observerUntil, until)
    }

    @Test
    fun extendedUntilElapsed_laterEvidence_extendsTheWindow() {
        val expiredLongAgo = 50_000L
        val now = 200_000L
        assertEquals(
            now + Timings.OEM_SCAN_SUPPRESS_WINDOW_MS,
            OemScanWindowPolicy.extendedUntilElapsed(
                nowElapsed = now,
                currentUntilElapsed = expiredLongAgo,
                windowMs = Timings.OEM_SCAN_SUPPRESS_WINDOW_MS
            )
        )
    }
}
