package com.focusguard.app.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedBlockingPolicyTest {

    @Test
    fun individualPlatformSettingsOverrideMasterSetting() {
        assertFalse(blocked(ReelTargetRegistry.INSTAGRAM_PACKAGE, master = true))
        assertFalse(blocked(ReelTargetRegistry.FACEBOOK_PACKAGE, master = true))
        assertFalse(blocked(ReelTargetRegistry.FACEBOOK_LITE_PACKAGE, master = true))
        assertFalse(blocked(ReelTargetRegistry.YOUTUBE_PACKAGE, master = true))

        assertTrue(blocked(ReelTargetRegistry.INSTAGRAM_PACKAGE, instagram = true))
        assertTrue(blocked(ReelTargetRegistry.FACEBOOK_PACKAGE, facebook = true))
        assertTrue(blocked(ReelTargetRegistry.FACEBOOK_LITE_PACKAGE, facebook = true))
        assertTrue(blocked(ReelTargetRegistry.YOUTUBE_PACKAGE, youtube = true))
    }

    @Test
    fun masterSettingAppliesToSupportedPlatformsWithoutIndividualControls() {
        assertTrue(blocked(ReelTargetRegistry.SNAPCHAT_PACKAGE, master = true))
        assertTrue(blocked(ReelTargetRegistry.TIKTOK_PACKAGE, master = true))
        assertTrue(blocked(ReelTargetRegistry.TIKTOK_LITE_PACKAGE, master = true))
        assertTrue(blocked(ReelTargetRegistry.REDDIT_PACKAGE, master = true))
        assertFalse(blocked(ReelTargetRegistry.SNAPCHAT_PACKAGE))
    }

    @Test
    fun unknownPackagesAreNeverBlockedByFeedPolicy() {
        assertFalse(blocked("com.example.unlisted", master = true))
        assertTrue(blocked(ReelTargetRegistry.INSTAGRAM_PACKAGE.uppercase(), instagram = true))
    }

    private fun blocked(
        packageName: String,
        master: Boolean = false,
        instagram: Boolean = false,
        facebook: Boolean = false,
        youtube: Boolean = false
    ): Boolean = FeedBlockingPolicy.isBlocked(
        packageName = packageName,
        masterEnabled = master,
        instagramEnabled = instagram,
        facebookEnabled = facebook,
        youtubeEnabled = youtube
    )
}
