package com.focusguard.app.ui.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the shield's cause detection to the exact reason sentences the five callers send. A future
 * edit that renames a reason must fail here instead of silently showing "restart" for a tamper.
 */
class RecoveryCauseTest {

    @Test
    fun `admin deactivation block is admin cause`() {
        assertEquals(
            RecoveryCause.ADMIN_DEACTIVATION_REQUESTED,
            RecoveryCause.fromReason("Admin Deactivation Blocked: FocusGuard Uninstall Protection is active!")
        )
    }

    @Test
    fun `admin loss responder is admin cause`() {
        assertEquals(
            RecoveryCause.ADMIN_REMOVED,
            RecoveryCause.fromReason("Device Admin was removed while Uninstall Protection is active")
        )
    }

    @Test
    fun `clone removal is clone cause`() {
        assertEquals(
            RecoveryCause.CLONE_REMOVED,
            RecoveryCause.fromReason(
                "A cloned instance was removed, which turned off Accessibility. " +
                        "Please turn Accessibility back ON to keep your device protected."
            )
        )
    }

    @Test
    fun `boot receiver source is restart cause`() {
        assertEquals(
            RecoveryCause.RESTART,
            RecoveryCause.fromReason("Accessibility is off (BootReceiver:BOOT_COMPLETED).")
        )
    }

    @Test
    fun `user present source is restart cause`() {
        assertEquals(
            RecoveryCause.RESTART,
            RecoveryCause.fromReason("Accessibility is off (BootReceiver:android.intent.action.USER_PRESENT).")
        )
    }

    @Test
    fun `quickboot source is restart cause`() {
        assertEquals(
            RecoveryCause.RESTART,
            RecoveryCause.fromReason("Device event (android.intent.action.QUICKBOOT_POWERON): Restoring accessibility protection")
        )
    }

    @Test
    fun `observer confirmed off is turned off cause`() {
        assertEquals(
            RecoveryCause.TURNED_OFF,
            RecoveryCause.fromReason("Accessibility is off (AccessibilityObserver:ConfirmedOff).")
        )
    }

    @Test
    fun `tamper source is turned off cause`() {
        assertEquals(
            RecoveryCause.TURNED_OFF,
            RecoveryCause.fromReason("Accessibility is off (AccessibilityObserver:Tamper).")
        )
    }

    @Test
    fun `package replaced is not claimed as restart`() {
        assertEquals(
            RecoveryCause.TURNED_OFF,
            RecoveryCause.fromReason(
                "Device event (android.intent.action.MY_PACKAGE_REPLACED): Restoring accessibility protection"
            )
        )
    }

    @Test
    fun `empty reason falls back to neutral cause`() {
        assertEquals(RecoveryCause.TURNED_OFF, RecoveryCause.fromReason(""))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(RecoveryCause.ADMIN_REMOVED, RecoveryCause.fromReason("DEVICE ADMIN REMOVED"))
        assertEquals(RecoveryCause.CLONE_REMOVED, RecoveryCause.fromReason("CLONED INSTANCE REMOVED"))
    }

    @Test
    fun `only admin cause blocks admin shield behaviour`() {
        assertTrue(RecoveryCause.ADMIN_REMOVED.isAdminBlock)
        assertTrue(RecoveryCause.ADMIN_DEACTIVATION_REQUESTED.isAdminBlock)
        assertTrue(RecoveryCause.ADMIN_REMOVED.requiresAdminReactivation)
        assertFalse(RecoveryCause.ADMIN_DEACTIVATION_REQUESTED.requiresAdminReactivation)
        assertFalse(RecoveryCause.RESTART.isAdminBlock)
        assertFalse(RecoveryCause.CLONE_REMOVED.isAdminBlock)
        assertFalse(RecoveryCause.TURNED_OFF.isAdminBlock)
    }
}
