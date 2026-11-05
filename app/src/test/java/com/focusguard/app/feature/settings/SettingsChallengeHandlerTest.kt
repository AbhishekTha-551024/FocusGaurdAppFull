package com.focusguard.app.feature.settings

import com.focusguard.app.feature.settings.ui.SettingsChallengeHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsChallengeHandlerTest {

    @Test
    fun removingFrictionDescribesThatUninstallProtectionRemainsActive() {
        val challenge = SettingsChallengeHandler.createRemoveChallengeState(3)

        assertEquals("REMOVE_CHALLENGE", challenge.targetPayload)
        assertEquals("Accountability Challenge", challenge.targetName)
        assertTrue(challenge.description.contains("Friction challenge only"))
        assertTrue(challenge.description.contains("Uninstall Protection remains active with 3 days remaining"))
    }

    @Test
    fun removingFrictionWithoutATimedLockSaysAdminStateIsUnchanged() {
        val challenge = SettingsChallengeHandler.createRemoveChallengeState(0)

        assertTrue(challenge.description.contains("Uninstall Protection and Device Admin remain unchanged"))
    }
}
