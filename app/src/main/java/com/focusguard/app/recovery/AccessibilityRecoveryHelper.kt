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

            if (!currentServices.contains(targetComponent)) {
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
            }

            // Always ensure the master accessibility toggle is ON
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )

            Log.i(TAG, "Successfully enabled AccessibilityService programmatically via WRITE_SECURE_SETTINGS!")
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

        // 1. If service is already active, cancel any previous health alert and return
        if (isAccessibilityActive(context)) {
            Log.i(TAG, "Accessibility service is already active. Dismissing recovery notices.")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 2. Try Layer 1: Instant programmatic silent enable (< 1ms)
        val programmaticSuccess = tryEnableAccessibilityProgrammatically(context)
        if (programmaticSuccess) {
            Log.i(TAG, "Accessibility restored silently via WRITE_SECURE_SETTINGS from $source!")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 3. If in Direct Boot mode, we cannot read CE storage (Room/DataStore) or show normal UI
        if (isDirectBootLocked(context)) {
            Log.w(TAG, "Device is in Direct Boot (locked). Programmatic enable attempted; awaiting USER_PRESENT.")
            return
        }

        // 4. Check if protection is configured (AGENTS Rule 3: strictly when admin active AND uninstall protection enabled)
        val isProtected = try {
            val fastUninstall = repository?.isFastUninstallProtectionEnabled() ?: false
            val isAdmin = FocusGuardDeviceAdminReceiver.isDeviceAdminActive(context)
            isAdmin && fastUninstall
        } catch (e: Exception) {
            Log.w(TAG, "Could not check fast protection flags: ${e.message}")
            false
        }

        if (isProtected) {
            Log.w(TAG, "Protection is configured but Accessibility is OFF after $source! Triggering Layer 2 & 3 recovery.")

            // LAYER 2: Launch urgent full-screen recovery activity (via SYSTEM_ALERT_WINDOW exemption)
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

            // LAYER 3: Post persistent high-priority notification with fullScreenIntent
            try {
                AccessibilityHealthWorker.postReactivationNotification(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post reactivation notification: ${e.message}", e)
            }
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
