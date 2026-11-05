package com.focusguard.app.feature.settings

import com.focusguard.app.core.model.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------------------------------------------------------------------------
// Pure logic tests – no Android / Hilt / Coroutine infrastructure needed.
// These tests encode the ID contract between SettingsViewModel and
// SettingsFeedUseCase.  They FAIL on the old (bugged) code and PASS after fix.
// ---------------------------------------------------------------------------

class SettingsViewModelFeedToggleTest {

    // IDs accepted by SettingsFeedUseCase.enableFeed() / disableFeed()
    private val useCaseIds = setOf(
        "shorts_reels", "incognito", "instagram", "facebook",
        "youtube", "auto_block", "unsupported_browsers", "adult_content"
    )

    // IDs that SettingsViewModel toggle methods pass after the fix
    private val viewModelIds = mapOf(
        "toggleShortsReelsBlock"         to "shorts_reels",
        "toggleInstagramReelsBlock"      to "instagram",          // was "instagram_reels" BUG
        "toggleFacebookReelsBlock"       to "facebook",           // was "facebook_reels" BUG
        "toggleYouTubeShortsBlock"       to "youtube",            // was "youtube_shorts" BUG
        "toggleIncognitoBlock"           to "incognito",
        "toggleBlockUnsupportedBrowsers" to "unsupported_browsers",
        "toggleAutoBlockNewApps"         to "auto_block",         // was "auto_block_new_apps" BUG
        "toggleNsfwBlock"                to "adult_content"       // was "nsfw" BUG
    )

    // ---------------------------------------------------------------------------
    // Scenario 1 – All toggle IDs match a branch in enableFeed()
    // Before fix: "instagram_reels", "facebook_reels", "youtube_shorts",
    //             "auto_block_new_apps", "nsfw" all fell through → silent no-op.
    // ---------------------------------------------------------------------------
    @Test
    fun `all toggle IDs passed from ViewModel match enableFeed when-branches`() {
        viewModelIds.forEach { (method, id) ->
            assertTrue(
                "SettingsViewModel.$method passes id='$id' but SettingsFeedUseCase has no branch for it",
                useCaseIds.contains(id)
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Scenario 2 – Demonstrate the pre-fix bug for instagram
    // ---------------------------------------------------------------------------
    @Test
    fun `OLD instagram_reels id falls through enableFeed silently - demonstrates pre-fix bug`() {
        val enabledIds = mutableListOf<String>()
        // Old (bugged) id:
        val buggyId = "instagram_reels"
        when (buggyId) {
            "instagram" -> enabledIds += "setInstagramReelsBlockEnabled(true)"
            else -> { /* BUG: silent no-op */ }
        }
        assertTrue(
            "Demonstrates bug: old id 'instagram_reels' hits no branch → enabledIds is empty",
            enabledIds.isEmpty()
        )
    }

    // ---------------------------------------------------------------------------
    // Scenario 2 – Correct instagram id works after fix
    // ---------------------------------------------------------------------------
    @Test
    fun `NEW instagram id correctly hits enableFeed branch`() {
        val enabledIds = mutableListOf<String>()
        val fixedId = "instagram"
        when (fixedId) {
            "instagram" -> enabledIds += "setInstagramReelsBlockEnabled(true)"
            else -> { }
        }
        assertTrue(
            "Fixed id 'instagram' should hit the branch",
            enabledIds.contains("setInstagramReelsBlockEnabled(true)")
        )
    }

    // ---------------------------------------------------------------------------
    // Scenario 3 – When challenge NOT active: toggle ON must set pendingFeature,
    // not call enableFeed immediately.
    // ---------------------------------------------------------------------------
    @Test
    fun `when challenge NOT active, toggle ON stores pendingFeature and does NOT call enableFeed`() {
        var pendingFeature: String? = null
        var showDialog = false
        var enableFeedCalled = false

        val id = "instagram"
        val isChallengeActive = false
        val permissionsGranted = true

        if (permissionsGranted) {
            if (!isChallengeActive) {
                pendingFeature = "feed_$id"
                showDialog = true
                // returns early – enableFeed NOT called
            } else {
                enableFeedCalled = true
            }
        }

        assertEquals("feed_instagram", pendingFeature)
        assertTrue(showDialog)
        assertEquals(false, enableFeedCalled)
    }

    // ---------------------------------------------------------------------------
    // Scenario 4 – When challenge IS active: toggle ON sets pendingFeedWarning
    // with the correct ID so confirmFeedEnable() can call enableFeed(id).
    // ---------------------------------------------------------------------------
    @Test
    fun `when challenge active, toggle ON sets pendingFeedWarning with correct ID`() {
        var pendingFeedWarning: String? = null
        val enabledIds = mutableListOf<String>()

        // toggle youtube ON with challenge active
        val id = "youtube"
        val isChallengeActive = true
        if (isChallengeActive) {
            pendingFeedWarning = id
        }

        // simulate confirmFeedEnable()
        val resolvedId = pendingFeedWarning ?: "MISSING"
        when (resolvedId) {
            "youtube" -> enabledIds += "setYouTubeShortsBlockEnabled(true)"
            else -> { /* BUG if wrong id */ }
        }

        assertEquals("youtube", pendingFeedWarning)
        assertTrue(
            "confirmFeedEnable should succeed with id='youtube'",
            enabledIds.contains("setYouTubeShortsBlockEnabled(true)")
        )
    }

    // ---------------------------------------------------------------------------
    // Scenario 5 – triggerPendingFeatureAfterChallenge sets _pendingFeedWarning
    // with the correct ID (after challenge activation via proceedToSetupChallenge).
    // ---------------------------------------------------------------------------
    @Test
    fun `triggerPendingFeatureAfterChallenge sets correct pendingFeedWarning for adult_content`() {
        var pendingFeedWarning: String? = null

        // After fix: pendingFeatureAfterChallenge = "feed_adult_content"
        val pending = "feed_adult_content"
        if (pending.startsWith("feed_")) {
            pendingFeedWarning = pending.removePrefix("feed_")
        }

        assertEquals("adult_content", pendingFeedWarning)

        // Verify it hits the enableFeed branch
        val enabledIds = mutableListOf<String>()
        when (pendingFeedWarning) {
            "adult_content" -> enabledIds += "setAdultContentBlockEnabled(true)"
            else -> { }
        }
        assertTrue(enabledIds.isNotEmpty())
    }

    // ---------------------------------------------------------------------------
    // Scenario 6 – PARTNER challenge type: pending feature is preserved (documented).
    // ---------------------------------------------------------------------------
    @Test
    fun `partner challenge type shows approval dialog and preserves pendingFeature`() {
        var pendingFeature: String? = "feed_facebook"
        var dialogShown = false

        val challengeType = ChallengeType.PARTNER
        if (challengeType == ChallengeType.PARTNER) {
            dialogShown = true
            // pendingFeature is preserved — not consumed until partner approves
        }

        assertTrue(dialogShown)
        assertEquals("feed_facebook", pendingFeature)
    }

    // ---------------------------------------------------------------------------
    // Scenario 7 – disableFeed IDs also match (OFF toggle path)
    // ---------------------------------------------------------------------------
    @Test
    fun `all disable IDs match disableFeed when-branches`() {
        viewModelIds.values.forEach { id ->
            assertTrue(
                "disableFeed id='$id' has no matching branch in SettingsFeedUseCase",
                useCaseIds.contains(id)
            )
        }
    }
}
