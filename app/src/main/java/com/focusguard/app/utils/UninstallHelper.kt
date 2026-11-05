package com.focusguard.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.recovery.UninstallTeardownGuard
import com.focusguard.app.service.FocusGuardAccessibilityService

private const val TAG = "UninstallHelper"

/**
 * UninstallHelper
 * ===============
 * Handles graceful de-escalation of elevated system privileges (Device Admin and Accessibility)
 * before invoking Android's package deletion flow.
 *
 * Problem it solves:
 * 1. Android OS strictly blocks uninstallation of any app that has an active Device Administrator.
 *    The system "Uninstall" button is either disabled or displays an error dialog.
 * 2. If Accessibility remains active, the service continues to observe events until unbound.
 *
 * Solution:
 * 1. Deactivates Device Admin via [DevicePolicyManager.removeActiveAdmin].
 * 2. Disables FocusGuard's Accessibility Service via [AccessibilityService.disableSelf].
 * 3. Launches Android's native uninstaller confirmation dialog via [Intent.ACTION_DELETE].
 *
 * Steps 1 and 2 are irreversible from inside the app, and the uninstaller gives no result, so
 * [com.focusguard.app.recovery.UninstallTeardownGuard] watches whether this process survives the
 * prompt: a cancelled uninstall re-asserts exactly the privileges this method gave up.
 */
object UninstallHelper {

    fun performCleanUninstall(context: Context) {
        Log.i(TAG, "Starting clean uninstall sequence for FocusGuard...")

        // Safety net for callers that did not arm earlier: whatever protection is still readable here
        // is exactly what the two steps below destroy, so it is what a cancelled uninstall puts back.
        UninstallTeardownGuard.armBeforeTeardown(context)

        // Step 1: Remove Device Admin privileges (clears OS-level uninstall block)
        try {
            FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
            Log.i(TAG, "Device Admin deactivated successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating Device Admin: ${e.message}", e)
        }

        // Step 2: Disable Accessibility Service
        try {
            FocusGuardAccessibilityService.disableAccessibilityService(context)
            Log.i(TAG, "Accessibility Service disabled successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Accessibility Service: ${e.message}", e)
        }

        // Step 3: Launch Android's native uninstaller prompt
        try {
            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(uninstallIntent)
            Log.i(TAG, "Launched system uninstaller dialog (ACTION_DELETE).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ACTION_DELETE intent: ${e.message}", e)
            // Fallback: Open application details settings where Uninstall button is now enabled
            try {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open application details settings: ${ex.message}", ex)
            }
        }

        // Neither the uninstaller nor the details screen returns a result, so the only evidence
        // available to this app is whether it is still installed afterwards.
        UninstallTeardownGuard.scheduleCancelCheck(context)
    }
}
