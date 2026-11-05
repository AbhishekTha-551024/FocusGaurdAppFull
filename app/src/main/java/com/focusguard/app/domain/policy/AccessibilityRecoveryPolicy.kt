package com.focusguard.app.domain.policy

/**
 * Pure dismissal rules for the Accessibility recovery gate.
 */
object AccessibilityRecoveryPolicy {

    /**
     * Recovery is complete only after the service is healthy. An Accessibility setting that is
     * enabled before Android binds the service is not enough to dismiss the gate.
     */
    fun canDismissRecovery(
        isAccessibilityHealthy: Boolean,
        isAdminBlock: Boolean,
        requiresAdminReactivation: Boolean = false,
        isDeviceAdminActive: Boolean = false
    ): Boolean {
        if (!isAccessibilityHealthy) return false
        if (requiresAdminReactivation) return isDeviceAdminActive
        return !isAdminBlock
    }
}
