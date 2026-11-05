package com.focusguard.app.service.handlers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherHideAppsPolicyTest {

    @Test
    fun `blocks hide apps only for confirmed FocusGuard target`() {
        assertTrue(shouldBlockLauncherHideApps(LauncherTarget.FOCUSGUARD))
    }

    @Test
    fun `does not block hide apps for other app targets`() {
        assertFalse(shouldBlockLauncherHideApps(LauncherTarget.OTHER))
    }

    @Test
    fun `does not block hide apps for unknown targets`() {
        assertFalse(shouldBlockLauncherHideApps(LauncherTarget.UNKNOWN))
    }
}
