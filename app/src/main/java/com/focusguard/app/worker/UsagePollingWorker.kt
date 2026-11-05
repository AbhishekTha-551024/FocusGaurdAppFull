package com.focusguard.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.usage.UsageStatsReader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

private const val TAG = "UsagePollingWorker"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsagePollingWorkerEntryPoint {
    fun repository(): FocusGuardRepository
    fun usageStatsReader(): UsageStatsReader
}

/**
 * UsagePollingWorker
 * ==================
 * Periodic (15-min) background auditor that checks whether PACKAGE_USAGE_STATS
 * has been revoked in system settings while time limits are configured,
 * logging a SecurityEvent to the audit trail if tampered.
 */
class UsagePollingWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            appContext,
            UsagePollingWorkerEntryPoint::class.java
        )
    }

    override suspend fun doWork(): Result {
        try {
            val repository = entryPoint.repository()
            val usageStatsReader = entryPoint.usageStatsReader()

            val isOnboardingComplete = repository.isOnboardingComplete.first()
            val activeTimeLimitsCount = repository.activeTimeLimitsCount.first()

            if (isOnboardingComplete && activeTimeLimitsCount > 0) {
                val hasPermission = usageStatsReader.hasUsageAccessPermission()
                if (!hasPermission) {
                    Log.w(TAG, "Usage Access permission has been REVOKED while active time limits exist!")
                    repository.logEvent(
                        EventType.ADMIN_DISABLED,
                        "Usage Access permission revoked: App time limits cannot be accurately measured"
                    )
                } else {
                    Log.d(TAG, "Usage Access permission check OK.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in UsagePollingWorker execution", e)
        }

        return Result.success()
    }
}
