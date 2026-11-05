package com.focusguard.app.core.datastore

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreferences @Inject constructor(
    private val core: PreferencesCore
) {
    private val dataStore = core.dataStore
    private val syncPrefs get() = core.syncPrefs
    private val prefScope = core.prefScope

    val syncSessionLockActive: Boolean
        get() {
            if (!syncPrefs.getBoolean("session_lock_active", false)) return false
            val end = syncPrefs.getLong("session_end_time_ms", 0L)
            if (System.currentTimeMillis() >= end) {
                syncPrefs.edit().putBoolean("session_lock_active", false).apply()
                return false
            }
            return true
        }

    val syncSessionEndTimeMs: Long
        get() = syncPrefs.getLong("session_end_time_ms", 0L)

    val syncSessionTotalMinutes: Int
        get() = syncPrefs.getInt("session_total_minutes", 25)

    val syncSuitcasePackages: Set<String>
        get() = syncPrefs.getStringSet("session_suitcase_packages", emptySet()) ?: emptySet()

    val syncEmergencyParachutesCount: Int
        get() {
            replenishDailyParachuteIfNeeded()
            return syncPrefs.getInt("emergency_parachutes_count", 2)
        }

    val syncTotalFocusSessionsCompleted: Int
        get() = syncPrefs.getInt("total_focus_sessions_completed", 0)

    val syncTotalFocusMinutesCompleted: Long
        get() = syncPrefs.getLong("total_focus_minutes_completed", 0L)

    fun replenishDailyParachuteIfNeeded() {
        val today = core.getTodayDateString()
        val lastDate = syncPrefs.getString("last_parachute_regen_date", "")
        if (today != lastDate) {
            val current = syncPrefs.getInt("emergency_parachutes_count", 2)
            if (current < 2) {
                val updated = (current + 1).coerceAtMost(2)
                syncPrefs.edit()
                    .putInt("emergency_parachutes_count", updated)
                    .putString("last_parachute_regen_date", today)
                    .apply()
                prefScope.launch {
                    try {
                        dataStore.edit {
                            it[PreferenceKeys.EMERGENCY_PARACHUTES_COUNT] = updated
                            it[PreferenceKeys.LAST_PARACHUTE_REGEN_DATE] = today
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("SessionPrefs", "Failed to update parachute count: ${e.message}")
                    }
                }
            } else {
                syncPrefs.edit().putString("last_parachute_regen_date", today).apply()
            }
        }
    }

    suspend fun startSessionLock(durationMinutes: Int, suitcasePackages: Set<String>) {
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        val editor = syncPrefs.edit()
        editor.putBoolean("session_lock_active", true)
        editor.putLong("session_end_time_ms", endTime)
        editor.putInt("session_total_minutes", durationMinutes)
        editor.putStringSet("session_suitcase_packages", suitcasePackages)
        editor.apply()

        dataStore.edit {
            it[PreferenceKeys.SESSION_LOCK_ACTIVE] = true
            it[PreferenceKeys.SESSION_END_TIME_MS] = endTime
            it[PreferenceKeys.SESSION_TOTAL_MINUTES] = durationMinutes
            it[PreferenceKeys.SESSION_SUITCASE_PACKAGES] = suitcasePackages
        }
    }

    suspend fun stopSessionLock() {
        syncPrefs.edit().putBoolean("session_lock_active", false).apply()
        dataStore.edit { it[PreferenceKeys.SESSION_LOCK_ACTIVE] = false }
    }

    suspend fun consumeEmergencyParachute(): Boolean {
        val current = syncPrefs.getInt("emergency_parachutes_count", 2)
        if (current <= 0) return false
        val updated = current - 1
        syncPrefs.edit()
            .putInt("emergency_parachutes_count", updated)
            .putBoolean("session_lock_active", false)
            .apply()
        dataStore.edit {
            it[PreferenceKeys.EMERGENCY_PARACHUTES_COUNT] = updated
            it[PreferenceKeys.SESSION_LOCK_ACTIVE] = false
        }
        return true
    }

    suspend fun saveSuitcasePackages(packages: Set<String>) {
        syncPrefs.edit().putStringSet("session_suitcase_packages", packages).apply()
        dataStore.edit { it[PreferenceKeys.SESSION_SUITCASE_PACKAGES] = packages }
    }

    suspend fun recordCompletedSession(durationMinutes: Int) {
        val sessions = syncPrefs.getInt("total_focus_sessions_completed", 0) + 1
        val minutes = syncPrefs.getLong("total_focus_minutes_completed", 0L) + durationMinutes
        val parachutes = (syncPrefs.getInt("emergency_parachutes_count", 2) + 1).coerceAtMost(2)
        syncPrefs.edit()
            .putBoolean("session_lock_active", false)
            .putInt("total_focus_sessions_completed", sessions)
            .putLong("total_focus_minutes_completed", minutes)
            .putInt("emergency_parachutes_count", parachutes)
            .apply()
        dataStore.edit {
            it[PreferenceKeys.SESSION_LOCK_ACTIVE] = false
            it[PreferenceKeys.TOTAL_FOCUS_SESSIONS_COMPLETED] = sessions
            it[PreferenceKeys.TOTAL_FOCUS_MINUTES_COMPLETED] = minutes
            it[PreferenceKeys.EMERGENCY_PARACHUTES_COUNT] = parachutes
        }
    }
}

