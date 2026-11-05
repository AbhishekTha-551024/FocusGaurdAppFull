package com.focusguard.app.capability

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.utils.BatteryOptimizationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CapabilityAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences
) {
    fun isAccessibilityGranted(): Boolean =
        AccessibilityRecoveryHelper.isAccessibilityConfiguredInSettings(context)

    fun isAccessibilityServiceConnected(): Boolean =
        AccessibilityRecoveryHelper.isAccessibilityHealthy(context)

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isOverlayGranted(): Boolean {
        val canDrawViaSettings = Settings.canDrawOverlays(context)
        if (!canDrawViaSettings) return false

        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            canDrawViaSettings
        }
    }

    fun isDeviceAdminGranted(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun isNotificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun isBatteryOptimizationIgnored(): Boolean =
        BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)

    fun isOemBackgroundConfigured(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isOemBrand = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                manufacturer.contains("poco") || manufacturer.contains("oppo") ||
                manufacturer.contains("realme") || manufacturer.contains("vivo") ||
                manufacturer.contains("iqoo") || manufacturer.contains("samsung") ||
                manufacturer.contains("oneplus") || manufacturer.contains("huawei") ||
                manufacturer.contains("honor")
        return if (!isOemBrand) true else
            (isBatteryOptimizationIgnored() || prefs.syncOemGuidanceAcknowledged)
    }
}
