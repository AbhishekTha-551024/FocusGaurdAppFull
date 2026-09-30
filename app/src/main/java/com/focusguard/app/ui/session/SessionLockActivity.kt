package com.focusguard.app.ui.session

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.overlay.DeepFocusLockOverlayContent
import com.focusguard.app.service.FocusSessionService
import com.focusguard.app.ui.theme.FocusGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * SessionLockActivity:
 * Dedicated full-screen Kiosk Lock Screen for #blockit Deep Focus Lockdown.
 *
 * Guarantees:
 * 1. Clean native Activity touch handling (never drops touches on Call or Allowed Apps).
 * 2. Uninterrupted coroutine lifecycle for live reverse ticking countdown clock.
 * 3. Shows over lock screen (showWhenLocked) and turns screen on (turnScreenOn).
 * 4. Back button is strictly consumed.
 * 5. SingleTask launch mode prevents multiple instances and eliminates screen flickering.
 */
@AndroidEntryPoint
class SessionLockActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SessionLockActivity"

        fun start(context: Context) {
            try {
                val intent = Intent(context, SessionLockActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch SessionLockActivity", ex)
            }
        }
    }

    @Inject
    lateinit var sessionBlockManager: SessionBlockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Ensure lock screen shows over Keyguard when phone is woken up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        hideSystemBars()

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ||
                (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
            ) {
                hideSystemBars()
            }
        }

        // Strictly consume hardware back button to enforce lockout
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back navigation disabled during Deep Focus Lockdown
            }
        })

        setContent {
            FocusGuardTheme {
                val sessionState by sessionBlockManager.sessionStateFlow.collectAsState()

                // Exit automatically once session completes or is safely aborted
                LaunchedEffect(sessionState.isActive) {
                    if (!sessionState.isActive) {
                        finish()
                    }
                }

                if (sessionState.isActive) {
                    DeepFocusLockOverlayContent(
                        endTimeMillis = sessionState.endTimeMillis,
                        packedPackages = sessionState.packedPackages,
                        parachutesCount = sessionState.parachutesCount,
                        onLaunchPackage = { targetPkg ->
                            try {
                                isLaunchingAllowedTool = true
                                val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
                                if (launchIntent != null) {
                                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                    startActivity(launchIntent)
                                } else {
                                    isLaunchingAllowedTool = false
                                }
                            } catch (ex: Exception) {
                                isLaunchingAllowedTool = false
                                Log.e(TAG, "Error launching allowed package: $targetPkg", ex)
                            }
                        },
                        onLaunchDialer = {
                            try {
                                isLaunchingAllowedTool = true
                                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                }
                                if (dialIntent.resolveActivity(packageManager) != null) {
                                    startActivity(dialIntent)
                                } else {
                                    val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tel:")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(fallback)
                                }
                            } catch (ex: Exception) {
                                isLaunchingAllowedTool = false
                                Log.e(TAG, "Error opening dialer", ex)
                            }
                        },
                        onEmergencyAbort = {
                            val success = sessionBlockManager.emergencyAbortWithParachute()
                            if (success) {
                                FocusSessionService.stop(this@SessionLockActivity)
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private var isLaunchingAllowedTool = false

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.hide(WindowInsetsCompat.Type.statusBars())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
            }
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            event.keyCode == KeyEvent.KEYCODE_HOME
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            keyCode == KeyEvent.KEYCODE_HOME
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            keyCode == KeyEvent.KEYCODE_HOME
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        hideSystemBars()
        return super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (!isLaunchingAllowedTool && sessionBlockManager.isSessionCurrentlyActive()) {
            SessionLockActivity.start(this)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If the user tries to press HOME while on the Deep Focus lock screen (without launching an allowed tool):
        // Immediately re-assert SessionLockActivity without animation!
        if (!isLaunchingAllowedTool && sessionBlockManager.isSessionCurrentlyActive()) {
            val intent = Intent(this, SessionLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        isLaunchingAllowedTool = false
        hideSystemBars()
        if (!sessionBlockManager.isSessionCurrentlyActive()) {
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        } else if (!isLaunchingAllowedTool && sessionBlockManager.isSessionCurrentlyActive()) {
            try {
                @Suppress("WrongConstant")
                val statusBarService = getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val collapse = statusBarManager.getMethod("collapsePanels")
                collapse.invoke(statusBarService)
            } catch (_: Exception) {}
            // Re-assert SessionLockActivity
            SessionLockActivity.start(this)
        }
    }

    override fun onStop() {
        super.onStop()
        isLaunchingAllowedTool = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
