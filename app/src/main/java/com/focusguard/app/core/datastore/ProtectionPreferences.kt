package com.focusguard.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.focusguard.app.core.database.entity.ChallengeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionPreferences @Inject constructor(
    private val core: PreferencesCore
) {
    private val dataStore = core.dataStore
    private val syncPrefs = core.syncPrefs
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
    val syncUninstallProtectionEnabled: Boolean
        get() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val userManager = context.getSystemService(android.os.UserManager::class.java)
                if (userManager != null && !userManager.isUserUnlocked) {
                    val deContext = context.createDeviceProtectedStorageContext()
                    val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    return dePrefs.getBoolean("uninstall_protection_enabled", false)
                }
            }
            return syncPrefs.getBoolean("uninstall_protection_enabled", false)
        }

    val syncUninstallProtectionExpiry: Long
        get() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val userManager = context.getSystemService(android.os.UserManager::class.java)
                if (userManager != null && !userManager.isUserUnlocked) {
                    val deContext = context.createDeviceProtectedStorageContext()
                    val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    return dePrefs.getLong("uninstall_protection_expiry_timestamp", 0L)
                }
            }
            return syncPrefs.getLong("uninstall_protection_expiry_timestamp", 0L)
        }

    val syncUninstallProtectionDays: Int
        get() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val userManager = context.getSystemService(android.os.UserManager::class.java)
                if (userManager != null && !userManager.isUserUnlocked) {
                    val deContext = context.createDeviceProtectedStorageContext()
                    val dePrefs = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    return dePrefs.getInt("uninstall_protection_days", 0)
                }
            }
            return syncPrefs.getInt("uninstall_protection_days", 0)
        }

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
        return System.currentTimeMillis() < expiry
    }

    fun getTimedUninstallProtectionRemainingDays(): Long {
        val expiry = syncUninstallProtectionExpiry
        if (expiry <= 0L) return 0L
        val diffMs = expiry - System.currentTimeMillis()
        return if (diffMs > 0) TimeUnit.MILLISECONDS.toDays(diffMs) + 1 else 0L
    }

    suspend fun startTimedUninstallProtection(days: Int) {
        val durationMs = days * 24 * 60 * 60 * 1000L
        val expiryTimestamp = System.currentTimeMillis() + durationMs

        syncPrefs.edit()
            .putBoolean("uninstall_protection_enabled", true)
            .putLong("uninstall_protection_expiry_timestamp", expiryTimestamp)
            .putInt("uninstall_protection_days", days)
            .apply()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val deContext = context.createDeviceProtectedStorageContext()
                deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("uninstall_protection_enabled", true)
                    .putLong("uninstall_protection_expiry_timestamp", expiryTimestamp)
                    .putInt("uninstall_protection_days", days)
                    .apply()
            } catch (e: Exception) {
                android.util.Log.d("ProtectionPrefs", "Failed to update DE cache: ${e.message}")
            }
        }

        dataStore.edit {
            it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = true
            it[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = expiryTimestamp
            it[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] = days
        }
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("uninstall_protection_enabled", enabled).apply()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val deContext = context.createDeviceProtectedStorageContext()
                deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("uninstall_protection_enabled", enabled)
                    .apply()
            } catch (e: Exception) {
                android.util.Log.d("ProtectionPrefs", "Failed to update DE cache: ${e.message}")
            }
        }
        dataStore.edit { it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = enabled }
    }

    suspend fun disableUninstallProtection() {
        syncPrefs.edit()
            .putBoolean("uninstall_protection_enabled", false)
            .putLong("uninstall_protection_expiry_timestamp", 0L)
            .putInt("uninstall_protection_days", 0)
            .apply()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val deContext = context.createDeviceProtectedStorageContext()
                deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("uninstall_protection_enabled", false)
                    .putLong("uninstall_protection_expiry_timestamp", 0L)
                    .putInt("uninstall_protection_days", 0)
                    .apply()
            } catch (e: Exception) {
                android.util.Log.d("ProtectionPrefs", "Failed to update DE cache: ${e.message}")
            }
        }

        dataStore.edit {
            it[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] = false
            it[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = 0L
            it[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] = 0
        }
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

