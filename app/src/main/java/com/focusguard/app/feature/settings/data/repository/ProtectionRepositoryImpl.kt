package com.focusguard.app.feature.settings.data.repository

import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.core.database.dao.SecurityEventDao
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.SecurityEvent
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProtectionRepo"

@Singleton
class ProtectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val eventDao: SecurityEventDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // ── Single Owner of In-Memory Protection Caches ──
    private val cachedUninstallProtection = AtomicBoolean(prefs.syncUninstallProtectionEnabled)
    private val cachedTimedLockExpiry = AtomicLong(prefs.syncUninstallProtectionExpiry)
    private val cachedTimedLockDays = AtomicInteger(prefs.syncUninstallProtectionDays)

    init {
        // Synchronize uninstall protection toggle & timed lock
        repoScope.launch {
            prefs.isUninstallProtectionEnabled.collect { enabled ->
                cachedUninstallProtection.set(enabled)
                Log.d(TAG, "Synchronized uninstall protection cache: $enabled")
            }
        }
        repoScope.launch {
            prefs.uninstallProtectionExpiryTimestamp.collect { expiry ->
                cachedTimedLockExpiry.set(expiry)
            }
        }
        repoScope.launch {
            prefs.uninstallProtectionDays.collect { days ->
                cachedTimedLockDays.set(days)
            }
        }

        // Startup cleanup: if protection is enabled but has corrupt state, silently disable it
        repoScope.launch {
            try {
                val userManager = context.getSystemService(UserManager::class.java)
                if (userManager == null || !userManager.isUserUnlocked) {
                    Log.d(TAG, "Startup cleanup skipped: device not yet unlocked")
                    return@launch
                }
                val enabled = prefs.isUninstallProtectionEnabled.first()
                val expiry  = prefs.uninstallProtectionExpiryTimestamp.first()
                val days    = prefs.uninstallProtectionDays.first()
                if (enabled && (days < 1 || expiry <= 0L)) {
                    Log.w(TAG, "Startup cleanup: corrupt uninstall protection state " +
                        "(enabled=true, days=$days, expiry=$expiry) — disabling protection flag only")
                    prefs.setUninstallProtectionEnabled(false)
                    cachedUninstallProtection.set(false)
                    cachedTimedLockExpiry.set(0L)
                    cachedTimedLockDays.set(0)
                    sendRefreshCachesBroadcast()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Startup cleanup: DataStore read failed, skipping", e)
            }
        }
    }

    // ── Flows ──
    val isUninstallProtectionEnabled: Flow<Boolean> get() = prefs.isUninstallProtectionEnabled
    val uninstallProtectionExpiryTimestamp: Flow<Long> get() = prefs.uninstallProtectionExpiryTimestamp
    val uninstallProtectionDays: Flow<Int> get() = prefs.uninstallProtectionDays

    // ── Fast In-Memory Checks (< 0.01ms Hot Path for Accessibility) ──
    fun isFastUninstallProtectionEnabled(): Boolean {
        if (TimeTamperGuard.isTampered() || TimeTamperGuard.checkAndValidateTime()) {
            return cachedUninstallProtection.get() && cachedTimedLockDays.get() >= 1
        }
        return isProtectionReallyActive(
            enabled = cachedUninstallProtection.get(),
            days    = cachedTimedLockDays.get(),
            expiry  = cachedTimedLockExpiry.get(),
            now     = System.currentTimeMillis()
        )
    }

    fun isFastTimedLockActive(): Boolean = isFastUninstallProtectionEnabled()

    fun getFastTimedLockRemainingDays(): Long {
        val expiry = cachedTimedLockExpiry.get()
        if (expiry <= 0L) return 0L
        if (TimeTamperGuard.isTampered() || TimeTamperGuard.checkAndValidateTime()) {
            return cachedTimedLockDays.get().toLong().coerceAtLeast(1L)
        }
        val diffMs = expiry - System.currentTimeMillis()
        return if (diffMs > 0) TimeUnit.MILLISECONDS.toDays(diffMs) + 1 else 0L
    }

    fun refreshUninstallProtectionNow() {
        repoScope.launch {
            try {
                val canonical = prefs.isUninstallProtectionEnabled.first()
                cachedUninstallProtection.set(canonical)
                cachedTimedLockExpiry.set(prefs.syncUninstallProtectionExpiry)
                cachedTimedLockDays.set(prefs.syncUninstallProtectionDays)
                Log.d(TAG, "refreshUninstallProtectionNow: synced from DataStore -> $canonical")
            } catch (e: Exception) {
                Log.e(TAG, "refreshUninstallProtectionNow: DataStore read failed, falling back to sync/DE cache", e)
                cachedUninstallProtection.set(prefs.syncUninstallProtectionEnabled)
                cachedTimedLockExpiry.set(prefs.syncUninstallProtectionExpiry)
                cachedTimedLockDays.set(prefs.syncUninstallProtectionDays)
            }
        }
    }

    // ── Mutations ──
    suspend fun startTimedUninstallProtection(days: Int) {
        prefs.startTimedUninstallProtection(days)
        val durationMs = TimeUnit.DAYS.toMillis(days.toLong().coerceAtLeast(1L))
        val expiry = System.currentTimeMillis() + durationMs
        cachedTimedLockExpiry.set(expiry)
        cachedTimedLockDays.set(days)
        cachedUninstallProtection.set(true)
        TimeTamperGuard.checkAndValidateTime()
        sendRefreshCachesBroadcast()
        logEvent(EventType.UNINSTALL_ATTEMPT, "Timed uninstall protection activated for $days days")
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        prefs.setUninstallProtectionEnabled(enabled)
        cachedUninstallProtection.set(enabled)
        if (!enabled) {
            cachedTimedLockExpiry.set(0L)
            cachedTimedLockDays.set(0)
            com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(context)
        }
        sendRefreshCachesBroadcast()
    }

    suspend fun disableUninstallProtection() {
        setUninstallProtectionEnabled(false)
    }

    fun sendRefreshCachesBroadcast() {
        try {
            val intent = Intent("com.focusguard.app.ACTION_REFRESH_CACHES").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "ACTION_REFRESH_CACHES broadcast sent to :blocker_service")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send REFRESH_CACHES broadcast", e)
        }
    }

    private suspend fun logEvent(type: EventType, detail: String) {
        try {
            eventDao.insert(SecurityEvent(type = type, detail = detail))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: ${e.message}")
        }
    }

    companion object {
        fun isProtectionReallyActive(enabled: Boolean, days: Int, expiry: Long, now: Long): Boolean {
            if (!enabled) return false
            if (days < 1) return false
            if (expiry <= 0L) return false
            return now < expiry
        }
    }
}

