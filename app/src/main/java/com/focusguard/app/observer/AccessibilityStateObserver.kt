package com.focusguard.app.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.focusguard.app.data.db.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.service.FocusGuardAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "AccessibilityObserver"

/**
 * AccessibilityStateObserver
 * ==========================
 * Observes Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES via ContentObserver.
 * Detects the exact instant the accessibility toggle is flipped off at the system level,
 * faster than UI-event node tree traversals.
 */
class AccessibilityStateObserver(
    private val context: Context,
    private val repository: FocusGuardRepository,
    private val scope: CoroutineScope
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val targetComponent = "${context.packageName}/${FocusGuardAccessibilityService::class.java.name}"
    private var verificationJob: Job? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        val isStillEnabled = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(context)

        if (!isStillEnabled) {
            // OEM platforms (Vivo FuntouchOS / OriginOS via com.vivo.imanager) temporarily blank
            // ENABLED_ACCESSIBILITY_SERVICES for 2-5 seconds when Settings opens for security scanning,
            // then automatically restore it. Debounce 7 seconds to avoid false-alarm notifications!
            //
            // CRITICAL FIX: Arm the OEM scan suppression window IMMEDIATELY (before the debounce
            // coroutine starts). This prevents handleSecurityTamper() from being called while the
            // OEM scanner is active — that call sends GLOBAL_ACTION_HOME which triggers Vivo's
            // anti-tapjacking watchdog and causes the service to be force-unbound.
            com.focusguard.app.service.FocusGuardAccessibilityService.markOemScanActive(windowMs = 12_000L)
            Log.d(TAG, "ContentObserver: OEM scan window armed. Starting 7s debounce check...")

            if (verificationJob == null || verificationJob?.isActive == false) {
                verificationJob = scope.launch(Dispatchers.IO) {
                    delay(7000L)
                    val stillOff = !com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(context)
                    if (stillOff) {
                        Log.w(TAG, "ContentObserver: FocusGuard Accessibility Service confirmed OFF after debounce! Triggering recovery.")
                        com.focusguard.app.recovery.AccessibilityRecoveryHelper.restoreProtectionFast(
                            context = context,
                            source = "AccessibilityObserver:ConfirmedOff",
                            repository = repository
                        )
                        val isOnboardingComplete = repository.isOnboardingComplete.first()
                        if (isOnboardingComplete) {
                            repository.logEvent(
                                EventType.ADMIN_DISABLED,
                                "Accessibility Service disabled by user in Settings [ContentObserver]"
                            )
                        }
                    } else {
                        Log.i(TAG, "ContentObserver: Accessibility Service restored within debounce window (OEM temporary check). No alert.")
                    }
                }
            }
        } else {
            verificationJob?.cancel()
            verificationJob = null
            com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(context)
        }
    }

    fun register() {
        try {
            val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            context.contentResolver.registerContentObserver(uri, false, this)
            Log.i(TAG, "AccessibilityStateObserver registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register AccessibilityStateObserver", e)
        }
    }

    fun unregister() {
        try {
            verificationJob?.cancel()
            verificationJob = null
            context.contentResolver.unregisterContentObserver(this)
            Log.i(TAG, "AccessibilityStateObserver unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister AccessibilityStateObserver", e)
        }
    }
}
