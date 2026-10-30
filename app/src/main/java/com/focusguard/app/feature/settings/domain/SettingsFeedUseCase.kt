package com.focusguard.app.feature.settings.domain

import android.content.Context
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.service.AdultContentVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsFeedUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository
) {
    suspend fun disableFeed(targetId: String) {
        when (targetId) {
            "shorts_reels" -> {
                repository.setShortsReelsBlockEnabled(false)
                repository.setInstagramReelsBlockEnabled(false)
                repository.setFacebookReelsBlockEnabled(false)
                repository.setYouTubeShortsBlockEnabled(false)
            }
            "incognito" -> repository.setIncognitoBlockEnabled(false)
            "instagram" -> repository.setInstagramReelsBlockEnabled(false)
            "facebook" -> repository.setFacebookReelsBlockEnabled(false)
            "youtube" -> repository.setYouTubeShortsBlockEnabled(false)
            "auto_block" -> {
                repository.setAutoBlockNewAppsEnabled(false)
                repository.unblockAllAutoBlockedNewApps()
            }
            "unsupported_browsers" -> repository.setBlockUnsupportedBrowsersEnabled(false)
            "adult_content" -> {
                repository.setAdultContentBlockEnabled(false)
                AdultContentVpnService.stop(context)
            }
        }
    }

    suspend fun enableFeed(targetId: String) {
        when (targetId) {
            "shorts_reels" -> {
                repository.setShortsReelsBlockEnabled(true)
                repository.setInstagramReelsBlockEnabled(true)
                repository.setFacebookReelsBlockEnabled(true)
                repository.setYouTubeShortsBlockEnabled(true)
            }
            "incognito" -> repository.setIncognitoBlockEnabled(true)
            "instagram" -> repository.setInstagramReelsBlockEnabled(true)
            "facebook" -> repository.setFacebookReelsBlockEnabled(true)
            "youtube" -> repository.setYouTubeShortsBlockEnabled(true)
            "auto_block" -> repository.setAutoBlockNewAppsEnabled(true)
            "unsupported_browsers" -> repository.setBlockUnsupportedBrowsersEnabled(true)
            "adult_content" -> {
                repository.setAdultContentBlockEnabled(true)
                AdultContentVpnService.start(context)
            }
        }
    }

    suspend fun setShortsReels(enable: Boolean) = repository.setShortsReelsBlockEnabled(enable)
    suspend fun setInstagram(enable: Boolean) = repository.setInstagramReelsBlockEnabled(enable)
    suspend fun setFacebook(enable: Boolean) = repository.setFacebookReelsBlockEnabled(enable)
    suspend fun setYouTube(enable: Boolean) = repository.setYouTubeShortsBlockEnabled(enable)
    suspend fun setIncognito(enable: Boolean) = repository.setIncognitoBlockEnabled(enable)
    suspend fun setUnsupportedBrowsers(enable: Boolean) = repository.setBlockUnsupportedBrowsersEnabled(enable)
    suspend fun setAutoBlockNewApps(enable: Boolean) {
        repository.setAutoBlockNewAppsEnabled(enable)
        if (!enable) repository.unblockAllAutoBlockedNewApps()
    }
}
