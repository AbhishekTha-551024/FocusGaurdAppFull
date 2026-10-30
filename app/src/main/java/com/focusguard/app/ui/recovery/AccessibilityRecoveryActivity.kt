package com.focusguard.app.ui.recovery

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.ui.theme.*
import com.focusguard.app.utils.BatteryOptimizationHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * AccessibilityRecoveryActivity
 * =============================
 * Immediate, full-screen recovery interface displayed when device restart or OEM battery kill
 * disables the FocusGuard Accessibility Service while protection is active.
 *
 * Guarantees:
 * 1. Appears over lockscreen (showWhenLocked) and turns screen on.
 * 2. Provides 1-tap deep-link to restore the Accessibility toggle.
 * 3. Shows device-specific instructions (e.g. Vivo/iQOO) to permanently fix reboot disablement.
 * 4. Automatically dismisses itself as soon as Accessibility is restored.
 * 5. Consumes back button to prevent circumventing protection.
 */
@AndroidEntryPoint
class AccessibilityRecoveryActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_REASON = "extra_reason"

        fun start(context: Context, reason: String = "") {
            if (!AccessibilityRecoveryHelper.isProtectionActive(context)) {
                android.util.Log.i("AccessibilityRecovery", "Protection is inactive, skipping start of AccessibilityRecoveryActivity")
                return
            }
            try {
                val intent = Intent(context, AccessibilityRecoveryActivity::class.java).apply {
                    putExtra(EXTRA_REASON, reason)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val bOptions = android.app.ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    try {
                        pendingIntent.send(context, 0, null, null, null, null, bOptions.toBundle())
                    } catch (_: Exception) {
                        context.startActivity(intent, bOptions.toBundle())
                    }
                } else {
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AccessibilityRecovery", "Failed to start AccessibilityRecoveryActivity", e)
            }
        }
    }

    @javax.inject.Inject
    lateinit var repository: com.focusguard.app.data.repository.FocusGuardRepository

    private var hasNavigatedToSettings = false
    private var settingsGuardJob: Job? = null

    private fun startSettingsGuard() {
        if (settingsGuardJob?.isActive == true) return
        settingsGuardJob = lifecycleScope.launch(Dispatchers.IO) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
            val isAdminBlock = reason.contains("Admin", ignoreCase = true)
            while (isActive && (isAdminBlock || !AccessibilityRecoveryHelper.isAccessibilityActive(this@AccessibilityRecoveryActivity))) {
                delay(100L)
                val now = System.currentTimeMillis()
                val events = usm?.queryEvents(now - 2000L, now)
                var topPkg: String? = null
                var topClass: String? = null
                if (events != null) {
                    val event = android.app.usage.UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                            topPkg = event.packageName
                            topClass = event.className
                        }
                    }
                }
                val isDangerous = topPkg == "com.android.settings" ||
                        topPkg == "com.google.android.packageinstaller" ||
                        topPkg == "com.android.packageinstaller" ||
                        topPkg == "com.vivo.doubleinstance" ||
                        topPkg == "com.vivo.abe"
                if (isDangerous) {
                    val isA11yScreen = topClass?.contains("Accessibility", ignoreCase = true) == true
                    if (!isA11yScreen && topClass != null) {
                        android.util.Log.w("AccessibilityRecovery", "Tamper attempt detected in $topPkg ($topClass)! Pulling recovery shield back to front.")
                        val intent = Intent(this@AccessibilityRecoveryActivity, AccessibilityRecoveryActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isProtected = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver.isDeviceAdminActive(this) &&
                repository.isFastUninstallProtectionEnabled()
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val isAdminBlock = reason.contains("Admin", ignoreCase = true)
        if (!isProtected || (!isAdminBlock && AccessibilityRecoveryHelper.isAccessibilityActive(this))) {
            finish()
            return
        }
        startSettingsGuard()
        enableEdgeToEdge()

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (AccessibilityRecoveryHelper.isAccessibilityActive(this@AccessibilityRecoveryActivity)) {
                    finish()
                } else {
                    Toast.makeText(
                        this@AccessibilityRecoveryActivity,
                        "⚠️ Accessibility must be turned ON to resume FocusGuard protection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        setContent {
            FocusGuardTheme {
                AccessibilityRecoveryScreen(
                    onRestoreClick = {
                        hasNavigatedToSettings = true
                        AccessibilityRecoveryHelper.openAccessibilitySettings(this@AccessibilityRecoveryActivity)
                    },
                    onOemFixClick = {
                        BatteryOptimizationHelper.openAutostartSettings(this@AccessibilityRecoveryActivity)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isProtected = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver.isDeviceAdminActive(this) &&
                repository.isFastUninstallProtectionEnabled()
        if (!isProtected) {
            finish()
            return
        }
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val isAdminBlock = reason.contains("Admin", ignoreCase = true)
        // If the user turned Accessibility ON and not in Admin Block mode, finish and confirm
        if (!isAdminBlock && AccessibilityRecoveryHelper.isAccessibilityActive(this)) {
            Toast.makeText(this, "🛡️ FocusGuard Protection Restored!", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
