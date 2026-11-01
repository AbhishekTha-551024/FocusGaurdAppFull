package com.focusguard.app.service.handlers

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import com.focusguard.app.core.common.constants.OemPackages
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FocusGuardService"

/**
 * Detects OEM security scan windows (e.g. Vivo iManager / SafeCenter, OnePlus, MIUI, Huawei)
 * where the system transiently clears ENABLED_ACCESSIBILITY_SERVICES while performing a scan.
 */
@Singleton
class OemScanDetector @Inject constructor() {

    fun isOemSecurityPackage(pkg: String): Boolean {
        return pkg == OemPackages.VIVO_IMANAGER ||
                pkg == OemPackages.VIVO_SAFE_CENTER ||
                pkg == OemPackages.VIVO_PERMISSION_MANAGER ||
                pkg == OemPackages.VIVO_ABE ||
                pkg == OemPackages.IQOO_SECURE ||
                pkg == "com.oneplus.security" ||
                pkg == "com.miui.securityadd" ||
                pkg == "com.huawei.systemmanager"
    }

    fun isAccessibilityTransientlyOff(contentResolver: ContentResolver, ownPackageName: String): Boolean {
        return try {
            val enabledSvcs = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            !enabledSvcs.contains(ownPackageName)
        } catch (e: Exception) {
            Log.d(TAG, "ENABLED_ACCESSIBILITY_SERVICES query error: ${e.message}")
            false
        }
    }
}

