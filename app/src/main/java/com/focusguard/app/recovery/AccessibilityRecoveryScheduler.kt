package com.focusguard.app.recovery

import android.content.Context
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.worker.AccessibilityHealthWorker

private const val TAG = "FG:Recovery"
private const val ATTEMPTS_KEY = "a11y_bind_verification_attempts"

/** Grace the OS gets to finish binding before we come back and verify. */
private const val BIND_VERIFICATION_DELAY_MS = 20_000L

/** Rapid re-assert attempts per boot/tamper episode before falling back to the periodic watchdog. */
private const val MAX_RAPID_ATTEMPTS = 8

/**
 * AccessibilityRecoveryScheduler
 * =============================
 * Owns the "did the OS actually finish binding our Accessibility Service?" follow-up.
 *
 * Writing Settings.Secure is not proof of binding: the framework still has to start the process and
 * bind the service, and Vivo's background manager can drop it again. Recovery therefore schedules a
 * delayed verification pass instead of trusting the setting and returning.
 *
 * Retries are bounded so a device that genuinely cannot bind does not loop every 20 seconds forever
 * — the 15-minute periodic watchdog stays as the slow backstop. Nothing here decides *whether*
 * recovery is allowed: [AccessibilityRecoveryHelper] keeps the "uninstall protection OFF => never
 * touch the setting" invariant.
 */
object AccessibilityRecoveryScheduler {

    fun scheduleBindingVerification(context: Context) {
        // WorkManager keeps its database in CE storage and is not initialised before first unlock.
        if (AccessibilityStatusChecker.isDirectBootLocked(context)) {
            Log.d(TAG, "Direct boot: binding verification deferred until the device is unlocked.")
            return
        }
        if (attempts(context) >= MAX_RAPID_ATTEMPTS) {
            Log.i(TAG, "Binding verification budget exhausted for this episode; periodic watchdog stays active.")
            return
        }
        AccessibilityHealthWorker.scheduleHealCheck(context, BIND_VERIFICATION_DELAY_MS, rapid = true)
    }

    fun recordAttempt(context: Context) {
        writeAttempts(context, attempts(context) + 1)
    }

    fun resetAttempts(context: Context) {
        if (attempts(context) != 0) writeAttempts(context, 0)
    }

    private fun attempts(context: Context): Int = try {
        DirectBootStorage.deCache(context).getInt(ATTEMPTS_KEY, 0)
    } catch (e: Exception) {
        Log.d(TAG, "Could not read binding verification attempts: ${e.message}")
        0
    }

    private fun writeAttempts(context: Context, value: Int) {
        try {
            DirectBootStorage.deCache(context).edit().putInt(ATTEMPTS_KEY, value).apply()
        } catch (e: Exception) {
            Log.d(TAG, "Could not persist binding verification attempts: ${e.message}")
        }
    }
}
