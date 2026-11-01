package com.focusguard.app.core.datastore

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedPreferences @Inject constructor(
    private val core: PreferencesCore
) {
    private val dataStore = core.dataStore
    private val syncPrefs = core.syncPrefs

    val isShortsReelsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.SHORTS_REELS_BLOCK_ENABLED] ?: false
    }

    val isInstagramReelsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.INSTAGRAM_REELS_BLOCK_ENABLED] ?: false
    }

    val isFacebookReelsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.FACEBOOK_REELS_BLOCK_ENABLED] ?: false
    }

    val isYouTubeShortsBlockEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.YOUTUBE_SHORTS_BLOCK_ENABLED] ?: false
    }

    val isAutoBlockNewAppsEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.AUTO_BLOCK_NEW_APPS] ?: false
    }

    val autoBlockEnabledTimestamp: Flow<Long> = dataStore.data.map {
        it[PreferenceKeys.AUTO_BLOCK_ENABLED_TIMESTAMP] ?: 0L
    }

    val reviewedPackages: Flow<Set<String>> = dataStore.data.map {
        it[PreferenceKeys.REVIEWED_PACKAGES] ?: emptySet()
    }

    val isIncognitoBlockEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.INCOGNITO_BLOCK_ENABLED] ?: false
    }

    val isBlockUnsupportedBrowsersEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED] ?: false
    }

    val isAdultContentBlockEnabled: Flow<Boolean> = dataStore.data.map {
        it[PreferenceKeys.ADULT_CONTENT_BLOCK_ENABLED] ?: false
    }

    // ── Synchronous 0ms RAM Snapshot Getters ──
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

    val syncReviewedPackages: Set<String>
        get() = syncPrefs.getStringSet("reviewed_packages", emptySet()) ?: emptySet()

    val syncIncognitoBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("incognito_block_enabled", false)

    val syncBlockUnsupportedBrowsersEnabled: Boolean
        get() = syncPrefs.getBoolean("block_unsupported_browsers_enabled", false)

    val syncAdultContentBlockEnabled: Boolean
        get() = syncPrefs.getBoolean("adult_content_block_enabled", false)

    suspend fun setShortsReelsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit()
            .putBoolean("shorts_reels_block_enabled", enabled)
            .putBoolean("instagram_reels_block_enabled", enabled)
            .putBoolean("facebook_reels_block_enabled", enabled)
            .putBoolean("youtube_shorts_block_enabled", enabled)
            .apply()

        dataStore.edit {
            it[PreferenceKeys.SHORTS_REELS_BLOCK_ENABLED] = enabled
            it[PreferenceKeys.INSTAGRAM_REELS_BLOCK_ENABLED] = enabled
            it[PreferenceKeys.FACEBOOK_REELS_BLOCK_ENABLED] = enabled
            it[PreferenceKeys.YOUTUBE_SHORTS_BLOCK_ENABLED] = enabled
        }
    }

    suspend fun setInstagramReelsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("instagram_reels_block_enabled", enabled).apply()
        dataStore.edit { it[PreferenceKeys.INSTAGRAM_REELS_BLOCK_ENABLED] = enabled }
    }

    suspend fun setFacebookReelsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("facebook_reels_block_enabled", enabled).apply()
        dataStore.edit { it[PreferenceKeys.FACEBOOK_REELS_BLOCK_ENABLED] = enabled }
    }

    suspend fun setYouTubeShortsBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("youtube_shorts_block_enabled", enabled).apply()
        dataStore.edit { it[PreferenceKeys.YOUTUBE_SHORTS_BLOCK_ENABLED] = enabled }
    }

    suspend fun setAutoBlockNewApps(enabled: Boolean) {
        val now = System.currentTimeMillis()
        syncPrefs.edit().putBoolean("auto_block_new_apps", enabled).apply()
        dataStore.edit {
            it[PreferenceKeys.AUTO_BLOCK_NEW_APPS] = enabled
            if (enabled) {
                it[PreferenceKeys.AUTO_BLOCK_ENABLED_TIMESTAMP] = now
            }
        }
    }

    suspend fun markAppReviewed(packageName: String) {
        val current = syncReviewedPackages.toMutableSet()
        current.add(packageName)
        syncPrefs.edit().putStringSet("reviewed_packages", current).apply()
        dataStore.edit { prefs ->
            val set = prefs[PreferenceKeys.REVIEWED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            set.add(packageName)
            prefs[PreferenceKeys.REVIEWED_PACKAGES] = set
        }
    }

    suspend fun clearAppReviewed(packageName: String) {
        val current = syncReviewedPackages.toMutableSet()
        current.remove(packageName)
        syncPrefs.edit().putStringSet("reviewed_packages", current).apply()
        dataStore.edit { prefs ->
            val set = prefs[PreferenceKeys.REVIEWED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            set.remove(packageName)
            prefs[PreferenceKeys.REVIEWED_PACKAGES] = set
        }
    }

    suspend fun setIncognitoBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("incognito_block_enabled", enabled).apply()
        dataStore.edit { it[PreferenceKeys.INCOGNITO_BLOCK_ENABLED] = enabled }
    }

    suspend fun setBlockUnsupportedBrowsersEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("block_unsupported_browsers_enabled", enabled).apply()
        dataStore.edit { it[PreferenceKeys.BLOCK_UNSUPPORTED_BROWSERS_ENABLED] = enabled }
    }

    suspend fun setAdultContentBlockEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("adult_content_block_enabled", enabled).apply()
        dataStore.edit { it[PreferenceKeys.ADULT_CONTENT_BLOCK_ENABLED] = enabled }
    }
}

