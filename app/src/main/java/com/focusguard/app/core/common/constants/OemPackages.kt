package com.focusguard.app.core.common.constants

/**
 * Centralized registry of system and OEM package names used for
 * event classification, accessibility filtering, tamper defense, and launcher interactions.
 */
object OemPackages {
    // Android Framework & Core
    const val ANDROID = "android"
    const val SYSTEM_UI = "com.android.systemui"
    const val SETTINGS = "com.android.settings"
    const val PLAY_STORE = "com.android.vending"
    const val PACKAGE_INSTALLER = "com.android.packageinstaller"
    const val GOOGLE_PACKAGE_INSTALLER = "com.google.android.packageinstaller"
    const val GMS = "com.google.android.gms"
    const val GSF = "com.google.android.gsf"
    const val CHROME = "com.android.chrome"
    const val YOUTUBE = "com.google.android.youtube"

    // BBK / Vivo / iQOO
    const val BBK_LAUNCHER = "com.bbk.launcher2"
    const val VIVO_ABE = "com.vivo.abe"
    const val VIVO_PERMISSION_MANAGER = "com.vivo.permissionmanager"
    const val VIVO_SAFE_CENTER = "com.vivo.safecenter"
    const val VIVO_IMANAGER = "com.vivo.imanager"
    const val VIVO_UPSLIDE = "com.vivo.upslide"
    const val VIVO_DOUBLE_INSTANCE = "com.vivo.doubleinstance"
    const val IQOO_SECURE = "com.iqoo.secure"

    // Xiaomi / MIUI / HyperOS
    const val MIUI_SECURITY_CENTER = "com.miui.securitycenter"
    const val MIUI_SECURITY_ADD = "com.miui.securityadd"

    // Oppo / ColorOS / Realme
    const val COLOROS_SAFE_CENTER = "com.coloros.safecenter"
    const val OPPO_SAFE = "com.oppo.safe"

    // OnePlus
    const val ONEPLUS_SECURITY = "com.oneplus.security"

    // Huawei / HarmonyOS
    const val HUAWEI_SYSTEM_MANAGER = "com.huawei.systemmanager"
}

