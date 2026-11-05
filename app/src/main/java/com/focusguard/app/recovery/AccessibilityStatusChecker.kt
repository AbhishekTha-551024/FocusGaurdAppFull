package com.focusguard.app.recovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.service.FocusGuardAccessibilityService

private const val TAG = "FG:Recovery"

/**
 * Read-only status checker for Accessibility Service and security permissions.
 * Extracted from AccessibilityRecoveryHelper to maintain single responsibility.
 */
object AccessibilityStatusChecker {

    /** Fully qualified component string as written to ENABLED_ACCESSIBILITY_SERVICES. */
    fun targetComponent(context: Context): String =
        "${context.packageName}/${FocusGuardAccessibilityService::class.java.name}"

    /** Leading-dot component variant some OEM builds keep in the same setting. */
    fun shortComponent(context: Context): String =
        "${context.packageName}/.service.${FocusGuardAccessibilityService::class.java.simpleName}"

    /**
     * True when the OS currently has FocusGuard's Accessibility Service enabled.
     *
     * Authority is the platform (AccessibilityManager + Settings.Secure), never the process-static
     * [FocusGuardAccessibilityService.isServiceRunning]. That flag can stay true across a framework
     * unbind, which is exactly the state that showed ON in-app while Android Settings showed OFF.
     * Reporting the OS value keeps the in-app status and the Settings screen in agreement.
     */
    fun isAccessibilityActive(context: Context): Boolean =
        isAccessibilityBoundNow(context) || isAccessibilityConfiguredInSettings(context)

    /**
     * Strict recovery gate: the service must be alive in this process AND still listed in
     * Settings.Secure.
     *
     * This is the only place the process-static flag is trusted, and only together with the OS
     * setting. Recovery and the polling probe must use this check: "enabled in Settings" alone is
     * not proof of a live binding, and a stale-ON repaint of the UI is not a reason to skip a heal.
     */
    fun isAccessibilityHealthy(context: Context): Boolean =
        FocusGuardAccessibilityService.isServiceRunning && isAccessibilityConfiguredInSettings(context)

    /**
     * Asks AccessibilityManager which of our services the framework currently reports as enabled.
     *
     * This is the OS view (Settings-derived), not proof that the service process is bound. Recovery
     * therefore uses [isAccessibilityHealthy] whenever it needs "alive in this process AND enabled
     * by the OS"; this function alone only answers the Settings-parity question.
     */
    fun isAccessibilityBoundNow(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager ?: return false
        if (!am.isEnabled) return false

        val services = try {
            am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
        } catch (_: Exception) {
            return false
        }

        return services.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }

    /**
     * Checks if the device is currently in Direct Boot mode (before user first unlock).
     */
    fun isDirectBootLocked(context: Context): Boolean = !DirectBootStorage.isUserUnlocked(context)

    /**
     * Checks if the app has been granted WRITE_SECURE_SETTINGS permission.
     */
    fun canWriteSecureSettings(context: Context): Boolean {
        return try {
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if FocusGuard's Accessibility Service is already enabled in Android's
     * Secure Settings (meaning the user or system already turned it ON).
     *
     * During device boot, Android's system_server takes several seconds to bind to the
     * service. This check distinguishes between "already configured, waiting for OS binding"
     * vs "actually disabled by the user".
     */
    fun isAccessibilityConfiguredInSettings(context: Context): Boolean {
        return try {
            val isA11yGloballyEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!isA11yGloballyEnabled) return false

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            isComponentEnabledInSetting(enabledServices, targetComponent(context), shortComponent(context))
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isAccessibilityConfiguredInSettings: ${e.message}")
            false
        }
    }

    /**
     * Pure logic helper for matching service component strings in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
     */
    fun isComponentEnabledInSetting(
        enabledServices: String?,
        targetComponent: String,
        shortComponent: String
    ): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        return enabledServices.split(":").any { item ->
            val trimmed = item.trim()
            trimmed.equals(targetComponent, ignoreCase = true) ||
                    trimmed.equals(shortComponent, ignoreCase = true)
        }
    }
}
