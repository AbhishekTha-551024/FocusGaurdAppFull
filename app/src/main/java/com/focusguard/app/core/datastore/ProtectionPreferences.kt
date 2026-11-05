package com.focusguard.app.core.datastore

import android.os.Build
import androidx.datastore.preferences.core.edit
import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.domain.model.CommitmentLockState
import com.focusguard.app.domain.policy.ProtectionPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Fast-read cache keys for the timed uninstall lock (CE snapshot + DE mirror share these names). */
internal const val KEY_TIMED_LOCK_ENABLED = "uninstall_protection_enabled"
internal const val KEY_TIMED_LOCK_EXPIRY = "uninstall_protection_expiry_timestamp"
internal const val KEY_TIMED_LOCK_DAYS = "uninstall_protection_days"
private const val DE_CACHE_LOG_TAG = "ProtectionPrefs"

@Singleton
class ProtectionPreferences @Inject constructor(
    private val core: PreferencesCore,
    private val uptimeLedger: UptimeLedger
) {
    private val dataStore = core.dataStore
    private val syncPrefs get() = core.syncPrefs
    private val context = core.context

    val isUninstallProtectionEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] ?: false
    }

    val uninstallProtectionExpiryTimestamp: Flow<Long> = dataStore.data.map {
        it[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] ?: 0L
    }

    val uninstallProtectionDays: Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] ?: 0
    }

    /**
     * The three timed-lock fields read from ONE DataStore emission.
     *
     * The separate flows above are fine for screens, but combining them can briefly yield a
     * half-applied pair — a new deadline still carrying the old day count — because each key emits on
     * its own. The mirror watcher must never push such a torn record to the device, so it reads here.
     */
    fun timedLockState(deviceId: String): Flow<CommitmentLockState> = dataStore.data.map { prefs ->
        CommitmentLockState(
            deviceId = deviceId,
            isTimedLockActive = prefs[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] ?: false,
            days = prefs[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] ?: 0,
            expiryWallMs = prefs[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] ?: 0L,
            recordedAtMs = System.currentTimeMillis()
        )
    }

    val challengeType: Flow<ChallengeType> = dataStore.data.map {
        val name = it[PreferenceKeys.CHALLENGE_TYPE] ?: ChallengeType.TYPING.name
        try { ChallengeType.valueOf(name) } catch (_: Exception) { ChallengeType.TYPING }
    }

    val commitmentSentence: Flow<String> = dataStore.data.map {
        val s = it[PreferenceKeys.COMMITMENT_SENTENCE] ?: DEFAULT_COMMITMENT_SENTENCE
        if (s.trim().length >= 500) s.trim() else DEFAULT_COMMITMENT_SENTENCE
    }

    val timeDelayHours: Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.TIME_DELAY_HOURS] ?: 24
    }

    val isChallengeActive: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.IS_CHALLENGE_ACTIVE] ?: false
    }

    val isAntiCloningShieldEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.ANTI_CLONING_SHIELD_ENABLED] ?: true
    }

    // ── Synchronous 0ms RAM Snapshot Getters ──
    // DirectBootStorage already answers from the DE mirror before first user unlock, so every
    // protection key below stays readable after a reboot while CE storage is still locked.
    val syncUninstallProtectionEnabled: Boolean
        get() = DirectBootStorage.readCache(context).getBoolean(KEY_TIMED_LOCK_ENABLED, false)

    val syncUninstallProtectionExpiry: Long
        get() = DirectBootStorage.readCache(context).getLong(KEY_TIMED_LOCK_EXPIRY, 0L)

    val syncUninstallProtectionDays: Int
        get() = DirectBootStorage.readCache(context).getInt(KEY_TIMED_LOCK_DAYS, 0)

    val syncChallengeType: ChallengeType
        get() {
            val name = syncPrefs.getString("challenge_type", ChallengeType.TYPING.name) ?: ChallengeType.TYPING.name
            return try { ChallengeType.valueOf(name) } catch (_: Exception) { ChallengeType.TYPING }
        }

    val syncCommitmentSentence: String
        get() {
            val s = syncPrefs.getString("commitment_sentence", DEFAULT_COMMITMENT_SENTENCE) ?: DEFAULT_COMMITMENT_SENTENCE
            return if (s.trim().length >= 500) s.trim() else DEFAULT_COMMITMENT_SENTENCE
        }

    val syncTimeDelayHours: Int
        get() = syncPrefs.getInt("time_delay_hours", 24)

    val syncIsChallengeActive: Boolean
        get() = syncPrefs.getBoolean("is_challenge_active", false)

    val syncAntiCloningShieldEnabled: Boolean
        get() = true

    fun isTimedUninstallProtectionActive(): Boolean {
        if (!syncUninstallProtectionEnabled) return false
        val days = syncUninstallProtectionDays
        if (days < 1) return false
        val expiry = syncUninstallProtectionExpiry
        if (expiry <= 0L) return false
        // The wall clock alone can be wound forward from Settings, so the lock also needs
        // verifiable monotonic uptime behind it before it counts as finished.
        val requiredMs = ProtectionPolicy.timedLockRequiredMs(days = days, oneDayMs = Timings.ONE_DAY_MS)
        return !ProtectionPolicy.isTimedLockLapsed(
            expiryMs = expiry,
            wallNowMs = System.currentTimeMillis(),
            requiredMs = requiredMs,
            elapsedUptimeMs = if (requiredMs > 0L) uptimeLedger.msSinceProtectionArmed() else 0L
        )
    }

    fun getTimedUninstallProtectionRemainingDays(): Long {
        val expiry = syncUninstallProtectionExpiry
        if (expiry <= 0L) return 0L
        val wallRemainingMs = expiry - System.currentTimeMillis()
        val requiredMs = ProtectionPolicy.timedLockRequiredMs(
            days = syncUninstallProtectionDays,
            oneDayMs = Timings.ONE_DAY_MS
        )
        // The lock ends when BOTH clocks agree, so what is genuinely left is the larger of the two.
        val uptimeRemainingMs =
            if (requiredMs > 0L) requiredMs - uptimeLedger.msSinceProtectionArmed() else 0L
        val diffMs = maxOf(wallRemainingMs, uptimeRemainingMs)
        return if (diffMs > 0) TimeUnit.MILLISECONDS.toDays(diffMs) + 1 else 0L
    }

    /**
     * Writes the timed uninstall lock fields to both fast-read caches: the CE snapshot and the DE
     * mirror. A null field is left untouched so a flag-only write cannot clobber a running countdown.
     */
    private fun writeTimedLockCaches(enabled: Boolean, expiryMs: Long?, days: Int?) {
        val editor = syncPrefs.edit().putBoolean(KEY_TIMED_LOCK_ENABLED, enabled)
        if (expiryMs != null) editor.putLong(KEY_TIMED_LOCK_EXPIRY, expiryMs)
        if (days != null) editor.putInt(KEY_TIMED_LOCK_DAYS, days)
        editor.apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val deEditor = DirectBootStorage.deCache(context).edit()
                    .putBoolean(KEY_TIMED_LOCK_ENABLED, enabled)
                if (expiryMs != null) deEditor.putLong(KEY_TIMED_LOCK_EXPIRY, expiryMs)
                if (days != null) deEditor.putInt(KEY_TIMED_LOCK_DAYS, days)
                deEditor.apply()
            } catch (e: Exception) {
                android.util.Log.d(DE_CACHE_LOG_TAG, "Failed to update DE cache: ${e.message}")
            }
        }
    }

    /**
     * This install's timed-lock state in the same shape the server record uses, so
     * [com.focusguard.app.domain.policy.CommitmentRestorePolicy] can compare the two directly.
     * Cache-only reads (Invariant Rule 3), so it is safe on any thread.
     */
    fun localCommitmentState(deviceId: String): CommitmentLockState = CommitmentLockState(
        deviceId = deviceId,
        isTimedLockActive = syncUninstallProtectionEnabled,
        days = syncUninstallProtectionDays,
        expiryWallMs = syncUninstallProtectionExpiry,
        recordedAtMs = System.currentTimeMillis()
    )

    suspend fun startTimedUninstallProtection(days: Int) {
        val durationMs = days * Timings.ONE_DAY_MS
        val expiryTimestamp = System.currentTimeMillis() + durationMs

        // Anchor the countdown to monotonic uptime as well as to the wall clock.
        uptimeLedger.recordProtectionArmedAt()

        writeTimedLockCaches(enabled = true, expiryMs = expiryTimestamp, days = days)

        dataStore.edit {
            it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = true
            it[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = expiryTimestamp
            it[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] = days
        }
    }

    /**
     * Re-arms a timed uninstall lock that the server says this device still owes, keeping the
     * ORIGINAL deadline instead of restarting the countdown from today.
     *
     * @param days              whole days left as computed by the restore policy (>= 1)
     * @param expiryWallMs      the server's own deadline
     * @param repinUptimeAnchor pin the monotonic anchor only when local state had no live lock; an
     *                          in-progress lock keeps its anchor so earned uptime is not thrown away
     */
    suspend fun reArmRestoredTimedProtection(
        days: Int,
        expiryWallMs: Long,
        repinUptimeAnchor: Boolean
    ) {
        val safeDays = days.coerceAtLeast(1)
        if (repinUptimeAnchor) uptimeLedger.recordProtectionArmedAt()

        writeTimedLockCaches(enabled = true, expiryMs = expiryWallMs, days = safeDays)

        dataStore.edit {
            it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = true
            it[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = expiryWallMs
            it[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] = safeDays
        }
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        writeTimedLockCaches(enabled = enabled, expiryMs = null, days = null)
        dataStore.edit { it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = enabled }

        // Dropped protection has no countdown left to prove.
        if (!enabled) uptimeLedger.clearProtectionArmedAt()
    }

    suspend fun disableUninstallProtection() {
        writeTimedLockCaches(enabled = false, expiryMs = 0L, days = 0)

        dataStore.edit {
            it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = false
            it[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = 0L
            it[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] = 0
        }

        uptimeLedger.clearProtectionArmedAt()
    }

    suspend fun setChallengeType(type: ChallengeType) {
        syncPrefs.edit().putString("challenge_type", type.name).apply()
        dataStore.edit { it[PreferenceKeys.CHALLENGE_TYPE] = type.name }
    }

    suspend fun setCommitmentSentence(sentence: String) {
        val trimmed = sentence.trim()
        val valid = if (trimmed.length >= 500) trimmed else DEFAULT_COMMITMENT_SENTENCE
        syncPrefs.edit().putString("commitment_sentence", valid).apply()
        dataStore.edit { it[PreferenceKeys.COMMITMENT_SENTENCE] = valid }
    }

    suspend fun setTimeDelayHours(hours: Int) {
        val safeHours = hours.coerceAtLeast(1)
        syncPrefs.edit().putInt("time_delay_hours", safeHours).apply()
        dataStore.edit { it[PreferenceKeys.TIME_DELAY_HOURS] = safeHours }
    }

    suspend fun setChallengeActive(active: Boolean) {
        syncPrefs.edit().putBoolean("is_challenge_active", active).apply()
        dataStore.edit { it[PreferenceKeys.IS_CHALLENGE_ACTIVE] = active }
    }

    suspend fun setAntiCloningShieldEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("anti_cloning_shield_enabled", true).apply()
        dataStore.edit { it[PreferenceKeys.ANTI_CLONING_SHIELD_ENABLED] = true }
    }
}

