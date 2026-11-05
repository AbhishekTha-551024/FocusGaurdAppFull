package com.focusguard.app.service.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassNameMatchersTest {

    @Test
    fun isShadeClass_returnsTrue_forNotificationShadeVariants() {
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.statusbar.phone.NotificationShadeWindowView"))
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.statusbar.phone.NotificationPanelView"))
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.qs.QuickSettings"))
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.shade.NotificationShade"))
    }

    @Test
    fun isShadeClass_returnsTrue_caseInsensitive() {
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.notificationshade"))
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.notificationpanel"))
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.quicksettings"))
        assertTrue(ClassNameMatchers.isShadeClass("com.android.systemui.shade"))
    }

    @Test
    fun isShadeClass_returnsFalse_forNonShadeClasses() {
        assertFalse(ClassNameMatchers.isShadeClass("android.widget.FrameLayout"))
        assertFalse(ClassNameMatchers.isShadeClass("com.android.launcher3.Launcher"))
        assertFalse(ClassNameMatchers.isShadeClass("com.instagram.android.MainActivity"))
    }

    @Test
    fun recentsAndOverview_trueOnlyForIsShadeOrRecentsClass_falseForIsShadeClass() {
        val recentsClass = "com.android.systemui.recents.RecentsActivity"
        val overviewClass = "com.android.quickstep.OverviewCommandHelper"

        // False for isShadeClass
        assertFalse(ClassNameMatchers.isShadeClass(recentsClass))
        assertFalse(ClassNameMatchers.isShadeClass(overviewClass))

        // True for isShadeOrRecentsClass
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass(recentsClass))
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass(overviewClass))

        // Case-insensitivity check for recents and overview
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass("com.android.systemui.recents"))
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass("com.android.systemui.overview"))
    }

    @Test
    fun isShadeOrRecentsClass_returnsTrue_forShadeVariants() {
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass("NotificationShade"))
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass("NotificationPanel"))
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass("QuickSettings"))
        assertTrue(ClassNameMatchers.isShadeOrRecentsClass("Shade"))
    }

    @Test
    fun isShadeOrRecentsClass_returnsFalse_forNonMatchingClasses() {
        assertFalse(ClassNameMatchers.isShadeOrRecentsClass("android.widget.LinearLayout"))
        assertFalse(ClassNameMatchers.isShadeOrRecentsClass("com.android.launcher3.Workspace"))
    }

    @Test
    fun isSettingsOrA11yPackage_returnsTrue_forEachOfSixSubstrings() {
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.android.settings"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.vivo.imanager"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.vivo.safecenter"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.android.permissionmanager"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.google.android.permissioncontroller"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.miui.securitycenter"))
    }

    @Test
    fun isSettingsOrA11yPackage_returnsTrue_caseInsensitive() {
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("COM.ANDROID.SETTINGS"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("Com.Vivo.IMANAGER"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("COM.VIVO.SAFECENTER"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.android.PERMISSIONMANAGER"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.google.android.PERMISSIONCONTROLLER"))
        assertTrue(ClassNameMatchers.isSettingsOrA11yPackage("com.miui.SECURITYCENTER"))
    }

    @Test
    fun isSettingsOrA11yPackage_returnsFalse_forNormalPackages() {
        assertFalse(ClassNameMatchers.isSettingsOrA11yPackage("com.instagram.android"))
        assertFalse(ClassNameMatchers.isSettingsOrA11yPackage("com.google.android.youtube"))
        assertFalse(ClassNameMatchers.isSettingsOrA11yPackage("com.whatsapp"))
        assertFalse(ClassNameMatchers.isSettingsOrA11yPackage("com.focusguard.app"))
    }
}

