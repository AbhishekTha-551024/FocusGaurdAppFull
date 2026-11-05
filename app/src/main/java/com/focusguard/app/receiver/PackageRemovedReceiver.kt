package com.focusguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.service.BootCoverService
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import com.focusguard.app.worker.AccessibilityHealthWorker

private const val TAG = "FGPackageRemovedReceiver"

/**
 * PackageRemovedReceiver
 * ======================
 * ANTI-CLONE UNINSTALL DEFENSE:
 * When an OEM Dual App clones FocusGuard (com.focusguard.app) under a secondary
 * user profile (userId > 0), uninstalling that clone fires ACTION_PACKAGE_REMOVED
 * for com.focusguard.app. On some OEMs this also triggers the OS to disable the
 * primary user's Accessibility Services.
 *
 * This receiver instantly restores protection and takes over the screen via
 * [BootCoverService] and [AccessibilityRecoveryActivity] so the user cannot
 * access Settings to tamper with the app.
 */
class PackageRemovedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pkg = intent.data?.schemeSpecificPart ?: return

        // Only care about our own package
        if (pkg != context.packageName) return

        // Only act from the primary user (userId == 0)
        val myUserId = Process.myUid() / 100000
        if (myUserId != 0) return

        when (action) {
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (action == Intent.ACTION_PACKAGE_REMOVED && isReplacing) {
                    // Normal update — not a clone removal, ignore
                    Log.d(TAG, "FocusGuard update in progress (EXTRA_REPLACING=true) — ignoring")
                    return
                }

                Log.w(TAG, "FocusGuard package event: action=$action — Clone removal may have disrupted Accessibility Service!")

                val isProtected = AccessibilityRecoveryHelper.isProtectionActive(context)
                if (isProtected) {
                    Log.w(TAG, "Clone removal detected while Uninstall Protection is ACTIVE! Restoring protection and mounting cover overlay immediately.")

                    // 1. Attempt programmatic restore (<1ms if WRITE_SECURE_SETTINGS)
                    AccessibilityRecoveryHelper.restoreProtectionFast(
                        context = context,
                        source = "PackageRemovedReceiver:$action"
                    )

                    // 2. Launch BootCoverService overlay if accessibility is not running
                    try {
                        BootCoverService.start(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start BootCoverService: ${e.message}", e)
                    }

                    // 3. Launch full-screen AccessibilityRecoveryActivity
                    try {
                        AccessibilityRecoveryActivity.start(
                            context = context,
                            reason = "A cloned instance was removed, which turned off Accessibility. Please turn Accessibility back ON to keep your device protected."
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start AccessibilityRecoveryActivity: ${e.message}", e)
                    }
                }

                // 4. Also post persistent high-priority notification as fallback
                try {
                    AccessibilityHealthWorker.postReactivationNotification(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post reactivation notification: ${e.message}", e)
                }
            }
        }
    }
}
