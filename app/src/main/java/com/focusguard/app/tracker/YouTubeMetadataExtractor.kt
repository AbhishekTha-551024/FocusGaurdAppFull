package com.focusguard.app.tracker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTubeMetadataExtractor
 * ========================
 * High-performance, zero-leak extractor for YouTube video titles, channel names, descriptions,
 * search queries, search suggestions, and voice input results.
 */
@Singleton
class YouTubeMetadataExtractor @Inject constructor(
    private val chatQueryExtractor: ChatQueryExtractor
) {

    companion object {
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
    }

    fun extractYouTubeMetadata(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent? = null
    ): List<BrowserUrlTracker.YouTubeContentItem> {
        val results = mutableListOf<BrowserUrlTracker.YouTubeContentItem>()

        // 1. Fast-Path: real-time typed text in active input event
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val eventText = event.text.joinToString(" ").trim()
            if (eventText.isNotBlank()) {
                results.add(BrowserUrlTracker.YouTubeContentItem(eventText, BrowserUrlTracker.YouTubeContentType.SEARCH_QUERY))
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
                            results.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.SEARCH_QUERY))
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
                            results.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.CHANNEL_NAME))
                        }
                        if (!desc.isNullOrBlank() && desc != text) {
                            results.add(BrowserUrlTracker.YouTubeContentItem(desc, BrowserUrlTracker.YouTubeContentType.CHANNEL_NAME))
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
                            results.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.VIDEO_TITLE))
                        }
                        if (!desc.isNullOrBlank() && desc != text) {
                            results.add(BrowserUrlTracker.YouTubeContentItem(desc, BrowserUrlTracker.YouTubeContentType.VIDEO_TITLE))
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
                            results.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.DESCRIPTION))
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
                            results.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.SEARCH_SUGGESTION))
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
                            results.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.SEARCH_QUERY))
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
        }

        // 8. Universal On-Screen Visible Text Scanner (Catches Channel Name, Title, and Captions even if View-IDs are obfuscated)
        collectVisibleYouTubeTexts(rootNode, results, maxDepth = 6, currentDepth = 0)

        return results
    }

    private fun collectVisibleYouTubeTexts(
        node: AccessibilityNodeInfo?,
        outList: MutableList<BrowserUrlTracker.YouTubeContentItem>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (node == null || currentDepth > maxDepth) return

        try {
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrBlank() && text.length >= 2) {
                outList.add(BrowserUrlTracker.YouTubeContentItem(text, BrowserUrlTracker.YouTubeContentType.VIDEO_TITLE))
            }
            if (!desc.isNullOrBlank() && desc.length >= 2 && desc != text) {
                outList.add(BrowserUrlTracker.YouTubeContentItem(desc, BrowserUrlTracker.YouTubeContentType.CHANNEL_NAME))
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                collectVisibleYouTubeTexts(child, outList, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
            }
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }
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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
        }

        return chatQueryExtractor.searchDynamicEditableOrFocused(rootNode, maxDepth = 4, currentDepth = 0)
    }
}
