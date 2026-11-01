package com.focusguard.app.service.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PackageClassifierTest
 * =====================
 * Unit tests for [PackageClassifier] verifying keyboard, system/launcher,
 * settings, and installer package detection logic.
 */
class PackageClassifierTest {

    @Test
    fun testBbkLauncher2CountsAsSystemOrLauncher() {
        // com.bbk.launcher2 is Vivo / iQOO stock launcher
        assertTrue(
            "com.bbk.launcher2 must count as system-or-launcher",
            PackageClassifier.isSystemOrLauncherPackage("com.bbk.launcher2")
        )
    }

    @Test
    fun testGboardIsKeyboardPackage() {
        // Gboard Latin IME
        assertTrue(
            "com.google.android.inputmethod.latin must be classified as a keyboard package",
            PackageClassifier.isKeyboardPackage("com.google.android.inputmethod.latin")
        )
    }

    @Test
    fun testAndroidSettingsIsSettingsPackage() {
        // AOSP standard settings
        assertTrue(
            "com.android.settings must be classified as a settings package",
            PackageClassifier.isSettingsPackage("com.android.settings")
        )
    }

    @Test
    fun testPackageInstallerAndVendingAreInstallerPackages() {
        // Package installer
        assertTrue(
            "com.google.android.packageinstaller must be an installer package",
            PackageClassifier.isInstallerPackage("com.google.android.packageinstaller", "")
        )
        // Google Play Store
        assertTrue(
            "com.android.vending must be an installer package",
            PackageClassifier.isInstallerPackage("com.android.vending", "")
        )
    }

    @Test
    fun testYouTubeIsNotSystemNorKeyboardNorSettings() {
        val ytPkg = "com.google.android.youtube"
        assertFalse(
            "YouTube must NOT be classified as system or launcher",
            PackageClassifier.isSystemOrLauncherPackage(ytPkg)
        )
        assertFalse(
            "YouTube must NOT be classified as a keyboard package",
            PackageClassifier.isKeyboardPackage(ytPkg)
        )
        assertFalse(
            "YouTube must NOT be classified as a settings package",
            PackageClassifier.isSettingsPackage(ytPkg)
        )
    }
}
