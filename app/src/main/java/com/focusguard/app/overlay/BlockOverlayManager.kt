package com.focusguard.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.ServiceLifecycleOwner
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FGBlockOverlay"

enum class OverlayBlockType {
    UNINSTALL_ATTEMPT,
    SETTINGS_TAMPER,
    APP_BLOCK,
    KEYWORD_BLOCK,
    SCHEDULE_BLOCK,
    TIME_LIMIT_BLOCK,
    SHORT_OR_REEL_BLOCK,
    INCOGNITO_BLOCK,
    CLONER_BLOCK,
    SESSION_LOCK_BLOCK
}

// ── Preset color palettes ──────────────────────────────────────────────────
private val Navy950    = Color(0xFF07140D)
private val Navy900    = Color(0xFF0B1F15)
private val Navy800    = Color(0xFF0E2419)
private val Indigo400  = Color(0xFF22C55E)
private val Purple400  = Color(0xFF4ADE80)
private val Amber400   = Color(0xFFFBBF24)
private val Rose400    = Color(0xFFFB7185)
private val White90    = Color(0xE6FFFFFF)
private val White60    = Color(0x99FFFFFF)
private val White40    = Color(0x66FFFFFF)

data class OverlayPreset(
    val id: Int,
    val name: String,
    val gradient: List<Color>
)

val OVERLAY_PRESETS = listOf(
    OverlayPreset(0, "Dark Gradient", listOf(Navy950, Navy900, Navy950)),
    OverlayPreset(1, "Deep Space",    listOf(Color(0xFF020818), Color(0xFF0D1B4B), Color(0xFF07112E))),
    OverlayPreset(2, "Mountain",      listOf(Color(0xFF0A1F0F), Color(0xFF1B3A2F), Color(0xFF0A1F0F))),
    OverlayPreset(3, "Minimal Dark",  listOf(Color(0xFF000000), Color(0xFF0F0F0F), Color(0xFF000000))),
    OverlayPreset(4, "Neon Calm",     listOf(Color(0xFF0D0D1A), Color(0xFF150D2E), Color(0xFF0D0D1A)))
)

@Singleton
class BlockOverlayManager @Inject constructor(
    private val wallpaperManager: OverlayWallpaperManager
) {

    private var serviceRef: WeakReference<AccessibilityService>? = null
    private var windowManager: WindowManager? = null
    private var currentOverlayView: View? = null
    private var currentLifecycleOwner: ServiceLifecycleOwner? = null
    private var dismissRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var _isShowing = false
    val isOverlayShowing: Boolean get() = _isShowing

    // Overlay theme settings (updated from ViewModel before showing)
    var currentPresetId: Int = 0
    var currentDimAlpha: Float = 0.55f

    fun setServiceContext(service: AccessibilityService) {
        serviceRef = WeakReference(service)
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "AccessibilityService context attached to BlockOverlayManager.")
    }

    fun clearServiceContext() {
        dismissOverlay()
        serviceRef = null
        windowManager = null
    }

    fun showOverlay(
        durationSeconds: Int = 5,
        blockType: OverlayBlockType = OverlayBlockType.UNINSTALL_ATTEMPT,
        targetName: String = "FocusGuard Protection",
        sourcePackage: String? = null,
        description: String = "Action protected by friction delay.",
        customMessage: String? = null,
        sendHomeOnDismiss: Boolean = true,
        onAttached: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (_isShowing) {
            Log.d(TAG, "showOverlay: Overlay is already showing, re-asserting and resetting timer for $sourcePackage.")
            dismissInternal()
        }

        // Fast path: If already on Main Looper (typical for AccessibilityService events), execute immediately!
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOverlayInternal(
                durationSeconds, blockType, targetName, sourcePackage,
                description, customMessage, sendHomeOnDismiss, onAttached, onDismissed
            )
        } else {
            mainHandler.post {
                showOverlayInternal(
                    durationSeconds, blockType, targetName, sourcePackage,
                    description, customMessage, sendHomeOnDismiss, onAttached, onDismissed
                )
            }
        }
    }

    private fun showOverlayInternal(
        durationSeconds: Int,
        blockType: OverlayBlockType,
        targetName: String,
        sourcePackage: String?,
        description: String = "",
        customMessage: String? = null,
        sendHomeOnDismiss: Boolean = true,
        onAttached: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        val service = serviceRef?.get() ?: run {
            Log.e(TAG, "Cannot show overlay: AccessibilityService context is null.")
            return
        }

        // Cleanly remove any existing overlay view first (100% synchronous)
        dismissInternal()

        val lifecycleOwner = ServiceLifecycleOwner()
        currentLifecycleOwner = lifecycleOwner
        lifecycleOwner.start()

        val wallpaperBitmap = wallpaperManager.getBitmapOrNull()
        val presetId = currentPresetId
        val dimAlpha = currentDimAlpha

        val composeView = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FocusGuardTheme {
                    BlockOverlayContent(
                        durationSeconds = durationSeconds,
                        blockType = blockType,
                        targetName = targetName,
                        contextLine = description,
                        customMessage = customMessage,
                        wallpaperBitmap = wallpaperBitmap,
                        presetId = presetId,
                        dimAlpha = dimAlpha,
                        onDismiss = {
                            dismissOverlay()
                            if (sendHomeOnDismiss) {
                                sendToHome(service)
                            }
                            onDismissed()
                        }
                    )
                }
            }
        }

        attachOverlay(service, composeView, onAttached)

        // Safety fallback timeout: auto-dismiss if compose countdown fails to dismiss
        val maxWaitMs = ((durationSeconds + 5) * 1000L).coerceIn(10_000L, 60_000L)
        val safetyTimeout = Runnable {
            if (_isShowing) {
                Log.w(TAG, "Safety fallback timeout triggered ($maxWaitMs ms) -> force dismissing overlay.")
                dismissInternal()
                if (sendHomeOnDismiss) {
                    sendToHome(service)
                }
                onDismissed()
            }
        }
        dismissRunnable = safetyTimeout
        mainHandler.postDelayed(safetyTimeout, maxWaitMs)
    }

    fun showMindfulPauseOverlay(
        packageName: String,
        appName: String,
        breathingDurationSeconds: Int = 6,
        isReturning: Boolean = false,
        minutesSinceLastSession: Int = 0,
        todayOpenCount: Int = 1,
        onAllowanceGranted: (durationMinutes: Int, intentText: String, category: String) -> Unit,
        onWalkAway: () -> Unit
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showMindfulPauseInternal(packageName, appName, breathingDurationSeconds, isReturning, minutesSinceLastSession, todayOpenCount, onAllowanceGranted, onWalkAway)
        } else {
            mainHandler.post {
                showMindfulPauseInternal(packageName, appName, breathingDurationSeconds, isReturning, minutesSinceLastSession, todayOpenCount, onAllowanceGranted, onWalkAway)
            }
        }
    }

    private fun showMindfulPauseInternal(
        packageName: String,
        appName: String,
        breathingDurationSeconds: Int,
        isReturning: Boolean,
        minutesSinceLastSession: Int,
        todayOpenCount: Int,
        onAllowanceGranted: (durationMinutes: Int, intentText: String, category: String) -> Unit,
        onWalkAway: () -> Unit
    ) {
        val service = serviceRef?.get() ?: run {
            Log.e(TAG, "Cannot show mindful pause overlay: AccessibilityService context is null.")
            return
        }

        dismissInternal()

        val lifecycleOwner = ServiceLifecycleOwner()
        currentLifecycleOwner = lifecycleOwner
        lifecycleOwner.start()

        val composeView = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FocusGuardTheme {
                    MindfulBreathingContent(
                        packageName = packageName,
                        appName = appName,
                        breathingDurationSeconds = breathingDurationSeconds,
                        isReturning = isReturning,
                        minutesSinceLastSession = minutesSinceLastSession,
                        todayOpenCount = todayOpenCount,
                        onContinueWithAllowance = { mins, intentText, cat ->
                            dismissOverlay()
                            onAllowanceGranted(mins, intentText, cat)
                        },
                        onWalkAway = {
                            dismissOverlay()
                            onWalkAway()
                        }
                    )
                }
            }
        }

        attachOverlay(service, composeView) {}

        val safetyTimeout = Runnable {
            if (_isShowing) {
                dismissInternal()
                onWalkAway()
            }
        }
        dismissRunnable = safetyTimeout
        mainHandler.postDelayed(safetyTimeout, 180_000L)
    }

    private fun applyImmersiveMode(view: View) {
        view.fitsSystemWindows = false
        @Suppress("DEPRECATION")
        view.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
            }
        }
    }

    private fun attachOverlay(service: Context, view: View, onAttached: () -> Unit) {
        val wm = windowManager ?: (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
            windowManager = it
        }
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
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
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        applyImmersiveMode(view)

        @Suppress("DEPRECATION")
        view.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0 ||
                (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0
            ) {
                applyImmersiveMode(view)
            }
        }

        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_HOME
            ) {
                Log.d(TAG, "Hardware key $keyCode consumed by BlockOverlay")
                true
            } else {
                false
            }
        }

        try {
            // Defensively ensure any previously attached view is detached first to prevent "View already added" crash
            if (currentOverlayView != null) {
                dismissInternal()
            }
            wm.addView(view, params)
            currentOverlayView = view
            _isShowing = true
            Log.i(TAG, "TYPE_ACCESSIBILITY_OVERLAY attached successfully with immersive system bars hidden.")
            // FIX NH5: Defer onAttached by one Choreographer frame (≈8ms on 120Hz).
            // wm.addView() schedules the overlay for the next VSYNC — calling onAttached()
            // synchronously meant GLOBAL_ACTION_HOME fired before the overlay pixel was
            // on screen, creating a brief PiP window gap on 120Hz OEM displays.
            view.post {
                applyImmersiveMode(view)
                onAttached()
            }
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "WindowManager BadTokenException attaching overlay — service may be recycling", e)
            dismissInternal()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "WindowManager IllegalStateException attaching overlay (view already added)", e)
            dismissInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach overlay window", e)
            dismissInternal()
        }
    }

    fun dismissOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal()
        } else {
            mainHandler.post { dismissInternal() }
        }
    }

    private fun dismissInternal() {
        // 1. Cancel pending dismiss callback
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        // 2. Remove view synchronously from WindowManager
        currentOverlayView?.let { view ->
            try {
                (view as? ComposeView)?.disposeComposition()
                windowManager?.removeViewImmediate(view)
                Log.i(TAG, "Overlay view removed immediately from WindowManager.")
            } catch (e: Exception) {
                try {
                    windowManager?.removeView(view)
                } catch (e2: Exception) {
                    Log.w(TAG, "Error removing overlay view: ${e2.message}")
                }
            }
        }
        currentOverlayView = null

        // 3. Safely stop and clear lifecycle
        try {
            currentLifecycleOwner?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping lifecycle owner: ${e.message}")
        }
        currentLifecycleOwner = null

        _isShowing = false
    }

    private fun sendToHome(context: Context) {
        try {
            (context as? AccessibilityService)?.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME
            )
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(homeIntent)
            Log.i(TAG, "Sent user to Home screen after overlay time ended.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to Home screen: ${e.message}", e)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Block Overlay Content — Full-screen with custom wallpaper / preset
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun BlockOverlayContent(
    durationSeconds: Int,
    blockType: OverlayBlockType,
    targetName: String,
    contextLine: String,
    customMessage: String? = null,
    wallpaperBitmap: Bitmap? = null,
    presetId: Int = 0,
    dimAlpha: Float = 0.55f,
    onDismiss: () -> Unit = {}
) {
    val (headline, accentColor, icon) = when (blockType) {
        OverlayBlockType.UNINSTALL_ATTEMPT -> Triple("Uninstall Protected", Rose400,    Icons.Default.Security)
        OverlayBlockType.SETTINGS_TAMPER   -> Triple("Settings Protected",  Rose400,    Icons.Default.Lock)
        OverlayBlockType.APP_BLOCK         -> Triple("App Blocked",         Indigo400,  Icons.Default.Block)
        OverlayBlockType.KEYWORD_BLOCK     -> Triple("Keyword Blocked",     Rose400,    Icons.Default.Language)
        OverlayBlockType.SCHEDULE_BLOCK    -> Triple("Focus Schedule",      Amber400,   Icons.Default.Schedule)
        OverlayBlockType.TIME_LIMIT_BLOCK  -> Triple("Daily Limit Reached", Purple400,  Icons.Default.HourglassTop)
        OverlayBlockType.SHORT_OR_REEL_BLOCK -> {
            val title = when {
                targetName.contains("Shorts", ignoreCase = true) -> "Shorts Blocked"
                targetName.contains("Reels", ignoreCase = true) -> "Reels Blocked"
                else -> "Shorts & Reels Blocked"
            }
            Triple(title, Rose400, Icons.Default.PlayCircle)
        }
        OverlayBlockType.INCOGNITO_BLOCK   -> Triple("Incognito Blocked",   Rose400,    Icons.Default.VisibilityOff)
        OverlayBlockType.CLONER_BLOCK      -> Triple("App Cloner Blocked",  Rose400,    Icons.Default.Security)
        OverlayBlockType.SESSION_LOCK_BLOCK -> Triple("Deep Focus Lockdown", Amber400,   Icons.Default.Lock)
    }

    val preset = OVERLAY_PRESETS.getOrElse(presetId) { OVERLAY_PRESETS[0] }

    // Countdown and animation state
    val secondsLeft = remember(durationSeconds) { mutableIntStateOf(durationSeconds) }
    val isTimeUp = remember(durationSeconds) { mutableStateOf(durationSeconds <= 0) }
    val animatedProgress = remember(durationSeconds) { Animatable(1.0f) }

    // 1. Arc smooth 120 FPS animation (runs only in Draw phase for maximum hardware acceleration)
    LaunchedEffect(durationSeconds) {
        if (durationSeconds > 0) {
            animatedProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationSeconds * 1000,
                    easing = LinearEasing
                )
            )
        } else {
            animatedProgress.snapTo(0f)
        }
    }

    // 2. Reliable 1-second countdown loop (updates secondsLeft, enables Close button, and auto-dismisses)
    LaunchedEffect(durationSeconds) {
        if (durationSeconds > 0) {
            for (sec in durationSeconds downTo 1) {
                secondsLeft.intValue = sec
                kotlinx.coroutines.delay(1000L)
            }
        }
        secondsLeft.intValue = 0
        isTimeUp.value = true
        // Auto-dismiss after 600ms grace period so overlay doesn't hang forever blocking accessibility events
        kotlinx.coroutines.delay(600L)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {

        // ── LAYER 1: Background (custom image OR preset gradient) ──────────────
        if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled) {
            androidx.compose.foundation.Image(
                bitmap = wallpaperBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(preset.gradient)
                    )
            )
        }

        // ── LAYER 2: Glassmorphism scrim ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = dimAlpha * 0.7f),
                            Color.Black.copy(alpha = dimAlpha),
                            Color.Black.copy(alpha = dimAlpha * 0.9f)
                        )
                    )
                )
        )

        // ── LAYER 3: Content ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // CENTER: Countdown ring + info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Accent Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Text(text = headline, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Countdown Ring
                CountdownRing(
                    secondsLeft = secondsLeft.intValue,
                    progressProvider = { animatedProgress.value },
                    accentColor = accentColor
                )

                // Target name + description
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (blockType == OverlayBlockType.KEYWORD_BLOCK || blockType == OverlayBlockType.INCOGNITO_BLOCK || blockType == OverlayBlockType.CLONER_BLOCK || blockType == OverlayBlockType.SESSION_LOCK_BLOCK) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = when (blockType) {
                                    OverlayBlockType.INCOGNITO_BLOCK -> "PRIVATE BROWSING"
                                    OverlayBlockType.CLONER_BLOCK -> "CLONED APP DETECTED"
                                    OverlayBlockType.SESSION_LOCK_BLOCK -> "PHONE LOCKED DOWN"
                                    else -> "BLOCKED KEYWORD"
                                },
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (blockType == OverlayBlockType.KEYWORD_BLOCK) "“$targetName”" else targetName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = contextLine,
                        color = White90,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Exit / Close Overlay Button
            // Locked while countdown is active; enabled as soon as time is up!
            Button(
                onClick = {
                    if (isTimeUp.value) {
                        onDismiss()
                    }
                },
                enabled = isTimeUp.value,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTimeUp.value) accentColor else accentColor.copy(alpha = 0.18f),
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    disabledContentColor = White40
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (isTimeUp.value) accentColor else Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = if (isTimeUp.value) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isTimeUp.value) Color.White else White40,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isTimeUp.value) "Close Overlay" else "Wait (${secondsLeft.intValue}s)",
                    color = if (isTimeUp.value) Color.White else White40,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // BOTTOM: Motivational breathing line or user custom message
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                BreathingDots(accentColor = accentColor)
                Text(
                    text = when {
                        !customMessage.isNullOrBlank() -> customMessage
                        isTimeUp.value -> "Time complete. Tap 'Close Overlay' to return."
                        else -> "Take a breath. Resetting your focus..."
                    },
                    color = if (!customMessage.isNullOrBlank() || isTimeUp.value) Color.White.copy(alpha = 0.85f) else White40,
                    fontSize = if (!customMessage.isNullOrBlank() || isTimeUp.value) 13.sp else 12.sp,
                    fontWeight = if (!customMessage.isNullOrBlank() || isTimeUp.value) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CountdownRing(
    secondsLeft: Int,
    progressProvider: () -> Float,
    accentColor: Color
) {
    Box(
        modifier = Modifier.size(170.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            // Track ring
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc: reads progressProvider() directly in Draw phase for 120 FPS
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progressProvider(),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center number
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (secondsLeft > 0) "$secondsLeft" else "✓",
                color = Color.White,
                fontSize = if (secondsLeft > 0) 48.sp else 52.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (secondsLeft > 0) "SECONDS" else "COMPLETED",
                color = if (secondsLeft > 0) White40 else accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun BreathingDots(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, delayMillis = 200), RepeatMode.Reverse),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, delayMillis = 400), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(alpha1, alpha2, alpha3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = alpha))
            )
        }
    }
}
