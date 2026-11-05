package com.focusguard.app.feature.settings.data.repository

import android.util.Log
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DistractionFeedRepo"

@Singleton
class DistractionFeedRepositoryImpl @Inject constructor(
    private val prefs: FocusGuardPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // ── Single Owner of In-Memory Distraction Feed Caches ──
    private val cachedShortsReelsBlockEnabled = AtomicBoolean(prefs.syncShortsReelsBlockEnabled)
    private val cachedInstagramReelsBlockEnabled = AtomicBoolean(prefs.syncInstagramReelsBlockEnabled)
    private val cachedFacebookReelsBlockEnabled = AtomicBoolean(prefs.syncFacebookReelsBlockEnabled)
    private val cachedYouTubeShortsBlockEnabled = AtomicBoolean(prefs.syncYouTubeShortsBlockEnabled)
    private val cachedIncognitoBlockEnabled = AtomicBoolean(prefs.syncIncognitoBlockEnabled)
    private val cachedBlockUnsupportedBrowsersEnabled = AtomicBoolean(prefs.syncBlockUnsupportedBrowsersEnabled)
    private val cachedAdultContentBlockEnabled = AtomicBoolean(prefs.syncAdultContentBlockEnabled)
    private val cachedAntiCloningShieldEnabled = AtomicBoolean(prefs.syncAntiCloningShieldEnabled)

    init {
        repoScope.launch {
            prefs.isShortsReelsBlockEnabled.collect { enabled ->
                cachedShortsReelsBlockEnabled.set(enabled)
                Log.d(TAG, "Synchronized Shorts/Reels block cache: $enabled")
            }
        }
        repoScope.launch {
            prefs.isInstagramReelsBlockEnabled.collect { enabled ->
                cachedInstagramReelsBlockEnabled.set(enabled)
            }
        }
        repoScope.launch {
            prefs.isFacebookReelsBlockEnabled.collect { enabled ->
                cachedFacebookReelsBlockEnabled.set(enabled)
            }
        }
        repoScope.launch {
            prefs.isYouTubeShortsBlockEnabled.collect { enabled ->
                cachedYouTubeShortsBlockEnabled.set(enabled)
            }
        }
        repoScope.launch {
            prefs.isIncognitoBlockEnabled.collect { enabled ->
                cachedIncognitoBlockEnabled.set(enabled)
                Log.d(TAG, "Synchronized incognito block cache: $enabled")
            }
        }
        repoScope.launch {
            prefs.isBlockUnsupportedBrowsersEnabled.collect { enabled ->
                cachedBlockUnsupportedBrowsersEnabled.set(enabled)
                Log.d(TAG, "Synchronized block unsupported browsers cache: $enabled")
            }
        }
        repoScope.launch {
            prefs.isAntiCloningShieldEnabled.collect { enabled ->
                cachedAntiCloningShieldEnabled.set(enabled)
                Log.d(TAG, "Synchronized anti-cloning shield cache: $enabled")
            }
        }
        repoScope.launch {
            prefs.isAdultContentBlockEnabled.collect { enabled ->
                cachedAdultContentBlockEnabled.set(enabled)
                Log.d(TAG, "Synchronized adult content block cache: $enabled")
            }
        }
    }

    // ── Fast In-Memory Checks (< 0.01ms Hot Path for Accessibility) ──
    fun isFastShortsReelsBlockEnabled(): Boolean = cachedShortsReelsBlockEnabled.get()
    fun isFastInstagramReelsBlockEnabled(): Boolean = cachedInstagramReelsBlockEnabled.get()
    fun isFastFacebookReelsBlockEnabled(): Boolean = cachedFacebookReelsBlockEnabled.get()
    fun isFastYouTubeShortsBlockEnabled(): Boolean = cachedYouTubeShortsBlockEnabled.get()
    fun isFastIncognitoBlockEnabled(): Boolean = cachedIncognitoBlockEnabled.get()
    fun isFastBlockUnsupportedBrowsersEnabled(): Boolean = cachedBlockUnsupportedBrowsersEnabled.get()
    fun isFastAdultContentBlockEnabled(): Boolean = cachedAdultContentBlockEnabled.get()
    fun isFastAntiCloningShieldEnabled(): Boolean = cachedAntiCloningShieldEnabled.get()

    // ── Flows ──
    val isShortsReelsBlockEnabled: Flow<Boolean> get() = prefs.isShortsReelsBlockEnabled
    val isInstagramReelsBlockEnabled: Flow<Boolean> get() = prefs.isInstagramReelsBlockEnabled
    val isFacebookReelsBlockEnabled: Flow<Boolean> get() = prefs.isFacebookReelsBlockEnabled
    val isYouTubeShortsBlockEnabled: Flow<Boolean> get() = prefs.isYouTubeShortsBlockEnabled
    val isIncognitoBlockEnabled: Flow<Boolean> get() = prefs.isIncognitoBlockEnabled
    val isBlockUnsupportedBrowsersEnabled: Flow<Boolean> get() = prefs.isBlockUnsupportedBrowsersEnabled
    val isAdultContentBlockEnabled: Flow<Boolean> get() = prefs.isAdultContentBlockEnabled
    val isAntiCloningShieldEnabled: Flow<Boolean> get() = prefs.isAntiCloningShieldEnabled

    // ── Mutations ──
    suspend fun setShortsReelsBlockEnabled(enabled: Boolean) {
        cachedShortsReelsBlockEnabled.set(enabled)
        cachedInstagramReelsBlockEnabled.set(enabled)
        cachedFacebookReelsBlockEnabled.set(enabled)
        cachedYouTubeShortsBlockEnabled.set(enabled)
        prefs.setShortsReelsBlockEnabled(enabled)
    }

    suspend fun setInstagramReelsBlockEnabled(enabled: Boolean) {
        cachedInstagramReelsBlockEnabled.set(enabled)
        prefs.setInstagramReelsBlockEnabled(enabled)
    }

    suspend fun setFacebookReelsBlockEnabled(enabled: Boolean) {
        cachedFacebookReelsBlockEnabled.set(enabled)
        prefs.setFacebookReelsBlockEnabled(enabled)
    }

    suspend fun setYouTubeShortsBlockEnabled(enabled: Boolean) {
        cachedYouTubeShortsBlockEnabled.set(enabled)
        prefs.setYouTubeShortsBlockEnabled(enabled)
    }

    suspend fun setIncognitoBlockEnabled(enabled: Boolean) {
        cachedIncognitoBlockEnabled.set(enabled)
        prefs.setIncognitoBlockEnabled(enabled)
    }

    suspend fun setBlockUnsupportedBrowsersEnabled(enabled: Boolean) {
        cachedBlockUnsupportedBrowsersEnabled.set(enabled)
        prefs.setBlockUnsupportedBrowsersEnabled(enabled)
    }

    suspend fun setAdultContentBlockEnabled(enabled: Boolean) {
        cachedAdultContentBlockEnabled.set(enabled)
        prefs.setAdultContentBlockEnabled(enabled)
    }

    suspend fun setAntiCloningShieldEnabled(enabled: Boolean) {
        cachedAntiCloningShieldEnabled.set(enabled)
        prefs.setAntiCloningShieldEnabled(enabled)
    }
}
