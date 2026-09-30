package com.focusguard.app.tracker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BrowserUrlTracker
 * =================
 * High-performance, zero-leak URL & search-query extractor for supported mobile browsers,
 * YouTube native app, and universal 3rd-party app search bars (e.g. Pinterest, Instagram).
 *
 * Employs view-ID fast lookup first to avoid full node-tree traversals, with a bounded
 * fallback for customized OEM / updated browser versions.
 *
 * Strictly uses SafeNodeUtils to avoid Android 14+ double-recycle crashes and memory leaks.
 */
@Singleton
class BrowserUrlTracker @Inject constructor() {

    private val browserResourceIdMap: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text"
        ),
        "org.mozilla.firefox" to listOf(
            "org.mozilla.firefox:id/url_bar_title",
            "org.mozilla.firefox:id/toolbar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_edit_url_view"
        ),
        "com.microsoft.emmx" to listOf(
            "com.microsoft.emmx:id/url_bar",
            "com.microsoft.emmx:id/search_box_text"
        ),
        "com.brave.browser" to listOf(
            "com.brave.browser:id/url_bar",
            "com.brave.browser:id/search_box_text"
        ),
        "com.sec.android.app.sbrowser" to listOf(
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.sec.android.app.sbrowser:id/url_bar"
        ),
        "com.opera.browser" to listOf(
            "com.opera.browser:id/url_field"
        ),
        "com.opera.mini.native" to listOf(
            "com.opera.mini.native:id/url_field"
        ),
        "com.vivaldi.browser" to listOf(
            "com.vivaldi.browser:id/url_bar"
        ),
        "com.duckduckgo.mobile.android" to listOf(
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.duckduckgo.mobile.android:id/search_edit_text"
        ),
        "com.kiwibrowser.browser" to listOf(
            "com.kiwibrowser.browser:id/url_bar"
        ),
        "com.heytap.browser" to listOf(
            "com.heytap.browser:id/search_hint",
            "com.heytap.browser:id/url_bar",
            "com.heytap.browser:id/address_bar"
        ),
        "com.mi.globalbrowser" to listOf(
            "com.mi.globalbrowser:id/url_bar"
        ),
        "org.mozilla.focus" to listOf(
            "org.mozilla.focus:id/display_url",
            "org.mozilla.focus:id/urlview"
        ),
        "com.opera.gx" to listOf(
            "com.opera.gx:id/url_field",
            "com.opera.gx:id/search_edit_text"
        ),
        "com.aloha.browser" to listOf(
            "com.aloha.browser:id/url_edit_text",
            "com.aloha.browser:id/search_view"
        ),
        "org.torproject.torbrowser" to listOf(
            "org.torproject.torbrowser:id/url_bar_title",
            "org.torproject.torbrowser:id/toolbar"
        ),
        "mark.via.gp" to listOf(
            "mark.via.gp:id/b7",
            "mark.via.gp:id/search_box"
        ),
        "com.UCMobile.intl" to listOf(
            "com.UCMobile.intl:id/address_bar",
            "com.UCMobile.intl:id/url_bar"
        ),
        "org.mozilla.rocket" to listOf(
            "org.mozilla.rocket:id/url_bar_title",
            "org.mozilla.rocket:id/display_url",
            "org.mozilla.rocket:id/url_edit_text"
        ),
        "org.torproject.torbrowser_alpha" to listOf(
            "org.torproject.torbrowser_alpha:id/url_bar_title",
            "org.torproject.torbrowser_alpha:id/toolbar"
        ),
        "com.nationaledtech.spinbrowser" to listOf(
            "com.nationaledtech.spinbrowser:id/url_bar",
            "com.nationaledtech.spinbrowser:id/address_bar",
            "com.nationaledtech.spinbrowser:id/url",
            "com.nationaledtech.spinbrowser:id/search_box"
        ),
        "com.nationaledtech.spinbrowserplus" to listOf(
            "com.nationaledtech.spinbrowserplus:id/url_bar",
            "com.nationaledtech.spinbrowserplus:id/address_bar",
            "com.nationaledtech.spinbrowserplus:id/url",
            "com.nationaledtech.spinbrowserplus:id/search_box"
        ),
        "com.ecosia.android" to listOf(
            "com.ecosia.android:id/url_bar",
            "com.ecosia.android:id/search_box_text"
        ),
        "com.hsv.freeadblockerbrowser" to listOf(
            "com.hsv.freeadblockerbrowser:id/url_bar",
            "com.hsv.freeadblockerbrowser:id/search_box_text",
            "com.hsv.freeadblockerbrowser:id/address_bar"
        ),
        "idm.internet.download.manager" to listOf(
            "idm.internet.download.manager:id/address_bar",
            "idm.internet.download.manager:id/url_bar",
            "idm.internet.download.manager:id/search_box"
        ),
        "idm.internet.download.manager.plus" to listOf(
            "idm.internet.download.manager.plus:id/address_bar",
            "idm.internet.download.manager.plus:id/url_bar",
            "idm.internet.download.manager.plus:id/search_box"
        ),
        "idm.internet.download.manager.lite" to listOf(
            "idm.internet.download.manager.lite:id/address_bar",
            "idm.internet.download.manager.lite:id/url_bar",
            "idm.internet.download.manager.lite:id/search_box"
        ),
        "com.instantbits.cast.webvideo" to listOf(
            "com.instantbits.cast.webvideo:id/address_bar",
            "com.instantbits.cast.webvideo:id/url",
            "com.instantbits.cast.webvideo:id/search_view",
            "com.instantbits.cast.webvideo:id/web_view_url"
        ),
        "com.vivaldi.browser.snapshot" to listOf(
            "com.vivaldi.browser.snapshot:id/url_bar"
        )
    )

    companion object {
        private val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "app.revanced.android.youtube"
        )
        private val YOUTUBE_SEARCH_IDS = listOf(
            "com.google.android.youtube:id/search_edit_text",
            "com.google.android.youtube:id/search_query",
            "com.google.android.youtube:id/query"
        )
        private val YOUTUBE_CHANNEL_IDS = listOf(
            "com.google.android.youtube:id/channel_name",
            "com.google.android.youtube:id/owner_name",
            "com.google.android.youtube:id/creator_name",
            "com.google.android.youtube:id/uploader_name",
            "com.google.android.youtube:id/channel_title",
            "com.google.android.youtube:id/byline",
            "com.google.android.youtube:id/reel_channel_name",
            "com.google.android.youtube:id/reel_player_channel_name",
            "com.google.android.youtube:id/reel_player_subtitle"
        )
        private val YOUTUBE_TITLE_IDS = listOf(
            "com.google.android.youtube:id/video_title",
            "com.google.android.youtube:id/title",
            "com.google.android.youtube:id/watch_title",
            "com.google.android.youtube:id/player_video_title",
            "com.google.android.youtube:id/reel_player_title",
            "com.google.android.youtube:id/shorts_title"
        )
        private val YOUTUBE_DESCRIPTION_IDS = listOf(
            "com.google.android.youtube:id/description",
            "com.google.android.youtube:id/expandable_description",
            "com.google.android.youtube:id/description_text",
            "com.google.android.youtube:id/full_description",
            "com.google.android.youtube:id/video_description"
        )
        private val YOUTUBE_SUGGESTION_IDS = listOf(
            "com.google.android.youtube:id/search_suggestion_text",
            "com.google.android.youtube:id/search_type_ahead_text",
            "com.google.android.youtube:id/query_builder_text",
            "com.google.android.youtube:id/search_suggestion_item",
            "com.google.android.youtube:id/text"
        )
        private val YOUTUBE_VOICE_IDS = listOf(
            "com.google.android.youtube:id/voice_search_result",
            "com.google.android.youtube:id/transcription",
            "com.google.android.youtube:id/voice_input_container"
        )
        private val BROWSER_SUGGESTION_IDS = listOf(
            "com.android.chrome:id/line_1",
            "com.android.chrome:id/line_2",
            "com.android.chrome:id/suggestion_text",
            "com.android.chrome:id/omnibar_results_container",
            "org.mozilla.firefox:id/suggestion_title",
            "org.mozilla.firefox:id/suggestion_url",
            "com.microsoft.emmx:id/suggestion_title",
            "com.microsoft.emmx:id/suggestion_url",
            "com.brave.browser:id/line_1",
            "com.brave.browser:id/line_2",
            "com.duckduckgo.mobile.android:id/suggestion",
            "com.sec.android.app.sbrowser:id/suggestion_text"
        )

        val VERIFIED_SUPPORTED_BROWSERS = setOf(
            // Chrome
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            // Firefox & Firefox Lite
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.rocket",
            // Microsoft Edge
            "com.microsoft.emmx",
            // Brave
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.brave.browser_nightly",
            // Samsung Internet
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            // Opera & Opera Mini & Opera GX
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.touch",
            "com.opera.gx",
            // DuckDuckGo
            "com.duckduckgo.mobile.android",
            // Vivaldi
            "com.vivaldi.browser",
            "com.vivaldi.browser.snapshot",
            // Tor Browser
            "org.torproject.torbrowser",
            "org.torproject.torbrowser_alpha",
            // Spin Safe Browser
            "com.nationaledtech.spinbrowser",
            "com.nationaledtech.spinbrowserplus",
            // Ecosia
            "com.ecosia.android",
            // Free Adblocker Browser
            "com.hsv.freeadblockerbrowser",
            // 1DM / 1DM+ Browser
            "idm.internet.download.manager",
            "idm.internet.download.manager.plus",
            "idm.internet.download.manager.lite",
            // Web Video Cast
            "com.instantbits.cast.webvideo"
        )

        val KNOWN_UNSUPPORTED_BROWSERS = setOf(
            "org.torproject.android",
            "com.cloudmosa.puffinfree",
            "com.cloudmosa.puffin",
            "org.mozilla.focus",
            "com.aloha.browser",
            "mark.via.gp",
            "com.ucmobile.intl",
            "com.uc.browser.en",
            "com.transsion.phoenix",
            "com.mi.globalbrowser",
            "com.heytap.browser",
            "com.vivo.browser",
            "com.kiwibrowser.browser",
            "mobi.mgeek.tunnybrowser",
            "com.ghostery.android.ghostery",
            "com.qwant.liberty",
            "org.bromite.bromite",
            "de.baumann.browser",
            "com.stoutner.privacybrowser.standard",
            "acr.browser.barebones",
            "onion.torsearch",
            "com.android.browser",
            "com.z20.browser",
            "com.pure.browser",
            "com.cake.browser",
            "com.xbrowser.play",
            "com.mycompany.app.soulbrowser"
        )
    }

    private val unsupportedBrowserCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

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

    fun isSupportedBrowser(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (VERIFIED_SUPPORTED_BROWSERS.contains(lower)) return true
        if (browserResourceIdMap.containsKey(lower)) return true
        return lower.contains("browser") ||
                lower.contains("chrome") ||
                lower.contains("firefox")
    }

    fun isUnsupportedBrowser(pkg: String, context: android.content.Context? = null): Boolean {
        val lower = pkg.lowercase()
        if (lower.startsWith("com.focusguard.app")) return false
        if (VERIFIED_SUPPORTED_BROWSERS.contains(lower)) return false
        if (isYouTubePackage(lower) || isMessagingOrChatPackage(lower)) return false

        unsupportedBrowserCache[lower]?.let { return it }

        if (KNOWN_UNSUPPORTED_BROWSERS.contains(lower) ||
            lower.contains("puffin") ||
            lower.contains("aloha.browser") ||
            lower.contains("soulbrowser") ||
            lower.contains("xbrowser") ||
            lower.contains("pure.browser") ||
            lower.contains("dolphin") ||
            lower.contains("ghostery") ||
            (lower.contains("browser") && !VERIFIED_SUPPORTED_BROWSERS.contains(lower))
        ) {
            unsupportedBrowserCache[lower] = true
            return true
        }

        if (context != null) {
            try {
                val pm = context.packageManager
                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
                    addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                    setPackage(lower)
                }
                val matches = pm.queryIntentActivities(webIntent, 0)
                val isBrowser = matches.isNotEmpty()
                unsupportedBrowserCache[lower] = isBrowser
                return isBrowser
            } catch (_: Exception) {
                // ignore
            }
        }

        return false
    }

    fun isYouTubePackage(pkg: String): Boolean {
        return YOUTUBE_PACKAGES.contains(pkg) || pkg.contains("youtube", ignoreCase = true)
    }

    fun isMessagingOrChatPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.whatsapp" ||
                lower == "com.whatsapp.w4b" ||
                lower.contains("telegram") ||
                lower.contains("signal") ||
                lower == "com.google.android.apps.messaging" ||
                lower == "com.samsung.android.messaging" ||
                lower.contains("mms") ||
                lower.contains("sms") ||
                lower == "com.facebook.orca"
    }

    fun extractUniversalSearchOrInputQuery(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): String? {
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val textList = event.text
            if (!textList.isNullOrEmpty()) {
                val joined = textList.joinToString(" ").trim()
                if (joined.isNotBlank()) {
                    return joined
                }
            }
            try {
                val sourceText = event.source?.text?.toString()?.trim()
                if (!sourceText.isNullOrBlank()) {
                    return sourceText
                }
            } catch (_: Throwable) {}
        }

        if (rootNode == null) return null

        try {
            val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val fText = focused.text?.toString()?.trim()
                SafeNodeUtils.safeRecycle(focused)
                if (!fText.isNullOrBlank()) {
                    return fText
                }
            }
        } catch (_: Throwable) {}

        val universalSearchIds = listOf(
            "search_src_text",
            "search_edit_text",
            "search_input",
            "search_query",
            "query",
            "search_bar_text",
            "search_text",
            "et_search",
            "search_view"
        )
        for (resId in universalSearchIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (n in nodes) {
                        val txt = n.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(n)
                        if (!txt.isNullOrBlank()) {
                            return txt
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return searchDynamicEditableOrFocused(rootNode, maxDepth = 4, currentDepth = 0)
    }

    fun extractYouTubeMetadata(rootNode: AccessibilityNodeInfo?, event: android.view.accessibility.AccessibilityEvent? = null): List<YouTubeContentItem> {
        val results = mutableListOf<YouTubeContentItem>()

        // 1. Fast-Path: real-time typed text in active input event
        if (event != null && event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val eventText = event.text.joinToString(" ").trim()
            if (eventText.isNotBlank()) {
                results.add(YouTubeContentItem(eventText, YouTubeContentType.SEARCH_QUERY))
            }
        }

        if (rootNode == null) return results

        // 2. Active Search Bar Query
        for (resId in YOUTUBE_SEARCH_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(YouTubeContentItem(text, YouTubeContentType.SEARCH_QUERY))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. Channel / Creator / Uploader Name
        for (resId in YOUTUBE_CHANNEL_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        val desc = node.contentDescription?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(YouTubeContentItem(text, YouTubeContentType.CHANNEL_NAME))
                        }
                        if (!desc.isNullOrBlank() && desc != text) {
                            results.add(YouTubeContentItem(desc, YouTubeContentType.CHANNEL_NAME))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 4. Video Title
        for (resId in YOUTUBE_TITLE_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        val desc = node.contentDescription?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(YouTubeContentItem(text, YouTubeContentType.VIDEO_TITLE))
                        }
                        if (!desc.isNullOrBlank() && desc != text) {
                            results.add(YouTubeContentItem(desc, YouTubeContentType.VIDEO_TITLE))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 5. Video Description
        for (resId in YOUTUBE_DESCRIPTION_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(YouTubeContentItem(text, YouTubeContentType.DESCRIPTION))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 6. Search Suggestions & Autocomplete Dropdown
        for (resId in YOUTUBE_SUGGESTION_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(YouTubeContentItem(text, YouTubeContentType.SEARCH_SUGGESTION))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 7. Voice Search Query
        for (resId in YOUTUBE_VOICE_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(YouTubeContentItem(text, YouTubeContentType.SEARCH_QUERY))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 8. Universal On-Screen Visible Text Scanner (Catches Channel Name, Title, and Captions even if View-IDs are obfuscated)
        collectVisibleYouTubeTexts(rootNode, results, maxDepth = 6, currentDepth = 0)

        return results
    }

    /**
     * Deprecated: Raw C++ substring search lacked word boundaries and matched static UI buttons/menus.
     * All keyword filtering is now strictly consolidated in KeywordBlocker Trie engine.
     */
    fun checkNativeKeywordsOnScreen(rootNode: AccessibilityNodeInfo?, keywords: Set<String>): String? {
        return null
    }

    private fun collectVisibleYouTubeTexts(
        node: AccessibilityNodeInfo?,
        outList: MutableList<YouTubeContentItem>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (node == null || currentDepth > maxDepth) return

        try {
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank() && text.length >= 2) {
                outList.add(YouTubeContentItem(text, YouTubeContentType.VIDEO_TITLE))
            }
            if (!desc.isNullOrBlank() && desc.length >= 2 && desc != text) {
                outList.add(YouTubeContentItem(desc, YouTubeContentType.CHANNEL_NAME))
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                collectVisibleYouTubeTexts(child, outList, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
            }
        } catch (_: Throwable) {}
    }

    fun extractYouTubeSearchQuery(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null

        for (resId in YOUTUBE_SEARCH_IDS) {
            try {
                val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!matchingNodes.isNullOrEmpty()) {
                    var extractedText: String? = null
                    for (node in matchingNodes) {
                        if (extractedText == null) {
                            val text = node.text?.toString()?.trim()
                            if (!text.isNullOrEmpty()) {
                                extractedText = text
                            }
                        }
                        SafeNodeUtils.safeRecycle(node)
                    }
                    if (!extractedText.isNullOrEmpty()) {
                        return extractedText
                    }
                }
            } catch (_: Throwable) {}
        }

        return searchDynamicEditableOrFocused(rootNode, maxDepth = 4, currentDepth = 0)
    }

    private fun searchDynamicEditableOrFocused(node: AccessibilityNodeInfo?, maxDepth: Int, currentDepth: Int): String? {
        if (node == null || currentDepth > maxDepth || !SafeNodeUtils.isInspectable(node)) return null

        try {
            if ((node.isEditable || node.isFocused) && node.className?.contains("EditText") == true) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    return text
                }
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                val result = searchDynamicEditableOrFocused(child, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
                if (result != null) return result
            }
        } catch (_: Throwable) {}
        return null
    }

    fun extractUrlOrQuery(rootNode: AccessibilityNodeInfo?, pkg: String): String? {
        if (rootNode == null) return null

        val candidateIds = browserResourceIdMap[pkg] ?: emptyList()
        for (resId in candidateIds) {
            try {
                val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (!matchingNodes.isNullOrEmpty()) {
                    var extractedText: String? = null
                    for (node in matchingNodes) {
                        if (extractedText == null) {
                            val text = node.text?.toString()?.trim()
                            if (!text.isNullOrEmpty()) {
                                extractedText = text
                            }
                        }
                        SafeNodeUtils.safeRecycle(node)
                    }
                    if (!extractedText.isNullOrEmpty()) {
                        return extractedText
                    }
                }
            } catch (_: Throwable) {}
        }

        return boundedSearchForUrl(rootNode, maxDepth = 4, currentDepth = 0)
    }

    fun isBrowserSettingsScreen(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, pkg: String): Boolean {
        if (!isSupportedBrowser(pkg)) return false

        val className = event?.className?.toString() ?: ""
        if (className.contains("Settings", ignoreCase = true) ||
            className.contains("Preference", ignoreCase = true)) {
            return true
        }

        val winTitle = rootNode?.window?.title?.toString()?.trim() ?: ""
        if (winTitle.equals("Settings", ignoreCase = true) ||
            winTitle.contains("Chrome Settings", ignoreCase = true) ||
            winTitle.contains("Browser Settings", ignoreCase = true)) {
            return true
        }

        val settingsIds = listOf(
            "com.android.chrome:id/preferences_list",
            "com.android.chrome:id/settings_content",
            "org.mozilla.firefox:id/settings_container",
            "com.microsoft.emmx:id/settings_content"
        )
        for (id in settingsIds) {
            try {
                val nodes = rootNode?.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    for (n in nodes) SafeNodeUtils.safeRecycle(n)
                    return true
                }
            } catch (_: Throwable) {}
        }

        return false
    }

    fun extractBrowserStrings(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, pkg: String): List<String> {
        if (isBrowserSettingsScreen(rootNode, event, pkg)) {
            return emptyList()
        }

        val results = mutableListOf<String>()

        // 1. Fast-Path: Real-time event text (typed in address bar or Google search input)
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val eventText = event.text.joinToString(" ").trim()
            if (eventText.isNotBlank()) {
                results.add(eventText)
            }
        }

        // 2. Window Title (e.g. "Chrome: reddit - Google Search")
        val winTitle = rootNode?.window?.title?.toString()?.trim()
        if (!winTitle.isNullOrBlank()) {
            results.add(winTitle)
        }

        // 3. Address Bar URL / Query via View IDs
        val candidateIds = browserResourceIdMap[pkg] ?: emptyList()
        for (resId in candidateIds) {
            try {
                val matchingNodes = rootNode?.findAccessibilityNodeInfosByViewId(resId)
                if (!matchingNodes.isNullOrEmpty()) {
                    for (node in matchingNodes) {
                        val text = node.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(text)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 4. Autocomplete & search suggestion drop-downs in browsers
        for (resId in BROWSER_SUGGESTION_IDS) {
            try {
                val matchingNodes = rootNode?.findAccessibilityNodeInfosByViewId(resId)
                if (!matchingNodes.isNullOrEmpty()) {
                    for (node in matchingNodes) {
                        val text = node.text?.toString()?.trim()
                        SafeNodeUtils.safeRecycle(node)
                        if (!text.isNullOrBlank()) {
                            results.add(text)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 5. Focused Input in browser (e.g. Google Search input box on webpage)
        try {
            val focused = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val fText = focused.text?.toString()?.trim()
                SafeNodeUtils.safeRecycle(focused)
                if (!fText.isNullOrBlank()) {
                    results.add(fText)
                }
            }
        } catch (_: Throwable) {}

        // 6. On-Screen visible web text, article headings & titles (depth 6 scan for webpage DOM)
        collectVisibleBrowserTexts(rootNode, results, maxDepth = 6, currentDepth = 0)

        return results
    }

    private fun collectVisibleBrowserTexts(
        node: AccessibilityNodeInfo?,
        outList: MutableList<String>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (node == null || currentDepth > maxDepth || !SafeNodeUtils.isInspectable(node)) return

        try {
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank() && text.length >= 2) {
                outList.add(text)
            }
            if (!desc.isNullOrBlank() && desc.length >= 2 && desc != text) {
                outList.add(desc)
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                collectVisibleBrowserTexts(child, outList, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
            }
        } catch (_: Throwable) {}
    }

    private fun boundedSearchForUrl(node: AccessibilityNodeInfo?, maxDepth: Int, currentDepth: Int): String? {
        if (node == null || currentDepth > maxDepth || !SafeNodeUtils.isInspectable(node)) return null

        try {
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val text = node.text?.toString()?.trim() ?: ""

            if ((viewId.contains("url") || viewId.contains("search") || viewId.contains("address") || viewId.contains("location")) && text.isNotEmpty()) {
                return text
            }

            if (node.isEditable && text.isNotEmpty()) {
                return text
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                val result = boundedSearchForUrl(child, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
                if (result != null) return result
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Detects if the current browser window is genuinely inside an Incognito, InPrivate, Secret, or Private tab.
     * Prevents false positives during normal browsing, Google searches for "incognito", and opening 3-dots menus.
     */
    fun isIncognitoOrPrivateTab(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, packageName: String): Boolean {
        if (!isSupportedBrowser(packageName)) return false

        // 1. Browser Settings Immunity: Never block settings in Chrome, Firefox, Edge, etc.
        if (isBrowserSettingsScreen(rootNode, event, packageName)) return false

        // 2. Click Intent Intercept: User explicitly clicked "New Incognito tab" from the menu
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val eventTexts = event.text.map { it.toString() }
            val desc = event.contentDescription?.toString() ?: ""
            if (eventTexts.any { isNewIncognitoTabAction(it) } || isNewIncognitoTabAction(desc)) {
                return true
            }
        }

        // 3. Node-level inspection for genuine active incognito indicators
        if (rootNode != null) {
            return hasIncognitoIndicators(rootNode)
        }

        return false
    }

    private fun isNewIncognitoTabAction(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return lower.contains("new incognito tab") ||
                lower.contains("new inprivate tab") ||
                lower.contains("new private tab") ||
                lower.contains("turn on secret mode") ||
                lower.contains("close incognito tabs")
    }

    private fun hasIncognitoIndicators(node: AccessibilityNodeInfo?, maxDepth: Int = 5, currentDepth: Int = 0): Boolean {
        if (node == null || currentDepth > maxDepth || !SafeNodeUtils.isInspectable(node)) return false

        try {
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""

            // 1. Chrome / Edge / Firefox Toolbar Tab Switcher Button (in Incognito mode)
            // When in Incognito, Chrome's top-right tab counter button has description: "1 open incognito tab", "2 open incognito tabs", etc.
            // In normal mode, it says: "1 open tab", "2 open tabs", etc. (NO "incognito").
            if (desc.contains("open incognito tab", ignoreCase = true) ||
                desc.contains("open inprivate tab", ignoreCase = true) ||
                desc.contains("switch to standard tabs", ignoreCase = true)) {
                return true
            }

            // 2. Tab Switcher Toggle: ONLY matches if Incognito is actually SELECTED
            // In Chrome's tab switcher, incognito_toggle_button exists in normal mode too!
            // It is ONLY active when node.isSelected == true or node.isChecked == true!
            if (viewId.contains("incognito_toggle") || viewId.contains("incognito_tabs") ||
                viewId.contains("inprivate_toggle") || viewId.contains("secret_mode_toggle")) {
                if (node.isSelected || node.isChecked) {
                    return true
                }
            }

            // Tab Switcher selection via contentDescription
            if (desc.contains("incognito tabs, selected", ignoreCase = true) ||
                desc.contains("selected, incognito tabs", ignoreCase = true) ||
                desc.contains("private tab selected", ignoreCase = true) ||
                desc.contains("inprivate tab selected", ignoreCase = true)) {
                return true
            }

            // 3. Close all incognito tabs button (visible in tab switcher when incognito tabs exist)
            if (viewId.contains("close_all_incognito_tabs_button") ||
                viewId.contains("close_all_incognito") ||
                text.equals("Close all incognito tabs", ignoreCase = true) ||
                desc.equals("Close all incognito tabs", ignoreCase = true)) {
                return true
            }

            // 4. Native Browser Incognito / Private Splash / New Tab Screen Headlines
            // Note: These exact phrases appear exclusively on native private new tab pages, never on normal web pages.
            if (text.contains("You've gone incognito", ignoreCase = true) ||
                text.contains("You’ve gone incognito", ignoreCase = true) ||
                text.contains("InPrivate browsing", ignoreCase = true) ||
                text.contains("You're browsing InPrivate", ignoreCase = true) ||
                text.contains("Secret mode is on", ignoreCase = true) ||
                text.contains("Turn off Secret mode", ignoreCase = true) ||
                (text.contains("Private Browsing", ignoreCase = true) &&
                 (desc.contains("Firefox clears", ignoreCase = true) || text.contains("Firefox clears", ignoreCase = true))) ||
                (text.contains("Private Tab", ignoreCase = true) &&
                 (desc.contains("Brave doesn't remember", ignoreCase = true) || text.contains("Brave doesn't remember", ignoreCase = true)))
            ) {
                return true
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                val found = hasIncognitoIndicators(child, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
                if (found) return true
            }
        } catch (_: Throwable) {}

        return false
    }
}
