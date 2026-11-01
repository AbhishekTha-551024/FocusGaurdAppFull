package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CloudSyncCoordinator"

@Singleton
class CloudSyncCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val appBlockSync: AppBlockSync,
    private val keywordSync: KeywordSync,
    private val mindfulSync: MindfulSync,
    private val scheduleSync: ScheduleSync,
    private val timeLimitSync: TimeLimitSync,
    private val accountabilitySync: AccountabilitySync
) {
    private val syncExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in CloudSync scope: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + syncExceptionHandler)

    private val isRestoringFromCloud = AtomicBoolean(false)
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var currentActiveUid: String? = null
    private val syncJobs = CopyOnWriteArrayList<Job>()

    fun startSync() {
        scope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null && user.id != currentActiveUid) {
                    currentActiveUid = user.id
                    Log.i(TAG, "Authenticated user detected. Starting Cloud Sync...")
                    cancelActiveSyncJobs()
                    startSyncForUser(user)
                } else if (user == null) {
                    currentActiveUid = null
                    cancelActiveSyncJobs()
                    _syncStatus.value = SyncStatus.IDLE
                    Log.i(TAG, "No user logged in. Cloud Sync paused.")
                }
            }
        }
    }

    suspend fun syncNow(): Result<Unit> {
        val user = authRepository.syncCurrentUser ?: return Result.failure(IllegalStateException("No authenticated user"))
        return try {
            cancelActiveSyncJobs()
            startSyncForUser(user)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cancelActiveSyncJobs() {
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
    }

    private suspend fun startSyncForUser(user: AuthUser) {
        _syncStatus.value = SyncStatus.SYNCING

        val restoreSuccess: Boolean = try {
            // Step 1: Initial Restore from Cloud (Firestore -> Room & Prefs)
            isRestoringFromCloud.set(true)
            val success = restoreFromCloud(user.id)
            isRestoringFromCloud.set(false)
            if (success) {
                Log.i(TAG, "Cloud restore phase completed successfully across all 6 sections.")
            } else {
                Log.w(TAG, "Cloud restore encountered errors in one or more sections — aborting backup and observers to protect cloud data.")
            }
            success
        } catch (e: Exception) {
            isRestoringFromCloud.set(false)
            Log.w(TAG, "Cloud restore threw unexpectedly — skipping backup to protect cloud data: ${e.message}")
            false
        }

        if (!restoreSuccess) {
            _syncStatus.value = SyncStatus.ERROR
            Log.w(TAG, "Aborting cloud sync: cloud restore did not fully succeed.")
            return
        }

        try {
            // Step 2: Push any local entries not yet in cloud
            backupLocalToCloud(user.id)
            _syncStatus.value = SyncStatus.SYNCED
            Log.i(TAG, "Initial bidirectional sync completed successfully.")
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.ERROR
            Log.w(TAG, "Cloud backup encountered an issue: ${e.message}")
        }

        // Step 3: Observe Local DB Changes and Mirror to Firestore
        val blocklistJob = appBlockSync.watchAndSync(scope, user.id, { isRestoringFromCloud.get() }) {
            _syncStatus.value = SyncStatus.SYNCED
        }
        val keywordsJob = keywordSync.watchAndSync(scope, user.id, { isRestoringFromCloud.get() }) {
            _syncStatus.value = SyncStatus.SYNCED
        }
        val mindfulJob = mindfulSync.watchAndSync(scope, user.id, { isRestoringFromCloud.get() }) {
            _syncStatus.value = SyncStatus.SYNCED
        }
        val schedulesJob = scheduleSync.watchAndSync(scope, user.id, { isRestoringFromCloud.get() }) {
            _syncStatus.value = SyncStatus.SYNCED
        }
        val timeLimitsJob = timeLimitSync.watchAndSync(scope, user.id, { isRestoringFromCloud.get() }) {
            _syncStatus.value = SyncStatus.SYNCED
        }
        val accountabilityJob = accountabilitySync.watchAndSync(scope, user.id, { isRestoringFromCloud.get() }) {
            _syncStatus.value = SyncStatus.SYNCED
        }

        syncJobs.add(blocklistJob)
        syncJobs.add(keywordsJob)
        syncJobs.add(mindfulJob)
        syncJobs.add(schedulesJob)
        syncJobs.add(timeLimitsJob)
        syncJobs.add(accountabilityJob)
    }

    private suspend fun restoreFromCloud(uid: String): Boolean {
        Log.i(TAG, "Restoring cloud data...")
        val r1 = appBlockSync.restore(uid)
        val r2 = keywordSync.restore(uid)
        val r3 = mindfulSync.restore(uid)
        val r4 = scheduleSync.restore(uid)
        val r5 = timeLimitSync.restore(uid)
        val r6 = accountabilitySync.restore(uid)
        return r1 && r2 && r3 && r4 && r5 && r6
    }

    private suspend fun backupLocalToCloud(uid: String) {
        appBlockSync.backup(uid)
        keywordSync.backup(uid)
        mindfulSync.backup(uid)
        scheduleSync.backup(uid)
        timeLimitSync.backup(uid)
        accountabilitySync.backup(uid)
    }
}

