package com.focusguard.app.service.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.receiver.NewAppInstallReceiver
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.utils.DeviceAdminManager
import javax.inject.Inject

/**
 * Manages the dynamic broadcast receivers registered and unregistered by
 * [FocusGuardAccessibilityService].
 *
 * Rules:
 * - NOT @Singleton: Holds mutable registration flags tied to a specific service lifecycle.
 * - Does NOT hold a Context field: Context is passed per method call to prevent leaks.
 * - Exact logic, try/catch, and log texts copied from FocusGuardAccessibilityService.
 */
class ServiceReceiverManager @Inject constructor(
    private val repository: FocusGuardRepository,
    private val deviceAdminManager: DeviceAdminManager,
    private val newAppInstallReceiver: NewAppInstallReceiver
) {

    // When the UI process (Main) toggles uninstall protection, it broadcasts
    // ACTION_REFRESH_CACHES so the :blocker_service process updates its AtomicBoolean
    // caches instantly -- no 30-second DataStore polling lag.
    private val refreshCachesReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != FocusGuardAccessibilityService.ACTION_REFRESH_CACHES) return
            Log.d(TAG, "REFRESH_CACHES broadcast received -- syncing all in-memory caches")
            repository.refreshUninstallProtectionNow()
            deviceAdminManager.refreshCache()
        }
    }
    private var isRefreshCachesReceiverRegistered = false

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            com.focusguard.app.blockers.TimeTamperGuard.onSystemTimeOrTimeZoneChanged()
        }
    }
    private var isTimeReceiverRegistered = false

    private var isNewAppReceiverRegistered = false

    private var userUnlockedReceiver: BroadcastReceiver? = null

    /**
     * Registers early receivers (time change and cache refresh) before periodic refresh
     * and Direct Boot checks begin.
     */
    fun registerEarlyReceivers(context: Context) {
        if (!isTimeReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(timeChangeReceiver, filter)
            isTimeReceiverRegistered = true
        }

        // Register cross-process cache-refresh receiver
        if (!isRefreshCachesReceiverRegistered) {
            val refreshFilter = IntentFilter(FocusGuardAccessibilityService.ACTION_REFRESH_CACHES)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(refreshCachesReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(refreshCachesReceiver, refreshFilter)
                }
                isRefreshCachesReceiverRegistered = true
                Log.d(TAG, "REFRESH_CACHES receiver registered.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register REFRESH_CACHES receiver", e)
            }
        }
    }

    /**
     * Dynamically registers NewAppInstallReceiver so modern Android (API 26-35) delivers ACTION_PACKAGE_ADDED.
     * Note: Evaluated without an outer flag guard to maintain exact original behavior.
     */
    fun registerNewAppReceiver(context: Context) {
        try {
            val packageFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(newAppInstallReceiver, packageFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(newAppInstallReceiver, packageFilter)
            }
            isNewAppReceiverRegistered = true
            Log.i(TAG, "Dynamic NewAppInstallReceiver registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register dynamic NewAppInstallReceiver", e)
        }
    }

    /**
     * Registers USER_UNLOCKED receiver during Direct Boot.
     * On receive, unregisters inline and triggers [onUserUnlocked].
     */
    fun registerUserUnlockedReceiver(context: Context, onUserUnlocked: () -> Unit) {
        if (userUnlockedReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    Log.i(TAG, "ACTION_USER_UNLOCKED received -- initializing CE storage and observers.")
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.d(TAG, "userUnlockedReceiver inline unregister error: ${e.message}")
                    }
                    userUnlockedReceiver = null
                    onUserUnlocked()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        try {
            context.registerReceiver(receiver, filter)
            userUnlockedReceiver = receiver
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register USER_UNLOCKED receiver: ${e.message}", e)
        }
    }

    /**
     * Called during service onUnbind. Unregisters newAppInstallReceiver and refreshCachesReceiver only.
     */
    fun unregisterOnUnbind(context: Context) {
        if (isNewAppReceiverRegistered) {
            try {
                context.unregisterReceiver(newAppInstallReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering NewAppInstallReceiver", e)
            }
            isNewAppReceiverRegistered = false
        }
        if (isRefreshCachesReceiverRegistered) {
            try {
                context.unregisterReceiver(refreshCachesReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering refreshCachesReceiver", e)
            }
            isRefreshCachesReceiverRegistered = false
        }
    }

    /**
     * Called during service onDestroy. Unregisters all 4 receivers and resets flags.
     */
    fun unregisterAll(context: Context) {
        if (isNewAppReceiverRegistered) {
            try {
                context.unregisterReceiver(newAppInstallReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering NewAppInstallReceiver", e)
            }
            isNewAppReceiverRegistered = false
        }
        if (isRefreshCachesReceiverRegistered) {
            try {
                context.unregisterReceiver(refreshCachesReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering refreshCachesReceiver in onDestroy", e)
            }
            isRefreshCachesReceiverRegistered = false
        }
        if (isTimeReceiverRegistered) {
            try {
                context.unregisterReceiver(timeChangeReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering timeChangeReceiver", e)
            }
            isTimeReceiverRegistered = false
        }
        userUnlockedReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.d(TAG, "userUnlockedReceiver unregister error: ${e.message}")
            }
            userUnlockedReceiver = null
        }
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}

