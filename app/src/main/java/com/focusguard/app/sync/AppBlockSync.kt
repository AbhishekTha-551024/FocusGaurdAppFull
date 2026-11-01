package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.database.dao.BlockedAppDao
import com.focusguard.app.core.database.entity.BlockedAppEntity
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

private const val TAG = "AppBlockSync"

@Singleton
class AppBlockSync @Inject constructor(
    private val blockedAppDao: BlockedAppDao,
    private val firestore: FirebaseFirestore
) {

    suspend fun restore(uid: String): Boolean {
        return try {
            val appsSnapshot = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_APP_BLOCKLIST)
                .get()
                .await()

            for (doc in appsSnapshot.documents) {
                val pkg = doc.getString(SyncPaths.FIELD_PACKAGE_NAME) ?: doc.id
                val name = doc.getString(SyncPaths.FIELD_APP_NAME) ?: pkg
                val isBrowser = doc.getBoolean(SyncPaths.FIELD_IS_AUTO_BLOCKED_UNSUPPORTED_BROWSER) ?: false
                val isNew = doc.getBoolean(SyncPaths.FIELD_IS_AUTO_BLOCKED_NEW_INSTALL) ?: false
                val entity = BlockedAppEntity(
                    packageName = pkg,
                    appName = name,
                    dateAdded = doc.getLong(SyncPaths.FIELD_DATE_ADDED) ?: System.currentTimeMillis(),
                    isAutoBlockedUnsupportedBrowser = isBrowser,
                    isAutoBlockedNewInstall = isNew
                )
                blockedAppDao.insert(entity)
            }
            Log.i(TAG, "Restored ${appsSnapshot.size()} blocked apps from cloud.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring blocked apps: ${e.message}")
            false
        }
    }

    suspend fun backup(uid: String) {
        val apps = blockedAppDao.watchBlockedApps().first()
        syncBlockedAppsToCloud(uid, apps)
    }

    suspend fun syncBlockedAppsToCloud(uid: String, list: List<BlockedAppEntity>) {
        try {
            val collection = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_APP_BLOCKLIST)
            val currentCloud = collection.get().await()
            val cloudPkgs = currentCloud.documents.map { it.id }.toSet()
            val localPkgs = list.map { it.packageName }.toSet()

            for (app in list) {
                val data = hashMapOf(
                    SyncPaths.FIELD_PACKAGE_NAME to app.packageName,
                    SyncPaths.FIELD_APP_NAME to app.appName,
                    SyncPaths.FIELD_DATE_ADDED to app.dateAdded,
                    SyncPaths.FIELD_IS_AUTO_BLOCKED_UNSUPPORTED_BROWSER to app.isAutoBlockedUnsupportedBrowser,
                    SyncPaths.FIELD_IS_AUTO_BLOCKED_NEW_INSTALL to app.isAutoBlockedNewInstall,
                    SyncPaths.FIELD_UPDATED_AT to System.currentTimeMillis()
                )
                collection.document(app.packageName).set(data, SetOptions.merge())
            }

            val toDelete = cloudPkgs - localPkgs
            for (pkg in toDelete) {
                collection.document(pkg).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync blocked apps to cloud: ${e.message}")
        }
    }

    fun watchAndSync(
        scope: CoroutineScope,
        uid: String,
        isRestoring: () -> Boolean,
        onSynced: () -> Unit
    ): Job {
        return scope.launch {
            blockedAppDao.watchBlockedApps().drop(1).collect { list ->
                if (!isRestoring()) {
                    syncBlockedAppsToCloud(uid, list)
                    onSynced()
                }
            }
        }
    }
}

