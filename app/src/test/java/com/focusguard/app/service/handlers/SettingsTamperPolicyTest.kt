package com.focusguard.app.service.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTamperPolicyTest {

    @Test
    fun `search screens are identified without inspecting or logging query text`() {
        assertTrue(SettingsTamperPolicy.isSettingsSearchScreen("SettingsSearchFragment", false))
        assertTrue(SettingsTamperPolicy.isSettingsSearchScreen("android.view.ViewGroup", true))
        assertFalse(SettingsTamperPolicy.isSettingsSearchScreen("SettingsDashboard", false))
    }

    @Test
    fun `only clicked destructive labels are classified`() {
        assertEquals(
            SettingsDestructiveAction.UNINSTALL,
            SettingsTamperPolicy.classifyClickedAction(true, "Uninstall", false)
        )
        assertEquals(
            SettingsDestructiveAction.NONE,
            SettingsTamperPolicy.classifyClickedAction(false, "Uninstall", false)
        )
        assertEquals(
            SettingsDestructiveAction.NONE,
            SettingsTamperPolicy.classifyClickedAction(true, "Search settings", false)
        )
        assertEquals(
            SettingsDestructiveAction.DISABLE,
            SettingsTamperPolicy.classifyClickedAction(true, "Deactivate", false)
        )
        assertEquals(
            SettingsDestructiveAction.FORCE_STOP,
            SettingsTamperPolicy.classifyClickedAction(true, "Force stop", false)
        )
        assertEquals(
            SettingsDestructiveAction.CLEAR_DATA,
            SettingsTamperPolicy.classifyClickedAction(true, "Clear storage", false)
        )
        assertEquals(
            SettingsDestructiveAction.DISABLE,
            SettingsTamperPolicy.classifyClickedAction(true, "On", true)
        )
    }

    @Test
    fun `accessibility protection requires a FocusGuard disable action`() {
        assertTrue(
            SettingsTamperPolicy.shouldBlockAccessibilityDisable(
                isAccessibilityScreen = true,
                targetsFocusGuard = true,
                action = SettingsDestructiveAction.DISABLE
            )
        )
        assertFalse(
            SettingsTamperPolicy.shouldBlockAccessibilityDisable(
                isAccessibilityScreen = true,
                targetsFocusGuard = true,
                action = SettingsDestructiveAction.NONE
            )
        )
        assertFalse(
            SettingsTamperPolicy.shouldBlockAccessibilityDisable(
                isAccessibilityScreen = true,
                targetsFocusGuard = false,
                action = SettingsDestructiveAction.DISABLE
            )
        )
    }

    @Test
    fun `device admin protection requires a FocusGuard deactivation action`() {
        assertTrue(
            SettingsTamperPolicy.shouldBlockDeviceAdminDisable(
                isDeviceAdminScreen = true,
                targetsFocusGuard = true,
                action = SettingsDestructiveAction.DISABLE
            )
        )
        assertFalse(
            SettingsTamperPolicy.shouldBlockDeviceAdminDisable(
                isDeviceAdminScreen = false,
                targetsFocusGuard = true,
                action = SettingsDestructiveAction.DISABLE
            )
        )
        assertFalse(
            SettingsTamperPolicy.shouldBlockDeviceAdminDisable(
                isDeviceAdminScreen = true,
                targetsFocusGuard = true,
                action = SettingsDestructiveAction.NONE
            )
        )
    }

    @Test
    fun `app info is allowed until a destructive action targets FocusGuard`() {
        assertFalse(
            SettingsTamperPolicy.shouldBlockFocusGuardAppAction(
                isFocusGuardAppDetailsPage = true,
                action = SettingsDestructiveAction.NONE
            )
        )
        assertTrue(
            SettingsTamperPolicy.shouldBlockFocusGuardAppAction(
                isFocusGuardAppDetailsPage = true,
                action = SettingsDestructiveAction.CLEAR_DATA
            )
        )
        assertFalse(
            SettingsTamperPolicy.shouldBlockFocusGuardAppAction(
                isFocusGuardAppDetailsPage = false,
                action = SettingsDestructiveAction.UNINSTALL
            )
        )
    }
}
