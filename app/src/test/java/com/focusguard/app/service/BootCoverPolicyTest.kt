package com.focusguard.app.service

import com.focusguard.app.recovery.AutoRecoveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCoverPolicyTest {

    @Test
    fun `fallback delay is exactly 35 seconds`() {
        assertEquals(35_000L, BootCoverPolicy.FALLBACK_DELAY_MS)
    }

    @Test
    fun `maximum cover duration is bounded to two and a half minutes`() {
        assertEquals(150_000L, BootCoverPolicy.MAX_COVER_DURATION_MS)
    }

    @Test
    fun `shouldStartService needs an unhealthy shield and overlay permission`() {
        assertTrue(
            "Shield not healthy and permission granted -> start allowed",
            BootCoverPolicy.shouldStartService(isProtectionReady = false, canDrawOverlays = true)
        )
        assertFalse(
            "Shield already healthy -> should skip start",
            BootCoverPolicy.shouldStartService(isProtectionReady = true, canDrawOverlays = true)
        )
        assertFalse(
            "Overlay permission missing -> should skip start",
            BootCoverPolicy.shouldStartService(isProtectionReady = false, canDrawOverlays = false)
        )
        assertFalse(
            "Both healthy and permission missing -> should skip start",
            BootCoverPolicy.shouldStartService(isProtectionReady = true, canDrawOverlays = false)
        )
    }

    @Test
    fun `shouldAbortMount aborts only when the shield is verifiably back`() {
        assertTrue(
            "Pre-mount race: abort when connected and rules are ready",
            BootCoverPolicy.shouldAbortMount(isProtectionReady = true)
        )
        assertFalse(
            "Pre-mount: proceed while the binding is not confirmed",
            BootCoverPolicy.shouldAbortMount(isProtectionReady = false)
        )
    }

    @Test
    fun `shouldRemoveAfterMount removes view when the shield came back during attachment`() {
        assertTrue(
            "Post-mount race: remove view when shield is healthy",
            BootCoverPolicy.shouldRemoveAfterMount(isProtectionReady = true)
        )
        assertFalse(
            "Post-mount: keep view while the binding is unconfirmed",
            BootCoverPolicy.shouldRemoveAfterMount(isProtectionReady = false)
        )
    }

    @Test
    fun `shouldTriggerFallback triggers only when the shield is down and the overlay is attached`() {
        assertTrue(
            "Fallback trigger: shield not healthy and overlay attached",
            BootCoverPolicy.shouldTriggerFallback(isProtectionReady = false, isOverlayAttached = true)
        )
        assertFalse(
            "Do not fallback if the shield is already healthy",
            BootCoverPolicy.shouldTriggerFallback(isProtectionReady = true, isOverlayAttached = true)
        )
        assertFalse(
            "Do not fallback if overlay is no longer attached",
            BootCoverPolicy.shouldTriggerFallback(isProtectionReady = false, isOverlayAttached = false)
        )
        assertFalse(
            "Do not fallback if shield healthy and overlay detached",
            BootCoverPolicy.shouldTriggerFallback(isProtectionReady = true, isOverlayAttached = false)
        )
    }

    @Test
    fun `cover releases when the shield is back or when there is nothing left to guard`() {
        assertTrue(
            "Startup readiness confirmed -> the cover must go",
            BootCoverPolicy.shouldReleaseCover(isProtectionReady = true, isProtectionActive = true)
        )
        assertTrue(
            "Invariant Rule 3: protection OFF means the cover has no reason to exist",
            BootCoverPolicy.shouldReleaseCover(isProtectionReady = false, isProtectionActive = false)
        )
        assertFalse(
            "Vulnerability gap (protection ON, shield down) must keep the cover mounted",
            BootCoverPolicy.shouldReleaseCover(isProtectionReady = false, isProtectionActive = true)
        )
    }

    @Test
    fun `maximum hold releases the cover only when readiness is incomplete and overlay is attached`() {
        assertTrue(
            BootCoverPolicy.shouldReleaseAfterMaximumDuration(
                isProtectionReady = false,
                isOverlayAttached = true
            )
        )
        assertFalse(
            BootCoverPolicy.shouldReleaseAfterMaximumDuration(
                isProtectionReady = true,
                isOverlayAttached = true
            )
        )
        assertFalse(
            BootCoverPolicy.shouldReleaseAfterMaximumDuration(
                isProtectionReady = false,
                isOverlayAttached = false
            )
        )
    }

    @Test
    fun `heal attempts run only while protection is armed and the boot budget holds`() {
        assertTrue(
            BootCoverPolicy.shouldHealWhileCovered(isProtectionActive = true, attemptsUsed = 0)
        )
        assertTrue(
            "Last allowed attempt sits one below the budget",
            BootCoverPolicy.shouldHealWhileCovered(
                isProtectionActive = true,
                attemptsUsed = BootCoverPolicy.MAX_HEAL_ATTEMPTS - 1
            )
        )
        assertFalse(
            "Budget exhausted -> stop re-writing the setting",
            BootCoverPolicy.shouldHealWhileCovered(
                isProtectionActive = true,
                attemptsUsed = BootCoverPolicy.MAX_HEAL_ATTEMPTS
            )
        )
        assertFalse(
            "Invariant Rule 3: protection OFF never triggers a heal",
            BootCoverPolicy.shouldHealWhileCovered(isProtectionActive = false, attemptsUsed = 0)
        )
    }

    @Test
    fun `sentinel cadence matches the probe cadence so two heal sources never stack`() {
        // The boot cover sentinel stops when the application process takes over (A11yAutoRecoveryProbe).
        // Sharing one interval keeps the cover and the probe from issuing two secure writes back to
        // back, which is what makes Vivo re-bind and un-bind the service in a loop.
        assertEquals(
            AutoRecoveryPolicy.PROTECTED_PROBE_INTERVAL_MS,
            BootCoverSentinel.HEAL_INTERVAL_MS
        )
    }

    @Test
    fun `boot heal budget outlasts the fallback screen delay`() {
        // The last silent attempt must still happen after the UI switched to the manual fallback,
        // otherwise the cover gives up on the shield before its own grace period is over.
        val budgetMs = AutoRecoveryPolicy.PROTECTED_PROBE_INTERVAL_MS * BootCoverPolicy.MAX_HEAL_ATTEMPTS
        assertTrue(budgetMs > BootCoverPolicy.FALLBACK_DELAY_MS)
    }
}
