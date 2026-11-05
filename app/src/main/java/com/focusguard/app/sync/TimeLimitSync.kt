package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.database.dao.AppTimeLimitDao
import com.focusguard.app.core.database.entity.AppTimeLimitEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TimeLimitSync"

@Singleton
class TimeLimitSync @Inject constructor(
    private val appTimeLimitDao: AppTimeLimitDao,
    private val firestore: FirebaseFirestore
) {

    suspend fun restore(uid: String): Boolean {
        return try {
            val limitsSnapshot = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_TIME_LIMITS)
                .get()
                .await()

            for (doc in limitsSnapshot.documents) {
                val pkg = doc.getString(SyncPaths.FIELD_PACKAGE_NAME) ?: doc.id
                val name = doc.getString(SyncPaths.FIELD_APP_NAME) ?: pkg
                val minutes = doc.getLong(SyncPaths.FIELD_DAILY_LIMIT_MINUTES)?.toInt() ?: 60
                val isEnabled = doc.getBoolean(SyncPaths.FIELD_IS_ENABLED) ?: true
                val entity = AppTimeLimitEntity(
                    packageName = pkg,
                    appName = name,
                    dailyLimitMinutes = minutes,
                    isEnabled = isEnabled,
                    dateConfigured = doc.getLong(SyncPaths.FIELD_DATE_CONFIGURED) ?: System.currentTimeMillis()
                )
                appTimeLimitDao.upsert(entity)
            }
            Log.i(TAG, "Restored ${limitsSnapshot.size()} daily time limits from cloud.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring time limits: ${e.message}")
            false
        }
    }

    suspend fun backup(uid: String) {
        val limits = appTimeLimitDao.watchAllTimeLimits().first()
        syncTimeLimitsToCloud(uid, limits)
    }

    suspend fun syncTimeLimitsToCloud(uid: String, list: List<AppTimeLimitEntity>) {
        try {
            val collection = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_TIME_LIMITS)
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

            for (limit in list) {
                val data = hashMapOf(
                    SyncPaths.FIELD_PACKAGE_NAME to limit.packageName,
                    SyncPaths.FIELD_APP_NAME to limit.appName,
                    SyncPaths.FIELD_DAILY_LIMIT_MINUTES to limit.dailyLimitMinutes,
                    SyncPaths.FIELD_IS_ENABLED to limit.isEnabled,
                    SyncPaths.FIELD_DATE_CONFIGURED to limit.dateConfigured,
                    SyncPaths.FIELD_UPDATED_AT to System.currentTimeMillis()
                )
                collection.document(limit.packageName).set(data, SetOptions.merge())
            }

            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync time limits to cloud: ${e.message}")
        }
    }

    fun watchAndSync(
        scope: CoroutineScope,
        uid: String,
        isRestoring: () -> Boolean,
        onSynced: () -> Unit
    ): Job {
        return scope.launch {
            appTimeLimitDao.watchAllTimeLimits().drop(1).collect { list ->
                if (!isRestoring()) {
                    syncTimeLimitsToCloud(uid, list)
                    onSynced()
                }
            }
        }
    }
}

