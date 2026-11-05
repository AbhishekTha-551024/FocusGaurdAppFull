package com.focusguard.app.tracker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BrowserUrlTracker
 * =================
 * High-performance facade for browser URL/omnibox tracking, YouTube metadata extraction,
 * private/incognito detection, and universal search query extraction.
 *
 * Delegates to specialized components:
 * - [BrowserPackageRegistry]: Package registry & resource IDs
 * - [BrowserOmniboxTracker]: URL & omnibox text extraction
 * - [YouTubeMetadataExtractor]: YouTube video title, channel, and search query extraction
 * - [ChatQueryExtractor]: Universal 3rd-party app input/search extraction
 * - [BrowserIncognitoDetector]: Incognito & private tab detection
 */
@Singleton
class BrowserUrlTracker @Inject constructor(
    private val packageRegistry: BrowserPackageRegistry,
    private val omniboxTracker: BrowserOmniboxTracker,
    private val youtubeExtractor: YouTubeMetadataExtractor,
    private val chatExtractor: ChatQueryExtractor,
    private val incognitoDetector: BrowserIncognitoDetector
) {

    enum class YouTubeContentType {
        SEARCH_QUERY,
        CHANNEL_NAME,
        VIDEO_TITLE,
        DESCRIPTION,
        SEARCH_SUGGESTION
    }

    data class YouTubeContentItem(
        val text: String,
        val type: YouTubeContentType
    )

    companion object {
        val VERIFIED_SUPPORTED_BROWSERS: Set<String>
            get() = BrowserPackageRegistry.VERIFIED_SUPPORTED_BROWSERS

        val KNOWN_UNSUPPORTED_BROWSERS: Set<String>
            get() = BrowserPackageRegistry.KNOWN_UNSUPPORTED_BROWSERS
    }

    fun isSupportedBrowser(pkg: String): Boolean =
        packageRegistry.isSupportedBrowser(pkg)

    fun isUnsupportedBrowser(pkg: String): Boolean =
        packageRegistry.isUnsupportedBrowser(pkg)

    fun isYouTubePackage(pkg: String): Boolean =
        packageRegistry.isYouTubePackage(pkg)

    fun isMessagingOrChatPackage(pkg: String): Boolean =
        packageRegistry.isMessagingOrChatPackage(pkg)

    fun extractUniversalSearchOrInputQuery(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): String? =
        chatExtractor.extractUniversalSearchOrInputQuery(rootNode, event)

    fun extractYouTubeMetadata(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent? = null
    ): List<YouTubeContentItem> =
        youtubeExtractor.extractYouTubeMetadata(rootNode, event)

    /**
     * Deprecated: Raw C++ substring search lacked word boundaries and matched static UI buttons/menus.
     * All keyword filtering is now strictly consolidated in KeywordBlocker Trie engine.
     */
    fun checkNativeKeywordsOnScreen(rootNode: AccessibilityNodeInfo?, keywords: Set<String>): String? = null

    fun extractYouTubeSearchQuery(rootNode: AccessibilityNodeInfo?): String? =
        youtubeExtractor.extractYouTubeSearchQuery(rootNode)

    fun extractUrlOrQuery(rootNode: AccessibilityNodeInfo?, pkg: String): String? =
        omniboxTracker.extractUrlOrQuery(rootNode, pkg)

    fun isBrowserSettingsScreen(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, pkg: String): Boolean =
        omniboxTracker.isBrowserSettingsScreen(rootNode, event, pkg)

    fun extractBrowserStrings(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, pkg: String): List<String> =
        omniboxTracker.extractBrowserStrings(rootNode, event, pkg)

    fun isIncognitoOrPrivateTab(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, packageName: String): Boolean =
        incognitoDetector.isIncognitoOrPrivateTab(rootNode, event, packageName)
}
