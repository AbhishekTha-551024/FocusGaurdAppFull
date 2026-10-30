package com.focusguard.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

val Context.focusGuardDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "focusguard_prefs")

/** SharedPreferences key holding the consumed Emergency Support Passcode fingerprints. */
private const val SUPPORT_USED_CODES_KEY = "support_used_codes"

/** Consumed codes are only memorable inside the engine's grace window; keep a little slack. */
private const val SUPPORT_LEDGER_RETENTION_DAYS = 5L

private val ISO_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

const val DEFAULT_COMMITMENT_SENTENCE = "[Focus-Shield::v4.2]--Do I truly need to unlock this restricted app right now, or am I merely succumbing to an impulsive 'dopamine twitch'? I acknowledge that deep work demands total concentration; yet here I am, deliberately bypassing my self-imposed rules [status: \"interrupted\"; friction: high]. Every override reinforces weakness--eroding discipline, focus, and mental clarity; whereas enduring boredom builds resilience {habit-strength: +1}. Why trade my long-term goals for momentary amusement? I consciously accept full personal responsibility for this lapse (cost-factor: elevated @ 100% & risk = high); no excuses, no shortcuts! Stop, breathe, and reflect: is this distraction worth breaking my promise to myself? Confirm override [auth: #FG-902/unlock; verified: true]."

@Singleton
class FocusGuardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.focusGuardDataStore
    private val syncPrefs = context.getSharedPreferences("focusguard_sync_cache", Context.MODE_PRIVATE)

    private val prefExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("FocusGuardPreferences", "Pref error safely intercepted: ${throwable.message}", throwable)
    }
    private val prefScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO + prefExceptionHandler)

    init {
        // Eagerly hydrate syncPrefs from DataStore to ensure 0ms reads on Frame 1
        prefScope.launch {
            try {
                dataStore.data.collect { prefs ->
                    val editor = syncPrefs.edit()
                    prefs[Keys.CHALLENGE_TYPE]?.let { editor.putString("challenge_type", it) }
                    prefs[Keys.COMMITMENT_SENTENCE]?.let {
                        val valid = if (it.trim().length >= 500) it.trim() else DEFAULT_COMMITMENT_SENTENCE
                        editor.putString("commitment_sentence", valid)
                    }
                    prefs[Keys.TIME_DELAY_HOURS]?.let { editor.putInt("time_delay_hours", it) }
                    prefs[Keys.IS_CHALLENGE_ACTIVE]?.let { editor.putBoolean("is_challenge_active", it) }
                    prefs[Keys.UNINSTALL_PROTECTION_ENABLED]?.let { editor.putBoolean("uninstall_protection_enabled", it) }
                    prefs[Keys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP]?.let { editor.putLong("uninstall_protection_expiry_timestamp", it) }
                    prefs[Keys.UNINSTALL_PROTECTION_DAYS]?.let { editor.putInt("uninstall_protection_days", it) }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        try {
                            val deContext = context.createDeviceProtectedStorageContext()
                            val deEditor = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE).edit()
                            prefs[Keys.UNINSTALL_PROTECTION_ENABLED]?.let { deEditor.putBoolean("uninstall_protection_enabled", it) }
                            prefs[Keys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP]?.let { deEditor.putLong("uninstall_protection_expiry_timestamp", it) }
                            prefs[Keys.UNINSTALL_PROTECTION_DAYS]?.let { deEditor.putInt("uninstall_protection_days", it) }
                            deEditor.apply()
                        } catch (e: Exception) {
                            android.util.Log.d("FocusGuardPrefs", "Failed to update DE cache: ${e.message}")
                        }
                    }
                    prefs[Keys.SHORTS_REELS_BLOCK_ENABLED]?.let { editor.putBoolean("shorts_reels_block_enabled", it) }
                    prefs[Keys.INSTAGRAM_REELS_BLOCK_ENABLED]?.let { editor.putBoolean("instagram_reels_block_enabled", it) }
                    prefs[Keys.FACEBOOK_REELS_BLOCK_ENABLED]?.let { editor.putBoolean("facebook_reels_block_enabled", it) }
                    prefs[Keys.YOUTUBE_SHORTS_BLOCK_ENABLED]?.let { editor.putBoolean("youtube_shorts_block_enabled", it) }
                    prefs[Keys.AUTO_BLOCK_NEW_APPS]?.let { editor.putBoolean("auto_block_new_apps", it) }
                    prefs[Keys.CUSTOM_BLOCK_MESSAGE]?.let { editor.putString("custom_block_message", it) }
                    prefs[Keys.BLOCK_COUNTDOWN_SECONDS]?.let { editor.putInt("block_countdown_seconds", it) }
                    prefs[Keys.OVERLAY_WALLPAPER_PATH]?.let { editor.putString("overlay_wallpaper_path", it) }
                    prefs[Keys.OVERLAY_PRESET_ID]?.let { editor.putInt("overlay_preset_id", it) }
                    prefs[Keys.OVERLAY_DIM_ALPHA]?.let { editor.putFloat("overlay_dim_alpha", it) }
                    prefs[Keys.REVIEWED_PACKAGES]?.let { editor.putStringSet("reviewed_packages", it) }
                    prefs[Keys.INCOGNITO_BLOCK_ENABLED]?.let { editor.putBoolean("incognito_block_enabled", it) }
                    prefs[Keys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED]?.let { editor.putBoolean("block_unsupported_browsers_enabled", it) }
                    prefs[Keys.ADULT_CONTENT_BLOCK_ENABLED]?.let { editor.putBoolean("adult_content_block_enabled", it) }
                    prefs[Keys.ANTI_CLONING_SHIELD_ENABLED]?.let { editor.putBoolean("anti_cloning_shield_enabled", it) }
                    prefs[Keys.AUTH_USER_ID]?.let { editor.putString("auth_user_id", it) }
                    prefs[Keys.AUTH_USER_NAME]?.let { editor.putString("auth_user_name", it) }
                    prefs[Keys.AUTH_USER_EMAIL]?.let { editor.putString("auth_user_email", it) }
                    prefs[Keys.AUTH_USER_PHOTO_URL]?.let { editor.putString("auth_user_photo_url", it) }
                    prefs[Keys.AUTH_IS_LOGGED_IN]?.let { editor.putBoolean("auth_is_logged_in", it) }
                    prefs[Keys.OEM_GUIDANCE_ACKNOWLEDGED]?.let { editor.putBoolean("oem_guidance_acknowledged", it) }
                    prefs[Keys.SESSION_LOCK_ACTIVE]?.let { editor.putBoolean("session_lock_active", it) }
                    prefs[Keys.SESSION_END_TIME_MS]?.let { editor.putLong("session_end_time_ms", it) }
                    prefs[Keys.SESSION_TOTAL_MINUTES]?.let { editor.putInt("session_total_minutes", it) }
                    prefs[Keys.SESSION_SUITCASE_PACKAGES]?.let { editor.putStringSet("session_suitcase_packages", it) }
                    prefs[Keys.EMERGENCY_PARACHUTES_COUNT]?.let { editor.putInt("emergency_parachutes_count", it) }
                    prefs[Keys.LAST_PARACHUTE_REGEN_DATE]?.let { editor.putString("last_parachute_regen_date", it) }
                    prefs[Keys.TOTAL_FOCUS_SESSIONS_COMPLETED]?.let { editor.putInt("total_focus_sessions_completed", it) }
                    prefs[Keys.TOTAL_FOCUS_MINUTES_COMPLETED]?.let { editor.putLong("total_focus_minutes_completed", it) }
                    editor.apply()
                }
            } catch (e: Exception) {
                // Log and ignore
            }
        }
    }

    // ── Synchronous 0ms RAM Snapshot Getters (Prevents Compose Toggle Flickering) ──
    val syncShortsReelsBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("shorts_reels_block_enabled", false)

    val syncInstagramReelsBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("instagram_reels_block_enabled", false)

    val syncFacebookReelsBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("facebook_reels_block_enabled", false)

    val syncYouTubeShortsBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("youtube_shorts_block_enabled", false)

    val syncAutoBlockNewApps: Boolean
        get() = syncPrefs.getBoolean("auto_block_new_apps", false)

    val syncIncognitoBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("incognito_block_enabled", false)

    val syncBlockUnsupportedBrowsersEnabled: Boolean
        get() = syncPrefs.getBoolean("block_unsupported_browsers_enabled", false)

    val syncAdultContentBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("adult_content_block_enabled", false)

    val syncAntiCloningShieldEnabled: Boolean
        get() = true

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

    val syncReviewedPackages: Set<String>
        get() = syncPrefs.getStringSet("reviewed_packages", emptySet()) ?: emptySet()

    val syncOemGuidanceAcknowledged: Boolean
        get() = syncPrefs.getBoolean("oem_guidance_acknowledged", false)

    val isOnboardingCompleteSync: Boolean
        get() = syncPrefs.getBoolean("onboarding_complete", false)

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

    // ── Deep Focus Lockdown (#blockit Engine) Sync Getters ──
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

    val syncCustomBlockMessage: String
        get() = syncPrefs.getString("custom_block_message", "") ?: ""

    val syncBlockCountdownSeconds: Int
        get() = syncPrefs.getInt("block_countdown_seconds", 5)

    val syncOverlayPresetId: Int
        get() = syncPrefs.getInt("overlay_preset_id", 0)

    val syncOverlayDimAlpha: Float
        get() = syncPrefs.getFloat("overlay_dim_alpha", 0.55f)

    val syncOverlayWallpaperPath: String
        get() = syncPrefs.getString("overlay_wallpaper_path", "") ?: ""

    // Synchronous cached counters for Home screen to avoid 0 -> N flicker on launch
    val syncBlockedAppsCount: Int
        get() = syncPrefs.getInt("cached_blocked_apps_count", 0)

    val syncBlockedKeywordsCount: Int
        get() = syncPrefs.getInt("cached_blocked_keywords_count", 0)

    val syncActiveTimeLimitsCount: Int
        get() = syncPrefs.getInt("cached_active_time_limits_count", 0)

    val syncActiveSchedulesCount: Int
        get() = syncPrefs.getInt("cached_active_schedules_count", 0)

    fun updateSyncCounts(apps: Int? = null, keywords: Int? = null, limits: Int? = null, schedules: Int? = null) {
        val editor = syncPrefs.edit()
        apps?.let { editor.putInt("cached_blocked_apps_count", it) }
        keywords?.let { editor.putInt("cached_blocked_keywords_count", it) }
        limits?.let { editor.putInt("cached_active_time_limits_count", it) }
        schedules?.let { editor.putInt("cached_active_schedules_count", it) }
        editor.apply()
    }

    fun getTodayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val CHALLENGE_TYPE = stringPreferencesKey("challenge_type")
        val COMMITMENT_SENTENCE = stringPreferencesKey("commitment_sentence")
        val TIME_DELAY_HOURS = intPreferencesKey("time_delay_hours")
        val IS_CHALLENGE_ACTIVE = booleanPreferencesKey("is_challenge_active")

        val OVERLAY_WALLPAPER_PATH = stringPreferencesKey("overlay_wallpaper_path")
        val OVERLAY_PRESET_ID = intPreferencesKey("overlay_preset_id")
        val OVERLAY_DIM_ALPHA = floatPreferencesKey("overlay_dim_alpha")

        val UNINSTALL_PROTECTION_ENABLED = booleanPreferencesKey("uninstall_protection_enabled")
        val UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP = longPreferencesKey("uninstall_protection_expiry_timestamp")
        val UNINSTALL_PROTECTION_DAYS = intPreferencesKey("uninstall_protection_days")

        val SHORTS_REELS_BLOCK_ENABLED = booleanPreferencesKey("shorts_reels_block_enabled")
        val INSTAGRAM_REELS_BLOCK_ENABLED = booleanPreferencesKey("instagram_reels_block_enabled")
        val FACEBOOK_REELS_BLOCK_ENABLED = booleanPreferencesKey("facebook_reels_block_enabled")
        val YOUTUBE_SHORTS_BLOCK_ENABLED = booleanPreferencesKey("youtube_shorts_block_enabled")

        val AUTO_BLOCK_NEW_APPS = booleanPreferencesKey("auto_block_new_apps")
        val AUTO_BLOCK_ENABLED_TIMESTAMP = longPreferencesKey("auto_block_enabled_timestamp")
        val REVIEWED_PACKAGES = stringSetPreferencesKey("reviewed_packages")

        val INCOGNITO_BLOCK_ENABLED = booleanPreferencesKey("incognito_block_enabled")
        val BLOCK_UNSUPPORTED_BROWSERS_ENABLED = booleanPreferencesKey("block_unsupported_browsers_enabled")
        val ADULT_CONTENT_BLOCK_ENABLED = booleanPreferencesKey("adult_content_block_enabled")
        val ANTI_CLONING_SHIELD_ENABLED = booleanPreferencesKey("anti_cloning_shield_enabled")

        val CUSTOM_BLOCK_MESSAGE = stringPreferencesKey("custom_block_message")
        val BLOCK_COUNTDOWN_SECONDS = intPreferencesKey("block_countdown_seconds")

        val DEVICE_ID = stringPreferencesKey("device_id")
        val SUPPORT_WRONG_ATTEMPTS = intPreferencesKey("support_wrong_attempts")
        val SUPPORT_COOLDOWN_UNTIL_MS = longPreferencesKey("support_cooldown_until_ms")

        val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        val AUTH_USER_NAME = stringPreferencesKey("auth_user_name")
        val AUTH_USER_EMAIL = stringPreferencesKey("auth_user_email")
        val AUTH_USER_PHOTO_URL = stringPreferencesKey("auth_user_photo_url")
        val AUTH_ID_TOKEN = stringPreferencesKey("auth_id_token")
        val AUTH_IS_LOGGED_IN = booleanPreferencesKey("auth_is_logged_in")
        val OEM_GUIDANCE_ACKNOWLEDGED = booleanPreferencesKey("oem_guidance_acknowledged")

        val SESSION_LOCK_ACTIVE = booleanPreferencesKey("session_lock_active")
        val SESSION_END_TIME_MS = longPreferencesKey("session_end_time_ms")
        val SESSION_TOTAL_MINUTES = intPreferencesKey("session_total_minutes")
        val SESSION_SUITCASE_PACKAGES = stringSetPreferencesKey("session_suitcase_packages")
        val EMERGENCY_PARACHUTES_COUNT = intPreferencesKey("emergency_parachutes_count")
        val LAST_PARACHUTE_REGEN_DATE = stringPreferencesKey("last_parachute_regen_date")
        val TOTAL_FOCUS_SESSIONS_COMPLETED = intPreferencesKey("total_focus_sessions_completed")
        val TOTAL_FOCUS_MINUTES_COMPLETED = longPreferencesKey("total_focus_minutes_completed")
    }

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
                dataStore.edit { it[Keys.DEVICE_ID] = newId }
            } catch (e: Exception) {
                android.util.Log.w("FocusGuardPrefs", "Failed to persist device id in datastore", e)
            }
        }
        return newId
    }

    fun getSupportCooldownRemainingSeconds(): Long {
        val until = syncPrefs.getLong("support_cooldown_until_ms", 0L)
        val now = System.currentTimeMillis()
        return if (until > now) (until - now) / 1000 else 0L
    }

    fun recordSupportCodeWrongAttempt(): Long {
        val attempts = syncPrefs.getInt("support_wrong_attempts", 0) + 1
        val editor = syncPrefs.edit()
        editor.putInt("support_wrong_attempts", attempts)
        val cooldownEnd = if (attempts >= 5) {
            val end = System.currentTimeMillis() + (15 * 60 * 1000L)
            editor.putLong("support_cooldown_until_ms", end)
            end
        } else {
            0L
        }
        editor.apply()
        return cooldownEnd
    }

    fun resetSupportCodeWrongAttempts() {
        syncPrefs.edit()
            .putInt("support_wrong_attempts", 0)
            .putLong("support_cooldown_until_ms", 0L)
            .apply()
    }

    // ── Emergency Support Passcode single-use ledger ────────────────────────────
    // A support code is a stateless HMAC over (deviceId, date, action, target), so the engine
    // alone cannot tell a fresh code from one that was already applied. This local ledger stores
    // only the one-way fingerprint prefixed with the date it matched ("yyyy-MM-dd|<fingerprint>"),
    // never the code itself, so a leaked preferences file cannot be replayed.
    private fun supportCodeLedger(): Set<String> =
        syncPrefs.getStringSet(SUPPORT_USED_CODES_KEY, emptySet()) ?: emptySet()

    fun isSupportCodeConsumed(matchedDate: String, codeFingerprint: String): Boolean =
        supportCodeLedger().contains(ledgerEntryFor(matchedDate, codeFingerprint))

    fun markSupportCodeConsumed(matchedDate: String, codeFingerprint: String) {
        val pruned = pruneSupportLedger(supportCodeLedger() + ledgerEntryFor(matchedDate, codeFingerprint))
        syncPrefs.edit().putStringSet(SUPPORT_USED_CODES_KEY, pruned).apply()
    }

    fun clearSupportCodeLedger() {
        syncPrefs.edit().remove(SUPPORT_USED_CODES_KEY).apply()
    }

    private fun ledgerEntryFor(matchedDate: String, codeFingerprint: String): String =
        "${normalizeLedgerDate(matchedDate)}|$codeFingerprint"

    private fun normalizeLedgerDate(matchedDate: String): String =
        if (matchedDate.matches(ISO_DATE_REGEX)) matchedDate else currentLedgerDate()

    private fun currentLedgerDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** ISO-8601 dates sort lexicographically, so a plain string compare is a safe time-window prune. */
    private fun pruneSupportLedger(entries: Set<String>): Set<String> {
        val cutoff = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(SUPPORT_LEDGER_RETENTION_DAYS)))
        return entries.filter { it.take(10) >= cutoff }.toSet()
    }

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.ONBOARDING_COMPLETE] ?: false
        syncPrefs.edit().putBoolean("onboarding_complete", v).apply()
        v
    }

    val challengeType: Flow<ChallengeType> = dataStore.data.map {
        val name = it[Keys.CHALLENGE_TYPE] ?: ChallengeType.TYPING.name
        syncPrefs.edit().putString("challenge_type", name).apply()
        try {
            ChallengeType.valueOf(name)
        } catch (e: Exception) {
            ChallengeType.TYPING
        }
    }

    val commitmentSentence: Flow<String> = dataStore.data.map {
        val s = it[Keys.COMMITMENT_SENTENCE] ?: DEFAULT_COMMITMENT_SENTENCE
        val valid = if (s.trim().length >= 500) s.trim() else DEFAULT_COMMITMENT_SENTENCE
        syncPrefs.edit().putString("commitment_sentence", valid).apply()
        valid
    }

    val timeDelayHours: Flow<Int> = dataStore.data.map {
        val h = it[Keys.TIME_DELAY_HOURS] ?: 24
        syncPrefs.edit().putInt("time_delay_hours", h).apply()
        h
    }

    val isChallengeActive: Flow<Boolean> = dataStore.data.map {
        val a = it[Keys.IS_CHALLENGE_ACTIVE] ?: false
        syncPrefs.edit().putBoolean("is_challenge_active", a).apply()
        a
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        syncPrefs.edit().putBoolean("onboarding_complete", complete).apply()
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setChallengeType(type: ChallengeType) {
        syncPrefs.edit().putString("challenge_type", type.name).apply()
        dataStore.edit { it[Keys.CHALLENGE_TYPE] = type.name }
    }

    suspend fun setCommitmentSentence(sentence: String) {
        val clean = if (sentence.trim().length >= 500) sentence.trim() else DEFAULT_COMMITMENT_SENTENCE
        syncPrefs.edit().putString("commitment_sentence", clean).apply()
        dataStore.edit { it[Keys.COMMITMENT_SENTENCE] = clean }
    }

    suspend fun setTimeDelayHours(hours: Int) {
        val h = hours.coerceAtLeast(1)
        syncPrefs.edit().putInt("time_delay_hours", h).apply()
        dataStore.edit { it[Keys.TIME_DELAY_HOURS] = h }
    }

    suspend fun setChallengeActive(active: Boolean) {
        syncPrefs.edit().putBoolean("is_challenge_active", active).apply()
        dataStore.edit { it[Keys.IS_CHALLENGE_ACTIVE] = active }
    }

    val overlayWallpaperPath: Flow<String> = dataStore.data.map {
        it[Keys.OVERLAY_WALLPAPER_PATH] ?: ""
    }

    val overlayPresetId: Flow<Int> = dataStore.data.map {
        it[Keys.OVERLAY_PRESET_ID] ?: 0
    }

    val overlayDimAlpha: Flow<Float> = dataStore.data.map {
        it[Keys.OVERLAY_DIM_ALPHA] ?: 0.55f
    }

    suspend fun setOverlayWallpaperPath(path: String) {
        syncPrefs.edit().putString("overlay_wallpaper_path", path).apply()
        dataStore.edit { it[Keys.OVERLAY_WALLPAPER_PATH] = path }
    }

    suspend fun setOverlayPresetId(id: Int) {
        syncPrefs.edit().putInt("overlay_preset_id", id.coerceIn(0, 4)).apply()
        dataStore.edit { it[Keys.OVERLAY_PRESET_ID] = id.coerceIn(0, 4) }
    }

    suspend fun setOverlayDimAlpha(alpha: Float) {
        syncPrefs.edit().putFloat("overlay_dim_alpha", alpha.coerceIn(0.2f, 0.9f)).apply()
        dataStore.edit { it[Keys.OVERLAY_DIM_ALPHA] = alpha.coerceIn(0.2f, 0.9f) }
    }

    suspend fun clearOverlayWallpaper() {
        syncPrefs.edit()
            .putString("overlay_wallpaper_path", "")
            .putInt("overlay_preset_id", 0)
            .apply()
        dataStore.edit {
            it[Keys.OVERLAY_WALLPAPER_PATH] = ""
            it[Keys.OVERLAY_PRESET_ID] = 0
        }
    }

    val isUninstallProtectionEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.UNINSTALL_PROTECTION_ENABLED] ?: false
        syncPrefs.edit().putBoolean("uninstall_protection_enabled", v).apply()
        v
    }

    val uninstallProtectionExpiryTimestamp: Flow<Long> = dataStore.data.map {
        val v = it[Keys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] ?: 0L
        syncPrefs.edit().putLong("uninstall_protection_expiry_timestamp", v).apply()
        v
    }

    val uninstallProtectionDays: Flow<Int> = dataStore.data.map {
        val v = it[Keys.UNINSTALL_PROTECTION_DAYS] ?: 0
        syncPrefs.edit().putInt("uninstall_protection_days", v).apply()
        v
    }

    suspend fun startTimedUninstallProtection(days: Int) {
        require(days in 1..30) { "Uninstall protection days must be between 1 and 30, got $days" }
        val durationMs = TimeUnit.DAYS.toMillis(days.toLong().coerceAtLeast(1L))
        val expiry = System.currentTimeMillis() + durationMs
        syncPrefs.edit()
            .putBoolean("uninstall_protection_enabled", true)
            .putLong("uninstall_protection_expiry_timestamp", expiry)
            .putInt("uninstall_protection_days", days)
            .apply()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val deContext = context.createDeviceProtectedStorageContext()
                deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("uninstall_protection_enabled", true)
                    .putLong("uninstall_protection_expiry_timestamp", expiry)
                    .putInt("uninstall_protection_days", days)
                    .apply()
            } catch (e: Exception) {
                android.util.Log.d("FocusGuardPrefs", "Failed to write DE cache: ${e.message}")
            }
        }
        dataStore.edit {
            it[Keys.UNINSTALL_PROTECTION_ENABLED] = true
            it[Keys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = expiry
            it[Keys.UNINSTALL_PROTECTION_DAYS] = days
        }
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        if (enabled) {
            android.util.Log.e("FocusGuardPrefs", "setUninstallProtectionEnabled(true) is rejected: use startTimedUninstallProtection(days)")
            return
        }
        syncPrefs.edit().apply {
            putBoolean("uninstall_protection_enabled", false)
            putLong("uninstall_protection_expiry_timestamp", 0L)
            putInt("uninstall_protection_days", 0)
            apply()
        }
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
                android.util.Log.d("FocusGuardPrefs", "Failed to clear DE cache: ${e.message}")
            }
        }
        com.focusguard.app.worker.AccessibilityHealthWorker.cancelReactivationNotification(context)
        dataStore.edit {
            it[Keys.UNINSTALL_PROTECTION_ENABLED] = false
            it[Keys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] = 0L
            it[Keys.UNINSTALL_PROTECTION_DAYS] = 0
        }
    }

    suspend fun disableUninstallProtection() {
        setUninstallProtectionEnabled(false)
    }

    val isShortsReelsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.SHORTS_REELS_BLOCK_ENABLED] ?: false
        syncPrefs.edit().putBoolean("shorts_reels_block_enabled", v).apply()
        v
    }

    suspend fun setShortsReelsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("shorts_reels_block_enabled", enabled).apply()
        dataStore.edit { it[Keys.SHORTS_REELS_BLOCK_ENABLED] = enabled }
    }

    val isInstagramReelsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.INSTAGRAM_REELS_BLOCK_ENABLED] ?: false
        syncPrefs.edit().putBoolean("instagram_reels_block_enabled", v).apply()
        v
    }

    suspend fun setInstagramReelsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("instagram_reels_block_enabled", enabled).apply()
        dataStore.edit { it[Keys.INSTAGRAM_REELS_BLOCK_ENABLED] = enabled }
    }

    val isFacebookReelsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.FACEBOOK_REELS_BLOCK_ENABLED] ?: false
        syncPrefs.edit().putBoolean("facebook_reels_block_enabled", v).apply()
        v
    }

    suspend fun setFacebookReelsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("facebook_reels_block_enabled", enabled).apply()
        dataStore.edit { it[Keys.FACEBOOK_REELS_BLOCK_ENABLED] = enabled }
    }

    val isYouTubeShortsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.YOUTUBE_SHORTS_BLOCK_ENABLED] ?: false
        syncPrefs.edit().putBoolean("youtube_shorts_block_enabled", v).apply()
        v
    }

    suspend fun setYouTubeShortsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("youtube_shorts_block_enabled", enabled).apply()
        dataStore.edit { it[Keys.YOUTUBE_SHORTS_BLOCK_ENABLED] = enabled }
    }

    val isAutoBlockNewAppsEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.AUTO_BLOCK_NEW_APPS] ?: false
        syncPrefs.edit().putBoolean("auto_block_new_apps", v).apply()
        v
    }

    val autoBlockEnabledTimestamp: Flow<Long> = dataStore.data.map {
        it[Keys.AUTO_BLOCK_ENABLED_TIMESTAMP] ?: 0L
    }

    suspend fun setAutoBlockNewApps(enabled: Boolean) {
        syncPrefs.edit().putBoolean("auto_block_new_apps", enabled).apply()
        dataStore.edit {
            it[Keys.AUTO_BLOCK_NEW_APPS] = enabled
            if (enabled) {
                val current = it[Keys.AUTO_BLOCK_ENABLED_TIMESTAMP] ?: 0L
                if (current == 0L) {
                    it[Keys.AUTO_BLOCK_ENABLED_TIMESTAMP] = System.currentTimeMillis() - 7200_000L
                }
            } else {
                it[Keys.AUTO_BLOCK_ENABLED_TIMESTAMP] = 0L
            }
        }
    }

    val reviewedPackages: Flow<Set<String>> = dataStore.data.map {
        val set = it[Keys.REVIEWED_PACKAGES] ?: emptySet()
        syncPrefs.edit().putStringSet("reviewed_packages", set).apply()
        set
    }

    suspend fun markAppReviewed(packageName: String) {
        val clean = packageName.lowercase()
        val currentSync = syncPrefs.getStringSet("reviewed_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSync.add(clean)
        syncPrefs.edit().putStringSet("reviewed_packages", currentSync).apply()

        dataStore.edit { prefs ->
            val current = prefs[Keys.REVIEWED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.add(clean)
            prefs[Keys.REVIEWED_PACKAGES] = current
        }
    }

    suspend fun clearAppReviewed(packageName: String) {
        val clean = packageName.lowercase()
        val currentSync = syncPrefs.getStringSet("reviewed_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSync.remove(clean)
        syncPrefs.edit().putStringSet("reviewed_packages", currentSync).apply()

        dataStore.edit { prefs ->
            val current = prefs[Keys.REVIEWED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.remove(clean)
            prefs[Keys.REVIEWED_PACKAGES] = current
        }
    }

    val customBlockMessage: Flow<String> = dataStore.data.map {
        val v = it[Keys.CUSTOM_BLOCK_MESSAGE] ?: ""
        syncPrefs.edit().putString("custom_block_message", v).apply()
        v
    }

    suspend fun setCustomBlockMessage(message: String) {
        syncPrefs.edit().putString("custom_block_message", message).apply()
        dataStore.edit { it[Keys.CUSTOM_BLOCK_MESSAGE] = message }
    }

    val blockCountdownSeconds: Flow<Int> = dataStore.data.map {
        val v = it[Keys.BLOCK_COUNTDOWN_SECONDS] ?: 5
        syncPrefs.edit().putInt("block_countdown_seconds", v).apply()
        v
    }

    suspend fun setBlockCountdownSeconds(seconds: Int) {
        syncPrefs.edit().putInt("block_countdown_seconds", seconds).apply()
        dataStore.edit { it[Keys.BLOCK_COUNTDOWN_SECONDS] = seconds }
    }

    val isIncognitoBlockEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.INCOGNITO_BLOCK_ENABLED] ?: false
        syncPrefs.edit().putBoolean("incognito_block_enabled", v).apply()
        v
    }

    suspend fun setIncognitoBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("incognito_block_enabled", enabled).apply()
        dataStore.edit { it[Keys.INCOGNITO_BLOCK_ENABLED] = enabled }
    }

    val isBlockUnsupportedBrowsersEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED] ?: false
        syncPrefs.edit().putBoolean("block_unsupported_browsers_enabled", v).apply()
        v
    }

    suspend fun setBlockUnsupportedBrowsersEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("block_unsupported_browsers_enabled", enabled).apply()
        dataStore.edit { it[Keys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED] = enabled }
    }

    val isAdultContentBlockEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.ADULT_CONTENT_BLOCK_ENABLED] ?: false
        syncPrefs.edit().putBoolean("adult_content_block_enabled", v).apply()
        v
    }

    suspend fun setAdultContentBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("adult_content_block_enabled", enabled).apply()
        dataStore.edit { it[Keys.ADULT_CONTENT_BLOCK_ENABLED] = enabled }
    }

    val isAntiCloningShieldEnabled: Flow<Boolean> = dataStore.data.map {
        val v = it[Keys.ANTI_CLONING_SHIELD_ENABLED] ?: true
        syncPrefs.edit().putBoolean("anti_cloning_shield_enabled", v).apply()
        v
    }

    suspend fun setAntiCloningShieldEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("anti_cloning_shield_enabled", enabled).apply()
        dataStore.edit { it[Keys.ANTI_CLONING_SHIELD_ENABLED] = enabled }
    }

    val currentAuthUser: Flow<AuthUser?> = dataStore.data.map { prefs ->
        val isLoggedIn = prefs[Keys.AUTH_IS_LOGGED_IN] ?: false
        if (!isLoggedIn) {
            null
        } else {
            val id = prefs[Keys.AUTH_USER_ID] ?: ""
            if (id.isBlank()) null
            else {
                AuthUser(
                    id = id,
                    displayName = prefs[Keys.AUTH_USER_NAME],
                    email = prefs[Keys.AUTH_USER_EMAIL],
                    photoUrl = prefs[Keys.AUTH_USER_PHOTO_URL],
                    idToken = prefs[Keys.AUTH_ID_TOKEN]
                )
            }
        }
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
            prefs[Keys.AUTH_IS_LOGGED_IN] = true
            prefs[Keys.AUTH_USER_ID] = user.id
            user.displayName?.let { prefs[Keys.AUTH_USER_NAME] = it } ?: prefs.remove(Keys.AUTH_USER_NAME)
            user.email?.let { prefs[Keys.AUTH_USER_EMAIL] = it } ?: prefs.remove(Keys.AUTH_USER_EMAIL)
            user.photoUrl?.let { prefs[Keys.AUTH_USER_PHOTO_URL] = it } ?: prefs.remove(Keys.AUTH_USER_PHOTO_URL)
            user.idToken?.let { prefs[Keys.AUTH_ID_TOKEN] = it } ?: prefs.remove(Keys.AUTH_ID_TOKEN)
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
            prefs[Keys.AUTH_IS_LOGGED_IN] = false
            prefs.remove(Keys.AUTH_USER_ID)
            prefs.remove(Keys.AUTH_USER_NAME)
            prefs.remove(Keys.AUTH_USER_EMAIL)
            prefs.remove(Keys.AUTH_USER_PHOTO_URL)
            prefs.remove(Keys.AUTH_ID_TOKEN)
        }
    }

    val oemGuidanceAcknowledged: Flow<Boolean> = dataStore.data.map {
        it[Keys.OEM_GUIDANCE_ACKNOWLEDGED] ?: false
    }

    suspend fun setOemGuidanceAcknowledged(acknowledged: Boolean) {
        syncPrefs.edit().putBoolean("oem_guidance_acknowledged", acknowledged).apply()
        dataStore.edit { it[Keys.OEM_GUIDANCE_ACKNOWLEDGED] = acknowledged }
    }

    data class SettingsSnapshot(
        val challengeType: ChallengeType = ChallengeType.TYPING,
        val commitmentSentence: String = DEFAULT_COMMITMENT_SENTENCE,
        val timeDelayHours: Int = 24,
        val isChallengeActive: Boolean = false,
        val isUninstallProtectionEnabled: Boolean = false,
        val uninstallProtectionExpiryTimestamp: Long = 0L,
        val uninstallProtectionDays: Int = 0,
        val isShortsReelsBlockEnabled: Boolean = false,
        val isInstagramReelsBlockEnabled: Boolean = false,
        val isFacebookReelsBlockEnabled: Boolean = false,
        val isYouTubeShortsBlockEnabled: Boolean = false,
        val isAutoBlockNewAppsEnabled: Boolean = false,
        val isIncognitoBlockEnabled: Boolean = false,
        val isBlockUnsupportedBrowsersEnabled: Boolean = false,
        val isAdultContentBlockEnabled: Boolean = false,
        val isAntiCloningShieldEnabled: Boolean = true,
        val overlayWallpaperPath: String = "",
        val overlayPresetId: Int = 0,
        val overlayDimAlpha: Float = 0.55f,
        val customBlockMessage: String = "",
        val blockCountdownSeconds: Int = 5
    )

    val settingsSnapshot: Flow<SettingsSnapshot> = dataStore.data.map { prefs ->
        val challengeTypeName = prefs[Keys.CHALLENGE_TYPE] ?: ChallengeType.TYPING.name
        val cType = try { ChallengeType.valueOf(challengeTypeName) } catch (_: Exception) { ChallengeType.TYPING }
        SettingsSnapshot(
            challengeType = cType,
            commitmentSentence = prefs[Keys.COMMITMENT_SENTENCE] ?: DEFAULT_COMMITMENT_SENTENCE,
            timeDelayHours = prefs[Keys.TIME_DELAY_HOURS] ?: 24,
            isChallengeActive = prefs[Keys.IS_CHALLENGE_ACTIVE] ?: false,
            isUninstallProtectionEnabled = prefs[Keys.UNINSTALL_PROTECTION_ENABLED] ?: false,
            uninstallProtectionExpiryTimestamp = prefs[Keys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] ?: 0L,
            uninstallProtectionDays = prefs[Keys.UNINSTALL_PROTECTION_DAYS] ?: 0,
            isShortsReelsBlockEnabled = prefs[Keys.SHORTS_REELS_BLOCK_ENABLED] ?: false,
            isInstagramReelsBlockEnabled = prefs[Keys.INSTAGRAM_REELS_BLOCK_ENABLED] ?: false,
            isFacebookReelsBlockEnabled = prefs[Keys.FACEBOOK_REELS_BLOCK_ENABLED] ?: false,
            isYouTubeShortsBlockEnabled = prefs[Keys.YOUTUBE_SHORTS_BLOCK_ENABLED] ?: false,
            isAutoBlockNewAppsEnabled = prefs[Keys.AUTO_BLOCK_NEW_APPS] ?: false,
            isIncognitoBlockEnabled = prefs[Keys.INCOGNITO_BLOCK_ENABLED] ?: false,
            isBlockUnsupportedBrowsersEnabled = prefs[Keys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED] ?: false,
            isAdultContentBlockEnabled = prefs[Keys.ADULT_CONTENT_BLOCK_ENABLED] ?: false,
            isAntiCloningShieldEnabled = prefs[Keys.ANTI_CLONING_SHIELD_ENABLED] ?: true,
            overlayWallpaperPath = prefs[Keys.OVERLAY_WALLPAPER_PATH] ?: "",
            overlayPresetId = prefs[Keys.OVERLAY_PRESET_ID] ?: 0,
            overlayDimAlpha = prefs[Keys.OVERLAY_DIM_ALPHA] ?: 0.55f,
            customBlockMessage = prefs[Keys.CUSTOM_BLOCK_MESSAGE] ?: "",
            blockCountdownSeconds = prefs[Keys.BLOCK_COUNTDOWN_SECONDS] ?: 5
        )
    }

    fun replenishDailyParachuteIfNeeded() {
        val today = getTodayDateString()
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
                            it[Keys.EMERGENCY_PARACHUTES_COUNT] = updated
                            it[Keys.LAST_PARACHUTE_REGEN_DATE] = today
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("FocusGuardPrefs", "Failed to update parachute count: ${e.message}")
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
            it[Keys.SESSION_LOCK_ACTIVE] = true
            it[Keys.SESSION_END_TIME_MS] = endTime
            it[Keys.SESSION_TOTAL_MINUTES] = durationMinutes
            it[Keys.SESSION_SUITCASE_PACKAGES] = suitcasePackages
        }
    }

    suspend fun stopSessionLock() {
        syncPrefs.edit().putBoolean("session_lock_active", false).apply()
        dataStore.edit { it[Keys.SESSION_LOCK_ACTIVE] = false }
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
            it[Keys.EMERGENCY_PARACHUTES_COUNT] = updated
            it[Keys.SESSION_LOCK_ACTIVE] = false
        }
        return true
    }

    suspend fun saveSuitcasePackages(packages: Set<String>) {
        syncPrefs.edit().putStringSet("session_suitcase_packages", packages).apply()
        dataStore.edit { it[Keys.SESSION_SUITCASE_PACKAGES] = packages }
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
            it[Keys.SESSION_LOCK_ACTIVE] = false
            it[Keys.TOTAL_FOCUS_SESSIONS_COMPLETED] = sessions
            it[Keys.TOTAL_FOCUS_MINUTES_COMPLETED] = minutes
            it[Keys.EMERGENCY_PARACHUTES_COUNT] = parachutes
        }
    }
}
