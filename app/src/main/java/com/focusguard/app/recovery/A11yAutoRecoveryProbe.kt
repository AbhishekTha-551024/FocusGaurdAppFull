package com.focusguard.app.recovery

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.worker.AccessibilityHealthWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:AutoRecovery"

/**
 * A11yAutoRecoveryProbe
 * =====================
 * Active (polling) watchdog for the Accessibility Service.
 *
 * Why an active probe exists:
 *  - The existing ContentObservers only fire when ENABLED_ACCESSIBILITY_SERVICES changes.
 *    When an OEM kills the binding without touching the secure value (Vivo imanager, process
 *    reclaim), no change event is delivered and nothing recovers the shield after a reboot.
 *  - This probe asks the framework directly, on a fixed cadence, whether OUR service is bound.
 *
 * Contract (AutoRecoveryPolicy):
 *  - Uninstall Protection ON  -> silently restore; escalate to the recovery gate after
 *    [AutoRecoveryPolicy.ESCALATION_THRESHOLD] failed attempts.
 *  - Uninstall Protection OFF -> do nothing at all; never enable Accessibility for the user.
 *
 * Runs in the application process, independent of the service being bound. Started by
 * FocusGuardApplication after the first unlock (CE storage available).
 */
@Singleton
class A11yAutoRecoveryProbe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var probeJob: Job? = null

    /** Monotonic timestamp of the last recovery attempt (Rule 4: never wall clock). */
    private val lastAttemptElapsed = AtomicLong(0L)

    private var consecutiveFailures: Int = 0

    fun start() {
        if (probeJob?.isActive == true) return
        probeJob = scope.launch {
            Log.i(TAG, "Accessibility auto-recovery probe started")
            while (isActive) {
                val protectionActive = AccessibilityRecoveryHelper.isProtectionActive(context, repository)
                if (AutoRecoveryPolicy.shouldAutoRecover(protectionActive)) {
                    // "Enabled in Settings" is not proof of a live binding: after a reboot the setting
                    // can name us while system_server never bound the service. Only the strict check
                    // (alive in this process + configured) clears the probation state.
                    if (AccessibilityStatusChecker.isAccessibilityHealthy(context)) {
                        resetFailures()
                    } else {
                        attemptRecovery()
                    }
                } else {
                    resetFailures()
                    AccessibilityHealthWorker.cancelReactivationNotification(context)
                }
                delay(AutoRecoveryPolicy.probeIntervalMs(protectionActive))
            }
        }
    }

    fun stop() {
        probeJob?.cancel()
        probeJob = null
        lastAttemptElapsed.set(0L)
        consecutiveFailures = 0
        Log.i(TAG, "Accessibility auto-recovery probe stopped")
    }

    private fun resetFailures() {
        if (consecutiveFailures != 0) {
            Log.i(TAG, "Accessibility is bound again — failure counter reset")
            consecutiveFailures = 0
        }
        lastAttemptElapsed.set(0L)
    }

    private fun attemptRecovery() {
        val now = SystemClock.elapsedRealtime()
        val gap = AutoRecoveryPolicy.recoveryGapMs(consecutiveFailures)
        if (now - lastAttemptElapsed.get() < gap) return

        lastAttemptElapsed.set(now)
        consecutiveFailures++
        Log.w(TAG, "Accessibility OFF while protection is ON -> recovery attempt #$consecutiveFailures")

        // Reuses the single recovery helper: silent WRITE_SECURE_SETTINGS first, then the
        // recovery activity/notification fallback when the silent path is unavailable.
        AccessibilityRecoveryHelper.restoreProtectionFast(
            context = context,
            source = "AutoRecoveryProbe:attempt#$consecutiveFailures",
            repository = repository
        )

        if (AutoRecoveryPolicy.shouldEscalate(consecutiveFailures)) {
            try {
                AccessibilityHealthWorker.postReactivationNotification(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post reactivation notification: ${e.message}", e)
            }
        }
    }
}
