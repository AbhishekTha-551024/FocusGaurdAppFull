/**
 * =====================================================================
 * FILE: CloudSyncEngine.kt
 * PACKAGE: com.focusguard.app.sync
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file FocusGuard application ka "Cloud Backup & Multi-Device Sync Engine" hai.
 * FocusGuard ek Offline-First application hai jahan saara security evaluation local RAM
 * aur Room Database se 0ms me hota hai. Ye engine local data aur Firebase Firestore
 * (`users/{uid}/`) ke beech bridge ka kaam karta hai taaki jab user kisi doosre phone
 * me login kare ya device switch kare, toh uska poora configuration bina loss ke restore ho sake.
 *
 * 🏗️ ARCHITECTURE & SYNC LIFECYCLE:
 * 1. Auth-Driven Lifecycle:
 *    - `authRepository.currentUser` Flow ko listen karta hai.
 *    - User login hote hi background bidirectional sync start hota hai.
 *    - User logout hote hi active coroutines cancel hokar state IDLE ho jati hai.
 * 2. Step 1: Restore From Cloud:
 *    - Firestore collections (`app_blocklist`, `blocked_keywords`, `mindful_apps`,
 *      `schedules`, `time_limits`, `accountability/settings`) se data fetch karke
 *      local Room tables aur DataStore preferences me merge karta hai.
 *    - Is dauran `isRestoringFromCloud = true` flag rehta hai taaki local insertions
 *      wapas cloud par echo (infinite loop) na banayein.
 * 3. Step 2: Push Local Additions:
 *    - Jo entries local me pehle se thein aur cloud me nahi thein, unhe Firestore me push karta hai.
 * 4. Step 3: Real-Time Local Observer Jobs:
 *    - Room DAOs ke Flow (`watchBlockedApps()`, `watchBlockedKeywords()`, etc.) ko observe karta hai
 *      aur local DB changes ko automatically Firestore me sync karta hai.
 *
 * 🔒 EXCLUDED BY DESIGN (Privacy & Local Security):
 * - Device Admin PIN, Uninstall Protection Duration, Custom Overlay Wallpaper path,
 *   aur Active Deep Focus sessions ko cloud me sync NAHI kiya jata; ye device-specific security
 *   measures hain jo local phone par hi rehte hain.
 * =====================================================================
 */

package com.focusguard.app.sync

import android.content.Context
import android.util.Log
import com.focusguard.app.core.datastore.DEFAULT_COMMITMENT_SENTENCE
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.database.dao.*
import com.focusguard.app.core.database.entity.*
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CopyOnWriteArrayList
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

private data class AccountabilityConfig(
    val isChallengeActive: Boolean,
    val challengeType: ChallengeType,
    val commitmentSentence: String,
    val timeDelayHours: Int
)

@Singleton
class CloudSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val blockedAppDao: BlockedAppDao,
    private val blockedKeywordDao: BlockedKeywordDao,
    private val mindfulDao: MindfulDao,
    private val scheduleDao: ScheduleDao,
    private val appTimeLimitDao: AppTimeLimitDao,
    private val prefs: FocusGuardPreferences,
    private val firestore: FirebaseFirestore
) {
    private val syncExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in CloudSyncEngine scope: ${throwable.message}", throwable)
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
            // ── Step 1: Initial Restore from Cloud (Firestore -> Room & Prefs) ─
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
            Log.w(TAG, "Aborting cloud sync for user ${user.id} because cloud restore did not fully succeed.")
            return
        }

        try {
            // ── Step 2: Push any local entries not yet in cloud ─
            backupLocalToCloud(user.id)
            _syncStatus.value = SyncStatus.SYNCED
            Log.i(TAG, "Initial bidirectional sync completed successfully.")
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.ERROR
            Log.w(TAG, "Cloud backup encountered an issue: ${e.message}")
        }

        // ── Step 3: Observe Local DB Changes and Mirror to Firestore ─
        val blocklistJob = scope.launch {
            blockedAppDao.watchBlockedApps().drop(1).collect { list ->
                if (!isRestoringFromCloud.get()) {
                    syncBlockedAppsToCloud(user.id, list)
                }
            }
        }

        val keywordsJob = scope.launch {
            blockedKeywordDao.watchBlockedKeywords().drop(1).collect { list ->
                if (!isRestoringFromCloud.get()) {
                    syncKeywordsToCloud(user.id, list)
                }
            }
        }

        val mindfulJob = scope.launch {
            mindfulDao.getAllMindfulApps().drop(1).collect { list ->
                if (!isRestoringFromCloud.get()) {
                    syncMindfulAppsToCloud(user.id, list)
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

        val accountabilityJob = scope.launch {
            combine(
                prefs.isChallengeActive,
                prefs.challengeType,
                prefs.commitmentSentence,
                prefs.timeDelayHours
            ) { active, type, sentence, delay ->
                AccountabilityConfig(active, type, sentence, delay)
            }.drop(1).collect { config ->
                if (!isRestoringFromCloud.get()) {
                    syncAccountabilityToCloud(
                        uid = user.id,
                        isChallengeActive = config.isChallengeActive,
                        challengeType = config.challengeType,
                        commitmentSentence = config.commitmentSentence,
                        timeDelayHours = config.timeDelayHours
                    )
                }
            }
        }

        syncJobs.add(blocklistJob)
        syncJobs.add(keywordsJob)
        syncJobs.add(mindfulJob)
        syncJobs.add(schedulesJob)
        syncJobs.add(timeLimitsJob)
        syncJobs.add(accountabilityJob)
    }

    /**
     * Pulls user's cloud configurations and merges into local Room DB and Preferences.
     */
    private suspend fun restoreFromCloud(uid: String): Boolean {
        Log.i(TAG, "Restoring cloud data for user $uid...")
        var allSectionsSucceeded = true

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
            allSectionsSucceeded = false
            Log.w(TAG, "Failed restoring blocked apps: ${e.message}")
        }

        // 2. Restore Blocked Keywords
        try {
            var keywordsSnapshot = firestore.collection("users")
                .document(uid)
                .collection("blocked_keywords")
                .get()
                .await()

            if (keywordsSnapshot.isEmpty) {
                val legacy = firestore.collection("users")
                    .document(uid)
                    .collection("keywords")
                    .get()
                    .await()
                if (!legacy.isEmpty) {
                    keywordsSnapshot = legacy
                }
            }

            for (doc in keywordsSnapshot.documents) {
                val keyword = doc.getString("keyword") ?: doc.id
                val clean = keyword.trim().lowercase()
                if (clean.length in 2..80) {
                    val entity = BlockedKeywordEntity(
                        keyword = clean,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                    blockedKeywordDao.insert(entity)
                }
            }
            Log.i(TAG, "Restored ${keywordsSnapshot.size()} blocked keywords from cloud.")
        } catch (e: Exception) {
            allSectionsSucceeded = false
            Log.w(TAG, "Failed restoring blocked keywords: ${e.message}")
        }

        // 3. Restore Mindful Apps
        try {
            val mindfulSnapshot = firestore.collection("users")
                .document(uid)
                .collection("mindful_apps")
                .get()
                .await()

            for (doc in mindfulSnapshot.documents) {
                val pkg = doc.getString("packageName") ?: doc.id
                val name = doc.getString("appName") ?: pkg
                val allowance = doc.getLong("defaultAllowanceMinutes")?.toInt() ?: 5
                val entity = MindfulAppEntity(
                    packageName = pkg,
                    appName = name,
                    defaultAllowanceMinutes = allowance,
                    dateAdded = doc.getLong("dateAdded") ?: System.currentTimeMillis()
                )
                mindfulDao.insertMindfulApp(entity)
            }
            Log.i(TAG, "Restored ${mindfulSnapshot.size()} mindful apps from cloud.")
        } catch (e: Exception) {
            allSectionsSucceeded = false
            Log.w(TAG, "Failed restoring mindful apps: ${e.message}")
        }

        // 4. Restore Focus Schedules
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
            allSectionsSucceeded = false
            Log.w(TAG, "Failed restoring schedules: ${e.message}")
        }

        // 5. Restore Daily Time Limits
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
            allSectionsSucceeded = false
            Log.w(TAG, "Failed restoring time limits: ${e.message}")
        }

        // 6. Restore Accountability Settings (Challenge State & Mode)
        try {
            val accountDoc = firestore.collection("users")
                .document(uid)
                .collection("accountability")
                .document("settings")
                .get()
                .await()

            if (accountDoc.exists()) {
                val cloudIsChallengeActive = accountDoc.getBoolean("isChallengeActive") ?: false
                val typeStr = accountDoc.getString("challengeType") ?: ChallengeType.TYPING.name
                val cloudChallengeType = try { ChallengeType.valueOf(typeStr) } catch (_: Exception) { ChallengeType.TYPING }
                val cloudSentence = accountDoc.getString("commitmentSentence") ?: DEFAULT_COMMITMENT_SENTENCE
                val cloudDelayHours = accountDoc.getLong("timeDelayHours")?.toInt() ?: 24

                val localIsChallengeActive = prefs.syncIsChallengeActive
                val localChallengeType = prefs.syncChallengeType
                val localCommitmentSentence = prefs.syncCommitmentSentence
                val localTimeDelayHours = prefs.syncTimeDelayHours

                if (localIsChallengeActive) {
                    if (!cloudIsChallengeActive) {
                        Log.w(TAG, "Cloud challenge is inactive while local challenge is ACTIVE. Preserving local active state.")
                    }
                    if (cloudChallengeType != localChallengeType) {
                        Log.w(TAG, "Cloud challengeType ($cloudChallengeType) differs from local ($localChallengeType). Preserving local challenge type.")
                    }
                    if (cloudSentence != localCommitmentSentence) {
                        Log.w(TAG, "Cloud commitmentSentence differs from local sentence. Preserving local commitment sentence.")
                    }
                    val resolvedDelayHours = kotlin.math.max(localTimeDelayHours, cloudDelayHours)
                    if (cloudDelayHours != localTimeDelayHours) {
                        Log.w(TAG, "Cloud timeDelayHours ($cloudDelayHours) differs from local ($localTimeDelayHours). Enforcing max: $resolvedDelayHours hours.")
                    }

                    prefs.setChallengeType(localChallengeType)
                    prefs.setCommitmentSentence(localCommitmentSentence)
                    prefs.setTimeDelayHours(resolvedDelayHours)
                    prefs.setChallengeActive(true)
                    Log.i(TAG, "Restored accountability with local-stronger rule: active=true, type=$localChallengeType, delayHours=$resolvedDelayHours")
                } else {
                    prefs.setChallengeType(cloudChallengeType)
                    prefs.setCommitmentSentence(cloudSentence)
                    prefs.setTimeDelayHours(cloudDelayHours)
                    prefs.setChallengeActive(cloudIsChallengeActive)
                    Log.i(TAG, "Restored accountability settings from cloud: isChallengeActive=$cloudIsChallengeActive, type=$cloudChallengeType, delayHours=$cloudDelayHours")
                }
            } else {
                Log.i(TAG, "No remote accountability document found for user $uid.")
            }
        } catch (e: Exception) {
            allSectionsSucceeded = false
            Log.w(TAG, "Failed restoring accountability settings: ${e.message}")
        }

        return allSectionsSucceeded
    }

    /**
     * Initial push of existing local Room data & Accountability to Firestore.
     */
    private suspend fun backupLocalToCloud(uid: String) {
        val apps = blockedAppDao.watchBlockedApps().first()
        syncBlockedAppsToCloud(uid, apps)

        val keywords = blockedKeywordDao.watchBlockedKeywords().first()
        syncKeywordsToCloud(uid, keywords)

        val mindfulApps = mindfulDao.getAllMindfulApps().first()
        syncMindfulAppsToCloud(uid, mindfulApps)

        val schedules = scheduleDao.watchAllSchedules().first()
        syncSchedulesToCloud(uid, schedules)

        val limits = appTimeLimitDao.watchAllTimeLimits().first()
        syncTimeLimitsToCloud(uid, limits)

        syncAccountabilityToCloud(
            uid = uid,
            isChallengeActive = prefs.syncIsChallengeActive,
            challengeType = prefs.syncChallengeType,
            commitmentSentence = prefs.syncCommitmentSentence,
            timeDelayHours = prefs.syncTimeDelayHours
        )
    }

    private suspend fun syncBlockedAppsToCloud(uid: String, list: List<BlockedAppEntity>) {
        try {
            val collection = firestore.collection("users").document(uid).collection("app_blocklist")
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

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

            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync blocked apps to cloud: ${e.message}")
        }
    }

    private suspend fun syncKeywordsToCloud(uid: String, list: List<BlockedKeywordEntity>) {
        try {
            val collection = firestore.collection("users").document(uid).collection("blocked_keywords")
            val currentCloud = collection.get().await()
            val cloudKeys = currentCloud.documents.map { it.id }.toSet()
            val localKeys = list.map { it.keyword.trim().lowercase() }.toSet()

            for (item in list) {
                val cleanKey = item.keyword.trim().lowercase()
                val data = hashMapOf(
                    "keyword" to cleanKey,
                    "createdAt" to item.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                )
                collection.document(cleanKey).set(data, SetOptions.merge())
            }

            val toDelete = cloudKeys - localKeys
            for (key in toDelete) {
                collection.document(key).delete()
            }
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync keywords to cloud: ${e.message}")
        }
    }

    private suspend fun syncMindfulAppsToCloud(uid: String, list: List<MindfulAppEntity>) {
        try {
            val collection = firestore.collection("users").document(uid).collection("mindful_apps")
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

            for (app in list) {
                val data = hashMapOf(
                    "packageName" to app.packageName,
                    "appName" to app.appName,
                    "defaultAllowanceMinutes" to app.defaultAllowanceMinutes,
                    "dateAdded" to app.dateAdded,
                    "updatedAt" to System.currentTimeMillis()
                )
                collection.document(app.packageName).set(data, SetOptions.merge())
            }

            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync mindful apps to cloud: ${e.message}")
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

    private suspend fun syncAccountabilityToCloud(
        uid: String,
        isChallengeActive: Boolean,
        challengeType: ChallengeType,
        commitmentSentence: String,
        timeDelayHours: Int
    ) {
        try {
            val docRef = firestore.collection("users")
                .document(uid)
                .collection("accountability")
                .document("settings")

            val data = hashMapOf(
                "isChallengeActive" to isChallengeActive,
                "challengeType" to challengeType.name,
                "commitmentSentence" to commitmentSentence,
                "timeDelayHours" to timeDelayHours,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
            Log.i(TAG, "Synced accountability settings to cloud: isChallengeActive=$isChallengeActive, type=${challengeType.name}")
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync accountability to cloud: ${e.message}")
        }
    }
}
