package com.focusguard.app.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

private const val TAG = "BatteryOptimization"

enum class OemBrand {
    VIVO,
    SAMSUNG,
    XIAOMI,
    ONEPLUS,
    OPPO_REALME,
    HUAWEI,
    GENERIC
}

object BatteryOptimizationHelper {

    fun getOemBrand(): OemBrand {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("vivo") || m.contains("iqoo") -> OemBrand.VIVO
            m.contains("samsung") -> OemBrand.SAMSUNG
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> OemBrand.XIAOMI
            m.contains("oneplus") -> OemBrand.ONEPLUS
            m.contains("oppo") || m.contains("realme") -> OemBrand.OPPO_REALME
            m.contains("huawei") || m.contains("honor") -> OemBrand.HUAWEI
            else -> OemBrand.GENERIC
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, opening generic settings", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                openApplicationDetailsSettings(context)
            }
        }
    }

    /**
     * Directly opens the most effective battery settings screen for the current device:
     * 1. If not yet exempted, prompts Android's native 1-tap ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog.
     * 2. If OEM-specific (Vivo, Xiaomi, Samsung, OnePlus), launches the manufacturer's background management page.
     * 3. Falls back to App Info where "Battery: Unrestricted" can be configured.
     */
    fun openDirectBatterySettings(context: Context) {
        try {
            Toast.makeText(context, getOemShortToast(), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show battery guidance toast: ${e.message}")
        }

        val isAlreadyIgnored = isBatteryOptimizationIgnored(context)
        if (!isAlreadyIgnored) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, opening OEM or settings", e)
            }
        }

        // Try OEM-specific page first
        val openedOem = openAutostartSettings(context)
        if (openedOem) return

        // Fallback: Generic battery optimization or app details
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            openApplicationDetailsSettings(context)
        }
    }

    fun openAutostartSettings(context: Context): Boolean {
        val brand = getOemBrand()
        val intents = when (brand) {
            OemBrand.VIVO -> listOf(
                Intent().apply {
                    component = ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.BackgroundHighUsageActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.vivo.abe", "com.vivo.abe.feature.power.ui.AppPowerManagerActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerManagerSettingsActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            OemBrand.XIAOMI -> listOf(
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent("miui.intent.action.OP_AUTO_START").apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            OemBrand.OPPO_REALME -> listOf(
                Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            OemBrand.ONEPLUS -> listOf(
                Intent().apply {
                    component = ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            OemBrand.SAMSUNG -> listOf(
                // Samsung Device Care battery / sleeping apps settings
                Intent().apply {
                    component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            OemBrand.HUAWEI -> listOf(
                Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            OemBrand.GENERIC -> listOf(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "OEM intent fallback failed: ${e.message}")
            }
        }

        // Final fallback: App Details
        openApplicationDetailsSettings(context)
        return true
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
        return when (getOemBrand()) {
            OemBrand.VIVO -> "Vivo / FuntouchOS"
            OemBrand.SAMSUNG -> "Samsung / One UI"
            OemBrand.XIAOMI -> "Xiaomi / MIUI / HyperOS"
            OemBrand.ONEPLUS -> "OnePlus / OxygenOS"
            OemBrand.OPPO_REALME -> if (Build.MANUFACTURER.lowercase().contains("realme")) "Realme / Realme UI" else "OPPO / ColorOS"
            OemBrand.HUAWEI -> "Huawei / EMUI"
            OemBrand.GENERIC -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    fun getOemShortName(): String {
        return when (getOemBrand()) {
            OemBrand.VIVO -> if (Build.MANUFACTURER.lowercase().contains("iqoo")) "iQOO" else "Vivo"
            OemBrand.SAMSUNG -> "Samsung"
            OemBrand.XIAOMI -> if (Build.MANUFACTURER.lowercase().contains("poco")) "Poco" else if (Build.MANUFACTURER.lowercase().contains("redmi")) "Redmi" else "Xiaomi"
            OemBrand.ONEPLUS -> "OnePlus"
            OemBrand.OPPO_REALME -> if (Build.MANUFACTURER.lowercase().contains("realme")) "Realme" else "OPPO"
            OemBrand.HUAWEI -> "Huawei"
            OemBrand.GENERIC -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    fun getOemProtectionTitle(): String {
        val shortName = getOemShortName()
        return if (isProblematicOem()) "$shortName Background Protection" else "Background Battery Protection"
    }

    fun getOemDialogHeadline(): String {
        val shortName = getOemShortName()
        return when (getOemBrand()) {
            OemBrand.VIVO -> "$shortName: Allow High Background Power"
            OemBrand.SAMSUNG -> "$shortName: Set Battery to Unrestricted"
            OemBrand.XIAOMI -> "$shortName: Enable Autostart & No Restrictions"
            OemBrand.ONEPLUS -> "$shortName: Enable Auto-Launch"
            OemBrand.OPPO_REALME -> "$shortName: Allow Background Activity"
            OemBrand.HUAWEI -> "$shortName: Manage Launch Manually"
            OemBrand.GENERIC -> "Background Protection: Unrestricted Battery"
        }
    }

    fun getOemWhyNeededText(): String {
        val shortName = getOemShortName()
        return "$shortName uses aggressive power saving that can terminate FocusGuard when the screen is locked. Setting battery usage to 'Unrestricted' guarantees focus protection stays active 24/7."
    }

    fun getOemActionSummary(): String {
        val shortName = getOemShortName()
        return when (getOemBrand()) {
            OemBrand.VIVO -> "Allow High Background Power & Auto-start so blocking stays active"
            OemBrand.SAMSUNG -> "Turn off 'Put to sleep' and set Battery to 'Unrestricted'"
            OemBrand.XIAOMI -> "Enable Autostart & select 'No restrictions' in Battery Saver"
            OemBrand.ONEPLUS -> "Enable Auto-launch & set Battery to 'Don't optimize'"
            OemBrand.OPPO_REALME -> "Enable Auto-launch & Allow Background Activity"
            OemBrand.HUAWEI -> "Set App Launch to 'Manage Manually' with all permissions"
            OemBrand.GENERIC -> "Set Battery to 'Unrestricted' so Android won't stop FocusGuard"
        }
    }

    fun getOemShortToast(): String {
        val shortName = getOemShortName()
        return when (getOemBrand()) {
            OemBrand.VIVO -> "Select 'Allow high background power consumption' for FocusGuard"
            OemBrand.SAMSUNG -> "Set FocusGuard Battery usage to 'Unrestricted'"
            OemBrand.XIAOMI -> "Enable Autostart and select 'No restrictions' in Battery Saver"
            OemBrand.ONEPLUS -> "Enable Auto-launch and set Battery to 'Don't optimize'"
            OemBrand.OPPO_REALME -> "Enable Auto-launch and Allow Background Activity"
            OemBrand.HUAWEI -> "Set FocusGuard App Launch to 'Manage manually'"
            OemBrand.GENERIC -> "Set FocusGuard Battery usage to 'Unrestricted'"
        }
    }

    fun isProblematicOem(): Boolean {
        val brand = getOemBrand()
        return brand != OemBrand.GENERIC
    }

    fun getOemStepInstructions(): List<String> {
        return when (getOemBrand()) {
            OemBrand.XIAOMI -> listOf(
                "Settings → Apps → Manage apps → FocusGuard → Autostart → Enable",
                "Settings → Battery → App battery saver → FocusGuard → No restrictions",
                "Recent Apps screen: Swipe down or long-press FocusGuard and tap 🔒 Lock"
            )
            OemBrand.OPPO_REALME -> listOf(
                "Settings → Battery → App Battery Management → FocusGuard → Allow background activity",
                "Settings → App Management → Auto-launch → FocusGuard → Enable",
                "Recent Apps screen: Tap 🔒 Lock icon on FocusGuard card"
            )
            OemBrand.VIVO -> listOf(
                "iManager → Permission Manager → Autostart → FocusGuard → Enable",
                "Settings → Battery → Background power consumption → FocusGuard → High background power consumption",
                "Recent Apps screen: Swipe down FocusGuard card and tap 🔒 Lock"
            )
            OemBrand.SAMSUNG -> listOf(
                "Settings → Apps → FocusGuard → Battery → Set to 'Unrestricted'",
                "Settings → Battery and device care → Battery → Background usage limits → Ensure FocusGuard is in 'Never sleeping apps'",
                "Recent Apps screen: Tap FocusGuard icon and select 'Lock this app'"
            )
            OemBrand.ONEPLUS -> listOf(
                "Settings → Apps → Auto-launch → FocusGuard → Enable",
                "Settings → Battery → Advanced settings → Optimize battery use → FocusGuard → Don't optimize",
                "Recent Apps screen: Tap 🔒 Lock on FocusGuard card"
            )
            OemBrand.HUAWEI -> listOf(
                "Settings → Battery → App launch → FocusGuard → Switch to 'Manage manually'",
                "Enable: Auto-launch, Secondary launch, and Run in background",
                "Recent Apps screen: Lock FocusGuard card"
            )
            OemBrand.GENERIC -> listOf(
                "Settings → Apps → FocusGuard → Battery → Set to 'Unrestricted'",
                "Keep FocusGuard active in Recent Apps screen"
            )
        }
    }
}
