package com.focusguard.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.service.ProtectionWatchdogService
import com.focusguard.app.worker.AccessibilityHealthWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FGBootReceiver"

/**
 * BootReceiver
 * ============
 * Re-arms FocusGuard services upon device boot, unlock, and package update.
 * Enforces immediate, zero-delay recovery of the Accessibility Service across:
 * - Device power-on / restart (BOOT_COMPLETED, QUICKBOOT_POWERON)
 * - Direct boot state before initial PIN unlock (LOCKED_BOOT_COMPLETED)
 * - Device unlock by user (USER_PRESENT)
 * - App updates (MY_PACKAGE_REPLACED)
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: FocusGuardRepository

    private val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled coroutine exception in BootReceiver", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast action: $action")

        val isDirectBoot = AccessibilityRecoveryHelper.isDirectBootLocked(context)

        // 1. Direct Boot Handler (before user enters PIN / Pattern)
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED || isDirectBoot) {
            Log.i(TAG, "Direct Boot detected ($action) — checking protection state before silent recovery.")
            // CE storage (Room/DataStore) is still locked, but DE storage + Settings.Secure is available
            if (AccessibilityRecoveryHelper.isProtectionActive(context, repository)) {
                Log.i(TAG, "Direct Boot: Uninstall protection is ACTIVE. Restoring accessibility programmatically.")
                AccessibilityRecoveryHelper.tryEnableAccessibilityProgrammatically(context)
            } else {
                Log.i(TAG, "Direct Boot: Uninstall protection is OFF. Skipping accessibility auto-enable.")
            }
            return
        }

        // 2. Main Boot / Unlock Handler (Credential storage is unlocked)
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // STEP A: If provisioned as Device Owner, enforce OS-level uninstallation block immediately
            try {
                if (FocusGuardDeviceAdminReceiver.isDeviceOwner(context)) {
                    FocusGuardDeviceAdminReceiver.checkAndApplyUninstallBlocked(context)
                    FocusGuardDeviceAdminReceiver.enforceDpcSecurityPolicies(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-enforce DPC policies on boot: ${e.message}")
            }

            // STEP A.5: Boot Cover Shield — Mount temporary cover overlay if protection is active
            // and Accessibility Service has not yet connected to close the boot vulnerability gap.
            try {
                if (AccessibilityRecoveryHelper.isProtectionActive(context, repository) &&
                    !com.focusguard.app.service.FocusGuardAccessibilityService.isServiceRunning
                ) {
                    com.focusguard.app.service.BootCoverService.start(context)
                    com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity.start(
                        context,
                        "Device event ($action): Restoring accessibility protection"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate BootCoverService/RecoveryActivity on boot: ${e.message}", e)
            }

            // STEP B: Restore Accessibility immediately (< 1ms if WRITE_SECURE_SETTINGS, else Activity + Alert)
            AccessibilityRecoveryHelper.restoreProtectionFast(
                context = context,
                source = "BootReceiver:$action",
                repository = repository
            )

            // STEP B: Ensure periodic health watchdog is enqueued
            try {
                AccessibilityHealthWorker.schedulePeriodicCheck(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule health check: ${e.message}")
            }

            // STEP C: Ensure no dangling background services
            try {
                ProtectionWatchdogService.stopService(context)
            } catch (e: Exception) {
                Log.d(TAG, "ProtectionWatchdogService stopService error: ${e.message}")
            }

            // STEP D: Reconcile any pending time-delay changes that matured while device was off
            val pendingResult = goAsync()
            scope.launch {
                try {
                    repository.reconcilePendingChanges()
                    repository.logEvent(
                        type = EventType.ADMIN_ENABLED,
                        detail = "BootReceiver processed $action: protection verified and pending changes reconciled"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reconciling pending changes on boot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
