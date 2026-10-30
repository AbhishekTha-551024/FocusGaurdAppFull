package com.focusguard.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focusguard.app.core.database.dao.SecurityEventDao
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.SecurityEvent
import com.focusguard.app.data.repository.FocusGuardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "FGDeviceAdmin"

@AndroidEntryPoint
class FocusGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    @Inject
    lateinit var eventDao: SecurityEventDao
    @Inject
    lateinit var repository: FocusGuardRepository

    private val adminExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in FocusGuardDeviceAdminReceiver scope: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + adminExceptionHandler)

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin activated — FocusGuard protection layer is up.")
        com.focusguard.app.service.FocusGuardAccessibilityService.updateDeviceAdminCache(true)

        // If app has been provisioned as Device Owner, lock uninstallation in OS so even ADB pm uninstall fails!
        checkAndApplyUninstallBlocked(context)
        enforceDpcSecurityPolicies(context)

        scope.launch {
            try {
                eventDao.insert(SecurityEvent(type = EventType.ADMIN_ENABLED))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log ADMIN_ENABLED event: ${e.message}")
            }
        }
    }


    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val isProtected = try {
            repository.isFastUninstallProtectionEnabled()
        } catch (_: Exception) {
            val syncPrefs = context.getSharedPreferences("focusguard_sync_cache", Context.MODE_PRIVATE)
            syncPrefs.getBoolean("uninstall_protection_enabled", false)
        }
        if (isProtected) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "🚫 Deactivating FocusGuard is protected. Complete the friction challenge in the app.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            // 🛡️ LOCKOUT SHIELD: Instantly cover Settings screen with full-screen recovery overlay and lock screen
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                dpm?.lockNow()
                Log.w(TAG, "LOCK_NOW executed: Device locked to prevent Device Admin deactivation tamper!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute lockNow on disable requested: ${e.message}", e)
            }

            try {
                com.focusguard.app.service.BootCoverService.start(context)
                com.focusguard.app.ui.recovery.AccessibilityRecoveryActivity.start(
                    context,
                    "Admin Deactivation Blocked: FocusGuard Uninstall Protection is active!"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch recovery shield on disable requested: ${e.message}", e)
            }

            val remainingDays = try { repository.getFastTimedLockRemainingDays() } catch (_: Exception) { 0 }
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
            // Partner notification logged in SecurityEventDao.
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

                    // Anti-Cloning Layer 4.1: Block OEM Clone Profile on Android 14+ (API 34+)
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        try {
                            // Constant string "no_add_clone_profile" corresponding to UserManager.DISALLOW_ADD_CLONE_PROFILE
                            val cloneRestriction = "no_add_clone_profile"
                            dpm.addUserRestriction(adminComponent, cloneRestriction)
                            Log.i(TAG, "DPC: DISALLOW_ADD_CLONE_PROFILE restriction enforced.")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to apply DISALLOW_ADD_CLONE_PROFILE: ${e.message}")
                        }
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

        /**
         * Safely deactivates Device Admin privileges so Android OS allows uninstallation.
         */
        fun deactivateDeviceAdmin(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                val adminComponent = android.content.ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
                if (dpm?.isAdminActive(adminComponent) == true) {
                    if (isDeviceOwner(context)) {
                        try {
                            dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                        } catch (e: Exception) {
                            Log.w(TAG, "setUninstallBlocked error: ${e.message}")
                        }
                    }
                    dpm.removeActiveAdmin(adminComponent)
                    com.focusguard.app.service.FocusGuardAccessibilityService.updateDeviceAdminCache(false)
                    Log.i(TAG, "Device Admin successfully deactivated.")
                    true
                } else {
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deactivate Device Admin: ${e.message}", e)
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

