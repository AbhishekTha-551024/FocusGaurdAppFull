package com.focusguard.app.recovery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import com.focusguard.app.worker.AccessibilityHealthWorker

private const val TAG = "AccessibilityRecovery"

/**
 * AccessibilityRecoveryHelper
 * ===========================
 * Handles zero-delay recovery of the Accessibility Service across device reboots,
 * power-cycles, and OEM background app management interventions (especially Vivo/FuntouchOS).
 *
 * Layers of recovery:
 * 1. Fast Silent Re-activation:
 *    If WRITE_SECURE_SETTINGS is granted (via ADB, Shizuku, or Device Owner),
 *    the service is programmatically re-injected into Settings.Secure in < 1ms.
 * 2. Urgent Full-Screen Recovery Activity:
 *    FocusGuard holds SYSTEM_ALERT_WINDOW, allowing background activity launches.
 *    If protection is configured and accessibility was stripped by restart,
 *    [AccessibilityRecoveryActivity] is launched instantly over the screen with a 1-tap restore.
 * 3. Max-Priority Heads-Up & Full-Screen Notification:
 *    Ensures immediate user alert with fullScreenIntent on lock screen or status bar.
 */
object AccessibilityRecoveryHelper {

    /**
     * Checks if FocusGuard's Accessibility Service is currently active in memory
     * OR enabled in the system's secure settings.
     */
    fun isAccessibilityActive(context: Context): Boolean {
        // Fast path 1: In-memory live service flag
        if (FocusGuardAccessibilityService.isServiceRunning) {
            return true
        }

        // Fast path 2: Query Android's system AccessibilityManager (authoritative OS framework source)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (am == null || !am.isEnabled) {
            return false
        }

        val runningServices = try {
            am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        } catch (_: Exception) {
            null
        }

        val isComponentRunning = runningServices?.any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        } == true

        if (!isComponentRunning) {
            return false
        }

        // Fast path 3: Verify Settings.Secure global toggle and exact component match
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
            val targetComponent = "${context.packageName}/${FocusGuardAccessibilityService::class.java.name}"
            val shortComponent = "${context.packageName}/.service.FocusGuardAccessibilityService"

            enabledServices.split(":").any { item ->
                val trimmed = item.trim()
                trimmed.equals(targetComponent, ignoreCase = true) ||
                        trimmed.equals(shortComponent, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking accessibility status: ${e.message}")
            false
        }
    }

    /**
     * Checks if the device is currently in Direct Boot mode (before user first unlock).
     */
    fun isDirectBootLocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(UserManager::class.java)
            userManager != null && !userManager.isUserUnlocked
        } else {
            false
        }
    }

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
            val targetComponent = "${context.packageName}/${FocusGuardAccessibilityService::class.java.name}"
            val shortComponent = "${context.packageName}/.service.FocusGuardAccessibilityService"

            enabledServices.split(":").any { item ->
                val trimmed = item.trim()
                trimmed.equals(targetComponent, ignoreCase = true) ||
                        trimmed.equals(shortComponent, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isAccessibilityConfiguredInSettings: ${e.message}")
            false
        }
    }

    /**
     * LAYER 1: Attempts silent, programmatic re-enablement of FocusGuardAccessibilityService
     * using WRITE_SECURE_SETTINGS permission.
     *
     * Returns true if successfully enabled or already enabled, false otherwise.
     */
    fun tryEnableAccessibilityProgrammatically(context: Context): Boolean {
        val targetComponent = "${context.packageName}/${FocusGuardAccessibilityService::class.java.name}"
        return try {
            val contentResolver = context.contentResolver
            val currentServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val newServices = when {
                currentServices.contains(targetComponent) -> currentServices
                currentServices.isBlank() -> targetComponent
                else -> "$currentServices:$targetComponent"
            }

            // Always write to re-assert with ContentObserver so system_server re-binds if stalled
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newServices
            )

            // Always ensure the master accessibility toggle is ON
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )

            Log.i(TAG, "Successfully asserted AccessibilityService programmatically via WRITE_SECURE_SETTINGS!")
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to programmatically enable accessibility: ${e.message}", e)
            false
        }
    }

    /**
     * Invariant Rule 3: Determines if protection is strictly active.
     * Protection is active ONLY when:
     * 1. Device Admin is currently enabled, AND
     * 2. Uninstall Protection is actively turned ON (and not expired).
     */
    fun isProtectionActive(context: Context, repository: FocusGuardRepository? = null): Boolean {
        // Fast Gate 1: Device Admin must be active
        if (!FocusGuardDeviceAdminReceiver.isDeviceAdminActive(context)) {
            return false
        }

        // Fast Gate 2: Uninstall protection must be enabled and unexpired
        return try {
            if (repository != null) {
                repository.isFastUninstallProtectionEnabled()
            } else {
                val syncPrefs = context.getSharedPreferences("focusguard_sync_cache", Context.MODE_PRIVATE)
                val enabled = syncPrefs.getBoolean("uninstall_protection_enabled", false)
                if (enabled) {
                    val expiry = syncPrefs.getLong("uninstall_protection_expiry_timestamp", 0L)
                    if (expiry <= 0L) true else System.currentTimeMillis() < expiry
                } else if (isDirectBootLocked(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val deContext = context.createDeviceProtectedStorageContext()
                    val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    dePrefs.getBoolean("uninstall_protection_enabled", false)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error evaluating isProtectionActive: ${e.message}")
            false
        }
    }

    /**
     * Restores protection as fast as possible across reboots or after being killed by the OS.
     *
     * @param context Application or BroadcastReceiver context.
     * @param source Description of the trigger (e.g., "BootReceiver:BOOT_COMPLETED", "USER_PRESENT").
     * @param repository Optional repository instance to verify protection configuration.
     */
    fun restoreProtectionFast(
        context: Context,
        source: String,
        repository: FocusGuardRepository? = null
    ) {
        Log.i(TAG, "restoreProtectionFast triggered from source: $source")

        // 1. If service is already active, cancel any previous health alert and return immediately
        if (isAccessibilityActive(context)) {
            Log.i(TAG, "Accessibility service is already active. Dismissing recovery notices.")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 2. CRITICAL PROTECTION GATE (Invariant Rule 3):
        // Only recover or auto-enable Accessibility if Uninstall Protection is strictly ACTIVE.
        val isProtected = isProtectionActive(context, repository)
        if (!isProtected) {
            Log.i(TAG, "restoreProtectionFast: Protection is inactive (Uninstall Protection OFF or Admin inactive). Accessibility will stay OFF.")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 3. Try Layer 1: Instant programmatic silent enable (< 1ms) via WRITE_SECURE_SETTINGS
        val programmaticSuccess = tryEnableAccessibilityProgrammatically(context)
        if (programmaticSuccess) {
            Log.i(TAG, "Accessibility restored silently via WRITE_SECURE_SETTINGS from $source!")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 4. BOOT & GRACE PERIOD CHECK:
        // Only allow grace period if this is a benign boot event and NOT a clone removal or tamper event!
        val isExplicitTamperOrRemoval = source.contains("PackageRemovedReceiver") ||
                source.contains("Admin") ||
                source.contains("Tamper") ||
                source.contains("ConfirmedOff")
        val isConfiguredInSettings = isAccessibilityConfiguredInSettings(context)
        if (isConfiguredInSettings && !isExplicitTamperOrRemoval) {
            Log.i(TAG, "restoreProtectionFast: Accessibility is configured in Settings.Secure ($source). Allowing OS grace period to bind silently.")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 5. If in Direct Boot mode, we cannot read CE storage (Room/DataStore) or show normal UI
        if (isDirectBootLocked(context)) {
            Log.w(TAG, "Device is in Direct Boot (locked). Programmatic enable attempted; awaiting USER_PRESENT.")
            return
        }

        // 6. Layer 2 & Layer 3 recovery: Only triggered when Accessibility is ACTUALLY disabled in Settings
        // by user tamper or aggressive OS kill, and silent programmatic restore is unavailable.
        Log.w(TAG, "Protection is configured but Accessibility is genuinely OFF in Settings after $source! Triggering recovery.")

        // LAYER 2: Launch urgent recovery activity (only when user actually turned off accessibility in settings)
        try {
            if (Settings.canDrawOverlays(context)) {
                AccessibilityRecoveryActivity.start(
                    context = context,
                    reason = "Accessibility was turned off during device restart ($source)."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch AccessibilityRecoveryActivity: ${e.message}", e)
        }

        // LAYER 3: Post persistent high-priority notification
        try {
            AccessibilityHealthWorker.postReactivationNotification(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post reactivation notification: ${e.message}", e)
        }
    }

    /**
     * Universal intent to open Accessibility Settings.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}", e)
        }
    }
}
