package com.focusguard.app.feature.settings.domain

import android.net.Uri
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.overlay.wallpaper.OverlayWallpaperManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsOverlayUseCase @Inject constructor(
    private val repository: FocusGuardRepository,
    private val wallpaperManager: OverlayWallpaperManager,
    private val overlayManager: BlockOverlayManager
) {
    suspend fun saveWallpaper(uri: Uri): String? {
        val path = wallpaperManager.saveFromUri(uri)
        if (path != null) {
            repository.setOverlayWallpaperPath(path)
        }
        return path
    }

    suspend fun clearWallpaper() {
        wallpaperManager.clearWallpaper()
        repository.setOverlayWallpaperPath("")
    }

    suspend fun setPreset(id: Int) = repository.setOverlayPresetId(id)

    suspend fun setDimAlpha(alpha: Float) = repository.setOverlayDimAlpha(alpha)

    suspend fun setCustomBlockMessage(message: String) {
        val clean = message.trim()
        if (clean.isNotEmpty()) {
            when (val validation = com.focusguard.app.core.common.validator.InputValidator.validateCustomMessage(clean)) {
                is com.focusguard.app.core.common.validator.ValidationResult.Invalid -> throw IllegalArgumentException(validation.error.message)
                com.focusguard.app.core.common.validator.ValidationResult.Valid -> { /* valid */ }
            }
        }
        repository.setCustomBlockMessage(clean)
    }

    suspend fun setBlockCountdownSeconds(seconds: Int) = repository.setBlockCountdownSeconds(seconds)

    fun previewOverlay(
        presetId: Int,
        dimAlpha: Float,
        countdownSeconds: Int,
        customMessage: String
    ) {
        overlayManager.currentPresetId = presetId
        overlayManager.currentDimAlpha = dimAlpha
        overlayManager.showOverlay(
            durationSeconds = countdownSeconds,
            blockType = OverlayBlockType.APP_BLOCK,
            targetName = "Preview Mode",
            sourcePackage = "preview.focusguard",
            description = customMessage.ifBlank { "This page is blocked." },
            sendHomeOnDismiss = false
        )
    }
}
