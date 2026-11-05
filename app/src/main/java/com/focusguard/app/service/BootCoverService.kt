package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focusguard.app.R
import com.focusguard.app.core.startup.ProtectionStartupReadiness
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.lang.ref.WeakReference

private const val TAG = "FG:Shield"
private const val CHANNEL_ID = "focusguard_boot_shield_channel"
private const val NOTIFICATION_ID = com.focusguard.app.core.common.constants.NotificationIds.ID_BOOT_COVER_SHIELD

/**
 * BootCoverService
 * ================
 * Temporary foreground service that displays a full-screen cover overlay immediately after device boot.
 *
 * Protection Rationale:
 * - Following a restart, Android's AccessibilityManager takes time to bind to
 *   [FocusGuardAccessibilityService] and publish its initial rules snapshot.
 * - During this gap, user could potentially access the launcher to uninstall or clone FocusGuard.
 * - Delegates overlay window mounting to [BootCoverOverlayManager].
 */
@AndroidEntryPoint
class BootCoverService : Service() {

    @Inject
    lateinit var startupReadiness: ProtectionStartupReadiness

    internal lateinit var overlayManager: BootCoverOverlayManager
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val readinessScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isFallbackState by mutableStateOf(false)
    private var sentinel: BootCoverSentinel? = null
    private lateinit var timeoutController: BootCoverTimeoutController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlayManager = BootCoverOverlayManager(this, startupReadiness)
        timeoutController = BootCoverTimeoutController(
            context = this,
            handler = mainHandler,
            startupReadiness = startupReadiness,
            isOverlayAttached = { overlayManager.isOverlayAttached },
            onFallback = { isFallbackState = true },
            onMaximumDurationReached = { dismissInternal() }
        )
        activeInstance = WeakReference(this)
        createNotificationChannel()
        readinessScope.launch {
            startupReadiness.state.collect { state ->
                if (state.isReady) dismissInternal()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            Log.i(TAG, "Received ACTION_DISMISS intent -> dismissing boot cover immediately.")
            dismissInternal()
            return START_NOT_STICKY
        }

        // Start Foreground Service according to Android 14/15 specialUse requirements
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification: ${e.message}", e)
        }

        // RACE CONDITION CHECK 1: If the shield is verifiably back, do not show overlay!
        if (BootCoverPolicy.shouldAbortMount(startupReadiness.isReady())) {
            Log.i(TAG, "Protection startup is ready before mounting overlay. Dismissing immediately.")
            dismissInternal()
            return START_NOT_STICKY
        }

        // Condition 4: SYSTEM_ALERT_WINDOW permission verification
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show boot cover overlay: SYSTEM_ALERT_WINDOW permission not granted.")
            dismissInternal()
            return START_NOT_STICKY
        }

        showCoverOverlay()
        return START_NOT_STICKY
    }

    private fun showCoverOverlay() {
        if (overlayManager.hasOverlayView) return

        mainHandler.post {
            overlayManager.mountOverlay(
                isFallbackProvider = { isFallbackState },
                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                onDismissRequested = { dismissInternal() },
                onMounted = {
                    timeoutController.start()
                    armHealSentinel()
                }
            )
        }
    }

    /**
     * The cover is only a stop-gap: while it holds the screen the sentinel keeps trying to bring the
     * Accessibility binding back and releases the cover after the first rules snapshot is published.
     */
    private fun armHealSentinel() {
        if (sentinel == null) {
            sentinel = BootCoverSentinel(
                context = applicationContext,
                handler = mainHandler,
                startupReadiness = startupReadiness,
                onShieldNoLongerNeeded = { dismissInternal() }
            )
        }
        sentinel?.start()
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch accessibility settings: ${e.message}", e)
        }
    }

    fun dismissInternal() {
        mainHandler.post {
            sentinel?.stop()
            sentinel = null

            timeoutController.stop()

            overlayManager.dismissOverlay()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.d(TAG, "stopForeground error: ${e.message}")
            }

            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readinessScope.cancel()
        dismissInternal()
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        Log.i(TAG, "BootCoverService onDestroy completed.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusGuard Boot Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps device secure while FocusGuard services initialize on restart."
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusGuard Shield")
            .setContentText("Securing device after restart...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                pendingIntent?.let { setContentIntent(it) }
            }
            .build()
    }

    companion object {
        const val ACTION_DISMISS = "com.focusguard.app.service.BootCoverService.ACTION_DISMISS"

        @Volatile
        private var activeInstance: WeakReference<BootCoverService>? = null

        /**
         * Starts the BootCoverService while startup readiness is incomplete and overlay permission is granted.
         */
        fun start(context: Context) {
            val isProtectionReady = try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootCoverEntryPoint::class.java
                ).startupReadiness().isReady()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to read startup readiness before starting cover: ${e.message}", e)
                false
            }
            if (!BootCoverPolicy.shouldStartService(
                    isProtectionReady = isProtectionReady,
                    canDrawOverlays = Settings.canDrawOverlays(context)
                )
            ) {
                Log.d(TAG, "start: protection ready or SYSTEM_ALERT_WINDOW missing. Skipping BootCoverService.")
                return
            }

            try {
                val intent = Intent(context, BootCoverService::class.java)
                ContextCompat.startForegroundService(context, intent)
                Log.i(TAG, "BootCoverService start requested.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BootCoverService: ${e.message}", e)
            }
        }

        /**
         * Dismisses the cover overlay instantly and terminates the service.
         * Safe to call from any thread or context.
         */
        fun dismiss(context: Context) {
            activeInstance?.get()?.dismissInternal()

            try {
                val intent = Intent(context, BootCoverService::class.java).apply {
                    action = ACTION_DISMISS
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.d(TAG, "startService ACTION_DISMISS error: ${e.message}")
            }
        }
    }
}
