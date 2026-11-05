package com.focusguard.app.core.model

object FeedBlockingPolicy {
    fun isBlocked(
        packageName: String,
        masterEnabled: Boolean,
        instagramEnabled: Boolean,
        facebookEnabled: Boolean,
        youtubeEnabled: Boolean
    ): Boolean {
        return when {
            packageName.equals(ReelTargetRegistry.INSTAGRAM_PACKAGE, ignoreCase = true) ->
                instagramEnabled
            packageName.equals(ReelTargetRegistry.FACEBOOK_PACKAGE, ignoreCase = true) ||
                    packageName.equals(ReelTargetRegistry.FACEBOOK_LITE_PACKAGE, ignoreCase = true) ->
                facebookEnabled
            packageName.equals(ReelTargetRegistry.YOUTUBE_PACKAGE, ignoreCase = true) ->
                youtubeEnabled
            ReelTargetRegistry.targets.any {
                it.packageName.equals(packageName, ignoreCase = true)
            } -> masterEnabled
            else -> false
        }
    }
}
