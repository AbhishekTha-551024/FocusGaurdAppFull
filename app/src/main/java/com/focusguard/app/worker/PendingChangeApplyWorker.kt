package com.focusguard.app.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.focusguard.app.data.repository.FocusGuardRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val TAG = "PendingChangeWorker"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PendingChangeWorkerEntryPoint {
    fun repository(): FocusGuardRepository
}

class PendingChangeApplyWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository: FocusGuardRepository by lazy {
        EntryPointAccessors.fromApplication(
            appContext,
            PendingChangeWorkerEntryPoint::class.java
        ).repository()
    }

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Executing scheduled pending changes...")
            repository.reconcilePendingChanges()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error applying pending changes: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val BASE_WORK_NAME = "focusguard_apply_change_"
        private const val UNIQUE_WORK_NAME = "focusguard_apply_pending_changes"

        fun scheduleWork(context: Context, delayHours: Int, changeId: Long = 0L) {
            val delayMinutes = (delayHours * 60L).coerceAtLeast(1L)
            val request = OneTimeWorkRequestBuilder<PendingChangeApplyWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            val workName = if (changeId > 0L) "$BASE_WORK_NAME$changeId" else UNIQUE_WORK_NAME
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelWork(context: Context, changeId: Long) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork("$BASE_WORK_NAME$changeId")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling work for changeId $changeId: ${e.message}")
            }
        }
    }
}
