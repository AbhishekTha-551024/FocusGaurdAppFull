package com.focusguard.app.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSurfacePolicyTest {

    @Test
    fun selectedDefaultDialerIsAlwaysAUsableCallSurface() {
        assertTrue(
            CallSurfacePolicy.isCriticalCallSurface(
                packageName = "com.oem.contacts",
                defaultDialerPackage = "com.oem.contacts",
                callInProgress = false
            )
        )
    }

    @Test
    fun knownVoipAppIsExemptOnlyDuringAnActiveCall() {
        assertTrue(
            CallSurfacePolicy.isCriticalCallSurface(
                packageName = "com.whatsapp",
                defaultDialerPackage = null,
                callInProgress = true
            )
        )
        assertFalse(
            CallSurfacePolicy.isCriticalCallSurface(
                packageName = "com.whatsapp",
                defaultDialerPackage = null,
                callInProgress = false
            )
        )
    }

    @Test
    fun commonVoipAppsAreExemptOnlyDuringAnActiveCall() {
        val commonVoipApps = listOf(
            "com.google.android.apps.meetings",
            "com.facebook.orca",
            "jp.naver.line.android",
            "com.tencent.mm"
        )

        commonVoipApps.forEach { packageName ->
            assertTrue(
                "$packageName should be allowed while a call is active",
                CallSurfacePolicy.isCriticalCallSurface(
                    packageName = packageName,
                    defaultDialerPackage = null,
                    callInProgress = true
                )
            )
            assertFalse(
                "$packageName should remain blockable outside a call",
                CallSurfacePolicy.isCriticalCallSurface(
                    packageName = packageName,
                    defaultDialerPackage = null,
                    callInProgress = false
                )
            )
        }
    }

    @Test
    fun communicationModeDoesNotExemptUnrelatedApps() {
        assertFalse(
            CallSurfacePolicy.isAlwaysAllowedDuringSession(
                packageName = "com.instagram.android",
                selfPackage = "com.focusguard.app",
                defaultDialerPackage = null,
                defaultSmsPackage = null,
                voipCallInProgress = true
            )
        )
    }
}
