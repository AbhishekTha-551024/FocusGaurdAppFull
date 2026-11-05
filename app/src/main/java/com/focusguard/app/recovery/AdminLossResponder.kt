package com.focusguard.app.recovery

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.service.BootCoverService
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Recovery"

/** Minimum gap between two re-activation attempts, so a refused dialog is not spammed. */
private const val REASK_COOLDOWN_MS = 60_000L

private const val NEVER_CONTESTED = -1L

const val ADMIN_REASK_EXPLANATION =
    "FocusGuard Uninstall Protection is still ON. Its Device Administrator layer was removed, " +
            "which silently disarms the whole protection chain, because every recovery gate checks " +
            "that this admin is active. Re-activate to restore the protection you already agreed to."

/** Outcome of an admin-loss event, decided by pure logic so it is unit-testable. */
internal enum class AdminLossDecision {
    REACTIVATE_AND_SHIELD,
    SKIP_ADMIN_STILL_ACTIVE,
    SKIP_NOT_PROTECTED,
    SKIP_COOLDOWN
}

/**
 * Pure decision for a Device Admin deactivation.
 *
 * [msSinceLastContest] is [NEVER_CONTESTED] when this process has not contested yet, which must
 * allow an immediate attempt even in the first seconds after boot.
 *
 * Order matters: an active admin means there is nothing to fix, and a user who genuinely turned
 * Uninstall Protection OFF must not be chased (that is the declared no-auto-recovery rule). Only
 * then does the cooldown apply.
 */
internal fun decideAdminLoss(
    protectionEnabled: Boolean,
    adminStillActive: Boolean,
    msSinceLastContest: Long,
    cooldownMs: Long
): AdminLossDecision = when {
    adminStillActive -> AdminLossDecision.SKIP_ADMIN_STILL_ACTIVE
    !protectionEnabled -> AdminLossDecision.SKIP_NOT_PROTECTED
    msSinceLastContest >= 0L && msSinceLastContest < cooldownMs -> AdminLossDecision.SKIP_COOLDOWN
    else -> AdminLossDecision.REACTIVATE_AND_SHIELD
}

/**
 * AdminLossResponder
 * ==================
 * Contests a Device Admin deactivation while Uninstall Protection is still owed.
 *
 * Device Admin is not just one layer among many: `AccessibilityRecoveryHelper.isProtectionActive()`
 * checks it first, so the moment the admin is gone every auto-recovery path reports "protection
 * inactive" and stops restoring the Accessibility Service — even though the stored protection flag
 * and the timed lock countdown are still set. That was the quiet bypass: deactivate the admin, keep
 * the flag ON, and the app politely stops defending itself.
 *
 * The response asks the OS to bring the activation screen back and mounts the same shield the
 * deactivation-request callback uses, so the user cannot sit in a unprotected state while reading it.
 */
@Singleton
class AdminLossResponder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val lastContestAtMs = AtomicLong(NEVER_CONTESTED)

    /**
     * @param adminComponent the FocusGuard DeviceAdminReceiver component, supplied by the caller so
     * this class does not have to reference the receiver class.
     * @param protectionEnabled the *stored* Uninstall Protection flag. Must not be
     * [AccessibilityRecoveryHelper.isProtectionActive], which already returns false once the admin
     * is gone and would make this responder decide there is nothing to do.
     */
    internal fun respond(adminComponent: ComponentName, protectionEnabled: Boolean): AdminLossDecision {
        val sinceLastContest = lastContestAtMs.get().let {
            if (it == NEVER_CONTESTED) NEVER_CONTESTED else SystemClock.elapsedRealtime() - it
        }
        val decision = decideAdminLoss(
            protectionEnabled = protectionEnabled,
            adminStillActive = isAdminActive(adminComponent),
            msSinceLastContest = sinceLastContest,
            cooldownMs = REASK_COOLDOWN_MS
        )
        when (decision) {
            AdminLossDecision.REACTIVATE_AND_SHIELD -> {
                lastContestAtMs.set(SystemClock.elapsedRealtime())
                Log.w(TAG, "Admin deactivated while Uninstall Protection is ON: re-asking admin and mounting shield.")
                requestAdminReactivation(adminComponent)
                mountShield()
            }
            AdminLossDecision.SKIP_ADMIN_STILL_ACTIVE ->
                Log.i(TAG, "Admin loss event received but admin is still active: nothing to restore.")
            AdminLossDecision.SKIP_NOT_PROTECTED ->
                Log.i(TAG, "Uninstall Protection is OFF: honouring it, not re-asking Device Admin.")
            AdminLossDecision.SKIP_COOLDOWN ->
                Log.i(TAG, "Admin re-ask already attempted ${sinceLastContest}ms ago: waiting out the cooldown.")
        }
        return decision
    }

    private fun isAdminActive(adminComponent: ComponentName): Boolean = try {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        dpm?.isAdminActive(adminComponent) == true
    } catch (e: Exception) {
        Log.w(TAG, "Could not read admin state, treating as inactive: ${e.message}")
        false
    }

    private fun requestAdminReactivation(adminComponent: ComponentName) {
        try {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ADMIN_REASK_EXPLANATION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open the Device Admin activation screen: ${e.message}")
        }
    }

    private fun mountShield() {
        try {
            BootCoverService.start(context)
            AccessibilityRecoveryActivity.start(
                context,
                "Device Admin was removed while Uninstall Protection is active",
                allowAdminReactivation = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mount the admin-loss shield: ${e.message}")
        }
    }
}
