package com.focusguard.app.service.utils

import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.overlay.manager.OverlayBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision table for the OEM scan window.
 *
 * The rule being protected: a scan window may change HOW FocusGuard responds, never WHETHER an
 * attempt on its own Accessibility Service is acted upon. On Vivo the blanked setting that opens
 * the window is caused by the user entering Settings, so swallowing those attempts handed over
 * exactly the window in which the toggle could be flipped (live repro, Vivo V2355, 22:18:30.065).
 */
class TamperResponsePolicyTest {

    // ── classification ───────────────────────────────────────────────────────────────────

    @Test
    fun isSelfA11yProtectionTamper_whenAccessibilitySettingsShield_returnsTrue() {
        assertTrue(
            TamperResponsePolicy.isSelfA11yProtectionTamper(
                targetName = TamperResponsePolicy.TARGET_ACCESSIBILITY_SETTINGS,
                blockType = OverlayBlockType.SETTINGS_TAMPER
            )
        )
    }

    @Test
    fun isSelfA11yProtectionTamper_whenUninstallAttempt_returnsFalse() {
        assertFalse(
            TamperResponsePolicy.isSelfA11yProtectionTamper(
                targetName = TamperResponsePolicy.TARGET_ACCESSIBILITY_SETTINGS,
                blockType = OverlayBlockType.UNINSTALL_ATTEMPT
            )
        )
    }

    @Test
    fun isSelfA11yProtectionTamper_whenOtherSettingsTarget_returnsFalse() {
        assertFalse(
            TamperResponsePolicy.isSelfA11yProtectionTamper(
                targetName = "Developer Options",
                blockType = OverlayBlockType.SETTINGS_TAMPER
            )
        )
    }

    // ── exemption: unchanged from the historical list ──────────────────────────────────────

    @Test
    fun isExemptFromSuppression_coversEveryHistoricalExemptClass() {
        assertTrue(
            TamperResponsePolicy.isExemptFromSuppression(
                TamperResponsePolicy.TARGET_DEVICE_ADMINISTRATOR,
                OverlayBlockType.SETTINGS_TAMPER,
                "Device Admin Screen targeting FocusGuard"
            )
        )
        assertTrue(
            TamperResponsePolicy.isExemptFromSuppression(
                TamperResponsePolicy.TARGET_HIDE_APPS_PROTECTION,
                OverlayBlockType.SETTINGS_TAMPER,
                "Hide Apps"
            )
        )
        assertTrue(
            TamperResponsePolicy.isExemptFromSuppression(
                "Package Uninstaller",
                OverlayBlockType.UNINSTALL_ATTEMPT,
                "Uninstall attempt"
            )
        )
        assertTrue(
            TamperResponsePolicy.isExemptFromSuppression(
                "Security Settings",
                OverlayBlockType.SETTINGS_TAMPER,
                "unauthorized device admin deactivation"
            )
        )
        assertFalse(
            TamperResponsePolicy.isExemptFromSuppression(
                TamperResponsePolicy.TARGET_ACCESSIBILITY_SETTINGS,
                OverlayBlockType.SETTINGS_TAMPER,
                "Direct Click on FocusGuard in Accessibility Settings"
            )
        )
    }

    // ── the decision table, exhaustively ───────────────────────────────────────────────────

    @Test
    fun decide_outsideScanWindow_alwaysRunsTheFullResponse() {
        for (isExempt in listOf(true, false)) {
            for (isSelfA11y in listOf(true, false)) {
                assertEquals(
                    "window closed, exempt=$isExempt, selfA11y=$isSelfA11y",
                    TamperResponse.FULL_RESPONSE,
                    TamperResponsePolicy.decide(
                        isOemScanWindowActive = false,
                        isExemptFromSuppression = isExempt,
                        isSelfA11yProtection = isSelfA11y
                    )
                )
            }
        }
    }

    @Test
    fun decide_insideScanWindow_exemptAttemptsStillRunTheFullResponse() {
        for (isSelfA11y in listOf(true, false)) {
            assertEquals(
                TamperResponse.FULL_RESPONSE,
                TamperResponsePolicy.decide(
                    isOemScanWindowActive = true,
                    isExemptFromSuppression = true,
                    isSelfA11yProtection = isSelfA11y
                )
            )
        }
    }

    @Test
    fun decide_insideScanWindow_selfA11yAttemptHealsInsteadOfBeingSwallowed() {
        assertEquals(
            TamperResponse.HEAL_AND_COVER,
            TamperResponsePolicy.decide(
                isOemScanWindowActive = true,
                isExemptFromSuppression = false,
                isSelfA11yProtection = true
            )
        )
    }

    @Test
    fun decide_insideScanWindow_otherAttemptsKeepTheLegacySilentBehaviour() {
        assertEquals(
            TamperResponse.SILENT,
            TamperResponsePolicy.decide(
                isOemScanWindowActive = true,
                isExemptFromSuppression = false,
                isSelfA11yProtection = false
            )
        )
    }

    @Test
    fun decide_liveReproScenario_isContestedAndNavigatesNowhere() {
        // 22:18:30.065 on Vivo V2355: click on the FocusGuard row inside Accessibility Settings,
        // 4 seconds after the connect that Vivo's own blanking caused. Previously SILENT.
        val targetName = TamperResponsePolicy.TARGET_ACCESSIBILITY_SETTINGS
        val reason = "Direct Click on FocusGuard in Accessibility Settings"
        val response = TamperResponsePolicy.decide(
            isOemScanWindowActive = true,
            isExemptFromSuppression = TamperResponsePolicy.isExemptFromSuppression(
                targetName,
                OverlayBlockType.SETTINGS_TAMPER,
                reason
            ),
            isSelfA11yProtection = TamperResponsePolicy.isSelfA11yProtectionTamper(
                targetName,
                OverlayBlockType.SETTINGS_TAMPER
            )
        )
        assertEquals(TamperResponse.HEAL_AND_COVER, response)
    }

    // ── pacing of the contest that no longer navigates away ─────────────────────────────────

    /**
     * HEAL_AND_COVER leaves the user sitting on the Accessibility details screen, whose content
     * stream re-detects the same attempt several times per second. The first attempt must always
     * be answered instantly; repeats inside the debounce may not stack Toasts, haptics, secure
     * writes and full-screen gate launches.
     */
    @Test
    fun isSelfA11yContestDue_firstEverAttempt_isImmediate() {
        assertTrue(TamperResponsePolicy.isSelfA11yContestDue(lastContestElapsed = 0L, nowElapsed = 5_000L))
        assertTrue(TamperResponsePolicy.isSelfA11yContestDue(lastContestElapsed = -1L, nowElapsed = 5_000L))
    }

    @Test
    fun isSelfA11yContestDue_repeatInsideDebounce_isPacedOut() {
        val last = 100_000L
        assertFalse(TamperResponsePolicy.isSelfA11yContestDue(last, last))
        assertFalse(
            TamperResponsePolicy.isSelfA11yContestDue(last, last + Timings.SELF_A11Y_CONTEST_DEBOUNCE_MS - 1L)
        )
    }

    @Test
    fun isSelfA11yContestDue_atOrAfterDebounce_isContestedAgain() {
        val last = 100_000L
        assertTrue(TamperResponsePolicy.isSelfA11yContestDue(last, last + Timings.SELF_A11Y_CONTEST_DEBOUNCE_MS))
        assertTrue(TamperResponsePolicy.isSelfA11yContestDue(last, last + Timings.SELF_A11Y_CONTEST_DEBOUNCE_MS + 1L))
    }

    @Test
    fun isSelfA11yContestDue_clockRewind_doesNotSilenceTheGuardForever() {
        // elapsedRealtime is monotonic, so this should not happen; if a value from a future stamp
        // ever reaches the guard, the safe answer is to contest rather than to pace forever.
        assertTrue(TamperResponsePolicy.isSelfA11yContestDue(lastContestElapsed = 200_000L, nowElapsed = 10L))
    }

    @Test
    fun isSelfA11yContestDue_contentStreamFlood_answersOncePerDebounceWindow() {
        // Accessibility list content changes: one event every 250 ms for ten seconds, i.e. 40
        // re-detections of the same on-screen attempt. Without pacing that was 40 Toasts, haptics
        // and heal writes.
        val events = (0 until 40).map { 1_000_000L + it * 250L }
        var lastContest = 0L
        var contests = 0
        for (now in events) {
            if (TamperResponsePolicy.isSelfA11yContestDue(lastContest, now)) {
                contests++
                lastContest = now
            }
        }
        // Contests at t0, t0+3s, t0+6s and t0+9s.
        assertEquals(4, contests)
    }
}
