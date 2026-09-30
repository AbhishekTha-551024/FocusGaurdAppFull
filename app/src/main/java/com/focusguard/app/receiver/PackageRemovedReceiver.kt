package com.focusguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log

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
 * This receiver posts an urgent reactivation notification so the user can
 * re-enable accessibility in one tap.
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

                Log.w(TAG, "FocusGuard package event: action= — " +
                        "Clone removal may have disrupted Accessibility Service!")

                // Post an urgent notification so the user can re-enable accessibility in one tap
                try {
                    com.focusguard.app.worker.AccessibilityHealthWorker
                        .postReactivationNotification(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post reactivation notification: {e.message}", e)
                }
            }
        }
    }
}
