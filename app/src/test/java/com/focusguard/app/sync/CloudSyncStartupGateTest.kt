package com.focusguard.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncStartupGateTest {

    @Test
    fun unresolvedAuthenticationKeepsSettingsHidden() {
        assertTrue(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = false,
                isSignedIn = false,
                isLocalSettingsReady = true,
                syncStatus = SyncStatus.IDLE
            )
        )
    }

    @Test
    fun signedOutUserDoesNotWaitForCloudSync() {
        assertFalse(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = true,
                isSignedIn = false,
                isLocalSettingsReady = true,
                syncStatus = SyncStatus.IDLE
            )
        )
    }

    @Test
    fun localSettingsMustLoadBeforeShowingControls() {
        assertTrue(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = true,
                isSignedIn = false,
                isLocalSettingsReady = false,
                syncStatus = SyncStatus.IDLE
            )
        )
    }

    @Test
    fun signedInUserWaitsUntilInitialSyncSettles() {
        assertTrue(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = true,
                isSignedIn = true,
                isLocalSettingsReady = true,
                syncStatus = SyncStatus.IDLE
            )
        )
        assertTrue(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = true,
                isSignedIn = true,
                isLocalSettingsReady = true,
                syncStatus = SyncStatus.SYNCING
            )
        )
        assertFalse(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = true,
                isSignedIn = true,
                isLocalSettingsReady = true,
                syncStatus = SyncStatus.SYNCED
            )
        )
        assertFalse(
            CloudSyncStartupGate.shouldWaitForSettings(
                isAuthResolved = true,
                isSignedIn = true,
                isLocalSettingsReady = true,
                syncStatus = SyncStatus.ERROR
            )
        )
    }
}
