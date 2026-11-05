package com.focusguard.app.service

import android.content.Context
import android.os.Handler
import android.util.Log
import com.focusguard.app.core.startup.ProtectionStartupReadiness
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.recovery.AccessibilityRecoveryScheduler
import com.focusguard.app.recovery.AutoRecoveryPolicy

private const val TAG = "FG:Shield"

/**
 * BootCoverSentinel
 * =================
 * Closes the boot gap while [BootCoverService] holds the screen.
 *
 * The cover only blocked the launcher: nothing healed the Accessibility binding while it was mounted,
 * so a device that rebooted into a dropped binding sat behind the cover until the 15-minute periodic
 * watchdog. The sentinel runs the same silent restore on a monotonic cadence and releases the cover as
 * as Accessibility reconnects and the first rules snapshot is published.
 *
 * It deliberately repeats only the SILENT path. Escalation (recovery activity + alert notification)
 * stays with [AccessibilityRecoveryHelper]'s own callers, so a device that cannot bind does not get
 * the same screen re-launched every interval.
 */
internal class BootCoverSentinel(
    private val context: Context,
    private val handler: Handler,
    private val startupReadiness: ProtectionStartupReadiness,
    private val onShieldNoLongerNeeded: () -> Unit
) {

    companion object {
        /** Same cadence as the application-process probe, so the two never write back to back. */
        val HEAL_INTERVAL_MS: Long = AutoRecoveryPolicy.PROTECTED_PROBE_INTERVAL_MS
    }

    private var tickFuture: Runnable? = null

    /** Attempt counter lives with the instance: the service is recreated on every boot start. */
    private var attemptsUsed: Int = 0

    fun start() {
        stop()
        val task = object : Runnable {
            override fun run() {
                if (tick()) handler.postDelayed(this, HEAL_INTERVAL_MS)
            }
        }
        tickFuture = task
        handler.postDelayed(task, HEAL_INTERVAL_MS)
        Log.i(TAG, "Boot cover heal sentinel armed (every ${HEAL_INTERVAL_MS}ms).")
    }

    fun stop() {
        tickFuture?.let { handler.removeCallbacks(it) }
        tickFuture = null
    }

    /** @return true when another tick must be scheduled. */
    private fun tick(): Boolean {
        val isReady = startupReadiness.isReady()
        val isProtectionActive = AccessibilityRecoveryHelper.isProtectionActive(context)

        if (BootCoverPolicy.shouldReleaseCover(isReady, isProtectionActive)) {
            Log.i(TAG, "Boot readiness resolved (ready=$isReady protectionActive=$isProtectionActive) -> releasing cover.")
            onShieldNoLongerNeeded()
            return false
        }
        if (!BootCoverPolicy.shouldHealWhileCovered(isProtectionActive, attemptsUsed)) {
            Log.w(TAG, "Boot heal budget exhausted ($attemptsUsed/${BootCoverPolicy.MAX_HEAL_ATTEMPTS}); cover stays up for manual action.")
            return false
        }

        attemptsUsed++
        val asserted = AccessibilityRecoveryHelper.tryEnableAccessibilityProgrammatically(context)
        Log.i(TAG, "Boot cover heal attempt #$attemptsUsed (settingAsserted=$asserted)")
        if (asserted) {
            // The write is not proof of binding; keep the follow-up verification alive and re-check
            // on the next tick instead of assuming the shield came back.
            AccessibilityRecoveryScheduler.scheduleBindingVerification(context)
        }
        return true
    }
}
