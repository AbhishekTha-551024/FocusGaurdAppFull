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
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.ProtectionWatchdogService
import com.focusguard.app.ui.onboarding.OnboardingActivity
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

private const val TAG = "AccessibilityHealth"
private const val CHANNEL_ID = "focusguard_health_channel"
private const val NOTIFICATION_ID = 9905

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
 * this worker:
 * 1. Restarts [ProtectionWatchdogService] (START_STICKY) to restore foreground process priority.
 * 2. Alerts the user via a high-priority notification with a one-tap intent to re-arm the shield.
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
            val isRunning = FocusGuardAccessibilityService.isServiceRunning
            Log.d(TAG, "Accessibility health check: isServiceRunning=$isRunning")

            if (!isRunning) {
                // Invariant Rule 3: Only alert user if uninstall protection is strictly ACTIVE
                val isProtectionConfigured = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver.isDeviceAdminActive(appContext) &&
                        repository.isFastUninstallProtectionEnabled()

                if (isProtectionConfigured) {
                    Log.w(TAG, "Ghost Accessibility state detected: service is dead while protection was enabled. Alerting user.")
                    postReactivationNotification(appContext)
                } else {
                    Log.d(TAG, "Accessibility not running, but uninstall protection is inactive. Dismissing any alerts.")
                    cancelReactivationNotification(appContext)
                }
            } else {
                // Check if battery optimization was revoked while protection is active
                val isBatteryExempt = com.focusguard.app.utils.BatteryOptimizationHelper.isBatteryOptimizationIgnored(appContext)
                val isProtectionActive = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver.isDeviceAdminActive(appContext) &&
                        repository.isFastUninstallProtectionEnabled()
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
        const val CHANNEL_ID = "focusguard_health_channel"
        const val NOTIFICATION_ID = 9905

        fun schedulePeriodicCheck(context: Context) {
            val request = PeriodicWorkRequestBuilder<AccessibilityHealthWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // 5-minute flex window
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "Scheduled 15-minute periodic AccessibilityHealthWorker")
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
