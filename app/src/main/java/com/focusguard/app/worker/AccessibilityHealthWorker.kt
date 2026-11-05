package com.focusguard.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.focusguard.app.R
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.recovery.AccessibilityRecoveryScheduler
import com.focusguard.app.recovery.AccessibilityStatusChecker
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.ui.onboarding.OnboardingActivity
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val TAG = "AccessibilityHealth"
private const val CHANNEL_ID = "focusguard_health_channel"
private const val NOTIFICATION_ID = com.focusguard.app.core.common.constants.NotificationIds.ID_ACCESSIBILITY_HEALTH_ALERT

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccessibilityHealthWorkerEntryPoint {
    fun repository(): FocusGuardRepository
}

/**
 * AccessibilityHealthWorker
 * =========================
 * Background watchdog running every 15 minutes to detect and self-heal the "Ghost Accessibility" state.
 *
 * If Android OS killed or froze the AccessibilityService process in the background,
 * this worker alerts the user via a high-priority notification with a one-tap intent to re-arm the shield.
 */
class AccessibilityHealthWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository: FocusGuardRepository by lazy {
        EntryPointAccessors.fromApplication(
            appContext,
            AccessibilityHealthWorkerEntryPoint::class.java
        ).repository()
    }

    override suspend fun doWork(): Result {
        return try {
            val isRapidAttempt = inputData.getBoolean(KEY_RAPID, false)
            if (isRapidAttempt) AccessibilityRecoveryScheduler.recordAttempt(appContext)

            val isServiceAlive = FocusGuardAccessibilityService.isServiceRunning
            val isConfiguredInSettings = AccessibilityStatusChecker.isAccessibilityConfiguredInSettings(appContext)
            val isHealthy = isServiceAlive && isConfiguredInSettings
            Log.d(TAG, "Accessibility health check: alive=$isServiceAlive configuredInSettings=$isConfiguredInSettings rapid=$isRapidAttempt")

            // Invariant Rule 3: recover or alert only while uninstall protection is strictly ACTIVE.
            // Go through the shared gate so a worker that runs before the repository cache is hydrated
            // (directly after a reboot) still reads the device-protected mirror instead of "OFF".
            val isProtectionActive = AccessibilityRecoveryHelper.isProtectionActive(appContext, repository)

            if (!isHealthy) {
                if (isProtectionActive) {
                    // Ghost state (service dead or dropped from Settings while protection is ON):
                    // heal silently first and let the helper escalate to the recovery UI + alert only
                    // when the silent path is unavailable.
                    Log.w(TAG, "Accessibility is not bound while protection is ON. Healing.")
                    AccessibilityRecoveryHelper.restoreProtectionFast(
                        context = appContext,
                        source = "AccessibilityHealthWorker",
                        repository = repository
                    )
                } else {
                    Log.d(TAG, "Accessibility not running, but uninstall protection is inactive. Dismissing alerts.")
                    AccessibilityRecoveryScheduler.resetAttempts(appContext)
                    cancelReactivationNotification(appContext)
                }
            } else {
                AccessibilityRecoveryScheduler.resetAttempts(appContext)

                // Check if battery optimization was revoked while protection is active
                val isBatteryExempt = com.focusguard.app.utils.BatteryOptimizationHelper.isBatteryOptimizationIgnored(appContext)
                if (!isBatteryExempt && isProtectionActive) {
                    Log.w(TAG, "Battery Optimization exemption was revoked while protection enabled.")
                    postBatteryOptimizationNotification()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in AccessibilityHealthWorker: ${e.message}", e)
            Result.success()
        }
    }

    private fun postBatteryOptimizationNotification() {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusGuard Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Android stops background protection"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(appContext, OnboardingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            1,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("FocusGuard Battery Optimization Warning")
            .setContentText("Battery exemption was revoked. Background protection may be paused by OS.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "focusguard_accessibility_health_watchdog"
        private const val HEAL_WORK_NAME = "focusguard_accessibility_heal_check"
        private const val KEY_RAPID = "is_rapid_binding_attempt"
        const val CHANNEL_ID = "focusguard_health_channel"
        const val NOTIFICATION_ID = com.focusguard.app.core.common.constants.NotificationIds.ID_ACCESSIBILITY_HEALTH_ALERT

        /**
         * Registers the periodic watchdog. Returns false when WorkManager is not usable in this
         * process yet (it can be started by the framework before its initializer provider runs).
         */
        fun schedulePeriodicCheck(context: Context): Boolean {
            val request = PeriodicWorkRequestBuilder<AccessibilityHealthWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // 5-minute flex window
            ).build()

            return try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.i(TAG, "Scheduled 15-minute periodic AccessibilityHealthWorker")
                true
            } catch (e: IllegalStateException) {
                Log.w(TAG, "WorkManager not ready, periodic watchdog skipped: ${e.message}")
                false
            }
        }

        /**
         * One-shot heal pass after [delayMs]. Used to verify that the OS actually bound the service
         * instead of merely keeping it in Settings.Secure.
         */
        fun scheduleHealCheck(context: Context, delayMs: Long, rapid: Boolean) {
            val request = OneTimeWorkRequestBuilder<AccessibilityHealthWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_RAPID, rapid).build())
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            try {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    HEAL_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.i(TAG, "Scheduled one-shot accessibility heal check in ${delayMs}ms (rapid=$rapid)")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "WorkManager not ready, heal check skipped: ${e.message}")
            }
        }

        fun postReactivationNotification(context: Context) {
            if (!com.focusguard.app.recovery.AccessibilityRecoveryHelper.isProtectionActive(context)) {
                Log.d(TAG, "postReactivationNotification: protection is inactive, skipping.")
                cancelReactivationNotification(context)
                return
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "FocusGuard Health Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when Android stops background protection"
                    enableVibration(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Direct recovery activity intent
            val recoveryIntent = Intent(context, AccessibilityRecoveryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val recoveryPendingIntent = PendingIntent.getActivity(
                context,
                0,
                recoveryIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Direct settings intent as action
            val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                1,
                settingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("🚨 CRITICAL: FocusGuard Protection Paused")
                .setContentText("Accessibility was turned off. Tap to restore immediately.")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(recoveryPendingIntent)
                .addAction(R.drawable.ic_notification, "Restore Shield", settingsPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        fun cancelReactivationNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        }
    }
}
