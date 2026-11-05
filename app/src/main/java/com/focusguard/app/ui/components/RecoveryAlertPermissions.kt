package com.focusguard.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

private const val TAG = "FG:RecoveryAlerts"

/**
 * RecoveryAlertPermissions
 * ========================
 * Shared capability checks for the recovery-alert visibility work (Phase B).
 *
 * Moved verbatim from RecoveryAlertsGuard so both the Home guard and the Uninstall Protection
 * arming gate ask the same two questions:
 *  - are notifications currently able to reach the user?
 *  - how do we ask for them on this Android version?
 */
object RecoveryAlertPermissions {

    /** Android 13+ gates notifications behind a runtime permission; older versions use the app switch. */
    fun areNotificationsGranted(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestNotifications(context: Context, requestRuntime: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestRuntime()
        } else {
            openNotificationSettings(context)
        }
    }

    fun openNotificationSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.d(TAG, "Could not open notification settings: ${e.message}")
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )
            } catch (err: Exception) {
                Log.d(TAG, "Could not open app settings: ${err.message}")
            }
        }
    }
}
