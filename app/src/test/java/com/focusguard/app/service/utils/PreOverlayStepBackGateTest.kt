package com.focusguard.app.service.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PreOverlayStepBackGateTest
 * ==========================
 * Unit tests for [ServiceWindowActions.shouldStepBackBeforeOverlay].
 *
 * The pre-overlay BACK is what stops an app from resuming the blocked surface
 * (YouTube Short / Instagram Reels / Facebook Reels) after a block. It must never
 * reach a system package: BACK inside Settings opens another system page and
 * re-triggers blocking in a loop.
 */
class PreOverlayStepBackGateTest {

    @Test
    fun thirdPartyAppReceivesPreOverlayBack() {
        assertTrue(
            "A regular third-party app must be stepped back before the shield attaches",
            ServiceWindowActions.shouldStepBackBeforeOverlay(
                isSystemPackage = false,
                isLauncherPackage = false
            )
        )
    }

    @Test
    fun systemPackageNeverReceivesPreOverlayBack() {
        assertFalse(
            "System Settings / installer / system UI must never receive BACK",
            ServiceWindowActions.shouldStepBackBeforeOverlay(
                isSystemPackage = true,
                isLauncherPackage = false
            )
        )
    }

    @Test
    fun launcherNeverReceivesPreOverlayBack() {
        assertFalse(
            "The launcher must never receive BACK",
            ServiceWindowActions.shouldStepBackBeforeOverlay(
                isSystemPackage = false,
                isLauncherPackage = true
            )
        )
    }

    @Test
    fun systemLauncherNeverReceivesPreOverlayBack() {
        assertFalse(
            "A system launcher must never receive BACK",
            ServiceWindowActions.shouldStepBackBeforeOverlay(
                isSystemPackage = true,
                isLauncherPackage = true
            )
        )
    }
}
