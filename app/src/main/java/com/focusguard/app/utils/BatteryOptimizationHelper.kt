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
            } catch (_: Exception) {}
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

    fun getOemDialogHeadline(): String {
        return when (getOemBrand()) {
            OemBrand.VIVO -> "Vivo detected: Auto-start allow karein"
            OemBrand.SAMSUNG -> "Samsung detected: 'Put app to sleep' option OFF karein"
            OemBrand.XIAOMI -> "Xiaomi detected: Auto-start allow karein"
            OemBrand.ONEPLUS -> "OnePlus detected: Auto-launch enable karein"
            OemBrand.OPPO_REALME -> "${getOemShortName()} detected: Auto-launch allow karein"
            OemBrand.HUAWEI -> "Huawei detected: App launch 'Manual' set karein"
            OemBrand.GENERIC -> "Background Protection: 'Unrestricted' battery allow karein"
        }
    }

    fun getOemWhyNeededText(): String {
        val shortName = getOemShortName()
        return "$shortName aggressive battery saver use karta hai jo screen band hone par accessibility service ko kill kar deta hai. Phone restart ya low battery me bhi FocusGuard active rahe, iske liye yeh zaroori hai."
    }

    fun getOemActionSummary(): String {
        return when (getOemBrand()) {
            OemBrand.VIVO -> "Auto-start allow karein & High background power consumption enable karein"
            OemBrand.SAMSUNG -> "'Put app to sleep' OFF karein aur Battery 'Unrestricted' set karein"
            OemBrand.XIAOMI -> "Autostart ON karein aur Battery Saver me 'No restrictions' select karein"
            OemBrand.ONEPLUS -> "Auto-launch enable karein aur Battery optimization 'Don't optimize' karein"
            OemBrand.OPPO_REALME -> "Auto-launch aur Allow background activity ON karein"
            OemBrand.HUAWEI -> "App Launch ko 'Manage manually' set karein"
            OemBrand.GENERIC -> "Battery Optimization disable karein taaki app background me chal sake"
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
                "Recent Apps screen me FocusGuard card ko swipe-down / long-press karke 🔒 Lock karein"
            )
            OemBrand.OPPO_REALME -> listOf(
                "Settings → Battery → App Battery Management → FocusGuard → Allow background activity",
                "Settings → App Management → Auto-launch → FocusGuard → Enable",
                "Recent Apps screen me FocusGuard card par 🔒 Lock icon tap karein"
            )
            OemBrand.VIVO -> listOf(
                "iManager → Permission Manager → Autostart → FocusGuard → Enable",
                "Settings → Battery → Background power consumption → FocusGuard → High background power consumption",
                "Recent Apps screen me FocusGuard card ko swipe-down karke 🔒 Lock karein"
            )
            OemBrand.SAMSUNG -> listOf(
                "Settings → Apps → FocusGuard → Battery → Set to 'Unrestricted'",
                "Settings → Battery and device care → Battery → Background usage limits → Ensure FocusGuard is in 'Never sleeping apps'",
                "Recent Apps me FocusGuard card par tap karke 'Lock this app' select karein"
            )
            OemBrand.ONEPLUS -> listOf(
                "Settings → Apps → Auto-launch → FocusGuard → Enable",
                "Settings → Battery → Advanced settings → Optimize battery use → FocusGuard → Don't optimize",
                "Recent Apps me FocusGuard par 🔒 Lock tap karein"
            )
            OemBrand.HUAWEI -> listOf(
                "Settings → Battery → App launch → FocusGuard → Switch to 'Manage manually'",
                "Enable: Auto-launch, Secondary launch, and Run in background",
                "Recent Apps me FocusGuard card ko 🔒 Lock karein"
            )
            OemBrand.GENERIC -> listOf(
                "Settings → Apps → FocusGuard → Battery → Set to 'Unrestricted'",
                "Recent Apps me FocusGuard app ko swipe-away mat karein"
            )
        }
    }
}
