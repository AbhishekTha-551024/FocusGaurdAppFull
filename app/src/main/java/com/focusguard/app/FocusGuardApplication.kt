package com.focusguard.app

import android.app.Application
import androidx.work.*
import com.focusguard.app.worker.AccessibilityHealthWorker
import com.focusguard.app.worker.UsagePollingWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

import javax.inject.Inject

private const val WORKER_SCHEDULING_RETRY_MS = 3_000L
private const val MAX_WORKER_SCHEDULING_ATTEMPTS = 10

/**
 * FocusGuardApplication
 * ====================
 * App entry point -- initializes Hilt DI, starts Foreground Watchdog Service, schedules periodic background workers,
 * and starts CloudSyncEngine for offline-first bidirectional synchronization.
 */
@HiltAndroidApp
class FocusGuardApplication : Application() {

    // Lazy on purpose: constructing CloudSyncEngine pulls in FirebaseAuth, whose own preferences
    // live in CE storage. Eager field injection would therefore crash every process the framework
    // starts before the first user unlock (that crash is what left protection OFF after a reboot).
    @Inject
    lateinit var cloudSyncEngine: dagger.Lazy<com.focusguard.app.sync.CloudSyncEngine>

    @Inject
    lateinit var repository: dagger.Lazy<com.focusguard.app.data.repository.FocusGuardRepository>

    // Lazy for the same Direct Boot reason as above: the probe depends on the repository.
    @Inject
    lateinit var autoRecoveryProbe: dagger.Lazy<com.focusguard.app.recovery.A11yAutoRecoveryProbe>

    // Lazy for the same Direct Boot reason: the restore engine reaches Firestore, hence FirebaseAuth.
    @Inject
    lateinit var commitmentRestoreEngine: dagger.Lazy<com.focusguard.app.commitment.CommitmentRestoreEngine>

    /** Process-level Settings toggle detector (outlives the AccessibilityService). */
    private var accessibilityObserver: com.focusguard.app.observer.AccessibilityStateObserver? = null

    override fun onCreate() {
        // ── ANTI-CLONING INTEGRITY GUARD: Primary User (Owner) Verification ─
        // CRITICAL: This MUST be the very first statement -- before super.onCreate() triggers
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

        // Diagnostics: Read historical process exit reasons (API 30+) off the main thread
        com.focusguard.app.service.utils.FgDiagnostics.logHistoricalExitReasons(this)

        // Global crash handling and logging
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("FocusGuardApp", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Direct Boot Safety: If device is locked before initial user credential entry,
        // CE storage (WorkManager SQLite & CloudSync) is encrypted and inaccessible.
        // Defer until USER_UNLOCKED broadcast is received.
        val isDirectBoot = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isDirectBootLocked(this)
        if (!isDirectBoot) {
            startCommitmentRestore()
            cloudSyncEngine.get().startSync()
            scheduleBackgroundWorkers()
            autoRecoveryProbe.get().start()
            // A teardown marker left over from a previous boot means the uninstaller was opened and
            // the app survived it, so the privileges it gave up are owed back.
            com.focusguard.app.recovery.UninstallTeardownGuard.resumeIfPending(this)
        } else {
            android.util.Log.i("FocusGuardApp", "Direct Boot mode active: deferring CloudSync and WorkManager until device unlock.")
            val unlockReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == android.content.Intent.ACTION_USER_UNLOCKED) {
                        try {
                            unregisterReceiver(this)
                        } catch (e: Exception) {
                            android.util.Log.d("FocusGuardApp", "unregisterReceiver error: ${e.message}")
                        }
                        android.util.Log.i("FocusGuardApp", "Device unlocked (ACTION_USER_UNLOCKED): starting CloudSync and WorkManager.")
                        startCommitmentRestore()
                        cloudSyncEngine.get().startSync()
                        scheduleBackgroundWorkers()
                        registerProcessLevelAccessibilityObserver()
                        autoRecoveryProbe.get().start()
                        com.focusguard.app.recovery.UninstallTeardownGuard.resumeIfPending(this@FocusGuardApplication)
                    }
                }
            }
            try {
                registerReceiver(unlockReceiver, android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED))
            } catch (e: Exception) {
                android.util.Log.e("FocusGuardApp", "Failed to register USER_UNLOCKED receiver: ${e.message}")
            }
        }

        // Register process-level persistent Accessibility Observer (survives service unbind)
        if (!isDirectBoot) {
            registerProcessLevelAccessibilityObserver()
        }

    }

    /**
     * Registers the single Accessibility-toggle detector at process level.
     *
     * It must live outside the AccessibilityService: the state it watches for (service disabled or
     * unbound) is exactly when that service object no longer exists. The service used to register
     * its own copy, so every disable fired two recovery paths at once.
     */
    private fun registerProcessLevelAccessibilityObserver() {
        if (accessibilityObserver != null) return
        try {
            accessibilityObserver = com.focusguard.app.observer.AccessibilityStateObserver(
                context = this,
                repository = repository.get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            ).also { it.register() }
            android.util.Log.i("FocusGuardApp", "Process-level Accessibility ContentObserver registered successfully.")
        } catch (e: Exception) {
            android.util.Log.e("FocusGuardApp", "Failed to register process-level ContentObserver: ${e.message}")
        }
    }

    /**
     * Asks the server, by reinstall-stable device identity, whether this device still owes a timed
     * uninstall lock. Runs before the account sync so a reinstated lock is in place as early as
     * possible; the engine is idempotent and self-throttled, so both startup branches can call it.
     */
    private fun startCommitmentRestore() {
        try {
            commitmentRestoreEngine.get().start()
        } catch (e: Exception) {
            android.util.Log.e("FocusGuardApp", "Commitment restore could not start: ${e.javaClass.simpleName}")
        }
    }

    private fun scheduleBackgroundWorkers(attempt: Int = 0) {
        try {
            // Consolidated health & battery watchdog
            val watchdogScheduled = AccessibilityHealthWorker.schedulePeriodicCheck(this)

            val usageAuditRequest = PeriodicWorkRequestBuilder<UsagePollingWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "focusguard_usage_audit",
                ExistingPeriodicWorkPolicy.KEEP,
                usageAuditRequest
            )

            if (!watchdogScheduled) scheduleHealthWatchdogRetry()
        } catch (e: IllegalStateException) {
            // A process the framework starts at boot can reach Application.onCreate before
            // androidx.startup installs WorkManager's provider. Letting that throw would kill the
            // process (and with it the Accessibility bind), so retry until WorkManager is ready.
            android.util.Log.w(
                "FocusGuardApp",
                "WorkManager not initialised yet (attempt ${attempt + 1}): ${e.message}"
            )
            if (attempt < MAX_WORKER_SCHEDULING_ATTEMPTS) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    scheduleBackgroundWorkers(attempt + 1)
                }, WORKER_SCHEDULING_RETRY_MS)
            }
        }
    }

    private fun scheduleHealthWatchdogRetry() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            AccessibilityHealthWorker.schedulePeriodicCheck(this)
        }, WORKER_SCHEDULING_RETRY_MS)
    }
}
