package com.focusguard.app.service.handlers

import com.focusguard.app.core.common.constants.OemPackages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemScanDetectorTest {

    private val detector = OemScanDetector()

    @Test
    fun isOemSecurityPackage_returnsTrue_forAllEightOemSecurityPackages() {
        assertTrue(detector.isOemSecurityPackage(OemPackages.VIVO_IMANAGER))
        assertTrue(detector.isOemSecurityPackage(OemPackages.VIVO_SAFE_CENTER))
        assertTrue(detector.isOemSecurityPackage(OemPackages.VIVO_PERMISSION_MANAGER))
        assertTrue(detector.isOemSecurityPackage(OemPackages.VIVO_ABE))
        assertTrue(detector.isOemSecurityPackage(OemPackages.IQOO_SECURE))
        assertTrue(detector.isOemSecurityPackage("com.oneplus.security"))
        assertTrue(detector.isOemSecurityPackage("com.miui.securityadd"))
        assertTrue(detector.isOemSecurityPackage("com.huawei.systemmanager"))
    }

    @Test
    fun isOemSecurityPackage_returnsFalse_forNormalPackage() {
        assertFalse(detector.isOemSecurityPackage("com.instagram.android"))
        assertFalse(detector.isOemSecurityPackage("com.google.android.youtube"))
    }

    @Test
    fun isOemSecurityPackage_returnsFalse_forKeyboardPackage() {
        assertFalse(detector.isOemSecurityPackage("com.google.android.inputmethod.latin"))
        assertFalse(detector.isOemSecurityPackage("com.touchtype.swiftkey"))
    }
}

