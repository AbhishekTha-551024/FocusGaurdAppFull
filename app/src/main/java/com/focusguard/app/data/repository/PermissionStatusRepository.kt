package com.focusguard.app.data.repository

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.utils.BatteryOptimizationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ProtectionPermissionStatus(
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isDeviceAdminGranted: Boolean = false,
    val isUsageAccessGranted: Boolean = false,
    val isUsbShieldProtected: Boolean = true,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isOemSetupCompleted: Boolean = true
) {
    val pendingCount: Int
        get() {
            var count = 0
            if (!isAccessibilityGranted) count++
            if (!isOverlayGranted) count++
            if (!isDeviceAdminGranted) count++
            if (!isUsageAccessGranted) count++
            if (!isUsbShieldProtected) count++
            if (!isBatteryOptimizationIgnored) count++
            if (!isOemSetupCompleted) count++
            return count
        }

    val isAllGranted: Boolean
        get() = pendingCount == 0
}

@Singleton
class PermissionStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    val capabilityManager: com.focusguard.app.capability.CapabilityManager
) {
    private val _status = MutableStateFlow(computeStatus())
    val status: StateFlow<ProtectionPermissionStatus> = _status.asStateFlow()

    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            refresh()
        }
    }

    private val accessibilityStateChangeListener = android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener {
        refresh()
    }

    init {
        try {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                contentObserver
            )
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            am?.addAccessibilityStateChangeListener(accessibilityStateChangeListener)

            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            appOps?.startWatchingMode(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.packageName
            ) { _, _ ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    refresh()
                }
            }
            appOps?.startWatchingMode(
                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                context.packageName
            ) { _, _ ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    refresh()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PermissionStatusRepo", "Failed to register observers", e)
        }
    }

    fun computeStatus(): ProtectionPermissionStatus {
        val isA11y = capabilityManager.isAccessibilityGranted()
        val isOverlay = capabilityManager.isOverlayGranted()
        val isAdmin = capabilityManager.isDeviceAdminGranted()
        val isUsage = capabilityManager.isUsageAccessGranted()

        // USB & ADB Tamper Shield
        val isOwner = FocusGuardDeviceAdminReceiver.isDeviceOwner(context)
        val isUsb = FocusGuardDeviceAdminReceiver.isUsbDebuggingEnabled(context)
        val isUsbShieldProtected = isOwner || !isUsb

        val isBatteryIgnored = capabilityManager.isBatteryOptimizationIgnored()
        val isOemCompleted = capabilityManager.isOemBackgroundConfigured()

        return ProtectionPermissionStatus(
            isAccessibilityGranted = isA11y,
            isOverlayGranted = isOverlay,
            isDeviceAdminGranted = isAdmin,
            isUsageAccessGranted = isUsage,
            isUsbShieldProtected = isUsbShieldProtected,
            isBatteryOptimizationIgnored = isBatteryIgnored,
            isOemSetupCompleted = isOemCompleted
        )
    }

    fun refresh() {
        capabilityManager.refresh()
        _status.value = computeStatus()
    }

    suspend fun acknowledgeOemGuidance() {
        prefs.setOemGuidanceAcknowledged(true)
        refresh()
    }
}
