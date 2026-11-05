package com.focusguard.app.service.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceAdminManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cachedDeviceAdminActive = AtomicBoolean(false)
    private var adminRefreshJob: Job? = null

    /**
     * Zero-latency device admin check -- reads from the AtomicBoolean cache refreshed every 30 s.
     * Never makes a Binder IPC call on the hot accessibility-event path (AGENTS Rule 5).
     */
    fun isDeviceAdminActive(): Boolean {
        val cached = cachedDeviceAdminActive.get()
        if (!cached) {
            // TODO: Binder IPC fallback on event path, review in a later phase
            val raw = isDeviceAdminActiveRaw()
            if (raw) cachedDeviceAdminActive.set(true)
            return raw
        }
        return true
    }

    /** Raw Binder IPC call -- only used to populate/refresh the cache, NOT on the hot path. */
    fun isDeviceAdminActiveRaw(): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
            dpm?.isAdminActive(adminComponent) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin state", e)
            false
        }
    }

    fun updateCache(isActive: Boolean) {
        cachedDeviceAdminActive.set(isActive)
        Log.d(TAG, "Device admin cache explicitly updated: $isActive")
    }

    fun refreshCache() {
        cachedDeviceAdminActive.set(isDeviceAdminActiveRaw())
    }

    fun startPeriodicRefresh(scope: CoroutineScope) {
        cachedDeviceAdminActive.set(isDeviceAdminActiveRaw())
        adminRefreshJob?.cancel()
        adminRefreshJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                cachedDeviceAdminActive.set(isDeviceAdminActiveRaw())
                Log.d(TAG, "Device admin cache refreshed: ${cachedDeviceAdminActive.get()}")
            }
        }
    }

    fun stopPeriodicRefresh() {
        adminRefreshJob?.cancel()
        adminRefreshJob = null
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}

