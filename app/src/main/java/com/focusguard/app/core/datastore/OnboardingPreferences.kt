package com.focusguard.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.focusguard.app.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private const val SUPPORT_USED_CODES_KEY = "support_used_codes"
private const val SUPPORT_LEDGER_RETENTION_DAYS = 5L
private val ISO_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

@Singleton
class OnboardingPreferences @Inject constructor(
    private val core: PreferencesCore
) {
    private val dataStore = core.dataStore
    private val syncPrefs = core.syncPrefs
    private val context = core.context
    private val prefScope = core.prefScope

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.ONBOARDING_COMPLETE] ?: false
    }

    val currentAuthUser: Flow<AuthUser?> = dataStore.data.map { prefs ->
        val isLoggedIn = prefs[PreferenceKeys.AUTH_IS_LOGGED_IN] ?: false
        if (!isLoggedIn) {
            null
        } else {
            val id = prefs[PreferenceKeys.AUTH_USER_ID] ?: ""
            if (id.isBlank()) null
            else {
                AuthUser(
                    id = id,
                    displayName = prefs[PreferenceKeys.AUTH_USER_NAME],
                    email = prefs[PreferenceKeys.AUTH_USER_EMAIL],
                    photoUrl = prefs[PreferenceKeys.AUTH_USER_PHOTO_URL],
                    idToken = prefs[PreferenceKeys.AUTH_ID_TOKEN]
                )
            }
        }
    }

    val oemGuidanceAcknowledged: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.OEM_GUIDANCE_ACKNOWLEDGED] ?: false
    }

    // ── Synchronous 0ms RAM Snapshot Getters ──
    val isOnboardingCompleteSync: Boolean
        get() = syncPrefs.getBoolean("onboarding_complete", false)

    val syncOemGuidanceAcknowledged: Boolean
        get() = syncPrefs.getBoolean("oem_guidance_acknowledged", false)

    val syncIsLoggedIn: Boolean
        get() = syncPrefs.getBoolean("auth_is_logged_in", false)

    val syncAuthUser: AuthUser?
        get() {
            if (!syncIsLoggedIn) return null
            val id = syncPrefs.getString("auth_user_id", null) ?: return null
            val name = syncPrefs.getString("auth_user_name", null)
            val email = syncPrefs.getString("auth_user_email", null)
            val photo = syncPrefs.getString("auth_user_photo_url", null)
            return AuthUser(id = id, displayName = name, email = email, photoUrl = photo)
        }

    val syncBlockedAppsCount: Int
        get() = syncPrefs.getInt("cached_blocked_apps_count", 0)

    val syncBlockedKeywordsCount: Int
        get() = syncPrefs.getInt("cached_blocked_keywords_count", 0)

    val syncActiveTimeLimitsCount: Int
        get() = syncPrefs.getInt("cached_active_time_limits_count", 0)

    val syncActiveSchedulesCount: Int
        get() = syncPrefs.getInt("cached_active_schedules_count", 0)

    suspend fun setOnboardingComplete(complete: Boolean) {
        syncPrefs.edit().putBoolean("onboarding_complete", complete).apply()
        dataStore.edit { it[PreferenceKeys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setOemGuidanceAcknowledged(acknowledged: Boolean) {
        syncPrefs.edit().putBoolean("oem_guidance_acknowledged", acknowledged).apply()
        dataStore.edit { it[PreferenceKeys.OEM_GUIDANCE_ACKNOWLEDGED] = acknowledged }
    }

    suspend fun saveAuthUser(user: AuthUser) {
        syncPrefs.edit()
            .putBoolean("auth_is_logged_in", true)
            .putString("auth_user_id", user.id)
            .putString("auth_user_name", user.displayName ?: "")
            .putString("auth_user_email", user.email ?: "")
            .putString("auth_user_photo_url", user.photoUrl ?: "")
            .apply()

        dataStore.edit { prefs ->
            prefs[PreferenceKeys.AUTH_IS_LOGGED_IN] = true
            prefs[PreferenceKeys.AUTH_USER_ID] = user.id
            user.displayName?.let { prefs[PreferenceKeys.AUTH_USER_NAME] = it } ?: prefs.remove(PreferenceKeys.AUTH_USER_NAME)
            user.email?.let { prefs[PreferenceKeys.AUTH_USER_EMAIL] = it } ?: prefs.remove(PreferenceKeys.AUTH_USER_EMAIL)
            user.photoUrl?.let { prefs[PreferenceKeys.AUTH_USER_PHOTO_URL] = it } ?: prefs.remove(PreferenceKeys.AUTH_USER_PHOTO_URL)
            user.idToken?.let { prefs[PreferenceKeys.AUTH_ID_TOKEN] = it } ?: prefs.remove(PreferenceKeys.AUTH_ID_TOKEN)
        }
    }

    suspend fun clearAuthUser() {
        syncPrefs.edit()
            .putBoolean("auth_is_logged_in", false)
            .remove("auth_user_id")
            .remove("auth_user_name")
            .remove("auth_user_email")
            .remove("auth_user_photo_url")
            .apply()

        dataStore.edit { prefs ->
            prefs[PreferenceKeys.AUTH_IS_LOGGED_IN] = false
            prefs.remove(PreferenceKeys.AUTH_USER_ID)
            prefs.remove(PreferenceKeys.AUTH_USER_NAME)
            prefs.remove(PreferenceKeys.AUTH_USER_EMAIL)
            prefs.remove(PreferenceKeys.AUTH_USER_PHOTO_URL)
            prefs.remove(PreferenceKeys.AUTH_ID_TOKEN)
        }
    }

    fun updateSyncCounts(apps: Int? = null, keywords: Int? = null, limits: Int? = null, schedules: Int? = null) {
        val editor = syncPrefs.edit()
        apps?.let { editor.putInt("cached_blocked_apps_count", it) }
        keywords?.let { editor.putInt("cached_blocked_keywords_count", it) }
        limits?.let { editor.putInt("cached_active_time_limits_count", it) }
        schedules?.let { editor.putInt("cached_active_schedules_count", it) }
        editor.apply()
    }

    fun getTodayDateString(): String = core.getTodayDateString()

    fun getOrCreateDeviceId(): String {
        val existingId = syncPrefs.getString("device_id", null)
        if (!existingId.isNullOrBlank()) {
            return existingId
        }
        val sr = SecureRandom()
        val part1 = sr.nextInt(9000) + 1000
        val part2 = sr.nextInt(9000) + 1000
        val newId = "FG-$part1-$part2"
        syncPrefs.edit().putString("device_id", newId).apply()
        prefScope.launch {
            try {
                dataStore.edit { it[PreferenceKeys.DEVICE_ID] = newId }
            } catch (e: Exception) {
                android.util.Log.d("OnboardingPrefs", "Failed to persist deviceId: ${e.message}")
            }
        }
        return newId
    }

    fun getSupportCooldownRemainingSeconds(): Long {
        val until = syncPrefs.getLong("support_cooldown_until_ms", 0L)
        val now = System.currentTimeMillis()
        return if (until > now) (until - now + 999L) / 1000L else 0L
    }

    fun recordSupportCodeWrongAttempt(): Long {
        val attempts = syncPrefs.getInt("support_wrong_attempts", 0) + 1
        var cooldownUntil = syncPrefs.getLong("support_cooldown_until_ms", 0L)
        if (attempts >= 5) {
            cooldownUntil = System.currentTimeMillis() + 60_000L
        }
        syncPrefs.edit()
            .putInt("support_wrong_attempts", attempts)
            .putLong("support_cooldown_until_ms", cooldownUntil)
            .apply()
        prefScope.launch {
            try {
                dataStore.edit {
                    it[PreferenceKeys.SUPPORT_WRONG_ATTEMPTS] = attempts
                    it[PreferenceKeys.SUPPORT_COOLDOWN_UNTIL_MS] = cooldownUntil
                }
            } catch (e: Exception) {
                android.util.Log.d("OnboardingPrefs", "Failed to update wrong attempts: ${e.message}")
            }
        }
        val now = System.currentTimeMillis()
        return if (cooldownUntil > now) (cooldownUntil - now + 999L) / 1000L else 0L
    }

    fun resetSupportCodeWrongAttempts() {
        syncPrefs.edit()
            .putInt("support_wrong_attempts", 0)
            .putLong("support_cooldown_until_ms", 0L)
            .apply()
        prefScope.launch {
            try {
                dataStore.edit {
                    it[PreferenceKeys.SUPPORT_WRONG_ATTEMPTS] = 0
                    it[PreferenceKeys.SUPPORT_COOLDOWN_UNTIL_MS] = 0L
                }
            } catch (e: Exception) {
                android.util.Log.d("OnboardingPrefs", "Failed to reset wrong attempts: ${e.message}")
            }
        }
    }

    private fun supportLedgerPrefs() =
        context.getSharedPreferences("focusguard_support_ledger", Context.MODE_PRIVATE)

    fun supportCodeLedger(): Set<String> {
        val raw = supportLedgerPrefs().getStringSet(SUPPORT_USED_CODES_KEY, emptySet()) ?: emptySet()
        val pruned = pruneSupportLedger(raw)
        if (pruned.size != raw.size) {
            supportLedgerPrefs().edit().putStringSet(SUPPORT_USED_CODES_KEY, pruned).apply()
        }
        return pruned
    }

    fun isSupportCodeConsumed(matchedDate: String, codeFingerprint: String): Boolean {
        val entry = ledgerEntryFor(matchedDate, codeFingerprint) ?: return false
        return supportCodeLedger().contains(entry)
    }

    fun markSupportCodeConsumed(matchedDate: String, codeFingerprint: String) {
        val entry = ledgerEntryFor(matchedDate, codeFingerprint) ?: return
        val updated = supportCodeLedger().toMutableSet().apply { add(entry) }
        supportLedgerPrefs().edit().putStringSet(SUPPORT_USED_CODES_KEY, updated).apply()
    }

    fun clearSupportCodeLedger() {
        supportLedgerPrefs().edit().remove(SUPPORT_USED_CODES_KEY).apply()
    }

    private fun ledgerEntryFor(matchedDate: String, codeFingerprint: String): String? {
        val normDate = normalizeLedgerDate(matchedDate) ?: return null
        val fp = codeFingerprint.trim().lowercase()
        if (fp.length < 8) return null
        return "$normDate|$fp"
    }

    private fun normalizeLedgerDate(matchedDate: String): String? {
        val trimmed = matchedDate.trim()
        if (ISO_DATE_REGEX.matches(trimmed)) return trimmed
        val formats = listOf("yyyyMMdd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy/MM/dd")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed: Date? = sdf.parse(trimmed)
                if (parsed != null) {
                    val outSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    return outSdf.format(parsed)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun currentLedgerDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date())
    }

    private fun pruneSupportLedger(entries: Set<String>): Set<String> {
        val todayStr = currentLedgerDate()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val today = try { sdf.parse(todayStr) } catch (_: Exception) { null } ?: return entries
        val cutoffMs = today.time - (SUPPORT_LEDGER_RETENTION_DAYS * 86_400_000L)

        return entries.filter { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size != 2) return@filter false
            val entryDate = try { sdf.parse(parts[0]) } catch (_: Exception) { null } ?: return@filter false
            entryDate.time >= cutoffMs
        }.toSet()
    }
}
