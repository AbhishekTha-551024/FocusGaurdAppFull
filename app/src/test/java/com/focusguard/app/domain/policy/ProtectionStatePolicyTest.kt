package com.focusguard.app.domain.policy

import com.focusguard.app.domain.model.ProtectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionStatePolicyTest {
    @Test
    fun incompleteSetupIsUninitialized() {
        assertEquals(
            ProtectionState.UNINITIALIZED,
            ProtectionStatePolicy.resolve(readiness(isOnboardingComplete = false))
        )
    }

    @Test
    fun noConfiguredFeaturesIsDisabled() {
        assertEquals(
            ProtectionState.DISABLED,
            ProtectionStatePolicy.resolve(readiness(hasConfiguredProtection = false))
        )
    }

    @Test
    fun accessibilityOffRequiresRecovery() {
        assertEquals(
            ProtectionState.RECOVERY_REQUIRED,
            ProtectionStatePolicy.resolve(readiness(isAccessibilityEnabled = false))
        )
    }

    @Test
    fun enabledButUnconnectedServiceIsRecoveringNotProtected() {
        assertEquals(
            ProtectionState.RECOVERING,
            ProtectionStatePolicy.resolve(readiness(isAccessibilityServiceConnected = false))
        )
    }

    @Test
    fun missingAdminDegradesOnlyRequestedUninstallProtection() {
        assertEquals(
            ProtectionState.DEGRADED,
            ProtectionStatePolicy.resolve(
                readiness(uninstallProtectionEnabled = true, isDeviceAdminActive = false)
            )
        )
        assertEquals(
            ProtectionState.PROTECTED,
            ProtectionStatePolicy.resolve(
                readiness(uninstallProtectionEnabled = false, isDeviceAdminActive = false)
            )
        )
    }

    @Test
    fun connectedServiceWithRequestedAdminIsProtected() {
        assertEquals(
            ProtectionState.PROTECTED,
            ProtectionStatePolicy.resolve(readiness())
        )
    }

    private fun readiness(
        isOnboardingComplete: Boolean = true,
        hasConfiguredProtection: Boolean = true,
        isAccessibilityEnabled: Boolean = true,
        isAccessibilityServiceConnected: Boolean = true,
        uninstallProtectionEnabled: Boolean = true,
        isDeviceAdminActive: Boolean = true
    ) = ProtectionReadiness(
        isOnboardingComplete = isOnboardingComplete,
        hasConfiguredProtection = hasConfiguredProtection,
        isAccessibilityEnabled = isAccessibilityEnabled,
        isAccessibilityServiceConnected = isAccessibilityServiceConnected,
        uninstallProtectionEnabled = uninstallProtectionEnabled,
        isDeviceAdminActive = isDeviceAdminActive
    )
}
