package com.focusguard.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.core.common.constants.Timings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Recovery"

/**
 * UptimeLedger
 * ============
 * Monotonic proof that a timed uninstall lock actually ran for its full duration.
 *
 * The wall clock is user-editable, so an expiry compared against System.currentTimeMillis() can be
 * cancelled from Settings > Date & time: switch automatic time off, jump past the lock end, and the
 * lock reads as expired — which also stops accessibility auto-recovery and opens the uninstall
 * window. This ledger is built only from [SystemClock.elapsedRealtime] (Invariant Rule 4): it counts
 * real device uptime, ignores date and timezone changes, and resets exactly once per boot.
 *
 * Chain rule: total = persistedBase + (elapsedRealtime - persistedAnchor) while the boot identity is
 * unchanged. A reboot or a clock-epoch change breaks the chain, so only one capped
 * [Timings.UPTIME_LEDGER_MAX_STEP_MS] step is credited for the broken link — rebooting or shifting
 * the clock repeatedly can never buy more credit than the real time it costs. Under-crediting only
 * makes a lock run longer, which is the fail-closed direction.
 *
 * Durable state lives in device-protected storage, so it is readable at direct boot and survives a
 * reboot. Hot paths read the in-memory mirror only (Invariant Rule 3); storage is touched on the
 * throttled persist step.
 */
@Singleton
class UptimeLedger @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dePrefs: SharedPreferences by lazy { DirectBootStorage.deCache(context) }

    /** Accumulated monotonic uptime at [anchorRealtimeMs]. */
    private val totalMs = AtomicLong(NOT_HYDRATED)

    /** elapsedRealtime() paired with [totalMs]. */
    private val anchorRealtimeMs = AtomicLong(NO_ANCHOR)

    /** Boot identity (wall clock at boot) paired with the anchor. */
    private val anchorBootIdMs = AtomicLong(0L)

    /** Ledger total when the timed lock was armed. */
    private val armedAtMs = AtomicLong(NOT_HYDRATED)

    /** Wall clock reading of when this boot started: changes on reboot or a clock move. */
    private fun bootId(nowWallMs: Long, realtimeMs: Long): Long = nowWallMs - realtimeMs

    private fun hydrate() {
        if (totalMs.get() != NOT_HYDRATED && armedAtMs.get() != NOT_HYDRATED) return
        try {
            val sp = dePrefs
            if (totalMs.get() == NOT_HYDRATED) {
                totalMs.set(sp.getLong(KEY_TOTAL_MS, 0L))
                anchorRealtimeMs.set(sp.getLong(KEY_ANCHOR_REALTIME_MS, NO_ANCHOR))
                anchorBootIdMs.set(sp.getLong(KEY_ANCHOR_BOOT_ID_MS, 0L))
            }
            if (armedAtMs.get() == NOT_HYDRATED) {
                armedAtMs.set(sp.getLong(KEY_PROTECTION_ARMED_AT_MS, NO_ARM_STAMP))
            }
        } catch (e: Exception) {
            Log.w(TAG, "UptimeLedger hydrate failed: ${e.message}")
            if (totalMs.get() == NOT_HYDRATED) totalMs.set(0L)
            if (armedAtMs.get() == NOT_HYDRATED) armedAtMs.set(NO_ARM_STAMP)
        }
    }

    /** Monotonic uptime since boot, accumulated across reboots. Never rewinds. */
    fun currentUptimeMs(): Long = synchronized(this) {
        hydrate()
        val realtime = SystemClock.elapsedRealtime()
        val boot = bootId(System.currentTimeMillis(), realtime)
        val storedTotal = totalMs.get()
        val storedAnchor = anchorRealtimeMs.get()
        val storedBoot = anchorBootIdMs.get()

        val accrued = when {
            // Never anchored (fresh install or first read of a new epoch): this boot counts.
            storedAnchor == NO_ANCHOR -> realtime
            // Chain intact: same boot identity and elapsedRealtime still moving forward.
            storedBoot == boot && realtime >= storedAnchor -> realtime - storedAnchor
            // Rebooted or clock moved: credit one capped step, never the unverifiable gap.
            else -> Timings.UPTIME_LEDGER_MAX_STEP_MS.coerceAtMost(realtime.coerceAtLeast(0L))
        }

        val total = storedTotal + accrued
        val reanchored = storedAnchor == NO_ANCHOR || storedBoot != boot ||
            accrued >= Timings.UPTIME_LEDGER_PERSIST_STEP_MS
        if (reanchored) {
            totalMs.set(maxOf(total, storedTotal))
            anchorRealtimeMs.set(realtime)
            anchorBootIdMs.set(boot)
            persistLedger()
        }
        total
    }

    /**
     * Called when a timed lock starts: pins the ledger value the countdown must run past.
     * Survives reboot because it lands in device-protected storage.
     */
    fun recordProtectionArmedAt() {
        val total = synchronized(this) {
            currentUptimeMs().also { armedAtMs.set(it) }
        }
        persistArmedAt()
        Log.i(TAG, "UptimeLedger: timed lock armed at monotonic uptime ${total}ms")
    }

    /** Called when protection is dropped: there is no countdown left to prove. */
    fun clearProtectionArmedAt() {
        synchronized(this) { armedAtMs.set(NO_ARM_STAMP) }
        try {
            dePrefs.edit().remove(KEY_PROTECTION_ARMED_AT_MS).apply()
        } catch (e: Exception) {
            Log.w(TAG, "UptimeLedger clear failed: ${e.message}")
        }
    }

    /**
     * Monotonic ms elapsed since the timed lock was armed.
     *
     * A lock armed before this ledger existed has no stamp. Rather than reading that as "infinitely
     * elapsed" — the exact bypass this class exists to close — the stamp is pinned now and 0 is
     * returned, so the lock stays active and expires only after its real duration. Call this only
     * when a timed lock is configured, so untimed protection never gets a stray stamp.
     */
    fun msSinceProtectionArmed(): Long {
        val total = currentUptimeMs()
        return synchronized(this) {
            val armedAt = armedAtMs.get()
            if (armedAt == NO_ARM_STAMP || armedAt < 0L || armedAt > total) {
                armedAtMs.set(total)
                persistArmedAt()
                0L
            } else {
                total - armedAt
            }
        }
    }

    private fun persistLedger() {
        try {
            dePrefs.edit()
                .putLong(KEY_TOTAL_MS, totalMs.get())
                .putLong(KEY_ANCHOR_REALTIME_MS, anchorRealtimeMs.get())
                .putLong(KEY_ANCHOR_BOOT_ID_MS, anchorBootIdMs.get())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "UptimeLedger persist failed: ${e.message}")
        }
    }

    private fun persistArmedAt() {
        try {
            val armed = armedAtMs.get()
            val editor = dePrefs.edit()
            if (armed < 0L) editor.remove(KEY_PROTECTION_ARMED_AT_MS)
            else editor.putLong(KEY_PROTECTION_ARMED_AT_MS, armed)
            editor.apply()
        } catch (e: Exception) {
            Log.w(TAG, "UptimeLedger arm stamp persist failed: ${e.message}")
        }
    }

    companion object {
        /** Accumulated monotonic uptime (ms) at the moment of [KEY_ANCHOR_REALTIME_MS]. */
        const val KEY_TOTAL_MS = "uptime_ledger_total_ms"

        /** SystemClock.elapsedRealtime() paired with [KEY_TOTAL_MS]. */
        const val KEY_ANCHOR_REALTIME_MS = "uptime_ledger_anchor_realtime_ms"

        /** Boot identity (wall clock at boot) paired with the anchor. */
        const val KEY_ANCHOR_BOOT_ID_MS = "uptime_ledger_anchor_boot_id"

        /** Ledger total at the instant the timed uninstall lock was armed. */
        const val KEY_PROTECTION_ARMED_AT_MS = "protection_uptime_at_arm_ms"

        /**
         * Last verified answer to "is the timed lock still running?" from the monotonic gate,
         * published by the single evaluator
         * [com.focusguard.app.feature.settings.data.repository.ProtectionRepositoryImpl] so
         * direct-boot readers that hold no repository —
         * [com.focusguard.app.recovery.AccessibilityRecoveryHelper.isProtectionActive] — cannot be
         * talked out of protection by a wall-clock change. Defaults to true (fail closed).
         */
        const val KEY_TIMED_LOCK_STILL_RUNNING = "protection_timed_lock_still_running"

        /** In-memory marker: not read from storage yet. */
        private const val NOT_HYDRATED = -1L

        /** In-memory marker: no timed lock is armed. */
        private const val NO_ARM_STAMP = -2L

        /** In-memory + stored marker: the ledger has never been anchored. */
        private const val NO_ANCHOR = -3L
    }
}
