package com.focusguard.app.feature.home.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the visibility rule for recovery alerts:
 *  - ask when Uninstall Protection is armed without notification permission;
 *  - warn for as long as protection is on and alerts cannot reach the user.
 */
class RecoveryAlertsPolicyTest {

    @Test
    fun `prompts when protection is armed and notifications are missing`() {
        assertTrue(
            RecoveryAlertsPolicy.shouldPromptOnArm(
                wasProtectionActive = false,
                isProtectionActive = true,
                notificationsGranted = false
            )
        )
    }

    @Test
    fun `does not prompt for protection that was already active`() {
        assertFalse(
            RecoveryAlertsPolicy.shouldPromptOnArm(
                wasProtectionActive = true,
                isProtectionActive = true,
                notificationsGranted = false
            )
        )
    }

    @Test
    fun `does not prompt when notifications are already allowed`() {
        assertFalse(
            RecoveryAlertsPolicy.shouldPromptOnArm(
                wasProtectionActive = false,
                isProtectionActive = true,
                notificationsGranted = true
            )
        )
    }

    @Test
    fun `does not prompt when protection is switched off`() {
        assertFalse(
            RecoveryAlertsPolicy.shouldPromptOnArm(
                wasProtectionActive = true,
                isProtectionActive = false,
                notificationsGranted = false
            )
        )
    }

    @Test
    fun `warning follows protection and notification state`() {
        assertTrue(RecoveryAlertsPolicy.shouldShowWarning(isProtectionActive = true, notificationsGranted = false))
        assertFalse(RecoveryAlertsPolicy.shouldShowWarning(isProtectionActive = true, notificationsGranted = true))
        assertFalse(RecoveryAlertsPolicy.shouldShowWarning(isProtectionActive = false, notificationsGranted = false))
    }

    @Test
    fun `asks for notifications at arming time only when they are missing`() {
        assertTrue(RecoveryAlertsPolicy.shouldRequestNotificationsOnArm(notificationsGranted = false))
        assertFalse(RecoveryAlertsPolicy.shouldRequestNotificationsOnArm(notificationsGranted = true))
    }

    @Test
    fun `warns before arming only when both visible recovery channels are missing`() {
        assertTrue(
            RecoveryAlertsPolicy.shouldWarnBeforeArming(
                overlayGranted = false,
                notificationsGranted = false
            )
        )
    }

    @Test
    fun `overlay alone keeps arming quiet`() {
        assertFalse(
            RecoveryAlertsPolicy.shouldWarnBeforeArming(
                overlayGranted = true,
                notificationsGranted = false
            )
        )
    }

    @Test
    fun `notifications alone keep arming quiet`() {
        assertFalse(
            RecoveryAlertsPolicy.shouldWarnBeforeArming(
                overlayGranted = false,
                notificationsGranted = true
            )
        )
    }

    @Test
    fun `no warning when both channels are available`() {
        assertFalse(
            RecoveryAlertsPolicy.shouldWarnBeforeArming(
                overlayGranted = true,
                notificationsGranted = true
            )
        )
    }
}
