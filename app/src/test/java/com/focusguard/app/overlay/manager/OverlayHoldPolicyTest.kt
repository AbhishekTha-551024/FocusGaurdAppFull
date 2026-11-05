package com.focusguard.app.overlay.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHoldPolicyTest {

    private fun release(
        holdPackage: String? = "com.rovio.angrybirdsfriends",
        isWindowStateChanged: Boolean = true,
        foregroundPackage: String = "com.android.launcher3",
        isKeyboard: Boolean = false,
        isSettings: Boolean = false,
        isInstaller: Boolean = false,
        isCloner: Boolean = false,
        isRecents: Boolean = false,
        heldPackageIsActive: Boolean = false
    ) = OverlayHoldPolicy.shouldReleaseForForeground(
        holdPackage = holdPackage,
        isWindowStateChanged = isWindowStateChanged,
        foregroundPackage = foregroundPackage,
        isKeyboard = isKeyboard,
        isSettings = isSettings,
        isInstaller = isInstaller,
        isCloner = isCloner,
        isRecents = isRecents,
        heldPackageIsActive = heldPackageIsActive
    )

    @Test
    fun transientShieldIsNeverReleasedByForeground() {
        assertFalse(release(holdPackage = null))
    }

    @Test
    fun contentEventDoesNotReleaseShield() {
        assertFalse(release(isWindowStateChanged = false))
    }

    @Test
    fun samePackageDoesNotReleaseShield() {
        assertFalse(release(foregroundPackage = "com.rovio.angrybirdsfriends"))
        assertFalse(release(foregroundPackage = "COM.ROVIO.ANGRYBIRDSFRIENDS"))
    }

    @Test
    fun systemOverlayNoiseDoesNotReleaseWhileBlockedAppIsStillActive() {
        assertFalse(
            release(
                foregroundPackage = "com.vivo.card",
                heldPackageIsActive = true
            )
        )
        assertFalse(
            release(
                foregroundPackage = "com.google.android.gms",
                heldPackageIsActive = true
            )
        )
    }

    @Test
    fun launcherHomeReleasesShield() {
        assertTrue(release(foregroundPackage = "com.bbk.launcher2"))
    }

    @Test
    fun otherAppReleasesShield() {
        assertTrue(release(foregroundPackage = "com.instagram.android"))
    }

    @Test
    fun recentsReleasesShield() {
        assertTrue(
            release(
                foregroundPackage = "com.android.systemui",
                isRecents = true
            )
        )
    }

    @Test
    fun notificationShadeOrNavBarDoesNotReleaseShield() {
        assertFalse(
            release(
                foregroundPackage = "com.android.systemui",
                isRecents = false
            )
        )
    }

    @Test
    fun keyboardDoesNotReleaseShield() {
        assertFalse(release(foregroundPackage = "com.google.android.inputmethod.latin", isKeyboard = true))
    }

    @Test
    fun settingsAndInstallerAndClonerKeepProtectionPath() {
        assertFalse(release(foregroundPackage = "com.android.settings", isSettings = true))
        assertFalse(release(foregroundPackage = "com.google.android.packageinstaller", isInstaller = true))
        assertFalse(release(foregroundPackage = "com.parallel.space", isCloner = true))
    }

    @Test
    fun systemDialogDoesNotReleaseShield() {
        assertFalse(release(foregroundPackage = "android"))
    }
}
