package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusguard.app.R
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ProtectionWatchdog"
private const val CHANNEL_ID = "focusguard_watchdog_channel"
private const val ALERT_CHANNEL_ID = "focusguard_watchdog_alert_channel"
private const val NOTIFICATION_ID = 9901

@AndroidEntryPoint
class ProtectionWatchdogService : Service() {

    @Inject
    lateinit var repository: FocusGuardRepository

    private val watchdogExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Watchdog scope error: ${throwable.message}", throwable)
    }
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob + watchdogExceptionHandler)
    private var contentObserver: ContentObserver? = null
    private var isAlertStateActive = false
    private var alertDebounceJob: Job? = null
    // FIX NH1: Volatile flag prevents NPE when the Dispatchers.Default heartbeat loop
    // calls evaluateHealthAndSyncNotification() after onDestroy() has nullified resources.
    @Volatile private var isServiceDestroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ProtectionWatchdogService is retired — stopping immediately to remove FGS warning.")
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.d(TAG, "stopForeground cleanup error: ${e.message}")
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.d(TAG, "onStartCommand stopForeground error: ${e.message}")
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceDestroyed = true   // FIX NH1: signal heartbeat thread to stop accessing resources
        Log.i(TAG, "ProtectionWatchdogService onDestroy")
        alertDebounceJob?.cancel()
        alertDebounceJob = null
        serviceJob.cancel()
        contentObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering ContentObserver", e)
            }
        }
        contentObserver = null
    }

    private fun registerSettingsObserver() {
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    evaluateHealthAndSyncNotification()
                }
            }
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                observer
            )
            contentObserver = observer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Settings ContentObserver", e)
        }
    }

    private fun startWatchdogHeartbeat() {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(30_000L) // Check every 30 seconds
                evaluateHealthAndSyncNotification()
            }
        }
    }

    private fun evaluateHealthAndSyncNotification() {
        if (isServiceDestroyed) return   // FIX NH1: guard against post-onDestroy() access
        try {
            val isA11yRunning = FocusGuardAccessibilityService.isServiceRunning
            val isProtectionConfigured = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver.isDeviceAdminActive(this) &&
                    repository.isFastUninstallProtectionEnabled()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (!isA11yRunning && isProtectionConfigured) {
                if (!isAlertStateActive && (alertDebounceJob == null || alertDebounceJob?.isActive == false)) {
                    // Debounce by 7 seconds for Vivo iManager / OEM temporary setting checks
                    Log.d(TAG, "Watchdog detected Accessibility Service not running, debouncing 7s (OEM shield tolerance)...")
                    alertDebounceJob = scope.launch {
                        delay(7000L)
                        if (!FocusGuardAccessibilityService.isServiceRunning && !isServiceDestroyed) {
                            isAlertStateActive = true
                            Log.w(TAG, "Watchdog confirmed Accessibility Service OFF after 7s debounce! Escalating notification.")
                            notificationManager.notify(NOTIFICATION_ID, buildAlertNotification())
                        } else {
                            Log.i(TAG, "Accessibility Service restored within debounce window. No alert.")
                        }
                    }
                }
            } else {
                alertDebounceJob?.cancel()
                alertDebounceJob = null
                if (isAlertStateActive) {
                    isAlertStateActive = false
                    Log.i(TAG, "Watchdog detected Accessibility Service restored. Restoring normal notification.")
                    notificationManager.notify(NOTIFICATION_ID, buildNormalNotification())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating watchdog health", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val normalChannel = NotificationChannel(
                CHANNEL_ID,
                "FocusGuard Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps FocusGuard active and tamper-proof in the background."
                setShowBadge(false)
            }
            manager.createNotificationChannel(normalChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "FocusGuard Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when FocusGuard protection has been interrupted."
                setShowBadge(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildNormalNotification(): Notification {
        val launchIntent = Intent(this, OnboardingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard Shield Active")
            .setContentText("Continuous background protection running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildAlertNotification(): Notification {
        // Direct 1-tap intent to open Android Accessibility settings
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ Protection Interrupted")
            .setContentText("FocusGuard shield was disabled. Tap here to re-enable immediately.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .build()
    }

    companion object {
        fun startService(context: Context) {
            // Retired: No-op to match BlockerHero architecture (no persistent FGS / no battery warning banner)
            stopService(context)
        }

        fun stopService(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
                notificationManager?.cancel(CHANNEL_ID.hashCode())
                context.stopService(Intent(context, ProtectionWatchdogService::class.java))
                Log.i(TAG, "ProtectionWatchdogService stopped and notification dismissed.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ProtectionWatchdogService: ${e.message}", e)
            }
        }
    }
}
