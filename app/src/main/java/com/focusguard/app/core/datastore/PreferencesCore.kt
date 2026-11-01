package com.focusguard.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.focusguard.app.core.database.entity.ChallengeType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

val Context.focusGuardDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "focusguard_prefs")

const val DEFAULT_COMMITMENT_SENTENCE = "[Focus-Shield::v4.2]--Do I truly need to unlock this restricted app right now, or am I merely succumbing to an impulsive 'dopamine twitch'? I acknowledge that deep work demands total concentration; yet here I am, deliberately bypassing my self-imposed rules [status: \"interrupted\"; friction: high]. Every override reinforces weakness--eroding discipline, focus, and mental clarity; whereas enduring boredom builds resilience {habit-strength: +1}. Why trade my long-term goals for momentary amusement? I consciously accept full personal responsibility for this lapse (cost-factor: elevated @ 100% & risk = high); no excuses, no shortcuts! Stop, breathe, and reflect: is this distraction worth breaking my promise to myself? Confirm override [auth: #FG-902/unlock; verified: true]."

object PreferenceKeys {
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

typealias SettingsSnapshot = FocusGuardPreferences.SettingsSnapshot

@Singleton
class PreferencesCore @Inject constructor(
    @ApplicationContext val context: Context
) {
    val dataStore: DataStore<Preferences> = context.focusGuardDataStore
    val syncPrefs = context.getSharedPreferences("focusguard_sync_cache", Context.MODE_PRIVATE)

    private val prefExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("PreferencesCore", "Pref error safely intercepted: ${throwable.message}", throwable)
    }
    val prefScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + prefExceptionHandler)

    init {
        // Eagerly hydrate syncPrefs from DataStore to ensure 0ms reads on Frame 1
        prefScope.launch {
            try {
                dataStore.data.collect { prefs ->
                    val editor = syncPrefs.edit()
                    prefs[PreferenceKeys.CHALLENGE_TYPE]?.let { editor.putString("challenge_type", it) }
                    prefs[PreferenceKeys.COMMITMENT_SENTENCE]?.let {
                        val valid = if (it.trim().length >= 500) it.trim() else DEFAULT_COMMITMENT_SENTENCE
                        editor.putString("commitment_sentence", valid)
                    }
                    prefs[PreferenceKeys.TIME_DELAY_HOURS]?.let { editor.putInt("time_delay_hours", it) }
                    prefs[PreferenceKeys.IS_CHALLENGE_ACTIVE]?.let { editor.putBoolean("is_challenge_active", it) }
                    prefs[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED]?.let { editor.putBoolean("uninstall_protection_enabled", it) }
                    prefs[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP]?.let { editor.putLong("uninstall_protection_expiry_timestamp", it) }
                    prefs[PreferenceKeys.UNINSTALL_PROTECTION_DAYS]?.let { editor.putInt("uninstall_protection_days", it) }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        try {
                            val deContext = context.createDeviceProtectedStorageContext()
                            val deEditor = deContext.getSharedPreferences("focusguard_de_cache", Context.MODE_PRIVATE).edit()
                            prefs[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED]?.let { deEditor.putBoolean("uninstall_protection_enabled", it) }
                            prefs[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP]?.let { deEditor.putLong("uninstall_protection_expiry_timestamp", it) }
                            prefs[PreferenceKeys.UNINSTALL_PROTECTION_DAYS]?.let { deEditor.putInt("uninstall_protection_days", it) }
                            deEditor.apply()
                        } catch (e: Exception) {
                            android.util.Log.d("PreferencesCore", "Failed to update DE cache: ${e.message}")
                        }
                    }

                    prefs[PreferenceKeys.SHORTS_REELS_BLOCK_ENABLED]?.let { editor.putBoolean("shorts_reels_block_enabled", it) }
                    prefs[PreferenceKeys.INSTAGRAM_REELS_BLOCK_ENABLED]?.let { editor.putBoolean("instagram_reels_block_enabled", it) }
                    prefs[PreferenceKeys.FACEBOOK_REELS_BLOCK_ENABLED]?.let { editor.putBoolean("facebook_reels_block_enabled", it) }
                    prefs[PreferenceKeys.YOUTUBE_SHORTS_BLOCK_ENABLED]?.let { editor.putBoolean("youtube_shorts_block_enabled", it) }
                    prefs[PreferenceKeys.AUTO_BLOCK_NEW_APPS]?.let { editor.putBoolean("auto_block_new_apps", it) }
                    prefs[PreferenceKeys.CUSTOM_BLOCK_MESSAGE]?.let { editor.putString("custom_block_message", it) }
                    prefs[PreferenceKeys.BLOCK_COUNTDOWN_SECONDS]?.let { editor.putInt("block_countdown_seconds", it) }
                    prefs[PreferenceKeys.OVERLAY_WALLPAPER_PATH]?.let { editor.putString("overlay_wallpaper_path", it) }
                    prefs[PreferenceKeys.OVERLAY_PRESET_ID]?.let { editor.putInt("overlay_preset_id", it) }
                    prefs[PreferenceKeys.OVERLAY_DIM_ALPHA]?.let { editor.putFloat("overlay_dim_alpha", it) }
                    prefs[PreferenceKeys.REVIEWED_PACKAGES]?.let { editor.putStringSet("reviewed_packages", it) }
                    prefs[PreferenceKeys.INCOGNITO_BLOCK_ENABLED]?.let { editor.putBoolean("incognito_block_enabled", it) }
                    prefs[PreferenceKeys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED]?.let { editor.putBoolean("block_unsupported_browsers_enabled", it) }
                    prefs[PreferenceKeys.ADULT_CONTENT_BLOCK_ENABLED]?.let { editor.putBoolean("adult_content_block_enabled", it) }
                    prefs[PreferenceKeys.ANTI_CLONING_SHIELD_ENABLED]?.let { editor.putBoolean("anti_cloning_shield_enabled", it) }
                    prefs[PreferenceKeys.AUTH_USER_ID]?.let { editor.putString("auth_user_id", it) }
                    prefs[PreferenceKeys.AUTH_USER_NAME]?.let { editor.putString("auth_user_name", it) }
                    prefs[PreferenceKeys.AUTH_USER_EMAIL]?.let { editor.putString("auth_user_email", it) }
                    prefs[PreferenceKeys.AUTH_USER_PHOTO_URL]?.let { editor.putString("auth_user_photo_url", it) }
                    prefs[PreferenceKeys.AUTH_IS_LOGGED_IN]?.let { editor.putBoolean("auth_is_logged_in", it) }
                    prefs[PreferenceKeys.OEM_GUIDANCE_ACKNOWLEDGED]?.let { editor.putBoolean("oem_guidance_acknowledged", it) }
                    prefs[PreferenceKeys.SESSION_LOCK_ACTIVE]?.let { editor.putBoolean("session_lock_active", it) }
                    prefs[PreferenceKeys.SESSION_END_TIME_MS]?.let { editor.putLong("session_end_time_ms", it) }
                    prefs[PreferenceKeys.SESSION_TOTAL_MINUTES]?.let { editor.putInt("session_total_minutes", it) }
                    prefs[PreferenceKeys.SESSION_SUITCASE_PACKAGES]?.let { editor.putStringSet("session_suitcase_packages", it) }
                    prefs[PreferenceKeys.EMERGENCY_PARACHUTES_COUNT]?.let { editor.putInt("emergency_parachutes_count", it) }
                    prefs[PreferenceKeys.LAST_PARACHUTE_REGEN_DATE]?.let { editor.putString("last_parachute_regen_date", it) }
                    prefs[PreferenceKeys.TOTAL_FOCUS_SESSIONS_COMPLETED]?.let { editor.putInt("total_focus_sessions_completed", it) }
                    prefs[PreferenceKeys.TOTAL_FOCUS_MINUTES_COMPLETED]?.let { editor.putLong("total_focus_minutes_completed", it) }
                    editor.apply()
                }
            } catch (_: Exception) {
            }
        }
    }

    val settingsSnapshot: Flow<SettingsSnapshot> = dataStore.data.map { prefs ->
        val challengeTypeName = prefs[PreferenceKeys.CHALLENGE_TYPE] ?: ChallengeType.TYPING.name
        val cType = try { ChallengeType.valueOf(challengeTypeName) } catch (_: Exception) { ChallengeType.TYPING }
        SettingsSnapshot(
            challengeType = cType,
            commitmentSentence = prefs[PreferenceKeys.COMMITMENT_SENTENCE] ?: DEFAULT_COMMITMENT_SENTENCE,
            timeDelayHours = prefs[PreferenceKeys.TIME_DELAY_HOURS] ?: 24,
            isChallengeActive = prefs[PreferenceKeys.IS_CHALLENGE_ACTIVE] ?: false,
            isUninstallProtectionEnabled = prefs[PreferenceKeys.UNINSTALL_PROTECTION_ENABLED] ?: false,
            uninstallProtectionExpiryTimestamp = prefs[PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP] ?: 0L,
            uninstallProtectionDays = prefs[PreferenceKeys.UNINSTALL_PROTECTION_DAYS] ?: 0,
            isShortsReelsBlockEnabled = prefs[PreferenceKeys.SHORTS_REELS_BLOCK_ENABLED] ?: false,
            isInstagramReelsBlockEnabled = prefs[PreferenceKeys.INSTAGRAM_REELS_BLOCK_ENABLED] ?: false,
            isFacebookReelsBlockEnabled = prefs[PreferenceKeys.FACEBOOK_REELS_BLOCK_ENABLED] ?: false,
            isYouTubeShortsBlockEnabled = prefs[PreferenceKeys.YOUTUBE_SHORTS_BLOCK_ENABLED] ?: false,
            isAutoBlockNewAppsEnabled = prefs[PreferenceKeys.AUTO_BLOCK_NEW_APPS] ?: false,
            isIncognitoBlockEnabled = prefs[PreferenceKeys.INCOGNITO_BLOCK_ENABLED] ?: false,
            isBlockUnsupportedBrowsersEnabled = prefs[PreferenceKeys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED] ?: false,
            isAdultContentBlockEnabled = prefs[PreferenceKeys.ADULT_CONTENT_BLOCK_ENABLED] ?: false,
            isAntiCloningShieldEnabled = prefs[PreferenceKeys.ANTI_CLONING_SHIELD_ENABLED] ?: true,
            overlayWallpaperPath = prefs[PreferenceKeys.OVERLAY_WALLPAPER_PATH] ?: "",
            overlayPresetId = prefs[PreferenceKeys.OVERLAY_PRESET_ID] ?: 0,
            overlayDimAlpha = prefs[PreferenceKeys.OVERLAY_DIM_ALPHA] ?: 0.55f,
            customBlockMessage = prefs[PreferenceKeys.CUSTOM_BLOCK_MESSAGE] ?: "",
            blockCountdownSeconds = prefs[PreferenceKeys.BLOCK_COUNTDOWN_SECONDS] ?: 5
        )
    }

    fun getTodayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
