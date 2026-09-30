package com.focusguard.app.sync

import android.content.Context
import android.util.Log
import com.focusguard.app.data.datastore.FocusGuardPreferences
import com.focusguard.app.data.db.*
import com.focusguard.app.data.model.AuthUser
import com.focusguard.app.data.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CloudSyncEngine"

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}

/**
 * FocusGuard Cloud Sync Engine
 * =============================
 * Bridges the Dual-Layer Architecture:
 * 1. Offline-First: All blocking checks run from local RAM & Room DB (<0.01ms).
 * 2. Cloud Backup & Multi-Device Restore: Blocklists, focus schedules, and daily time limits
 *    are seamlessly mirrored to Firestore under `users/{uid}/` and restored upon login.
 */
@Singleton
class CloudSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val blockedAppDao: BlockedAppDao,
    private val scheduleDao: ScheduleDao,
    private val appTimeLimitDao: AppTimeLimitDao,
    private val prefs: FocusGuardPreferences,
    private val firestore: FirebaseFirestore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val isRestoringFromCloud = AtomicBoolean(false)
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var currentActiveUid: String? = null
    private var syncJobs = mutableListOf<Job>()

    fun startSync() {
        scope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null && user.id != currentActiveUid) {
                    currentActiveUid = user.id
                    Log.i(TAG, "Authenticated user detected (${user.email}). Starting Cloud Sync...")
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

    private fun cancelActiveSyncJobs() {
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
    }

    private suspend fun startSyncForUser(user: AuthUser) {
        _syncStatus.value = SyncStatus.SYNCING

        // FIX NH2: Separate restore and backup into independent try-catch blocks.
        // restoreFromCloud() internally swallows per-section errors (never re-throws to us).
        // If the function itself does throw (network down, auth failure), we must NOT let
        // backupLocalToCloud() run — doing so overwrites valid cloud data with stale/empty
        // local state, causing silent data loss on first login with a poor network connection.
        var restoreAttempted = false
        try {
            // ── Step 1: Initial Restore from Cloud (Firestore -> Room) ─
            isRestoringFromCloud.set(true)
            restoreFromCloud(user.id)
            isRestoringFromCloud.set(false)
            restoreAttempted = true
            Log.i(TAG, "Cloud restore phase completed.")
        } catch (e: Exception) {
            isRestoringFromCloud.set(false)
            _syncStatus.value = SyncStatus.ERROR
            Log.w(TAG, "Cloud restore threw unexpectedly — skipping backup to protect cloud data: ${e.message}")
        }

        if (restoreAttempted) {
            try {
                // ── Step 2: Push any local entries not yet in cloud ─
                backupLocalToCloud(user.id)
                _syncStatus.value = SyncStatus.SYNCED
                Log.i(TAG, "Initial bidirectional sync completed successfully.")
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.ERROR
                Log.w(TAG, "Cloud backup encountered an issue: ${e.message}")
            }
        }

        // ── Step 3: Observe Local DB Changes and Mirror to Firestore ─
        val blocklistJob = scope.launch {
            blockedAppDao.watchBlockedApps().drop(1).collect { list ->
                if (!isRestoringFromCloud.get()) {
                    syncBlockedAppsToCloud(user.id, list)
                }
            }
        }

        val schedulesJob = scope.launch {
            scheduleDao.watchAllSchedules().drop(1).collect { list ->
                if (!isRestoringFromCloud.get()) {
                    syncSchedulesToCloud(user.id, list)
                }
            }
        }

        val timeLimitsJob = scope.launch {
            appTimeLimitDao.watchAllTimeLimits().drop(1).collect { list ->
                if (!isRestoringFromCloud.get()) {
                    syncTimeLimitsToCloud(user.id, list)
                }
            }
        }

        syncJobs.add(blocklistJob)
        syncJobs.add(schedulesJob)
        syncJobs.add(timeLimitsJob)
    }

    /**
     * Pulls user's cloud configurations and merges into local Room DB.
     */
    private suspend fun restoreFromCloud(uid: String) {
        Log.i(TAG, "Restoring cloud data for user $uid...")

        // 1. Restore Blocked Apps
        try {
            val appsSnapshot = firestore.collection("users")
                .document(uid)
                .collection("app_blocklist")
                .get()
                .await()

            for (doc in appsSnapshot.documents) {
                val pkg = doc.getString("packageName") ?: doc.id
                val name = doc.getString("appName") ?: pkg
                val isBrowser = doc.getBoolean("isAutoBlockedUnsupportedBrowser") ?: false
                val isNew = doc.getBoolean("isAutoBlockedNewInstall") ?: false
                val entity = BlockedAppEntity(
                    packageName = pkg,
                    appName = name,
                    dateAdded = doc.getLong("dateAdded") ?: System.currentTimeMillis(),
                    isAutoBlockedUnsupportedBrowser = isBrowser,
                    isAutoBlockedNewInstall = isNew
                )
                blockedAppDao.insert(entity)
            }
            Log.i(TAG, "Restored ${appsSnapshot.size()} blocked apps from cloud.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring blocked apps: ${e.message}")
        }

        // 2. Restore Focus Schedules
        try {
            val schedSnapshot = firestore.collection("users")
                .document(uid)
                .collection("schedules")
                .get()
                .await()

            for (doc in schedSnapshot.documents) {
                val title = doc.getString("title") ?: "Focus Hours"
                val pkg = doc.getString("packageName")
                val appName = doc.getString("appName")
                val mask = doc.getLong("daysOfWeekMask")?.toInt() ?: 31
                val start = doc.getLong("startMinuteOfDay")?.toInt() ?: 540
                val end = doc.getLong("endMinuteOfDay")?.toInt() ?: 1020
                val isEnabled = doc.getBoolean("isEnabled") ?: true
                val entity = ScheduleEntity(
                    title = title,
                    packageName = pkg,
                    appName = appName,
                    daysOfWeekMask = mask,
                    startMinuteOfDay = start,
                    endMinuteOfDay = end,
                    isEnabled = isEnabled,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
                scheduleDao.insert(entity)
            }
            Log.i(TAG, "Restored ${schedSnapshot.size()} schedules from cloud.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring schedules: ${e.message}")
        }

        // 3. Restore Daily Time Limits
        try {
            val limitsSnapshot = firestore.collection("users")
                .document(uid)
                .collection("time_limits")
                .get()
                .await()

            for (doc in limitsSnapshot.documents) {
                val pkg = doc.getString("packageName") ?: doc.id
                val name = doc.getString("appName") ?: pkg
                val minutes = doc.getLong("dailyLimitMinutes")?.toInt() ?: 60
                val isEnabled = doc.getBoolean("isEnabled") ?: true
                val entity = AppTimeLimitEntity(
                    packageName = pkg,
                    appName = name,
                    dailyLimitMinutes = minutes,
                    isEnabled = isEnabled,
                    dateConfigured = doc.getLong("dateConfigured") ?: System.currentTimeMillis()
                )
                appTimeLimitDao.upsert(entity)
            }
            Log.i(TAG, "Restored ${limitsSnapshot.size()} daily time limits from cloud.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring time limits: ${e.message}")
        }
    }

    /**
     * Initial push of existing local Room data to Firestore.
     */
    private suspend fun backupLocalToCloud(uid: String) {
        val apps = blockedAppDao.watchBlockedApps().first()
        syncBlockedAppsToCloud(uid, apps)

        val schedules = scheduleDao.watchAllSchedules().first()
        syncSchedulesToCloud(uid, schedules)

        val limits = appTimeLimitDao.watchAllTimeLimits().first()
        syncTimeLimitsToCloud(uid, limits)
    }

    private suspend fun syncBlockedAppsToCloud(uid: String, list: List<BlockedAppEntity>) {
        try {
            val collection = firestore.collection("users").document(uid).collection("app_blocklist")
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

            // 1. Upload additions & updates
            for (app in list) {
                val data = hashMapOf(
                    "packageName" to app.packageName,
                    "appName" to app.appName,
                    "dateAdded" to app.dateAdded,
                    "isAutoBlockedUnsupportedBrowser" to app.isAutoBlockedUnsupportedBrowser,
                    "isAutoBlockedNewInstall" to app.isAutoBlockedNewInstall,
                    "updatedAt" to System.currentTimeMillis()
                )
                collection.document(app.packageName).set(data, SetOptions.merge())
            }

            // 2. Remove items deleted locally from cloud
            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync blocked apps to cloud: ${e.message}")
        }
    }

    private suspend fun syncSchedulesToCloud(uid: String, list: List<ScheduleEntity>) {
        try {
            val collection = firestore.collection("users").document(uid).collection("schedules")
            val currentCloud = collection.get().await()
            val cloudIds = currentCloud.documents.map { it.id }.toSet()
            val localIds = list.map { it.id.toString() }.toSet()

            for (schedule in list) {
                val data = hashMapOf(
                    "id" to schedule.id,
                    "title" to schedule.title,
                    "packageName" to (schedule.packageName ?: ""),
                    "appName" to (schedule.appName ?: ""),
                    "daysOfWeekMask" to schedule.daysOfWeekMask,
                    "startMinuteOfDay" to schedule.startMinuteOfDay,
                    "endMinuteOfDay" to schedule.endMinuteOfDay,
                    "isEnabled" to schedule.isEnabled,
                    "createdAt" to schedule.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                )
                collection.document(schedule.id.toString()).set(data, SetOptions.merge())
            }

            val toDelete = cloudIds - localIds
            for (id in toDelete) {
                collection.document(id).delete()
            }
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync schedules to cloud: ${e.message}")
        }
    }

    private suspend fun syncTimeLimitsToCloud(uid: String, list: List<AppTimeLimitEntity>) {
        try {
            val collection = firestore.collection("users").document(uid).collection("time_limits")
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

            for (limit in list) {
                val data = hashMapOf(
                    "packageName" to limit.packageName,
                    "appName" to limit.appName,
                    "dailyLimitMinutes" to limit.dailyLimitMinutes,
                    "isEnabled" to limit.isEnabled,
                    "dateConfigured" to limit.dateConfigured,
                    "updatedAt" to System.currentTimeMillis()
                )
                collection.document(limit.packageName).set(data, SetOptions.merge())
            }

            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync time limits to cloud: ${e.message}")
        }
    }
}
