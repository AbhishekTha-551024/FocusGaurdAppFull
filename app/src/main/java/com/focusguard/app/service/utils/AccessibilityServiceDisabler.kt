package com.focusguard.app.service.utils

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.focusguard.app.recovery.AccessibilityRecoveryHelper

object AccessibilityServiceDisabler {

    private const val TAG = "FocusGuardService"

    fun removeFromEnabledServices(context: Context, serviceClassName: String) {
        try {
            if (AccessibilityRecoveryHelper.canWriteSecureSettings(context)) {
                val targetComponent = "${context.packageName}/$serviceClassName"
                val shortComponent = "${context.packageName}/.service.FocusGuardAccessibilityService"
                val contentResolver = context.contentResolver
                val current = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val updated = current.split(":")
                    .filter { it.isNotBlank() && !it.equals(targetComponent, true) && !it.equals(shortComponent, true) }
                    .joinToString(":")
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updated
                )
                Log.i(TAG, "Removed FocusGuard from ENABLED_ACCESSIBILITY_SERVICES programmatically")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove from Secure.Settings: ${e.message}")
        }
    }
}

