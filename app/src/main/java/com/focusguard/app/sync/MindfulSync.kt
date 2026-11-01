package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.database.dao.MindfulDao
import com.focusguard.app.core.database.entity.MindfulAppEntity
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

private const val TAG = "MindfulSync"

@Singleton
class MindfulSync @Inject constructor(
    private val mindfulDao: MindfulDao,
    private val firestore: FirebaseFirestore
) {

    suspend fun restore(uid: String): Boolean {
        return try {
            val mindfulSnapshot = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_MINDFUL_APPS)
                .get()
                .await()

            for (doc in mindfulSnapshot.documents) {
                val pkg = doc.getString(SyncPaths.FIELD_PACKAGE_NAME) ?: doc.id
                val name = doc.getString(SyncPaths.FIELD_APP_NAME) ?: pkg
                val allowance = doc.getLong(SyncPaths.FIELD_DEFAULT_ALLOWANCE_MINUTES)?.toInt() ?: 5
                val entity = MindfulAppEntity(
                    packageName = pkg,
                    appName = name,
                    defaultAllowanceMinutes = allowance,
                    dateAdded = doc.getLong(SyncPaths.FIELD_DATE_ADDED) ?: System.currentTimeMillis()
                )
                mindfulDao.insertMindfulApp(entity)
            }
            Log.i(TAG, "Restored ${mindfulSnapshot.size()} mindful apps from cloud.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring mindful apps: ${e.message}")
            false
        }
    }

    suspend fun backup(uid: String) {
        val mindfulApps = mindfulDao.getAllMindfulApps().first()
        syncMindfulAppsToCloud(uid, mindfulApps)
    }

    suspend fun syncMindfulAppsToCloud(uid: String, list: List<MindfulAppEntity>) {
        try {
            val collection = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_MINDFUL_APPS)
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

            for (app in list) {
                val data = hashMapOf(
                    SyncPaths.FIELD_PACKAGE_NAME to app.packageName,
                    SyncPaths.FIELD_APP_NAME to app.appName,
                    SyncPaths.FIELD_DEFAULT_ALLOWANCE_MINUTES to app.defaultAllowanceMinutes,
                    SyncPaths.FIELD_DATE_ADDED to app.dateAdded,
                    SyncPaths.FIELD_UPDATED_AT to System.currentTimeMillis()
                )
                collection.document(app.packageName).set(data, SetOptions.merge())
            }

            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync mindful apps to cloud: ${e.message}")
        }
    }

    fun watchAndSync(
        scope: CoroutineScope,
        uid: String,
        isRestoring: () -> Boolean,
        onSynced: () -> Unit
    ): Job {
        return scope.launch {
            mindfulDao.getAllMindfulApps().drop(1).collect { list ->
                if (!isRestoring()) {
                    syncMindfulAppsToCloud(uid, list)
                    onSynced()
                }
            }
        }
    }
}

