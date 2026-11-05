package com.focusguard.app.sync

/**
 * Keeps controls hidden until the first local settings snapshot and any initial cloud restore
 * have settled, so controls never animate through stale values at startup.
 */
object CloudSyncStartupGate {
    fun shouldWaitForSettings(
        isAuthResolved: Boolean,
        isSignedIn: Boolean,
        isLocalSettingsReady: Boolean,
        syncStatus: SyncStatus
    ): Boolean {
        if (!isAuthResolved || !isLocalSettingsReady) return true
        if (!isSignedIn) return false
        return syncStatus != SyncStatus.SYNCED && syncStatus != SyncStatus.ERROR
    }
}
