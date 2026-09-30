package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusguard.app.R
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class FocusSessionService : Service() {

    @Inject
    lateinit var sessionBlockManager: SessionBlockManager

    private val sessionExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("FocusSessionService", "Session service scope error: ${throwable.message}", throwable)
    }
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob + sessionExceptionHandler)
    private var countdownJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "channel_focus_session_lock"
        private const val NOTIFICATION_ID = 4040
        private const val COMPLETION_NOTIFICATION_ID = 4041

        const val ACTION_START_SESSION = "com.focusguard.app.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.focusguard.app.action.STOP_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, FocusSessionService::class.java).apply {
                action = ACTION_START_SESSION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusSessionService::class.java).apply {
                action = ACTION_STOP_SESSION
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SESSION -> {
                stopCountdown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startCountdown()
            }
        }
        return START_STICKY
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        val notification = buildNotification(sessionBlockManager.getRemainingMinutes())
        startForeground(NOTIFICATION_ID, notification)

        countdownJob = serviceScope.launch {
            while (isActive && sessionBlockManager.isSessionCurrentlyActive()) {
                val remainingMins = sessionBlockManager.getRemainingMinutes()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID, buildNotification(remainingMins))
                delay(30_000L) // Refresh every 30 seconds
            }

            // Session has ended
            if (!sessionBlockManager.isSessionCurrentlyActive()) {
                postCompletionNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun buildNotification(remainingMins: Int): Notification {
        val openIntent = Intent(this, com.focusguard.app.ui.session.SessionLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minsText = if (remainingMins > 0) "$remainingMins min" else "< 1 min"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Deep Focus Lockdown Active")
            .setContentText("⏳ $minsText remaining • Only Calls & Packed Apps allowed")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun postCompletionNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = Intent(this, OnboardingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎉 Deep Focus Session Complete!")
            .setContentText("Your apps are now unlocked. Great job staying disciplined!")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(COMPLETION_NOTIFICATION_ID, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Deep Focus Lockdown Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live remaining time during active focus lockdown"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
