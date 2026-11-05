package com.focusguard.app.domain.policy

import com.focusguard.app.domain.model.ProtectionState

data class ProtectionReadiness(
    val isOnboardingComplete: Boolean,
    val hasConfiguredProtection: Boolean,
    val isAccessibilityEnabled: Boolean,
    val isAccessibilityServiceConnected: Boolean,
    val uninstallProtectionEnabled: Boolean,
    val isDeviceAdminActive: Boolean
)

object ProtectionStatePolicy {
    fun resolve(readiness: ProtectionReadiness): ProtectionState = when {
        !readiness.isOnboardingComplete -> ProtectionState.UNINITIALIZED
        !readiness.hasConfiguredProtection -> ProtectionState.DISABLED
        !readiness.isAccessibilityEnabled -> ProtectionState.RECOVERY_REQUIRED
        !readiness.isAccessibilityServiceConnected -> ProtectionState.RECOVERING
        readiness.uninstallProtectionEnabled && !readiness.isDeviceAdminActive ->
            ProtectionState.DEGRADED
        else -> ProtectionState.PROTECTED
    }
}
