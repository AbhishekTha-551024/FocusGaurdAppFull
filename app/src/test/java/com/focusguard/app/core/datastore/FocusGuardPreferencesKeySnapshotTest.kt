package com.focusguard.app.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusGuardPreferencesKeySnapshotTest {

    @Test
    fun verifyAll42PreferenceDataStoreKeysRemainStrictlyUnchanged() {
        // Group 1: Protection keys
        assertEquals("uninstall_protection_enabled", PreferenceKeys.UNINSTALL_PROTECTION_ENABLED.name)
        assertEquals("uninstall_protection_expiry_timestamp", PreferenceKeys.UNINSTALL_PROTECTION_EXPIRY_TIMESTAMP.name)
        assertEquals("uninstall_protection_days", PreferenceKeys.UNINSTALL_PROTECTION_DAYS.name)
        assertEquals("challenge_type", PreferenceKeys.CHALLENGE_TYPE.name)
        assertEquals("commitment_sentence", PreferenceKeys.COMMITMENT_SENTENCE.name)
        assertEquals("time_delay_hours", PreferenceKeys.TIME_DELAY_HOURS.name)
        assertEquals("is_challenge_active", PreferenceKeys.IS_CHALLENGE_ACTIVE.name)
        assertEquals("anti_cloning_shield_enabled", PreferenceKeys.ANTI_CLONING_SHIELD_ENABLED.name)

        // Group 2: Feed & Content keys
        assertEquals("shorts_reels_block_enabled", PreferenceKeys.SHORTS_REELS_BLOCK_ENABLED.name)
        assertEquals("instagram_reels_block_enabled", PreferenceKeys.INSTAGRAM_REELS_BLOCK_ENABLED.name)
        assertEquals("facebook_reels_block_enabled", PreferenceKeys.FACEBOOK_REELS_BLOCK_ENABLED.name)
        assertEquals("youtube_shorts_block_enabled", PreferenceKeys.YOUTUBE_SHORTS_BLOCK_ENABLED.name)
        assertEquals("auto_block_new_apps", PreferenceKeys.AUTO_BLOCK_NEW_APPS.name)
        assertEquals("auto_block_enabled_timestamp", PreferenceKeys.AUTO_BLOCK_ENABLED_TIMESTAMP.name)
        assertEquals("reviewed_packages", PreferenceKeys.REVIEWED_PACKAGES.name)
        assertEquals("incognito_block_enabled", PreferenceKeys.INCOGNITO_BLOCK_ENABLED.name)
        assertEquals("block_unsupported_browsers_enabled", PreferenceKeys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED.name)
        assertEquals("adult_content_block_enabled", PreferenceKeys.ADULT_CONTENT_BLOCK_ENABLED.name)

        // Group 3: Overlay keys
        assertEquals("overlay_wallpaper_path", PreferenceKeys.OVERLAY_WALLPAPER_PATH.name)
        assertEquals("overlay_preset_id", PreferenceKeys.OVERLAY_PRESET_ID.name)
        assertEquals("overlay_dim_alpha", PreferenceKeys.OVERLAY_DIM_ALPHA.name)
        assertEquals("custom_block_message", PreferenceKeys.CUSTOM_BLOCK_MESSAGE.name)
        assertEquals("block_countdown_seconds", PreferenceKeys.BLOCK_COUNTDOWN_SECONDS.name)

        // Group 4: Onboarding, Auth, Device & Support keys
        assertEquals("onboarding_complete", PreferenceKeys.ONBOARDING_COMPLETE.name)
        assertEquals("oem_guidance_acknowledged", PreferenceKeys.OEM_GUIDANCE_ACKNOWLEDGED.name)
        assertEquals("device_id", PreferenceKeys.DEVICE_ID.name)
        assertEquals("support_wrong_attempts", PreferenceKeys.SUPPORT_WRONG_ATTEMPTS.name)
        assertEquals("support_cooldown_until_ms", PreferenceKeys.SUPPORT_COOLDOWN_UNTIL_MS.name)
        assertEquals("auth_user_id", PreferenceKeys.AUTH_USER_ID.name)
        assertEquals("auth_user_name", PreferenceKeys.AUTH_USER_NAME.name)
        assertEquals("auth_user_email", PreferenceKeys.AUTH_USER_EMAIL.name)
        assertEquals("auth_user_photo_url", PreferenceKeys.AUTH_USER_PHOTO_URL.name)
        assertEquals("auth_id_token", PreferenceKeys.AUTH_ID_TOKEN.name)
        assertEquals("auth_is_logged_in", PreferenceKeys.AUTH_IS_LOGGED_IN.name)

        // Group 5: Session Lock keys
        assertEquals("session_lock_active", PreferenceKeys.SESSION_LOCK_ACTIVE.name)
        assertEquals("session_end_time_ms", PreferenceKeys.SESSION_END_TIME_MS.name)
        assertEquals("session_total_minutes", PreferenceKeys.SESSION_TOTAL_MINUTES.name)
        assertEquals("session_suitcase_packages", PreferenceKeys.SESSION_SUITCASE_PACKAGES.name)
        assertEquals("emergency_parachutes_count", PreferenceKeys.EMERGENCY_PARACHUTES_COUNT.name)
        assertEquals("last_parachute_regen_date", PreferenceKeys.LAST_PARACHUTE_REGEN_DATE.name)
        assertEquals("total_focus_sessions_completed", PreferenceKeys.TOTAL_FOCUS_SESSIONS_COMPLETED.name)
        assertEquals("total_focus_minutes_completed", PreferenceKeys.TOTAL_FOCUS_MINUTES_COMPLETED.name)
    }
}

