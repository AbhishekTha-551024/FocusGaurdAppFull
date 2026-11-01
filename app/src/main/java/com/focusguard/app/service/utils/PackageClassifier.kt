package com.focusguard.app.service.utils

import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.utils.LauncherDetector
import com.focusguard.app.core.common.constants.OemPackages

/**
 * Utility for classifying Android package names into categories such as:
 * - Software keyboards / IME
 * - Transient system overlays (toasts, volume sliders, screenshot tools)
 * - System tools & launchers (dialer, clock, home launchers)
 * - System Settings and OEM security centers
 * - Package installers & uninstaller activities
 */
object PackageClassifier {

    fun isKeyboardPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("honeyboard") ||
                lower.contains("swiftkey") ||
                lower.contains("gboard")
    }

    fun isTransientSystemPackage(pkg: String, isProtectionActive: Boolean = false): Boolean {
        val lower = pkg.lowercase()
        if (isProtectionActive && (lower.contains("permissioncontroller") || lower == OemPackages.ANDROID)) {
            return false
        }
        return lower == OemPackages.ANDROID ||
                lower == OemPackages.SYSTEM_UI ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.google.android.gms") ||
                lower.startsWith("com.google.android.gsf") ||
                lower.contains("permissioncontroller") ||
                lower.contains("screenshot") ||
                isKeyboardPackage(pkg)
    }

    fun isSystemOrLauncherPackage(
        pkg: String,
        launcherDetector: LauncherDetector? = null,
        isProtectionActive: Boolean = false,
        isSessionActive: Boolean = false
    ): Boolean {
        // Known cloner or dual app packages are only treated as non-system when protection is actively armed
        if ((isProtectionActive || isSessionActive) && AntiClonerEngine.isClonerPackage(pkg)) return false

        val lower = pkg.lowercase()
        return lower == OemPackages.ANDROID ||
                lower == OemPackages.SYSTEM_UI ||
                (launcherDetector?.isLauncherPackage(pkg) == true) ||
                lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                lower.contains("keyboard") ||
                lower.contains("inputmethod") ||
                lower.contains("clock") ||
                lower.contains("alarm") ||
                lower.contains("wellbeing") ||
                lower.contains("wellness") ||
                lower.contains("dialer") ||
                lower.contains("incallui") ||
                lower.contains("telecom") ||
                lower.contains("telephony") ||
                lower.contains("phone") ||
                lower.contains("truecaller") ||
                lower == "com.callapp.contacts" ||
                lower == "com.eycon.android" ||
                lower == "com.syncme.syncmeapp" ||
                lower.contains("callerid") ||
                // SECURITY FIX: Use structured match instead of broad `contains("settings")` which
                // allowed any app named e.g. "mysettings.bypass" to skip all time limits.
                isKnownSettingsPackage(lower) ||
                lower.contains("safecenter") ||
                lower.contains("permissioncontroller") ||
                lower.contains("applock") ||
                lower.contains("phonemaster") ||
                lower == OemPackages.ONEPLUS_SECURITY ||
                lower == OemPackages.IQOO_SECURE ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.google.android.gms") ||
                lower.startsWith("com.google.android.gsf") ||
                lower.startsWith("com.google.android.packageinstaller") ||
                lower.startsWith("com.android.packageinstaller") ||
                lower.startsWith("com.focusguard.app")
    }

    fun isSettingsPackage(lowerPkg: String): Boolean {
        return isKnownSettingsPackage(lowerPkg) ||
                lowerPkg == OemPackages.ANDROID ||
                lowerPkg.contains("settings") ||
                lowerPkg.contains("safecenter") ||
                lowerPkg.contains("securitycenter") ||
                lowerPkg.contains("securityadd") ||
                lowerPkg.contains("permissioncontroller") ||
                lowerPkg.contains("permissionmanager") ||
                lowerPkg.contains("applock") ||
                lowerPkg.contains("phonemaster") ||
                lowerPkg == OemPackages.ONEPLUS_SECURITY ||
                lowerPkg == OemPackages.IQOO_SECURE ||
                lowerPkg == OemPackages.VIVO_PERMISSION_MANAGER ||
                lowerPkg == OemPackages.VIVO_SAFE_CENTER ||
                lowerPkg == OemPackages.VIVO_IMANAGER ||
                lowerPkg == OemPackages.VIVO_ABE ||
                lowerPkg == OemPackages.VIVO_UPSLIDE
    }

    fun isInstallerPackage(pkg: String, className: String): Boolean {
        val lower = pkg.lowercase()
        return lower == OemPackages.PLAY_STORE ||
                lower.contains("packageinstaller") ||
                lower.contains("permissioncontroller") ||
                className.contains("UninstallerActivity", ignoreCase = true) ||
                className.contains("UninstallAppProgress", ignoreCase = true) ||
                className.contains("DeletePackage", ignoreCase = true)
    }

    fun isKnownSettingsPackage(lower: String): Boolean {
        return lower == OemPackages.SETTINGS ||
                lower.startsWith("com.android.settings.") ||
                lower.startsWith("com.samsung.android.settings") ||
                lower.startsWith("com.miui.settings") ||
                lower.startsWith("com.oneplus.settings") ||
                lower.startsWith("com.coloros.settings") ||
                lower.startsWith("com.oppo.settings") ||
                lower.startsWith("com.realme.settings") ||
                lower.startsWith("com.vivo.settings") ||
                lower.startsWith("com.huawei.systemmanager") ||
                lower.startsWith("com.asus.settings") ||
                lower.startsWith("com.htc.settings") ||
                lower.startsWith("com.lge.settings") ||
                lower.startsWith("com.motorola.settings") ||
                lower.startsWith("com.transsion.settings") ||   // Infinix / Tecno / itel
                lower.startsWith("com.hissage.settings") ||     // Transsion variant
                lower.startsWith("com.nothing.settings") ||     // Nothing Phone
                lower.startsWith("com.google.android.settings") // Pixel / GSI settings
    }
}

