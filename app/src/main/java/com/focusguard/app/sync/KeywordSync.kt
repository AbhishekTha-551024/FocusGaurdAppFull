package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.database.dao.BlockedKeywordDao
import com.focusguard.app.core.database.entity.BlockedKeywordEntity
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

private const val TAG = "KeywordSync"

@Singleton
class KeywordSync @Inject constructor(
    private val blockedKeywordDao: BlockedKeywordDao,
    private val firestore: FirebaseFirestore
) {

    suspend fun restore(uid: String): Boolean {
        return try {
            var keywordsSnapshot = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_BLOCKED_KEYWORDS)
                .get()
                .await()

            if (keywordsSnapshot.isEmpty) {
                val legacy = firestore.collection(SyncPaths.COLLECTION_USERS)
                    .document(uid)
                    .collection(SyncPaths.COLLECTION_KEYWORDS_LEGACY)
                    .get()
                    .await()
                if (!legacy.isEmpty) {
                    keywordsSnapshot = legacy
                }
            }

            for (doc in keywordsSnapshot.documents) {
                val keyword = doc.getString(SyncPaths.FIELD_KEYWORD) ?: doc.id
                val clean = keyword.trim().lowercase()
                if (clean.length in 2..80) {
                    val entity = BlockedKeywordEntity(
                        keyword = clean,
                        createdAt = doc.getLong(SyncPaths.FIELD_CREATED_AT) ?: System.currentTimeMillis()
                    )
                    blockedKeywordDao.insert(entity)
                }
            }
            Log.i(TAG, "Restored ${keywordsSnapshot.size()} blocked keywords from cloud.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring blocked keywords: ${e.message}")
            false
        }
    }

    suspend fun backup(uid: String) {
        val keywords = blockedKeywordDao.watchBlockedKeywords().first()
        syncKeywordsToCloud(uid, keywords)
    }

    suspend fun syncKeywordsToCloud(uid: String, list: List<BlockedKeywordEntity>) {
        try {
            val collection = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_BLOCKED_KEYWORDS)
            val currentCloud = collection.get().await()
            val cloudKeys = currentCloud.documents.map { it.id }.toSet()
            val localKeys = list.map { it.keyword.trim().lowercase() }.toSet()

            for (item in list) {
                val cleanKey = item.keyword.trim().lowercase()
                val data = hashMapOf(
                    SyncPaths.FIELD_KEYWORD to cleanKey,
                    SyncPaths.FIELD_CREATED_AT to item.createdAt,
                    SyncPaths.FIELD_UPDATED_AT to System.currentTimeMillis()
                )
                collection.document(cleanKey).set(data, SetOptions.merge())
            }

            val toDelete = cloudKeys - localKeys
            for (key in toDelete) {
                collection.document(key).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync keywords to cloud: ${e.message}")
        }
    }

    fun watchAndSync(
        scope: CoroutineScope,
        uid: String,
        isRestoring: () -> Boolean,
        onSynced: () -> Unit
    ): Job {
        return scope.launch {
            blockedKeywordDao.watchBlockedKeywords().drop(1).collect { list ->
                if (!isRestoring()) {
                    syncKeywordsToCloud(uid, list)
                    onSynced()
                }
            }
        }
    }
}

