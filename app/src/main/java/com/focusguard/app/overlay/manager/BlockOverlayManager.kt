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
import android.os.SystemClock
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

/** Poll interval while a held shield owns the screen. */
private const val HOLD_WATCH_INTERVAL_MS = 500L

/** Consecutive "blocked app not on screen" probes required before releasing a held shield. */
private const val HOLD_RELEASE_MISSES = 2

/**
 * A shield mounted during an app transition briefly sees no window for the blocked package at
 * all (launcher / OEM card events land in the same instant). Releases are ignored for this long
 * so the shield cannot dismiss itself seconds after appearing.
 */
private const val HOLD_MOUNT_GRACE_MS = 1500L

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
    private var holdWatchRunnable: Runnable? = null
    private var holdMissCount = 0
    private var holdMountedAtElapsed = 0L
    private var lastHoldCheckAtElapsed = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var _isShowing = false
    val isOverlayShowing: Boolean get() = _isShowing

    /**
     * Lower-case package that owns the currently held shield, or null when the overlay is a
     * transient one (countdown auto-dismiss). A held shield stays mounted while this package
     * is in the foreground and is released as soon as the user switches away from it.
     */
    @Volatile
    private var _holdPackage: String? = null
    val holdPackage: String? get() = _holdPackage

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
        holdUntilForegroundChanges: Boolean = false,
        onAttached: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        Log.i(TAG, "showOverlay called: type=$blockType target=$targetName pkg=$sourcePackage duration=${durationSeconds}s hold=$holdUntilForegroundChanges isAlreadyShowing=$_isShowing")
        if (_isShowing) {
            Log.d(TAG, "showOverlay: Overlay is already showing, re-asserting and resetting timer for $sourcePackage.")
            dismissInternal("showOverlay_reassert")
        }

        // Fast path: If already on Main Looper (typical for AccessibilityService events), execute immediately!
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOverlayInternal(
                durationSeconds, blockType, targetName, sourcePackage,
                description, customMessage, sendHomeOnDismiss, holdUntilForegroundChanges,
                onAttached, onDismissed
            )
        } else {
            mainHandler.post {
                showOverlayInternal(
                    durationSeconds, blockType, targetName, sourcePackage,
                    description, customMessage, sendHomeOnDismiss, holdUntilForegroundChanges,
                    onAttached, onDismissed
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
        holdUntilForegroundChanges: Boolean = false,
        onAttached: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        val service = serviceRef?.get() ?: run {
            Log.e(TAG, "Cannot show overlay: AccessibilityService context is null.")
            return
        }

        // Cleanly remove any existing overlay view first (100% synchronous)
        dismissInternal()

        val holdPackage = if (holdUntilForegroundChanges && !sourcePackage.isNullOrBlank()) {
            sourcePackage.lowercase()
        } else {
            null
        }

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
                        holdUntilForegroundChanges = holdPackage != null,
                        onDismiss = {
                            val wasHeld = holdPackage != null
                            dismissOverlay()
                            // Held shields only reach this point when the user explicitly taps
                            // "Leave this app" after the countdown; sending HOME is that action.
                            if (sendHomeOnDismiss || wasHeld) {
                                sendToHome(service)
                            }
                            onDismissed()
                        }
                    )
                }
            }
        }

        attachOverlay(service, composeView, allowHomeKey = holdPackage != null, onAttached = onAttached)
        // Publish the hold target only after the view was actually attached, so a failed
        // addView can never leave a phantom package that the guard tries to release.
        _holdPackage = if (_isShowing) holdPackage else null
        if (_holdPackage != null) startHoldWatcher()

        // Safety fallback timeout: auto-dismiss a transient overlay if the compose countdown
        // fails. Held shields are intentionally persistent, so they have no timer at all.
        if (holdPackage == null) {
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

        attachOverlay(service, composeView, onAttached = {})

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

    private fun attachOverlay(
        service: Context,
        view: View,
        allowHomeKey: Boolean = false,
        onAttached: () -> Unit = {}
    ) {
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
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    // A block screen that can be screenshotted or recorded is evidence about the
                    // user's own app usage, and its absence in a screenshot tells on them.
                    WindowManager.LayoutParams.FLAG_SECURE
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
            when {
                keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH -> {
                    Log.d(TAG, "Hardware key $keyCode consumed by BlockOverlay")
                    true
                }
                // Held block shields let the user leave with the system Home key. The overlay is
                // released by ActiveOverlayGuard as soon as the launcher becomes foreground.
                keyCode == KeyEvent.KEYCODE_HOME -> !allowHomeKey
                else -> false
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

    /**
     * Watches a held shield's package while the overlay owns the screen. Accessibility windows
     * are the only reliable source here: while the shield is focused, Home / app switches do not
     * always deliver a usable window-state event to the service.
     */
    private fun startHoldWatcher() {
        stopHoldWatcher()
        holdMissCount = 0
        holdMountedAtElapsed = SystemClock.elapsedRealtime()
        val watcher = object : Runnable {
            override fun run() {
                if (!_isShowing || _holdPackage.isNullOrBlank()) return
                if (releaseHeldShieldIfForegroundLeft()) return
                mainHandler.postDelayed(this, HOLD_WATCH_INTERVAL_MS)
            }
        }
        holdWatchRunnable = watcher
        mainHandler.postDelayed(watcher, HOLD_WATCH_INTERVAL_MS)
    }

    /**
     * Releases a held shield once its package has stopped owning an application window.
     *
     * Three guards prevent the "shield dismisses itself seconds after appearing" bug:
     * 1. A freshly mounted shield ignores the window probe entirely for [HOLD_MOUNT_GRACE_MS]
     *    (the blocked app's window is not yet enumerable during the launch transition, and
     *    launcher / OEM-card events land in that same instant).
     * 2. Evaluations are throttled to one per [HOLD_WATCH_INTERVAL_MS]. GUARD 0 calls this on
     *    every TYPE_WINDOWS_CHANGED event, so without throttling a single transient miss could
     *    stack into two "consecutive" misses within milliseconds.
     * 3. [HOLD_RELEASE_MISSES] throttled misses must agree before releasing, so a one-off
     *    window re-creation (rotation, activity transition) cannot drop the shield.
     *
     * @return true if a held shield was released.
     */
    fun releaseHeldShieldIfForegroundLeft(): Boolean {
        val heldPackage = _holdPackage ?: return false
        val service = serviceRef?.get() ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - holdMountedAtElapsed < HOLD_MOUNT_GRACE_MS) return false
        if (now - lastHoldCheckAtElapsed < HOLD_WATCH_INTERVAL_MS) return false
        lastHoldCheckAtElapsed = now
        if (ActiveWindowProbe.isPackageInForeground(service, heldPackage)) {
            holdMissCount = 0
            return false
        }
        holdMissCount++
        if (holdMissCount < HOLD_RELEASE_MISSES) return false
        Log.i(TAG, "Held shield: $heldPackage left the foreground -> releasing")
        dismissInternal("hold_foreground_changed")
        return true
    }

    private fun stopHoldWatcher() {
        holdWatchRunnable?.let { mainHandler.removeCallbacks(it) }
        holdWatchRunnable = null
    }

    fun dismissOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal("dismissOverlay_direct")
        } else {
            mainHandler.post { dismissInternal("dismissOverlay_posted") }
        }
    }

    private fun dismissInternal(callerReason: String = "unknown") {
        // 1. Cancel pending dismiss callback and the held-shield foreground watcher
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        stopHoldWatcher()

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
        _holdPackage = null
        holdMissCount = 0
    }

    private fun sendToHome(context: Context) {
        try {
            (context as? com.focusguard.app.service.FocusGuardAccessibilityService)?.sendGlobalAction(
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
