package com.focusguard.app.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRecoveryPolicyTest {

    @Test
    fun `enabled but unbound service does not complete recovery`() {
        assertFalse(
            AccessibilityRecoveryPolicy.canDismissRecovery(
                isAccessibilityHealthy = false,
                isAdminBlock = false
            )
        )
    }

    @Test
    fun `healthy service completes accessibility recovery`() {
        assertTrue(
            AccessibilityRecoveryPolicy.canDismissRecovery(
                isAccessibilityHealthy = true,
                isAdminBlock = false
            )
        )
    }

    @Test
    fun `admin recovery gate does not dismiss when accessibility is healthy`() {
        assertFalse(
            AccessibilityRecoveryPolicy.canDismissRecovery(
                isAccessibilityHealthy = true,
                isAdminBlock = true
            )
        )
    }

    @Test
    fun `admin removal recovery waits until admin is reactivated`() {
        assertFalse(
            AccessibilityRecoveryPolicy.canDismissRecovery(
                isAccessibilityHealthy = true,
                isAdminBlock = true,
                requiresAdminReactivation = true,
                isDeviceAdminActive = false
            )
        )
        assertTrue(
            AccessibilityRecoveryPolicy.canDismissRecovery(
                isAccessibilityHealthy = true,
                isAdminBlock = true,
                requiresAdminReactivation = true,
                isDeviceAdminActive = true
            )
        )
    }
}
