package com.focusguard.app.feature.session.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.overlay.deepfocus.DeepFocusLockOverlayContent
import com.focusguard.app.service.FocusSessionService
import com.focusguard.app.ui.theme.FocusGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false
    private var isLaunchingAllowedTool = false

    private val phoneCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (state == TelephonyManager.EXTRA_STATE_IDLE && sessionBlockManager.isSessionCurrentlyActive()) {
                    mainHandler.postDelayed({
                        if (sessionBlockManager.isSessionCurrentlyActive() && !sessionBlockManager.isInPhoneCall()) {
                            SessionLockActivity.start(this@SessionLockActivity)
                        }
                    }, 500L)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        try {
            ContextCompat.registerReceiver(
                this,
                phoneCallReceiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register phoneCallReceiver: ${e.message}")
        }

        SessionLockWindowHelper.setupWindowFlags(this)
        SessionLockWindowHelper.hideSystemBars(window)

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ||
                (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
            ) {
                SessionLockWindowHelper.hideSystemBars(window)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back navigation strictly disabled during Deep Focus Lockdown
            }
        })

        setContent {
            FocusGuardTheme {
                val sessionState by sessionBlockManager.sessionStateFlow.collectAsState()

                LaunchedEffect(sessionState.isActive) {
                    if (!sessionState.isActive) finish()
                }

                if (sessionState.isActive) {
                    DeepFocusLockOverlayContent(
                        endTimeMillis = sessionState.endTimeMillis,
                        packedPackages = sessionState.packedPackages,
                        parachutesCount = sessionState.parachutesCount,
                        onLaunchPackage = { targetPkg -> launchAllowedTool(targetPkg) },
                        onLaunchDialer = { launchDialer() },
                        onEmergencyAbort = {
                            if (sessionBlockManager.emergencyAbortWithParachute()) {
                                FocusSessionService.stop(this@SessionLockActivity)
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun launchAllowedTool(targetPkg: String) {
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
    }

    private fun launchDialer() {
        isLaunchingAllowedTool = true
        if (!SessionDialerLauncher.launch(this)) {
            isLaunchingAllowedTool = false
        }
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
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH || keyCode == KeyEvent.KEYCODE_HOME) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_APP_SWITCH || keyCode == KeyEvent.KEYCODE_HOME) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        SessionLockWindowHelper.hideSystemBars(window)
        return super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (!isLaunchingAllowedTool && sessionBlockManager.isSessionCurrentlyActive() && !sessionBlockManager.isInPhoneCall()) {
            SessionLockActivity.start(this)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isLaunchingAllowedTool && sessionBlockManager.isSessionCurrentlyActive() && !sessionBlockManager.isInPhoneCall()) {
            SessionLockActivity.start(this)
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
        SessionLockWindowHelper.hideSystemBars(window)
        if (!sessionBlockManager.isSessionCurrentlyActive()) finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            SessionLockWindowHelper.hideSystemBars(window)
        } else if (!isLaunchingAllowedTool && sessionBlockManager.isSessionCurrentlyActive() && !sessionBlockManager.isInPhoneCall()) {
            try {
                @Suppress("WrongConstant")
                val statusBarService = getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val collapse = statusBarManager.getMethod("collapsePanels")
                collapse.invoke(statusBarService)
            } catch (e: Exception) {
                android.util.Log.d("SessionLockActivity", "StatusBar collapse reflection error: ${e.message}")
            }
            SessionLockActivity.start(this)
        }
    }

    override fun onStop() {
        super.onStop()
        isLaunchingAllowedTool = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(phoneCallReceiver)
            } catch (e: Exception) {
                android.util.Log.d("SessionLockActivity", "unregisterReceiver error: ${e.message}")
            }
            isReceiverRegistered = false
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
