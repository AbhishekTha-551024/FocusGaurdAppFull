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
import androidx.lifecycle.lifecycleScope
import com.focusguard.app.domain.policy.AccessibilityRecoveryPolicy
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.BatteryOptimizationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AccessibilityRecoveryActivity
 * =============================
 * Immediate, full-screen recovery interface displayed when device restart or OEM battery kill
 * disables the FocusGuard Accessibility Service while protection is active.
 *
 * Guarantees:
 * 1. Appears over lockscreen (showWhenLocked) and turns screen on.
 * 2. Opens the Android Accessibility screen where the toggle can be switched back on. It does not
 *    claim to restore the toggle itself — silent restore happens earlier, in
 *    [AccessibilityRecoveryHelper.restoreProtectionFast].
 * 3. Shows device-specific instructions (e.g. Vivo/iQOO) to permanently fix reboot disablement.
 * 4. Automatically dismisses itself only after the required service(s) are healthy again.
 * 5. Consumes back button to prevent circumventing protection.
 * 6. Words the shield according to the real [RecoveryCause], not a blanket "restart" claim.
 */
@AndroidEntryPoint
class AccessibilityRecoveryActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_REASON = "extra_reason"
        private const val EXTRA_ALLOW_ADMIN_REACTIVATION = "extra_allow_admin_reactivation"
        private const val RESTORED_MESSAGE = "🛡️ FocusGuard Protection Restored!"

        fun start(
            context: Context,
            reason: String = "",
            allowAdminReactivation: Boolean = false
        ) {
            if (!allowAdminReactivation && !AccessibilityRecoveryHelper.isProtectionActive(context)) {
                android.util.Log.i("AccessibilityRecovery", "Protection is inactive, skipping start of AccessibilityRecoveryActivity")
                return
            }
            try {
                val intent = Intent(context, AccessibilityRecoveryActivity::class.java).apply {
                    putExtra(EXTRA_REASON, reason)
                    putExtra(EXTRA_ALLOW_ADMIN_REACTIVATION, allowAdminReactivation)
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

    private var settingsGuardJob: Job? = null

    /** Classified once per intent so the guard and every label agree about the reason. */
    private val cause: RecoveryCause by lazy {
        RecoveryCause.fromReason(intent.getStringExtra(EXTRA_REASON) ?: "")
    }

    private val isAdminReactivationRecovery: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_ALLOW_ADMIN_REACTIVATION, false) &&
                cause.requiresAdminReactivation
    }

    private fun isRecoveryProtected(): Boolean {
        val protectionStored = repository.isFastUninstallProtectionEnabled()
        val adminActive = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
            .isDeviceAdminActive(this)
        return protectionStored && (adminActive || isAdminReactivationRecovery)
    }

    private fun canDismissRecovery(): Boolean =
        AccessibilityRecoveryPolicy.canDismissRecovery(
            isAccessibilityHealthy = AccessibilityRecoveryHelper.isAccessibilityHealthy(this),
            isAdminBlock = cause.isAdminBlock,
            requiresAdminReactivation = isAdminReactivationRecovery,
            isDeviceAdminActive = com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
                .isDeviceAdminActive(this)
        )

    private fun startSettingsGuard() {
        if (settingsGuardJob?.isActive == true) return
        settingsGuardJob = lifecycleScope.launch(Dispatchers.IO) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            while (isActive && !canDismissRecovery()) {
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
                if (isDangerous && !isAdminReactivationRecovery) {
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
            if (isActive && canDismissRecovery()) {
                android.util.Log.i("AccessibilityRecovery", "Required recovery services are restored. Dismissing recovery shield.")
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@AccessibilityRecoveryActivity, RESTORED_MESSAGE, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isRecoveryProtected() || canDismissRecovery()) {
            finish()
            return
        }
        startSettingsGuard()
        enableEdgeToEdge()
        // Shield content stays out of screenshots, screen recording and the Recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

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
                if (canDismissRecovery()) {
                    finish()
                } else {
                    Toast.makeText(
                        this@AccessibilityRecoveryActivity,
                        backBlockedMessage(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        setContent {
            FocusGuardTheme {
                AccessibilityRecoveryScreen(
                    cause = cause,
                    onRestoreClick = {
                        AccessibilityRecoveryHelper.openAccessibilitySettings(this@AccessibilityRecoveryActivity)
                    },
                    onOemFixClick = {
                        BatteryOptimizationHelper.openAutostartSettings(this@AccessibilityRecoveryActivity)
                    },
                    onAdminRestoreClick = {
                        openDeviceAdminActivation()
                    }
                )
            }
        }
    }

    private fun backBlockedMessage(): String = if (cause.isAdminBlock) {
        "Device Admin must be restored before FocusGuard can resume protection."
    } else {
        "⚠️ Accessibility must be turned ON to resume FocusGuard protection."
    }

    private fun openDeviceAdminActivation() {
        try {
            val component = android.content.ComponentName(
                this,
                com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver::class.java
            )
            startActivity(
                Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        com.focusguard.app.recovery.ADMIN_REASK_EXPLANATION
                    )
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("AccessibilityRecovery", "Failed to open Device Admin activation", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isRecoveryProtected()) {
            finish()
            return
        }
        // A Settings toggle can be ON before Android has bound the service. Only a healthy service
        // can dismiss recovery and show the restored confirmation.
        if (canDismissRecovery()) {
            Toast.makeText(this, RESTORED_MESSAGE, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
