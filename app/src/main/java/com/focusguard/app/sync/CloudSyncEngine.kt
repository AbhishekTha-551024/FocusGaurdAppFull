package com.focusguard.app.sync

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}

/**
 * CloudSyncEngine
 * ===============
 * Facade for FocusGuard Cloud Backup & Multi-Device Sync.
 * Delegates all lifecycle, synchronization, and snapshot handling to [CloudSyncCoordinator].
 */
@Singleton
class CloudSyncEngine @Inject constructor(
    private val coordinator: CloudSyncCoordinator
) {
    val syncStatus: StateFlow<SyncStatus>
        get() = coordinator.syncStatus

    fun startSync() {
        coordinator.startSync()
    }

    suspend fun syncNow(): Result<Unit> {
        return coordinator.syncNow()
    }
}
