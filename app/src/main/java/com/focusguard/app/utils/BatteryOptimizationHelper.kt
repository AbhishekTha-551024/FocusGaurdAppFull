package com.focusguard.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

private const val TAG = "BatteryOptimization"

object BatteryOptimizationHelper {

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, opening generic settings", e)
            openApplicationDetailsSettings(context)
        }
    }

    fun openAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            val vivoIntents = listOf(
                Intent().apply {
                    component = ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.BackgroundHighUsageActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerManagerSettingsActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            for (vIntent in vivoIntents) {
                try {
                    context.startActivity(vIntent)
                    return true
                } catch (_: Exception) {}
            }
            openApplicationDetailsSettings(context)
            return true
        }

        val intent = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            manufacturer.contains("samsung") -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "OEM-specific intent failed, opening application details", e)
            openApplicationDetailsSettings(context)
            false
        }
    }

    fun openApplicationDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open application details settings", e)
        }
    }

    fun getOemDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> "Xiaomi / MIUI / HyperOS"
            manufacturer.contains("oppo") -> "OPPO / ColorOS"
            manufacturer.contains("realme") -> "Realme / Realme UI"
            manufacturer.contains("oneplus") -> "OnePlus / OxygenOS"
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> "Vivo / FuntouchOS"
            manufacturer.contains("samsung") -> "Samsung / One UI"
            else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    fun isProblematicOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
                m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ||
                m.contains("vivo") || m.contains("iqoo") || m.contains("huawei") || m.contains("honor")
    }

    fun getOemStepInstructions(): List<String> {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
                "Settings → Apps → Manage apps → FocusGuard → Autostart → Enable",
                "Settings → Battery → App battery saver → FocusGuard → No restrictions"
            )
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> listOf(
                "Settings → Battery → App Battery Management → FocusGuard → Allow background activity",
                "Settings → App Management → Auto-launch → FocusGuard → Enable"
            )
            m.contains("vivo") || m.contains("iqoo") -> listOf(
                "Settings → Battery → Background app refresh → FocusGuard → Enable",
                "Settings → More Settings → Permission Manager → Autostart → FocusGuard → Enable"
            )
            m.contains("samsung") -> listOf(
                "Settings → Apps → FocusGuard → Battery → Set to 'Unrestricted'",
                "Settings → Battery and device care → Battery → Background usage limits → Ensure FocusGuard is in 'Never sleeping apps'"
            )
            else -> listOf(
                "Settings → Apps → FocusGuard → Battery → Set to 'Unrestricted'"
            )
        }
    }
}
