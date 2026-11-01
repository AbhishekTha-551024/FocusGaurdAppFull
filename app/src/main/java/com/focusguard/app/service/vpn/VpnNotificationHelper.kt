package com.focusguard.app.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * VpnNotificationHelper
 * =====================
 * Encapsulates notification channel creation and foreground notification building for AdultContentVpnService.
 */
class VpnNotificationHelper(private val context: Context) {

    companion object {
        const val NOTIFICATION_CHANNEL = "focusguard_vpn_channel"
        val NOTIFICATION_ID = com.focusguard.app.core.common.constants.NotificationIds.ID_VPN_SERVICE
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Adult Content Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "FocusGuard DNS-level adult content filter" }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NOTIFICATION_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🔞 Adult Content Shield Active")
            .setContentText("DNS-level filtering + SafeSearch enforcement enabled")
            .setOngoing(true)
            .build()
    }
}

