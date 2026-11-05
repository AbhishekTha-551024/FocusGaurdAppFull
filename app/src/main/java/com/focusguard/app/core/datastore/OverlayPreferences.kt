package com.focusguard.app.core.datastore

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayPreferences @Inject constructor(
    private val core: PreferencesCore
) {
    private val dataStore = core.dataStore
    private val syncPrefs get() = core.syncPrefs

    val overlayWallpaperPath: Flow<String> = dataStore.data.map {
        it[PreferenceKeys.OVERLAY_WALLPAPER_PATH] ?: ""
    }

    val overlayPresetId: Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.OVERLAY_PRESET_ID] ?: 0
    }

    val overlayDimAlpha: Flow<Float> = dataStore.data.map {
        it[PreferenceKeys.OVERLAY_DIM_ALPHA] ?: 0.55f
    }

    val customBlockMessage: Flow<String> = dataStore.data.map {
        it[PreferenceKeys.CUSTOM_BLOCK_MESSAGE] ?: ""
    }

    val blockCountdownSeconds: Flow<Int> = dataStore.data.map {
        it[PreferenceKeys.BLOCK_COUNTDOWN_SECONDS] ?: 5
    }

    // ── Synchronous 0ms RAM Snapshot Getters ──
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

    suspend fun setOverlayWallpaperPath(path: String) {
        syncPrefs.edit().putString("overlay_wallpaper_path", path).apply()
        dataStore.edit { it[PreferenceKeys.OVERLAY_WALLPAPER_PATH] = path }
    }

    suspend fun clearOverlayWallpaper() {
        syncPrefs.edit().remove("overlay_wallpaper_path").apply()
        dataStore.edit { it.remove(PreferenceKeys.OVERLAY_WALLPAPER_PATH) }
    }

    suspend fun setOverlayPresetId(id: Int) {
        syncPrefs.edit().putInt("overlay_preset_id", id).apply()
        dataStore.edit { it[PreferenceKeys.OVERLAY_PRESET_ID] = id }
    }

    suspend fun setOverlayDimAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.10f, 0.95f)
        syncPrefs.edit().putFloat("overlay_dim_alpha", clamped).apply()
        dataStore.edit { it[PreferenceKeys.OVERLAY_DIM_ALPHA] = clamped }
    }

    suspend fun setCustomBlockMessage(message: String) {
        val trimmed = message.trim().take(120)
        syncPrefs.edit().putString("custom_block_message", trimmed).apply()
        dataStore.edit { it[PreferenceKeys.CUSTOM_BLOCK_MESSAGE] = trimmed }
    }

    suspend fun setBlockCountdownSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(3, 30)
        syncPrefs.edit().putInt("block_countdown_seconds", clamped).apply()
        dataStore.edit { it[PreferenceKeys.BLOCK_COUNTDOWN_SECONDS] = clamped }
    }
}

