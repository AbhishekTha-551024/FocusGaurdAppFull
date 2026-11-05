package com.focusguard.app.data.repository

import com.focusguard.app.core.model.FeedBlockingPolicy

fun FocusGuardRepository.isShortFormContentBlocked(packageName: String): Boolean =
    FeedBlockingPolicy.isBlocked(
        packageName = packageName,
        masterEnabled = isFastShortsReelsBlockEnabled(),
        instagramEnabled = isFastInstagramReelsBlockEnabled(),
        facebookEnabled = isFastFacebookReelsBlockEnabled(),
        youtubeEnabled = isFastYouTubeShortsBlockEnabled()
    )
