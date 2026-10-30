package com.focusguard.app

import android.app.Application
import androidx.work.*
import com.focusguard.app.service.ProtectionWatchdogService
import com.focusguard.app.worker.AccessibilityHealthWorker
import com.focusguard.app.worker.UsagePollingWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var repository: com.focusguard.app.data.repository.FocusGuardRepository

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
            cloudSyncEngine.startSync()
            scheduleBackgroundWorkers()
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
                        cloudSyncEngine.startSync()
                        scheduleBackgroundWorkers()
                        registerProcessLevelAccessibilityObserver()
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

        // BlockerHero architecture: No persistent foreground service / notification needed.
        // AccessibilityService and Device Admin handle 100% of protection natively without battery warning.
        try {
            ProtectionWatchdogService.stopService(this)
        } catch (e: Throwable) {
            android.util.Log.d("FocusGuardApp", "ProtectionWatchdogService stopService error: ${e.message}")
        }
    }

    private fun registerProcessLevelAccessibilityObserver() {
        try {
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                private var verificationJob: kotlinx.coroutines.Job? = null
                private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                    super.onChange(selfChange, uri)
                    val isA11yActive = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(this@FocusGuardApplication)
                    if (!isA11yActive) {
                        verificationJob?.cancel()
                        verificationJob = appScope.launch {
                            kotlinx.coroutines.delay(2000L) // 2s debounce for temporary checks
                            val stillOff = !com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(this@FocusGuardApplication)
                            if (stillOff) {
                                val isProtected = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isProtectionActive(this@FocusGuardApplication, repository)
                                if (isProtected) {
                                    android.util.Log.w("FocusGuardApp", "Process-Level Observer: Accessibility is OFF while protection is active! Restoring shield.")
                                    com.focusguard.app.recovery.AccessibilityRecoveryHelper.restoreProtectionFast(
                                        context = this@FocusGuardApplication,
                                        source = "AppProcessContentObserver",
                                        repository = repository
                                    )
                                    try {
                                        com.focusguard.app.service.BootCoverService.start(this@FocusGuardApplication)
                                        com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity.start(
                                            this@FocusGuardApplication,
                                            "Accessibility was disabled! Please re-enable to keep your device secure."
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("FocusGuardApp", "Failed to launch recovery shield from AppProcessContentObserver: ${e.message}")
                                    }
                                }
                            }
                        }
                    } else {
                        verificationJob?.cancel()
                        verificationJob = null
                    }
                }
            }
            contentResolver.registerContentObserver(
                android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                observer
            )
            android.util.Log.i("FocusGuardApp", "Process-level Accessibility ContentObserver registered successfully.")
        } catch (e: Exception) {
            android.util.Log.e("FocusGuardApp", "Failed to register process-level ContentObserver: ${e.message}")
        }
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
