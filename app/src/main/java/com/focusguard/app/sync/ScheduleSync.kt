package com.focusguard.app.sync

import android.util.Log
import com.focusguard.app.core.database.dao.ScheduleDao
import com.focusguard.app.core.database.entity.ScheduleEntity
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

private const val TAG = "ScheduleSync"

internal fun scheduleIdFromCloud(documentId: String, storedId: Long?): Long =
    storedId ?: documentId.toLongOrNull() ?: 0L

@Singleton
class ScheduleSync @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val firestore: FirebaseFirestore
) {

    suspend fun restore(uid: String): Boolean {
        return try {
            val schedSnapshot = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_SCHEDULES)
                .get()
                .await()

            for (doc in schedSnapshot.documents) {
                val scheduleId = scheduleIdFromCloud(
                    doc.id,
                    doc.getLong(SyncPaths.FIELD_SCHEDULE_ID)
                )
                val title = doc.getString(SyncPaths.FIELD_TITLE) ?: "Focus Hours"
                val pkg = doc.getString(SyncPaths.FIELD_PACKAGE_NAME)
                val appName = doc.getString(SyncPaths.FIELD_APP_NAME)
                val mask = doc.getLong(SyncPaths.FIELD_DAYS_OF_WEEK_MASK)?.toInt() ?: 31
                val start = doc.getLong(SyncPaths.FIELD_START_MINUTE_OF_DAY)?.toInt() ?: 540
                val end = doc.getLong(SyncPaths.FIELD_END_MINUTE_OF_DAY)?.toInt() ?: 1020
                val isEnabled = doc.getBoolean(SyncPaths.FIELD_IS_ENABLED) ?: true
                val entity = ScheduleEntity(
                    id = scheduleId,
                    title = title,
                    packageName = pkg,
                    appName = appName,
                    daysOfWeekMask = mask,
                    startMinuteOfDay = start,
                    endMinuteOfDay = end,
                    isEnabled = isEnabled,
                    createdAt = doc.getLong(SyncPaths.FIELD_CREATED_AT) ?: System.currentTimeMillis()
                )
                scheduleDao.insert(entity)
            }
            Log.i(TAG, "Restored ${schedSnapshot.size()} schedules from cloud.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring schedules: ${e.message}")
            false
        }
    }

    suspend fun backup(uid: String) {
        val schedules = scheduleDao.watchAllSchedules().first()
        syncSchedulesToCloud(uid, schedules)
    }

    suspend fun syncSchedulesToCloud(uid: String, list: List<ScheduleEntity>) {
        try {
            val collection = firestore.collection(SyncPaths.COLLECTION_USERS)
                .document(uid)
                .collection(SyncPaths.COLLECTION_SCHEDULES)
            val currentCloud = collection.get().await()
            val cloudIds = currentCloud.documents.map { it.id }.toSet()
            val localIds = list.map { it.id.toString() }.toSet()

            for (schedule in list) {
                val data = hashMapOf(
                    SyncPaths.FIELD_SCHEDULE_ID to schedule.id,
                    SyncPaths.FIELD_TITLE to schedule.title,
                    SyncPaths.FIELD_PACKAGE_NAME to (schedule.packageName ?: ""),
                    SyncPaths.FIELD_APP_NAME to (schedule.appName ?: ""),
                    SyncPaths.FIELD_DAYS_OF_WEEK_MASK to schedule.daysOfWeekMask,
                    SyncPaths.FIELD_START_MINUTE_OF_DAY to schedule.startMinuteOfDay,
                    SyncPaths.FIELD_END_MINUTE_OF_DAY to schedule.endMinuteOfDay,
                    SyncPaths.FIELD_IS_ENABLED to schedule.isEnabled,
                    SyncPaths.FIELD_CREATED_AT to schedule.createdAt,
                    SyncPaths.FIELD_UPDATED_AT to System.currentTimeMillis()
                )
                collection.document(schedule.id.toString()).set(data, SetOptions.merge())
            }

            val toDelete = cloudIds - localIds
            for (id in toDelete) {
                collection.document(id).delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync schedules to cloud: ${e.message}")
        }
    }

    fun watchAndSync(
        scope: CoroutineScope,
        uid: String,
        isRestoring: () -> Boolean,
        onSynced: () -> Unit
    ): Job {
        return scope.launch {
            scheduleDao.watchAllSchedules().drop(1).collect { list ->
                if (!isRestoring()) {
                    syncSchedulesToCloud(uid, list)
                    onSynced()
                }
            }
        }
    }
}
