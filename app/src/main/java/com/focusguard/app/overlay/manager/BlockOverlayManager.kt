/**
 * =====================================================================
 * FILE: BlockOverlayManager.kt
 * PACKAGE: com.focusguard.app.overlay.manager
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file FocusGuard application ka "Pure System Window Manager & Shield Controller" hai.
 * Iska single responsibility sirf Android WindowManager ke sath interact karna hai:
 *   - AccessibilityService window token se full-screen window attach/detach karna.
 *   - ServiceLifecycleOwner ke zariye ComposeView ki lifecycle manage karna.
 *   - Immersive mode aur Hardware Back/Home key interception ensure karna.
 *   - Dismiss hone par user ko safe Home screen redirection bhejna.
 * 
 * UI rendering ka kaam ab completely delegated hai:
 *   - Standard Block -> BlockOverlayContent (package: com.focusguard.app.overlay.standard)
 *   - Mindful Pause  -> MindfulBreathingContent (package: com.focusguard.app.overlay.mindful)
 * =====================================================================
 */

package com.focusguard.app.overlay.manager

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.overlay.mindful.MindfulBreathingContent
import com.focusguard.app.overlay.standard.BlockOverlayContent
import com.focusguard.app.overlay.wallpaper.OverlayWallpaperManager
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
        description: String? = null,
        customMessage: String? = null,
        sendHomeOnDismiss: Boolean = true,
        onAttached: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        Log.i(TAG, "showOverlay called: type=$blockType target=$targetName pkg=$sourcePackage duration=${durationSeconds}s isAlreadyShowing=$_isShowing")
        if (_isShowing) {
            Log.d(TAG, "showOverlay: Overlay is already showing, re-asserting and resetting timer for $sourcePackage.")
            dismissInternal("showOverlay_reassert")
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
        description: String? = null,
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
                dismissInternal("attachOverlay_cleanup")
            }
            wm.addView(view, params)
            currentOverlayView = view
            _isShowing = true
            Log.i(TAG, "addView SUCCESS: windowType=${params.type} view=$view hash=${view.hashCode()} with immersive system bars hidden.")
            view.post {
                applyImmersiveMode(view)
                val isAttached = view.isAttachedToWindow
                Log.i(TAG, "onAttached fired: view.isAttachedToWindow=$isAttached")
                onAttached()
            }
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "addView FAILED: WindowManager.BadTokenException attaching overlay — service may be recycling", e)
            dismissInternal("bad_token_exception")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "addView FAILED: WindowManager.IllegalStateException attaching overlay (view already added)", e)
            dismissInternal("illegal_state_exception")
        } catch (e: Exception) {
            Log.e(TAG, "addView FAILED: ${e.javaClass.simpleName} attaching overlay window: ${e.message}", e)
            dismissInternal("exception_${e.javaClass.simpleName}")
        }
    }

    fun dismissOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal("dismissOverlay_direct")
        } else {
            mainHandler.post { dismissInternal("dismissOverlay_posted") }
        }
    }

    private fun dismissInternal(callerReason: String = "unknown") {
        // 1. Cancel pending dismiss callback
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        // 2. Remove view synchronously from WindowManager
        currentOverlayView?.let { view ->
            try {
                (view as? ComposeView)?.disposeComposition()
                windowManager?.removeViewImmediate(view)
                Log.i(TAG, "Overlay removed: immediately from WindowManager (caller=$callerReason).")
            } catch (e: Exception) {
                try {
                    windowManager?.removeView(view)
                    Log.i(TAG, "Overlay removed: via fallback removeView (caller=$callerReason).")
                } catch (e2: Exception) {
                    Log.w(TAG, "Error removing overlay view: ${e2.message} (caller=$callerReason)")
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
