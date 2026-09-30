package com.focusguard.app

import android.app.Application
import androidx.work.*
import com.focusguard.app.service.ProtectionWatchdogService
import com.focusguard.app.worker.AccessibilityHealthWorker
import com.focusguard.app.worker.UsagePollingWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

import javax.inject.Inject

/**
 * FocusGuardApplication
 * ====================
 * App entry point — initializes Hilt DI, starts Foreground Watchdog Service, schedules periodic background workers,
 * and starts CloudSyncEngine for offline-first bidirectional synchronization.
 */
@HiltAndroidApp
class FocusGuardApplication : Application() {

    @Inject
    lateinit var cloudSyncEngine: com.focusguard.app.sync.CloudSyncEngine

    override fun onCreate() {
        // ── ANTI-CLONING INTEGRITY GUARD: Primary User (Owner) Verification ─
        // CRITICAL: This MUST be the very first statement — before super.onCreate() triggers
        // Hilt field injection (which starts Firebase/Firestore, CloudSyncEngine, etc.).
        // In Android, primary user is User 0. Cloners & OEM Dual Apps run under User 999, 95, etc.
        // FocusGuard strictly forbids running as a cloned instance.
        val myUid = android.os.Process.myUid()
        val userId = myUid / 100000
        if (userId != 0) {
            android.util.Log.e("FocusGuardApp", "FATAL: FocusGuard cannot run inside cloned/dual space (UserId: $userId). Terminating.")
            kotlin.system.exitProcess(1)
        }

        super.onCreate()
        cloudSyncEngine.startSync()

        // BlockerHero architecture: No persistent foreground service / notification needed.
        // AccessibilityService and Device Admin handle 100% of protection natively without battery warning.
        try {
            ProtectionWatchdogService.stopService(this)
        } catch (_: Throwable) {}
        scheduleBackgroundWorkers()
    }

    private fun scheduleBackgroundWorkers() {
        // Consolidated health & battery watchdog
        AccessibilityHealthWorker.schedulePeriodicCheck(this)

        val usageAuditRequest = PeriodicWorkRequestBuilder<UsagePollingWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "focusguard_usage_audit",
            ExistingPeriodicWorkPolicy.KEEP,
            usageAuditRequest
        )
    }
}
