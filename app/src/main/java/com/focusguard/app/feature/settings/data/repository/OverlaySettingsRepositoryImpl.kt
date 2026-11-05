package com.focusguard.app.feature.settings.data.repository

import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlaySettingsRepositoryImpl @Inject constructor(
    private val prefs: FocusGuardPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // ── Single Owner of In-Memory Overlay Caches ──
    private val cachedOverlayPresetId = AtomicInteger(0)
    private val cachedOverlayDimAlpha = AtomicReference<Float>(0.55f)
    private val cachedOverlayWallpaperPath = AtomicReference<String>("")
    private val cachedCustomBlockMessage = AtomicReference<String>("")
    private val cachedBlockCountdownSeconds = AtomicInteger(5)

    init {
        repoScope.launch {
            prefs.customBlockMessage.collect { msg ->
                cachedCustomBlockMessage.set(msg)
            }
        }
        repoScope.launch {
            prefs.blockCountdownSeconds.collect { sec ->
                cachedBlockCountdownSeconds.set(sec)
            }
        }
        repoScope.launch {
            prefs.overlayPresetId.collect { id ->
                cachedOverlayPresetId.set(id)
            }
        }
        repoScope.launch {
            prefs.overlayDimAlpha.collect { dim ->
                cachedOverlayDimAlpha.set(dim)
            }
        }
        repoScope.launch {
            prefs.overlayWallpaperPath.collect { path ->
                cachedOverlayWallpaperPath.set(path)
            }
        }
    }

    // ── Fast In-Memory Checks (< 0.01ms Hot Path for Accessibility Overlay) ──
    fun getFastOverlayPresetId(): Int = cachedOverlayPresetId.get()
    fun getFastOverlayDimAlpha(): Float = cachedOverlayDimAlpha.get() ?: 0.55f
    fun getFastOverlayWallpaperPath(): String = cachedOverlayWallpaperPath.get() ?: ""
    fun getFastCustomBlockMessage(): String = cachedCustomBlockMessage.get()
    fun getFastBlockCountdownSeconds(): Int = cachedBlockCountdownSeconds.get()

    // ── Flows ──
    val overlayWallpaperPath: Flow<String> get() = prefs.overlayWallpaperPath
    val overlayPresetId: Flow<Int> get() = prefs.overlayPresetId
    val overlayDimAlpha: Flow<Float> get() = prefs.overlayDimAlpha
    val customBlockMessage: Flow<String> get() = prefs.customBlockMessage
    val blockCountdownSeconds: Flow<Int> get() = prefs.blockCountdownSeconds

    // ── Mutations ──
    suspend fun setOverlayWallpaperPath(path: String) {
        prefs.setOverlayWallpaperPath(path)
        cachedOverlayWallpaperPath.set(path)
    }

    suspend fun setOverlayPresetId(id: Int) {
        prefs.setOverlayPresetId(id)
        cachedOverlayPresetId.set(id)
    }

    suspend fun setOverlayDimAlpha(alpha: Float) {
        prefs.setOverlayDimAlpha(alpha)
        cachedOverlayDimAlpha.set(alpha)
    }

    suspend fun clearOverlayWallpaper() {
        prefs.clearOverlayWallpaper()
        cachedOverlayWallpaperPath.set("")
    }

    suspend fun setCustomBlockMessage(message: String) {
        prefs.setCustomBlockMessage(message)
        cachedCustomBlockMessage.set(message)
    }

    suspend fun setBlockCountdownSeconds(seconds: Int) {
        prefs.setBlockCountdownSeconds(seconds)
        cachedBlockCountdownSeconds.set(seconds)
    }
}

