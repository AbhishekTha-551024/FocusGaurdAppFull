package com.focusguard.app.blockers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the narrow blocklist call-surface exemption.
 *
 * The real defect these guard against: the Deep Focus allowlist was reused to gate the
 * user's blocklist, so any package containing "phone" (com.phonepe.app) and every foreground
 * app during a call were silently exempt from blocking.
 */
class SessionBlockManagerCallSurfaceTest {

    @Test
    fun testDialerAndInCallUiAreCallSurfaces() {
        assertTrue(SessionBlockManager.isCallSurfaceName("com.android.dialer"))
        assertTrue(SessionBlockManager.isCallSurfaceName("com.google.android.dialer"))
        assertTrue(SessionBlockManager.isCallSurfaceName("com.android.incallui"))
        assertTrue(SessionBlockManager.isCallSurfaceName("com.android.phone"))
    }

    @Test
    fun testCallerIdAppsAreCallSurfaces() {
        assertTrue(SessionBlockManager.isCallSurfaceName("com.truecaller"))
        assertTrue(SessionBlockManager.isCallSurfaceName("gogolook.callgogolook2"))
    }

    @Test
    fun testPhonePeIsBlockable() {
        assertFalse(
            "com.phonepe.app must not be exempt just because the package name contains 'phone'",
            SessionBlockManager.isCallSurfaceName("com.phonepe.app")
        )
    }

    @Test
    fun testDistractionAppsAreBlockable() {
        assertFalse(SessionBlockManager.isCallSurfaceName("com.instagram.android"))
        assertFalse(SessionBlockManager.isCallSurfaceName("com.google.android.youtube"))
        assertFalse(SessionBlockManager.isCallSurfaceName("com.whatsapp"))
    }

    @Test
    fun testSelfAndSystemUiAreExempt() {
        assertTrue(SessionBlockManager.isCallSurfaceName("com.focusguard.app"))
        assertTrue(SessionBlockManager.isCallSurfaceName("com.android.systemui"))
    }

    @Test
    fun testCommunicationModeDoesNotAllowUnrelatedAppsInDeepFocus() {
        assertFalse(
            SessionAllowlist.isAlwaysAllowedPackage(
                pkg = "com.instagram.android",
                selfPackage = "com.focusguard.app",
                defaultDialerPackage = null,
                defaultSmsPackage = null,
                voipCallInProgress = true
            )
        )
        assertTrue(
            SessionAllowlist.isAlwaysAllowedPackage(
                pkg = "com.whatsapp",
                selfPackage = "com.focusguard.app",
                defaultDialerPackage = null,
                defaultSmsPackage = null,
                voipCallInProgress = true
            )
        )
    }
}
