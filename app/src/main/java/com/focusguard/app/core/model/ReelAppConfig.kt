package com.focusguard.app.core.model

data class ReelAppConfig(
    val packageName: String,
    val targetViewId: String,
    val blockAction: BlockAction,
    val alternateViewIds: List<String> = emptyList()
)

enum class BlockAction {
    BACK_PRESS,
    SHOW_OVERLAY,
    FORCE_HOME
}

object ReelTargetRegistry {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val FACEBOOK_LITE_PACKAGE = "com.facebook.lite"
    const val SNAPCHAT_PACKAGE = "com.snapchat.android"
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    const val TIKTOK_LITE_PACKAGE = "com.ss.android.ugc.trill"
    const val REDDIT_PACKAGE = "com.reddit.frontpage"

    val targets = listOf(
        ReelAppConfig(
            packageName = YOUTUBE_PACKAGE,
            targetViewId = "com.google.android.youtube:id/reel_watch_fragment_root",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.google.android.youtube:id/shorts_player_view",
                "com.google.android.youtube:id/reel_player_page_view",
                "com.google.android.youtube:id/shorts_video_layout",
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_watch_fragment",
                "com.google.android.youtube:id/shorts_container",
                "com.google.android.youtube:id/reel_player_view",
                "com.google.android.youtube:id/reel_watch_fragment_container",
                "com.google.android.youtube:id/reel_player_title",
                "com.google.android.youtube:id/shorts_title",
                "com.google.android.youtube:id/reel_pivot_button",
                "com.google.android.youtube:id/reel_sound_button"
            )
        ),
        ReelAppConfig(
            packageName = INSTAGRAM_PACKAGE,
            targetViewId = "com.instagram.android:id/clips_viewer_view_pager",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.instagram.android:id/reels_viewer_view_pager",
                "com.instagram.android:id/clips_view_pager",
                "com.instagram.android:id/reels_viewer_container",
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_viewer"
            )
        ),
        ReelAppConfig(
            packageName = FACEBOOK_PACKAGE,
            targetViewId = "com.facebook.katana:id/reels_viewer_container",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.facebook.katana:id/short_video_player",
                "com.facebook.katana:id/fb_reels_container",
                "com.facebook.katana:id/reel_viewer",
                "com.facebook.katana:id/fb_shorts_container",
                "com.facebook.katana:id/reels_tab",
                "com.facebook.katana:id/reels_video_view",
                "com.facebook.katana:id/fb_shorts_viewer_container"
            )
        ),
        ReelAppConfig(
            packageName = FACEBOOK_LITE_PACKAGE,
            targetViewId = "com.facebook.lite:id/reels_viewer_container",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.facebook.lite:id/short_video_player",
                "com.facebook.lite:id/fb_shorts_container",
                "com.facebook.lite:id/reels_tab"
            )
        ),
        ReelAppConfig(
            packageName = SNAPCHAT_PACKAGE,
            targetViewId = "com.snapchat.android:id/spotlight_page_container",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.snapchat.android:id/spotlight_fragment_container",
                "com.snapchat.android:id/spotlight_video_player_view",
                "com.snapchat.android:id/spotlight_feed_page"
            )
        ),
        ReelAppConfig(
            packageName = TIKTOK_PACKAGE,
            targetViewId = "com.zhiliaoapp.musically:id/viewpager_layout",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.zhiliaoapp.musically:id/video_container",
                "com.zhiliaoapp.musically:id/root_view"
            )
        ),
        ReelAppConfig(
            packageName = TIKTOK_LITE_PACKAGE,
            targetViewId = "com.ss.android.ugc.trill:id/viewpager_layout",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.ss.android.ugc.trill:id/video_container"
            )
        ),
        ReelAppConfig(
            packageName = REDDIT_PACKAGE,
            targetViewId = "com.reddit.frontpage:id/video_feed_container",
            blockAction = BlockAction.SHOW_OVERLAY,
            alternateViewIds = listOf(
                "com.reddit.frontpage:id/watch_feed_fragment",
                "com.reddit.frontpage:id/video_player_container"
            )
        )
    )
}
