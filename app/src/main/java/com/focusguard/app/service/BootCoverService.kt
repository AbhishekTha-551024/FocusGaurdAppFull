package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.R
import com.focusguard.app.service.ui.BootCoverContent
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.ServiceLifecycleOwner
import java.lang.ref.WeakReference

/**
 * BootCoverService
 * ================
 * Temporary foreground service that displays a full-screen cover overlay immediately after device boot.
 *
 * Protection Rationale:
 * - Following a restart, Android's AccessibilityManager takes several seconds to bind to
 *   [FocusGuardAccessibilityService].
 * - During this gap, user could potentially access the launcher to uninstall or clone FocusGuard.
 * - This service mounts a TYPE_APPLICATION_OVERLAY window with full touch interception until
 *   [FocusGuardAccessibilityService.onServiceConnected] runs and signals [dismiss].
 *
 * Fallback & Safety:
 * - 35-second timer fallback: If the accessibility service fails to bind within 35 seconds,
 *   the overlay transitions to a recovery prompt with a functional "Turn ON Accessibility" button.
 * - Double race-condition verification: Checks [FocusGuardAccessibilityService.isServiceRunning]
 *   both immediately before and after mounting the overlay.
 * - Permission guard: Safely skips overlay mounting if SYSTEM_ALERT_WINDOW is not granted.
 */
class BootCoverService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var fallbackRunnable: Runnable? = null
    private var isFallbackState by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = WeakReference(this)
        createNotificationChannel()
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

        // RACE CONDITION CHECK 1: If Accessibility Service already connected, do not show overlay!
        if (FocusGuardAccessibilityService.isServiceRunning) {
            Log.i(TAG, "Accessibility Service is already active before mounting overlay. Dismissing immediately.")
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
        if (overlayView != null) return

        mainHandler.post {
            // RACE CONDITION CHECK 2 (on Main Looper): Check again before adding view
            if (FocusGuardAccessibilityService.isServiceRunning) {
                Log.i(TAG, "Accessibility Service connected while preparing view. Aborting overlay mount.")
                dismissInternal()
                return@post
            }

            try {
                val wm = (getSystemService(Context.WINDOW_SERVICE) as? WindowManager).also {
                    windowManager = it
                } ?: run {
                    Log.e(TAG, "WindowManager unavailable")
                    stopSelf()
                    return@post
                }

                val currentLifecycle = ServiceLifecycleOwner().also {
                    lifecycleOwner = it
                    it.start()
                }

                val composeView = ComposeView(this).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(currentLifecycle))
                    setViewTreeLifecycleOwner(currentLifecycle)
                    setViewTreeViewModelStoreOwner(currentLifecycle)
                    setViewTreeSavedStateRegistryOwner(currentLifecycle)

                    setContent {
                        FocusGuardTheme {
                            BootCoverContent(
                                isFallback = isFallbackState,
                                onOpenAccessibilitySettings = {
                                    openAccessibilitySettings()
                                }
                            )
                        }
                    }
                }

                val params = WindowManager.LayoutParams().apply {
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    format = PixelFormat.TRANSLUCENT
                    @Suppress("DEPRECATION")
                    flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                    gravity = Gravity.CENTER
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                wm.addView(composeView, params)
                overlayView = composeView
                isOverlayAttached = true
                Log.i(TAG, "Boot cover overlay successfully attached to WindowManager.")

                // RACE CONDITION CHECK 3: If accessibility connected during addView, remove immediately!
                if (FocusGuardAccessibilityService.isServiceRunning) {
                    Log.i(TAG, "Accessibility Service connected during view attachment. Removing overlay.")
                    dismissInternal()
                    return@post
                }

                // Schedule 35-second fallback transition
                scheduleFallbackTimer()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to mount boot cover overlay: ${e.message}", e)
                dismissInternal()
            }
        }
    }

    private fun scheduleFallbackTimer() {
        fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        val fallbackTask = Runnable {
            if (!FocusGuardAccessibilityService.isServiceRunning && overlayView != null) {
                Log.w(TAG, "35-second boot grace period elapsed without Accessibility connection -> displaying fallback action screen.")
                isFallbackState = true
            }
        }
        fallbackRunnable = fallbackTask
        mainHandler.postDelayed(fallbackTask, 35_000L)
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
            fallbackRunnable?.let { mainHandler.removeCallbacks(it) }
            fallbackRunnable = null

            overlayView?.let { view ->
                try {
                    windowManager?.removeView(view)
                    Log.i(TAG, "Boot cover overlay removed from WindowManager.")
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing boot cover overlay: ${e.message}")
                }
            }
            overlayView = null
            isOverlayAttached = false

            lifecycleOwner?.stop()
            lifecycleOwner = null

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
        private const val TAG = "BootCoverService"
        private const val CHANNEL_ID = "focusguard_boot_shield_channel"
        private const val NOTIFICATION_ID = com.focusguard.app.core.common.constants.NotificationIds.ID_BOOT_COVER_SHIELD

        const val ACTION_DISMISS = "com.focusguard.app.service.BootCoverService.ACTION_DISMISS"

        @Volatile
        var isOverlayAttached: Boolean = false
            private set

        @Volatile
        private var activeInstance: WeakReference<BootCoverService>? = null

        /**
         * Starts the BootCoverService if Accessibility is not yet running and overlay permission is granted.
         */
        fun start(context: Context) {
            if (FocusGuardAccessibilityService.isServiceRunning) {
                Log.d(TAG, "start: FocusGuardAccessibilityService is already running. Skipping BootCoverService.")
                return
            }
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "start: SYSTEM_ALERT_WINDOW permission not granted. Skipping BootCoverService.")
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

