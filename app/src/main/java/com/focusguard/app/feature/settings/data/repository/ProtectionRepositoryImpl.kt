package com.focusguard.app.feature.settings.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.core.database.dao.SecurityEventDao
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.SecurityEvent
import com.focusguard.app.core.datastore.DirectBootStorage
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.datastore.UptimeLedger
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.domain.policy.ProtectionPolicy
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
    private val uptimeLedger: UptimeLedger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** DE mirror handle: loaded once, then served from the framework cache. */
    private val deCache: SharedPreferences by lazy { DirectBootStorage.deCache(context) }

    /** Last monotonic verdict published for direct-boot readers that hold no repository. */
    private val publishedTimedLockVerdict = AtomicInteger(VERDICT_NONE)

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
                // Only clean up truly corrupt timed state (days configured without expiry)
                if (enabled && days >= 1 && expiry <= 0L) {
                    Log.w(TAG, "Startup cleanup: corrupt timed protection state " +
                        "(enabled=true, days=$days, expiry=$expiry) — resetting corrupt timed lock")
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

    // ── Fast Checks (atomic caches + in-memory ledger; no Room/DataStore on the hot path) ──
    fun isFastUninstallProtectionEnabled(): Boolean {
        if (TimeTamperGuard.isTampered() || TimeTamperGuard.checkAndValidateTime()) {
            return cachedUninstallProtection.get()
        }
        val days = cachedTimedLockDays.get()
        val requiredMs = timedLockDurationMs(days)
        val active = isProtectionReallyActive(
            enabled = cachedUninstallProtection.get(),
            days    = days,
            expiry  = cachedTimedLockExpiry.get(),
            now     = System.currentTimeMillis(),
            requiredMs = requiredMs,
            elapsedUptimeMs = if (requiredMs > 0L) uptimeLedger.msSinceProtectionArmed() else 0L
        )
        // Only a timed lock has a verdict to publish; untimed protection is never "lapsed".
        if (requiredMs > 0L && cachedUninstallProtection.get()) publishTimedLockVerdict(active)
        return active
    }

    fun isFastTimedLockActive(): Boolean = isFastUninstallProtectionEnabled()

    /** True only when the ledger proves the whole configured duration really ran on device uptime. */
    fun isFastTimedLockDurationElapsed(): Boolean {
        val requiredMs = timedLockDurationMs(cachedTimedLockDays.get())
        return requiredMs > 0L && uptimeLedger.msSinceProtectionArmed() >= requiredMs
    }

    /** Uptime ms the timed lock still owes; 0 when protection is untimed. */
    fun fastTimedLockRemainingUptimeMs(): Long {
        val requiredMs = timedLockDurationMs(cachedTimedLockDays.get())
        if (requiredMs <= 0L) return 0L
        return requiredMs - uptimeLedger.msSinceProtectionArmed()
    }

    fun getFastTimedLockRemainingDays(): Long {
        val expiry = cachedTimedLockExpiry.get()
        if (expiry <= 0L) return 0L
        if (TimeTamperGuard.isTampered() || TimeTamperGuard.checkAndValidateTime()) {
            return cachedTimedLockDays.get().toLong().coerceAtLeast(1L)
        }
        val days = cachedTimedLockDays.get()
        // A timed lock is over only when the wall clock AND the monotonic ledger both agree, so
        // the honest remaining time is the larger of the two.
        val wallRemainingMs = expiry - System.currentTimeMillis()
        val requiredMs = timedLockDurationMs(days)
        val uptimeRemainingMs =
            if (requiredMs > 0L) requiredMs - uptimeLedger.msSinceProtectionArmed() else 0L
        val diffMs = maxOf(wallRemainingMs, uptimeRemainingMs)
        return if (diffMs > 0) TimeUnit.MILLISECONDS.toDays(diffMs) + 1 else 0L
    }

    /**
     * Publishes the monotonic verdict into device-protected storage so direct-boot readers that
     * hold no repository
     * ([com.focusguard.app.recovery.AccessibilityRecoveryHelper.isProtectionActive]) evaluate the
     * same rule instead of trusting the wall clock alone. Written only on state change.
     */
    private fun publishTimedLockVerdict(stillRunning: Boolean) {
        val newState = if (stillRunning) VERDICT_RUNNING else VERDICT_LAPSED
        val previous = publishedTimedLockVerdict.getAndSet(newState)
        if (previous == newState) return
        try {
            deCache.edit()
                .putBoolean(UptimeLedger.KEY_TIMED_LOCK_STILL_RUNNING, stillRunning)
                .apply()
            Log.i(TAG, "Timed lock verdict published to DE cache: stillRunning=$stillRunning")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish timed lock verdict: ${e.message}")
        }
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
        // A freshly armed lock is running by definition: publish that before anything can read it.
        publishTimedLockVerdict(true)
        sendRefreshCachesBroadcast()
        logEvent(EventType.UNINSTALL_ATTEMPT, "Timed uninstall protection activated for $days days")
    }

    /**
     * Arms/disarms Uninstall Protection. A running timed lock cannot be disarmed without
     * [challengeAuthorized] (completed challenge, owner support code, or proven expiry).
     */
    suspend fun setUninstallProtectionEnabled(
        enabled: Boolean,
        challengeAuthorized: Boolean = false
    ) {
        val timedLockRunning = ProtectionPolicy.requiresChallengeToDisarm(
            enabled = cachedUninstallProtection.get(),
            days = cachedTimedLockDays.get(),
            protectionReallyActive = isFastUninstallProtectionEnabled()
        )
        if (!enabled && !challengeAuthorized && timedLockRunning) {
            Log.w(TAG, "Refused to disarm Uninstall Protection: timed lock is still running.")
            logEvent(EventType.UNINSTALL_ATTEMPT, "Disarm blocked: timed lock still running")
            return
        }

        prefs.setUninstallProtectionEnabled(enabled)
        cachedUninstallProtection.set(enabled)
        if (!enabled) {
            cachedTimedLockExpiry.set(0L)
            cachedTimedLockDays.set(0)
            // No countdown left: restore the fail-closed default for the next arm.
            publishTimedLockVerdict(true)
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
        /** Wall-clock duration a timed lock of [days] must actually run, in monotonic uptime ms. */
        fun timedLockDurationMs(days: Int): Long =
            ProtectionPolicy.timedLockRequiredMs(days = days, oneDayMs = Timings.ONE_DAY_MS)

        fun isProtectionReallyActive(enabled: Boolean, days: Int, expiry: Long, now: Long): Boolean =
            ProtectionPolicy.isProtectionActive(enabled, days, expiry, now)

        /**
         * Single evaluator of the protection rule. A timed lock is over only when the wall clock is
         * past [expiry] AND [elapsedUptimeMs] of verifiable device uptime covers [requiredMs].
         */
        fun isProtectionReallyActive(
            enabled: Boolean,
            days: Int,
            expiry: Long,
            now: Long,
            requiredMs: Long,
            elapsedUptimeMs: Long
        ): Boolean = ProtectionPolicy.isProtectionActive(
            enabled, days, expiry, now, requiredMs, elapsedUptimeMs
        )

        /** Nothing published yet — readers must fail closed. */
        private const val VERDICT_NONE = 0

        /** Timed lock still running according to the monotonic gate. */
        private const val VERDICT_RUNNING = 1

        /** Timed lock provably finished on both clocks. */
        private const val VERDICT_LAPSED = 2
    }
}

