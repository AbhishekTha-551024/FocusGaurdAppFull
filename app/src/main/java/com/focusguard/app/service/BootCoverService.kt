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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.R
import com.focusguard.app.ui.theme.Emerald400
import com.focusguard.app.ui.theme.Emerald500
import com.focusguard.app.ui.theme.Forest700
import com.focusguard.app.ui.theme.Forest800
import com.focusguard.app.ui.theme.Forest900
import com.focusguard.app.ui.theme.Forest950
import com.focusguard.app.ui.theme.ForestBorder
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.ui.theme.Rose400
import com.focusguard.app.ui.theme.White60
import com.focusguard.app.ui.theme.White90
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
        private const val NOTIFICATION_ID = 9905

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

@Composable
private fun BootCoverContent(
    isFallback: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Full screen container that absorbs all touch events
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Forest950,
                        Forest900,
                        Color(0xFF020B06)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume all background touches */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isFallback) {
                // NORMAL INITIALIZING STATE (< 35s)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Emerald500.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Emerald500.copy(alpha = 0.25f))
                            .border(2.dp, Emerald500.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Securing Device",
                            tint = Emerald400,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Surface(
                    color = Emerald500.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "BOOT VERIFICATION ACTIVE",
                        color = Emerald400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "FocusGuard Shield",
                    color = White90,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Securing your device after restart...\nStarting protection services.",
                    color = White60,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Emerald400,
                    strokeWidth = 3.dp
                )

            } else {
                // FALLBACK STATE (>= 35s elapsed without accessibility connecting)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Rose400.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Rose400.copy(alpha = 0.25f))
                            .border(2.dp, Rose400.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Action Required",
                            tint = Rose400,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = Rose400.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "ACTION REQUIRED",
                        color = Rose400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Turn On Accessibility",
                    color = White90,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Accessibility service has not connected after restart. Tap the button below to restore your protection shield.",
                    color = White60,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Condition 5: Working button to launch settings so user is never trapped
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Forest950
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TURN ON ACCESSIBILITY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
