package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.AdultContentShield
import com.focusguard.app.blockers.KeywordBlocker
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.tracker.BrowserUrlTracker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordDetectionHandler @Inject constructor(
    private val browserUrlTracker: BrowserUrlTracker,
    private val repository: FocusGuardRepository,
    private val adultContentShield: AdultContentShield,
    private val keywordBlocker: KeywordBlocker
) {
    /**
     * Evaluates keywords, adult content shield, incognito browsing, and YouTube metadata.
     * Returns true if an early return was triggered (e.g. Incognito or Universal Search adult violation).
     */
    fun detectAndHandle(
        e: AccessibilityEvent?,
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        getCachedAppLabel: (String) -> String,
        triggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit
    ): Boolean {
        if (browserUrlTracker.isMessagingOrChatPackage(pkg)) {
            return false
        }

        if (browserUrlTracker.isYouTubePackage(pkg)) {
            val ytItems = browserUrlTracker.extractYouTubeMetadata(rootNode, e)
            for (item in ytItems) {
                if (item.text.length >= 2) {
                    if (repository.isFastAdultContentBlockEnabled()) {
                        val adultViolation = adultContentShield.checkViolation(item.text)
                        if (adultViolation != null) {
                            Log.i(TAG, "YouTube adult content violation detected in $pkg")
                            triggerBlock(
                                pkg,
                                "Explicit YouTube Content Blocked",
                                OverlayBlockType.KEYWORD_BLOCK,
                                "🔞 Adult Content Shield",
                                adultViolation.reason
                            )
                            break
                        }
                    }
                    val matchedKeyword = keywordBlocker.isViolationDetectedSync(item.text)
                    if (matchedKeyword != null) {
                        val (blockReason, blockDesc) = when (item.type) {
                            BrowserUrlTracker.YouTubeContentType.CHANNEL_NAME ->
                                "YouTube Channel Blocked: '$matchedKeyword'" to
                                "Videos from channel '$matchedKeyword' are blocked to protect your focus."
                            BrowserUrlTracker.YouTubeContentType.VIDEO_TITLE ->
                                "YouTube Video Blocked: '$matchedKeyword'" to
                                "The video containing '$matchedKeyword' in its title is blocked to protect your focus."
                            BrowserUrlTracker.YouTubeContentType.DESCRIPTION ->
                                "YouTube Description Blocked: '$matchedKeyword'" to
                                "Video with description containing '$matchedKeyword' is blocked to protect your focus."
                            BrowserUrlTracker.YouTubeContentType.SEARCH_QUERY ->
                                "YouTube Search Blocked: '$matchedKeyword'" to
                                "Searching for '$matchedKeyword' is blocked in YouTube to protect your focus."
                            BrowserUrlTracker.YouTubeContentType.SEARCH_SUGGESTION ->
                                "YouTube Suggestion Blocked: '$matchedKeyword'" to
                                "Search suggestion containing '$matchedKeyword' was blocked to protect your focus."
                        }
                        Log.i(TAG, "YouTube keyword violation detected in $pkg")
                        triggerBlock(
                            pkg,
                            blockReason,
                            OverlayBlockType.KEYWORD_BLOCK,
                            matchedKeyword,
                            blockDesc
                        )
                        break
                    }
                }
            }
        } else if (browserUrlTracker.isSupportedBrowser(pkg)) {
            if (repository.isFastIncognitoBlockEnabled() &&
                browserUrlTracker.isIncognitoOrPrivateTab(rootNode, e, pkg)
            ) {
                Log.i(TAG, "Incognito / Private tab blocked in $pkg")
                triggerBlock(
                    pkg,
                    "Incognito Mode Blocked",
                    OverlayBlockType.INCOGNITO_BLOCK,
                    "Incognito Mode",
                    "Private and Incognito browsing is blocked by FocusGuard to keep you accountable."
                )
                return true
            }

            val browserStrings = browserUrlTracker.extractBrowserStrings(rootNode, e, pkg)
            for (str in browserStrings) {
                if (str.length >= 2) {
                    if (repository.isFastAdultContentBlockEnabled()) {
                        val adultViolation = adultContentShield.checkViolation(str)
                        if (adultViolation != null) {
                            Log.i(TAG, "Browser adult content violation detected in $pkg")
                            triggerBlock(
                                pkg,
                                "Adult Content Blocked: '${adultViolation.matchedTerm}'",
                                OverlayBlockType.KEYWORD_BLOCK,
                                "🔞 Adult Content Shield",
                                adultViolation.reason
                            )
                            break
                        }
                    }
                    val matchedKeyword = keywordBlocker.isViolationDetectedSync(str)
                    if (matchedKeyword != null) {
                        Log.i(TAG, "Browser keyword violation detected in $pkg")
                        triggerBlock(
                            pkg,
                            "Website/Search Blocked: '$matchedKeyword'",
                            OverlayBlockType.KEYWORD_BLOCK,
                            matchedKeyword,
                            "Access to '$matchedKeyword' is blocked across all browsers."
                        )
                        break
                    }
                }
            }
        } else {
            val queryToInspect = browserUrlTracker.extractUniversalSearchOrInputQuery(rootNode, e)
            if (!queryToInspect.isNullOrBlank() && queryToInspect.length >= 2) {
                if (repository.isFastAdultContentBlockEnabled()) {
                    val adultViolation = adultContentShield.checkViolation(queryToInspect)
                    if (adultViolation != null) {
                        val appLabel = getCachedAppLabel(pkg)
                        Log.i(TAG, "Universal search adult content violation detected in $appLabel ($pkg)")
                        triggerBlock(
                            pkg,
                            "Adult Search Blocked in $appLabel",
                            OverlayBlockType.KEYWORD_BLOCK,
                            "🔞 Adult Content Shield",
                            adultViolation.reason
                        )
                        return true
                    }
                }
                val matchedKeyword = keywordBlocker.isViolationDetectedSync(queryToInspect)
                if (matchedKeyword != null) {
                    val appLabel = getCachedAppLabel(pkg)
                    Log.i(TAG, "Universal search keyword violation detected in $appLabel ($pkg)")
                    triggerBlock(
                        pkg,
                        "Keyword Blocked in $appLabel: '$matchedKeyword'",
                        OverlayBlockType.KEYWORD_BLOCK,
                        matchedKeyword,
                        "Searching for or typing '$matchedKeyword' is blocked in $appLabel."
                    )
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}

