package com.focusguard.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusguard.app.data.db.EventType
import com.focusguard.app.data.db.SecurityEvent
import com.focusguard.app.data.db.SecurityEventDao
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.recovery.AccessibilityRecoveryHelper
import com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FGDeviceAdmin"

/**
 * FocusGuard Device Admin Receiver
 * ==================================
 * Role: Establishes Device Admin privileges, which is the foundation for
 * uninstall protection on non-Device-Owner Play Store installs.
 *
 * What Device Admin CAN do for us (without Device Owner):
 *  - Prevent easy uninstall via Settings > Apps (the user sees a warning and
 *    must first deactivate admin — which our AccessibilityService intercepts).
 *  - Receive [onDisableRequested] to return a warning string shown in the
 *    system dialog before the user can proceed.
 *
 * What Device Admin CANNOT do (Device Owner only — not available here):
 *  - setUninstallBlocked(): requires isDeviceOwnerApp() == true. This API is
 *    intentionally NOT called here. See guard comment below.
 *
 * Real enforcement comes from [FocusGuardAccessibilityService] which intercepts
 * the deactivate-admin flow and shows the PIN gate before the user can proceed.
 * onDisableRequested is advisory only — the user can click through the dialog.
 */
@AndroidEntryPoint
class FocusGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject
    lateinit var eventDao: SecurityEventDao

    @Inject
    lateinit var repository: FocusGuardRepository

    // Use SupervisorJob so one failing coroutine doesn't cancel siblings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin activated — FocusGuard protection layer is up.")
        com.focusguard.app.service.FocusGuardAccessibilityService.updateDeviceAdminCache(true)

        // If app has been provisioned as Device Owner, lock uninstallation in OS so even ADB pm uninstall fails!
        checkAndApplyUninstallBlocked(context)
        enforceDpcSecurityPolicies(context)

        scope.launch {
            try {
                repository.setUninstallProtectionEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update uninstall protection state: ${e.message}")
            }
            eventDao.insert(SecurityEvent(type = EventType.ADMIN_ENABLED))
        }
    }


    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val isProtected = repository.isFastUninstallProtectionEnabled()
        if (isProtected) {
            // Restore accessibility immediately if it was killed or turned off
            AccessibilityRecoveryHelper.restoreProtectionFast(context, "DeviceAdmin:onDisableRequested", repository)

            try {
                // Immediately kick the user out of the Settings Deactivation flow to prevent bypass
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                context.startActivity(homeIntent)

                // Launch recovery activity to overlay the screen and block clicks
                AccessibilityRecoveryActivity.start(
                    context,
                    "Device Admin deactivation attempted while protection is active"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch home on disable requested", e)
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "🚫 Deactivating FocusGuard is protected. Complete the friction challenge in the app.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            val remainingDays = repository.getFastTimedLockRemainingDays()
            if (remainingDays > 0) {
                return "🚫 UNINSTALL BLOCKED: FocusGuard is hard-locked for $remainingDays more day(s)! " +
                        "You cannot deactivate or delete the app during this focus cycle."
            }
            return "🚫 UNINSTALL BLOCKED: FocusGuard is locked by Uninstall Protection. Complete the friction challenge in the app to modify."
        }

        return "FocusGuard uses administrator access to protect against unauthorized removal. " +
                "Deactivating will remove this protection. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin was deactivated! Protection layer is down.")
        com.focusguard.app.service.FocusGuardAccessibilityService.updateDeviceAdminCache(false)

        scope.launch {
            try {
                repository.setUninstallProtectionEnabled(false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update uninstall protection state: ${e.message}")
            }
            eventDao.insert(
                SecurityEvent(
                    type = EventType.ADMIN_DISABLED,
                    detail = "Device Admin deactivated by user"
                )
            )
            // TODO: Trigger FCM push notification to accountability partner.
            // The FCM call goes here once the partner system is built.
            // Pattern: accountabilityRepository.notifyPartner(event = "ADMIN_DISABLED")
        }
    }


    companion object {
        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        fun checkAndApplyUninstallBlocked(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
            val adminComponent = android.content.ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
            return if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                    Log.i(TAG, "Device Owner active: setUninstallBlocked(true) enabled! ADB pm uninstall is physically blocked.")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to call setUninstallBlocked: ${e.message}", e)
                    false
                }
            } else {
                false
            }
        }

        fun enforceDpcSecurityPolicies(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
            val adminComponent = android.content.ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
            return if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    // 1. Network & Private DNS bypass protection
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_VPN)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_NETWORK_RESET)

                    // 2. Safe Mode reboot bypass protection
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_SAFE_BOOT)

                    // 3. System Date & Time tamper bypass protection (Anti-Time Travel Guard)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_DATE_TIME)
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            dpm.setAutoTimeEnabled(adminComponent, true)
                            dpm.setAutoTimeZoneEnabled(adminComponent, true)
                        } else {
                            @Suppress("DEPRECATION")
                            dpm.setAutoTimeRequired(adminComponent, true)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set auto-time enforcement: ${e.message}")
                    }

                    // 4. Secondary user, Dual App & Work Profile bypass protection (Anti-Cloning Layer 4)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_ADD_USER)
                    @Suppress("DEPRECATION")
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_ADD_MANAGED_PROFILE)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_USER_SWITCH)
                    }

                    // 4.5 App Hide, Force Stop & Disable tamper bypass protection (DPC Hardware Lock)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_APPS_CONTROL)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_UNINSTALL_APPS)
                    dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_FACTORY_RESET)

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            dpm.setUserControlDisabledPackages(adminComponent, listOf(context.packageName))
                            Log.i(TAG, "DPC: setUserControlDisabledPackages enforced for FocusGuard.")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to call setUserControlDisabledPackages: ${e.message}")
                        }
                    }

                    // 5. Chrome Enterprise Managed Policy: Disable Incognito tabs
                    try {
                        val chromeBundle = android.os.Bundle().apply {
                            putInt("IncognitoModeAvailability", 1) // 1 = Disabled
                        }
                        dpm.setApplicationRestrictions(adminComponent, "com.android.chrome", chromeBundle)
                        Log.i(TAG, "Chrome Enterprise Policy: Incognito mode disabled.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to apply Chrome managed policy", e)
                    }

                    Log.i(TAG, "Device Owner active: All DPC security policies enforced (SafeBoot, DateTime, Guest, VPN, Chrome).")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to apply DPC security restrictions: ${e.message}", e)
                    false
                }
            } else {
                false
            }
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                val adminComponent = android.content.ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
                dpm?.isAdminActive(adminComponent) == true
            } catch (_: Exception) {
                false
            }
        }

        fun isUsbDebuggingEnabled(context: Context): Boolean {
            return try {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    android.provider.Settings.Global.ADB_ENABLED,
                    0
                ) == 1
            } catch (e: Exception) {
                false
            }
        }
    }
}

