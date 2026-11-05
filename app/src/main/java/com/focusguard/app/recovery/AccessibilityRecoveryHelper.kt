package com.focusguard.app.recovery

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.core.datastore.UptimeLedger
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import com.focusguard.app.worker.AccessibilityHealthWorker

private const val TAG = "FG:Recovery"

/** Monotonic timestamp of the last restore attempt, shared by every recovery source. */
private const val KEY_LAST_RESTORE_ATTEMPT_ELAPSED = "a11y_last_restore_attempt_elapsed"

/**
 * AccessibilityRecoveryHelper
 * ===========================
 * Handles zero-delay recovery of the Accessibility Service across device reboots,
 * power-cycles, and OEM background app management interventions (especially Vivo/FuntouchOS).
 *
 * Delegates status checks to [AccessibilityStatusChecker] and programmatic enablement
 * to [ProgrammaticAccessibilityEnabler].
 */
object AccessibilityRecoveryHelper {

    /**
     * Checks if FocusGuard's Accessibility Service is currently active in memory
     * OR enabled in the system's secure settings.
     */
    fun isAccessibilityActive(context: Context): Boolean =
        AccessibilityStatusChecker.isAccessibilityActive(context)

    /**
     * Checks if the device is currently in Direct Boot mode (before user first unlock).
     */
    fun isDirectBootLocked(context: Context): Boolean =
        AccessibilityStatusChecker.isDirectBootLocked(context)

    /**
     * Checks if the app has been granted WRITE_SECURE_SETTINGS permission.
     */
    fun canWriteSecureSettings(context: Context): Boolean =
        AccessibilityStatusChecker.canWriteSecureSettings(context)

    /**
     * Checks if FocusGuard's Accessibility Service is already enabled in Android's
     * Secure Settings (meaning the user or system already turned it ON).
     */
    fun isAccessibilityConfiguredInSettings(context: Context): Boolean =
        AccessibilityStatusChecker.isAccessibilityConfiguredInSettings(context)

    /**
     * Strict recovery gate: the service is alive in this process AND still configured in Settings.
     */
    fun isAccessibilityHealthy(context: Context): Boolean =
        AccessibilityStatusChecker.isAccessibilityHealthy(context)

    /**
     * LAYER 1: Attempts silent, programmatic re-enablement of FocusGuardAccessibilityService
     * using WRITE_SECURE_SETTINGS permission.
     */
    fun tryEnableAccessibilityProgrammatically(context: Context): Boolean =
        ProgrammaticAccessibilityEnabler.tryEnableAccessibilityProgrammatically(context)

    /**
     * Invariant Rule 3: Determines if protection is strictly active.
     * Protection is active ONLY when:
     * 1. Device Admin is currently enabled, AND
     * 2. Uninstall Protection is actively turned ON (and not expired).
     */
    fun isProtectionActive(context: Context, repository: FocusGuardRepository? = null): Boolean {
        // Fast Gate 1: Device Admin must be active
        if (!FocusGuardDeviceAdminReceiver.isDeviceAdminActive(context)) {
            return false
        }

        // Fast Gate 2: Uninstall protection must be enabled and unexpired.
        // A TRUE fast-cache read is authoritative. A FALSE read is NOT: right after boot the
        // repository's init coroutine may not have finished reading DataStore yet, and treating
        // that stale false as "protection off" is what silently skipped recovery after a reboot.
        // Fall through to the SharedPreferences / device-protected mirror instead.
        return try {
            if (repository != null && repository.isFastUninstallProtectionEnabled()) {
                true
            } else {
                // CE snapshot when unlocked, DE mirror when still locked. The plain CE read used to
                // throw at direct boot, which made this whole branch return false right after a reboot.
                val syncPrefs = DirectBootStorage.readCache(context)
                val enabled = syncPrefs.getBoolean("uninstall_protection_enabled", false)
                if (enabled) {
                    val expiry = syncPrefs.getLong("uninstall_protection_expiry_timestamp", 0L)
                    if (expiry <= 0L) true else !timedLockActuallyOver(context, expiry)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error evaluating isProtectionActive: ${e.message}")
            false
        }
    }

    /**
     * Wall-clock expiry is only half of the rule. A timed lock is over when the clock is past
     * [expiry] AND the monotonic verdict published by
     * [com.focusguard.app.feature.settings.data.repository.ProtectionRepositoryImpl] confirms the
     * full duration really ran on device uptime. No verdict stored means still running, so this
     * branch fails closed against a Settings > Date & time jump.
     */
    private fun timedLockActuallyOver(context: Context, expiry: Long): Boolean {
        if (System.currentTimeMillis() < expiry) return false
        return !DirectBootStorage.deCache(context).getBoolean(
            UptimeLedger.KEY_TIMED_LOCK_STILL_RUNNING,
            true
        )
    }

    /**
     * Restores protection as fast as possible across reboots or after being killed by the OS.
     *
     * @param context Application or BroadcastReceiver context.
     * @param source Description of the trigger (e.g., "BootReceiver:BOOT_COMPLETED", "USER_PRESENT").
     * @param repository Optional repository instance to verify protection configuration.
     */
    fun restoreProtectionFast(
        context: Context,
        source: String,
        repository: FocusGuardRepository? = null
    ) {
        Log.i(TAG, "restoreProtectionFast triggered from source: $source")

        // 1. If the service is bound AND still listed in Settings, cancel any previous health alert
        val isConfiguredInSettings = isAccessibilityConfiguredInSettings(context)
        if (isAccessibilityHealthy(context)) {
            Log.i(TAG, "Accessibility service is bound and configured. Dismissing recovery notices.")
            AccessibilityRecoveryScheduler.resetAttempts(context)
            resetRestoreAttemptGate(context)
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 2. CRITICAL PROTECTION GATE (Invariant Rule 3):
        // Only recover or auto-enable Accessibility if Uninstall Protection is strictly ACTIVE.
        val isProtected = isProtectionActive(context, repository)
        if (!isProtected) {
            Log.i(TAG, "restoreProtectionFast: Protection is inactive (Uninstall Protection OFF or Admin inactive). Accessibility will stay OFF.")
            resetRestoreAttemptGate(context)
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            return
        }

        // 2.5 SINGLE DUPLICATE GATE: the ContentObserver, the periodic probe, onUnbind and the
        // boot receiver can all describe the same failure seconds apart. Only the first trigger
        // runs the recovery layers; the rest are absorbed here instead of re-launching the
        // recovery screen or re-posting notifications.
        val nowElapsed = SystemClock.elapsedRealtime()
        val lastAttemptElapsed = readLastRestoreAttempt(context)
        if (AutoRecoveryPolicy.shouldSuppressDuplicate(lastAttemptElapsed, nowElapsed)) {
            Log.i(
                TAG,
                "Duplicate recovery trigger from $source (${nowElapsed - lastAttemptElapsed}ms since last attempt). Already being handled."
            )
            return
        }
        markRestoreAttempt(context, nowElapsed)

        // 3. Try Layer 1: Instant programmatic silent enable (< 1ms) via WRITE_SECURE_SETTINGS
        val programmaticSuccess = tryEnableAccessibilityProgrammatically(context)
        if (programmaticSuccess) {
            Log.i(TAG, "Accessibility restored silently via WRITE_SECURE_SETTINGS from $source!")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            // Writing the setting is not proof of binding: system_server still has to start the
            // process and bind it, and Vivo can drop it again. Verify instead of returning.
            AccessibilityRecoveryScheduler.scheduleBindingVerification(context)
            return
        }

        // 4. BOOT & GRACE PERIOD CHECK:
        // Only allow grace period if this is a benign boot event and NOT a clone removal or tamper event!
        val isExplicitTamperOrRemoval = source.contains("PackageRemovedReceiver") ||
                source.contains("Admin") ||
                source.contains("Tamper") ||
                source.contains("ConfirmedOff")
        if (isConfiguredInSettings && !isExplicitTamperOrRemoval) {
            Log.i(TAG, "restoreProtectionFast: Accessibility is configured in Settings.Secure ($source). Allowing OS grace period, then verifying the binding.")
            AccessibilityHealthWorker.cancelReactivationNotification(context)
            // This used to be a plain return: after a reboot the setting is configured while the OS
            // has not bound (or has abandoned) the service, so trusting it left protection silently OFF.
            AccessibilityRecoveryScheduler.scheduleBindingVerification(context)
            return
        }

        // 5. If in Direct Boot mode, we cannot read CE storage (Room/DataStore) or show normal UI
        if (isDirectBootLocked(context)) {
            Log.w(TAG, "Device is in Direct Boot (locked). Programmatic enable attempted; awaiting USER_PRESENT.")
            return
        }

        // 6. Layer 2 & Layer 3 recovery: Only triggered when Accessibility is ACTUALLY disabled in Settings
        // by user tamper or aggressive OS kill, and silent programmatic restore is unavailable.
        Log.w(TAG, "Protection is configured but Accessibility is genuinely OFF in Settings after $source! Triggering recovery.")

        // LAYER 2: Launch urgent recovery activity (only when user actually turned off accessibility in settings)
        try {
            if (Settings.canDrawOverlays(context)) {
                AccessibilityRecoveryActivity.start(
                    context = context,
                    // The source is passed through verbatim: the shield classifies it, so it must not
                    // claim "restart" when the caller was a tamper or an app update.
                    reason = "Accessibility is off ($source)."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch AccessibilityRecoveryActivity: ${e.message}", e)
        }

        // LAYER 3: Post persistent high-priority notification
        try {
            AccessibilityHealthWorker.postReactivationNotification(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post reactivation notification: ${e.message}", e)
        }
    }

    /**
     * Universal intent to open Accessibility Settings.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}", e)
        }
    }

    // ── Shared attempt gate ────────────────────────────────────────────────────────────────
    // Persisted in device-protected storage, like the reboot counters: the gate is needed
    // before first unlock and from non-Hilt entry points (receiver, worker, service callback).

    private fun readLastRestoreAttempt(context: Context): Long = try {
        DirectBootStorage.deCache(context).getLong(KEY_LAST_RESTORE_ATTEMPT_ELAPSED, 0L)
    } catch (e: Exception) {
        Log.d(TAG, "Could not read last restore attempt: ${e.message}")
        0L
    }

    private fun markRestoreAttempt(context: Context, nowElapsed: Long) {
        try {
            DirectBootStorage.deCache(context).edit()
                .putLong(KEY_LAST_RESTORE_ATTEMPT_ELAPSED, nowElapsed)
                .apply()
        } catch (e: Exception) {
            Log.d(TAG, "Could not persist last restore attempt: ${e.message}")
        }
    }

    private fun resetRestoreAttemptGate(context: Context) {
        if (readLastRestoreAttempt(context) == 0L) return
        try {
            DirectBootStorage.deCache(context).edit()
                .putLong(KEY_LAST_RESTORE_ATTEMPT_ELAPSED, 0L)
                .apply()
        } catch (e: Exception) {
            Log.d(TAG, "Could not reset restore attempt gate: ${e.message}")
        }
    }
}
