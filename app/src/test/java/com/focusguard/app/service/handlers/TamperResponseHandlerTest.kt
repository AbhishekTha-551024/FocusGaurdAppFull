package com.focusguard.app.service.handlers

import com.focusguard.app.overlay.manager.OverlayBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperResponseHandlerTest {

    @Test
    fun isExemptFromSuppression_whenDeviceAdminTarget_returnsTrue() {
        val result = TamperResponseHandler.isExemptFromSuppression(
            targetName = "Device Administrator",
            blockType = OverlayBlockType.SETTINGS_TAMPER,
            reason = "Accessibility tamper"
        )
        assertTrue(result)
    }

    @Test
    fun isExemptFromSuppression_whenHideAppsProtectionTarget_returnsTrue() {
        val result = TamperResponseHandler.isExemptFromSuppression(
            targetName = "Hide Apps Protection",
            blockType = OverlayBlockType.SETTINGS_TAMPER,
            reason = "Security tamper"
        )
        assertTrue(result)
    }

    @Test
    fun isExemptFromSuppression_whenUninstallAttemptBlockType_returnsTrue() {
        val result = TamperResponseHandler.isExemptFromSuppression(
            targetName = "Package Uninstaller",
            blockType = OverlayBlockType.UNINSTALL_ATTEMPT,
            reason = "Uninstall attempt"
        )
        assertTrue(result)
    }

    @Test
    fun isExemptFromSuppression_whenReasonContainsDeviceAdminCaseInsensitive_returnsTrue() {
        val result1 = TamperResponseHandler.isExemptFromSuppression(
            targetName = "Custom Settings",
            blockType = OverlayBlockType.SETTINGS_TAMPER,
            reason = "Unauthorized Device Admin deactivation"
        )
        assertTrue(result1)

        val result2 = TamperResponseHandler.isExemptFromSuppression(
            targetName = "Custom Settings",
            blockType = OverlayBlockType.SETTINGS_TAMPER,
            reason = "device admin revoked"
        )
        assertTrue(result2)
    }

    @Test
    fun isExemptFromSuppression_whenNormalSettingTamper_returnsFalse() {
        val result = TamperResponseHandler.isExemptFromSuppression(
            targetName = "Developer Options",
            blockType = OverlayBlockType.SETTINGS_TAMPER,
            reason = "USB debugging tamper"
        )
        assertFalse(result)
    }

    @Test
    fun buildToastMessage_whenCustomMessageNull_returnsDefaultWithTargetName() {
        val msg = TamperResponseHandler.buildToastMessage(
            targetName = "Developer Options",
            customToastMessage = null
        )
        assertEquals(
            "🚫 Developer Options is locked by FocusGuard. Complete the friction challenge in the app to modify.",
            msg
        )
    }

    @Test
    fun buildToastMessage_whenCustomMessageProvided_returnsCustomMessage() {
        val custom = "🚫 App cloning and dual apps are prohibited while FocusGuard protection is active."
        val msg = TamperResponseHandler.buildToastMessage(
            targetName = "App Cloning Prohibited",
            customToastMessage = custom
        )
        assertEquals(custom, msg)
    }
}
