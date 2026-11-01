package com.focusguard.app.service.handlers

import com.focusguard.app.blockers.ReelBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HeartbeatCoordinatorTest {

    @Test
    fun reelDescription_returnsExactDescription_forInstagramReels() {
        val desc = HeartbeatCoordinator.reelDescription("Instagram Reels")
        assertEquals(
            "Instagram Reels is blocked to protect your focus. Feed, profile, and direct messages remain accessible.",
            desc
        )
    }

    @Test
    fun reelDescription_returnsExactDescription_forFacebookReels() {
        val desc = HeartbeatCoordinator.reelDescription("Facebook Reels")
        assertEquals(
            "Facebook Reels is blocked to protect your focus. Feed, groups, and messages remain accessible.",
            desc
        )
    }

    @Test
    fun reelDescription_returnsExactDescription_forYouTubeShortsAndDefault() {
        val descShorts = HeartbeatCoordinator.reelDescription("YouTube Shorts")
        val descOther = HeartbeatCoordinator.reelDescription("Other Shorts")
        val expected = "YouTube Shorts is blocked to protect your focus. Normal videos and search remain accessible."
        assertEquals(expected, descShorts)
        assertEquals(expected, descOther)
    }

    @Test
    fun buildKeywordViolationText_returnsYouTubeSpecificText_whenIsYtViolIsTrue() {
        val (reason, desc) = HeartbeatCoordinator.buildKeywordViolationText(
            matchedKeyword = "gaming",
            appLabel = "YouTube",
            isYtViol = true,
            isBrowserViol = false
        )
        assertEquals("YouTube Search Blocked: 'gaming'", reason)
        assertEquals("Searching for 'gaming' is blocked in YouTube to protect your focus.", desc)
    }

    @Test
    fun buildKeywordViolationText_returnsBrowserSpecificText_whenIsBrowserViolIsTrue() {
        val (reason, desc) = HeartbeatCoordinator.buildKeywordViolationText(
            matchedKeyword = "casino",
            appLabel = "Chrome",
            isYtViol = false,
            isBrowserViol = true
        )
        assertEquals("Keyword Blocked: 'casino'", reason)
        assertEquals("Access to 'casino' is blocked across all browsers.", desc)
    }

    @Test
    fun buildKeywordViolationText_returnsGeneralAppText_whenNeitherYtNorBrowser() {
        val (reason, desc) = HeartbeatCoordinator.buildKeywordViolationText(
            matchedKeyword = "distraction",
            appLabel = "Notes",
            isYtViol = false,
            isBrowserViol = false
        )
        assertEquals("Keyword Blocked in Notes: 'distraction'", reason)
        assertEquals("Searching for or typing 'distraction' is blocked in Notes.", desc)
    }

    @Test
    fun reelBlockerPackageConstants_arePlainStringsUsableInJvm() {
        assertEquals("com.instagram.android", ReelBlocker.INSTAGRAM_PACKAGE)
        assertEquals("com.facebook.katana", ReelBlocker.FACEBOOK_PACKAGE)
        assertEquals("com.facebook.lite", ReelBlocker.FACEBOOK_LITE_PACKAGE)
        assertEquals("com.google.android.youtube", ReelBlocker.YOUTUBE_PACKAGE)
        assertNotNull(ReelBlocker.INSTAGRAM_PACKAGE)
    }
}

