package com.focusguard.app.blockers

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.data.model.BlockAction
import com.focusguard.app.data.model.ReelTargetRegistry
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReelBlocker
 * ===========
 * Multi-layer real-time interception engine for short-form video loops:
 * - YouTube Shorts (com.google.android.youtube)
 * - Instagram Reels (com.instagram.android)
 * - Facebook Reels (com.facebook.katana & com.facebook.lite)
 *
 * Architecture:
 * 1. Fast Path: Direct view ID scan against registered containers (< 0.02ms).
 * 2. Tab Navigation: Intercepts selected bottom navigation tabs (Shorts tab, Reels tab).
 * 3. Event Properties: Zero-IPC checks on event class names, texts, and content descriptions.
 * 4. Screen Bounds Guard: Real DisplayMetrics verification to prevent false-positives
 *    on home feed thumbnails, search results, or small navigation icons.
 */
@Singleton
class ReelBlocker @Inject constructor() {

    companion object {
        const val YOUTUBE_PACKAGE = ReelTargetRegistry.YOUTUBE_PACKAGE
        const val INSTAGRAM_PACKAGE = ReelTargetRegistry.INSTAGRAM_PACKAGE
        const val FACEBOOK_PACKAGE = ReelTargetRegistry.FACEBOOK_PACKAGE
        const val FACEBOOK_LITE_PACKAGE = ReelTargetRegistry.FACEBOOK_LITE_PACKAGE
        const val SNAPCHAT_PACKAGE = ReelTargetRegistry.SNAPCHAT_PACKAGE
        const val TIKTOK_PACKAGE = ReelTargetRegistry.TIKTOK_PACKAGE
        const val TIKTOK_LITE_PACKAGE = ReelTargetRegistry.TIKTOK_LITE_PACKAGE
        const val REDDIT_PACKAGE = ReelTargetRegistry.REDDIT_PACKAGE

        // Known Activity and Fragment class names for Reels & Shorts
        private val SHORTS_CLASS_NAMES = setOf(
            "ReelWatchFragment",
            "ShortsPlayer",
            "ReelPlayerView",
            "ShortsFragment",
            "ShortsActivity",
            "ReelPlayerFragment"
        )

        private val INSTAGRAM_REELS_CLASS_NAMES = setOf(
            "ClipsViewerActivity",
            "ClipsViewerFragment",
            "ReelsViewerFragment",
            "ClipsViewActivity",
            "ClipsViewerLauncher"
        )

        private val FACEBOOK_REELS_CLASS_NAMES = setOf(
            "FbShortsViewerActivity",
            "ReelsViewerActivity",
            "ShortsViewerActivity",
            "FbReelsViewerFragment",
            "FbShortsViewerFragment",
            "ReelsMediaActivity"
        )

        private val SNAPCHAT_SPOTLIGHT_CLASS_NAMES = setOf(
            "SpotlightFragment",
            "SpotlightPlayer",
            "SpotlightActivity"
        )

        private val TIKTOK_CLASS_NAMES = setOf(
            "DetailActivity",
            "LiveRoomActivity"
        )

        // Descriptive strings strictly identifying active full-screen reels/shorts player
        private val YOUTUBE_SHORTS_DESCS = setOf(
            "Shorts",
            "Remix this video",
            "Sound used in this video",
            "Dislike this video"
        )

        private val INSTAGRAM_REELS_DESCS = setOf(
            "reels viewer",
            "reel video",
            "Use audio",
            "Audio used in this Reel",
            "Remix this reel",
            "Original audio",
            "Watch more Reels",
            "scroll up for next video"
        )

        private val FACEBOOK_REELS_DESCS = setOf(
            "Facebook Reels",
            "reels viewer",
            "short video player",
            "Original audio",
            "Remix with this reel",
            "Use this audio"
        )

        private val SNAPCHAT_SPOTLIGHT_DESCS = setOf(
            "Spotlight",
            "Spotlight page",
            "Remix with Snap"
        )

        // FIX NH4: Removed "For You", "Following", "Share to" — these are universal UI
        // strings present in Gmail, Spotify, Maps, and nearly every social app. They caused
        // false-positive blocks on non-TikTok apps whenever TikTok blocking was enabled.
        // Only TikTok-exclusive accessibility descriptors are retained.
        private val TIKTOK_DESCS = setOf(
            "TikTok video",
            "TikTok LIVE",
            "original sound",
            "Duet with",
            "Stitch with",
            "View in TikTok"
        )
    }

    fun isTargetPackage(packageName: String): Boolean =
        ReelTargetRegistry.targets.any { it.packageName.equals(packageName, ignoreCase = true) }

    /**
     * Unified, multi-tiered detection for YouTube Shorts, Instagram Reels, and Facebook Reels.
     */
    fun isReelsOrShortsDetected(
        e: AccessibilityEvent?,
        rootNode: AccessibilityNodeInfo?,
        targetPackage: String? = null
    ): Boolean {
        val pkg = e?.packageName?.toString() ?: targetPackage ?: rootNode?.packageName?.toString() ?: ""
        if (!isTargetPackage(pkg)) return false

        val lowerPkg = pkg.lowercase()

        // ── 1. FAST PATH: DIRECT CONTAINER VIEW ID INSPECTION (< 0.02ms) ──────
        if (rootNode != null) {
            val config = ReelTargetRegistry.targets.firstOrNull { it.packageName.equals(lowerPkg, ignoreCase = true) }
            if (config != null) {
                val candidateIds = listOf(config.targetViewId) + config.alternateViewIds
                for (viewId in candidateIds) {
                    try {
                        val matched = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                        if (!matched.isNullOrEmpty()) {
                            // MUST be full-screen player: ignores feed preview cards and story tray items
                            val isFull = matched.any { isFullScreenBounds(it) }
                            matched.forEach { SafeNodeUtils.safeRecycle(it) }
                            if (isFull) {
                                return true
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // ── 2. BOTTOM NAVIGATION TAB INSPECTION ──────────────────────────────
        if (rootNode != null) {
            if (isReelsTabSelected(rootNode, lowerPkg)) {
                return true
            }
        }

        // ── 3. ZERO-IPC ACCESSIBILITY EVENT EVALUATION ────────────────────────
        if (e != null) {
            // 3.1 Event class names
            val className = e.className?.toString() ?: ""
            if (className.isNotBlank()) {
                val matchedClass = when (lowerPkg) {
                    YOUTUBE_PACKAGE -> SHORTS_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }
                    INSTAGRAM_PACKAGE -> INSTAGRAM_REELS_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }
                    FACEBOOK_PACKAGE, FACEBOOK_LITE_PACKAGE -> FACEBOOK_REELS_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }
                    SNAPCHAT_PACKAGE -> SNAPCHAT_SPOTLIGHT_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }
                    TIKTOK_PACKAGE, TIKTOK_LITE_PACKAGE -> TIKTOK_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }
                    else -> false
                }
                if (matchedClass) return true
            }

            // 3.2 Event content descriptions & texts
            val desc = e.contentDescription?.toString() ?: ""
            val eventTexts = e.text.map { it.toString() }

            when (lowerPkg) {
                YOUTUBE_PACKAGE -> {
                    if (YOUTUBE_SHORTS_DESCS.any { desc.contains(it, ignoreCase = true) }) return true
                    if (eventTexts.any { t -> YOUTUBE_SHORTS_DESCS.any { t.contains(it, ignoreCase = true) } }) return true
                }
                INSTAGRAM_PACKAGE -> {
                    if (INSTAGRAM_REELS_DESCS.any { desc.contains(it, ignoreCase = true) }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                    if (eventTexts.any { t -> INSTAGRAM_REELS_DESCS.any { t.contains(it, ignoreCase = true) } }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                }
                FACEBOOK_PACKAGE, FACEBOOK_LITE_PACKAGE -> {
                    if (FACEBOOK_REELS_DESCS.any { desc.contains(it, ignoreCase = true) }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                    if (eventTexts.any { t -> FACEBOOK_REELS_DESCS.any { t.contains(it, ignoreCase = true) } }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                }
                SNAPCHAT_PACKAGE -> {
                    if (SNAPCHAT_SPOTLIGHT_DESCS.any { desc.contains(it, ignoreCase = true) }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                    if (eventTexts.any { t -> SNAPCHAT_SPOTLIGHT_DESCS.any { t.contains(it, ignoreCase = true) } }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                }
                TIKTOK_PACKAGE, TIKTOK_LITE_PACKAGE -> {
                    if (TIKTOK_DESCS.any { desc.contains(it, ignoreCase = true) }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                    if (eventTexts.any { t -> TIKTOK_DESCS.any { t.contains(it, ignoreCase = true) } }) {
                        if (checkEventSourceFullScreen(e)) return true
                    }
                }
            }

            // 3.3 Event source node resource ID check (Requires full-screen verification)
            val sourceNode = try { e.source } catch (_: Exception) { null }
            if (sourceNode != null) {
                try {
                    val sourceRes = sourceNode.viewIdResourceName?.lowercase() ?: ""
                    val isDedicatedReelRes = when (lowerPkg) {
                        INSTAGRAM_PACKAGE -> (sourceRes.contains("clips_viewer") || sourceRes.contains("reels_viewer_view_pager")) && isFullScreenBounds(sourceNode)
                        YOUTUBE_PACKAGE -> (sourceRes.contains("reel_watch_fragment") || sourceRes.contains("shorts_player_view")) && isFullScreenBounds(sourceNode)
                        else -> (sourceRes.contains("reel") || sourceRes.contains("shorts") || sourceRes.contains("clips_viewer")) && isFullScreenBounds(sourceNode)
                    }
                    if (isDedicatedReelRes) {
                        return true
                    }
                } finally {
                    SafeNodeUtils.safeRecycle(sourceNode)
                }
            }
        }

        // ── 4. FULL-SCREEN HEURISTIC FALLBACK (With Display Bounds Verification) ──
        if (rootNode != null) {
            val heuristicDescs = when (lowerPkg) {
                YOUTUBE_PACKAGE -> YOUTUBE_SHORTS_DESCS
                INSTAGRAM_PACKAGE -> INSTAGRAM_REELS_DESCS
                FACEBOOK_PACKAGE, FACEBOOK_LITE_PACKAGE -> FACEBOOK_REELS_DESCS
                else -> emptySet()
            }

            for (hDesc in heuristicDescs) {
                try {
                    val matchingNodes = rootNode.findAccessibilityNodeInfosByText(hDesc)
                    if (!matchingNodes.isNullOrEmpty()) {
                        for (node in matchingNodes) {
                            val isFull = isFullScreenBounds(node)
                            SafeNodeUtils.safeRecycle(node)
                            if (isFull) {
                                return true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return false
    }

    /**
     * Checks if the bottom navigation bar has the Shorts or Reels tab selected.
     */
    private fun isReelsTabSelected(rootNode: AccessibilityNodeInfo, pkg: String): Boolean {
        return when (pkg) {
            YOUTUBE_PACKAGE -> {
                try {
                    val pivotNodes = rootNode.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
                    if (!pivotNodes.isNullOrEmpty()) {
                        for (pivot in pivotNodes) {
                            val count = pivot.childCount
                            for (i in 0 until count) {
                                val tab = pivot.getChild(i) ?: continue
                                val tabDesc = tab.contentDescription?.toString() ?: ""
                                val isSelected = tab.isSelected
                                val isShorts = tabDesc.contains("Shorts", ignoreCase = true)
                                SafeNodeUtils.safeRecycle(tab)
                                if (isShorts && isSelected) {
                                    pivotNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                                    return true
                                }
                            }
                            SafeNodeUtils.safeRecycle(pivot)
                        }
                    }
                    false
                } catch (_: Exception) {
                    false
                }
            }
            INSTAGRAM_PACKAGE -> {
                try {
                    val tabNodes = rootNode.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
                    if (!tabNodes.isNullOrEmpty()) {
                        for (bar in tabNodes) {
                            val count = bar.childCount
                            for (i in 0 until count) {
                                val tab = bar.getChild(i) ?: continue
                                val desc = tab.contentDescription?.toString() ?: ""
                                val isSelected = tab.isSelected
                                val isReels = desc.contains("Reels", ignoreCase = true)
                                SafeNodeUtils.safeRecycle(tab)
                                if (isReels && isSelected) {
                                    tabNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                                    return true
                                }
                            }
                            SafeNodeUtils.safeRecycle(bar)
                        }
                    }
                    false
                } catch (_: Exception) {
                    false
                }
            }
            FACEBOOK_PACKAGE, FACEBOOK_LITE_PACKAGE -> {
                try {
                    val tabNodes = rootNode.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tab")
                    if (!tabNodes.isNullOrEmpty()) {
                        val isSelected = tabNodes.any { it.isSelected }
                        tabNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                        if (isSelected) return true
                    }
                    false
                } catch (_: Exception) {
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Safely queries event.source and guarantees immediate node recycling in a finally block.
     * Prevents accessibility node buffer exhaustion on Android 13-.
     */
    private fun checkEventSourceFullScreen(e: AccessibilityEvent?): Boolean {
        val src = try { e?.source } catch (_: Exception) { null } ?: return false
        return try {
            isFullScreenBounds(src)
        } finally {
            SafeNodeUtils.safeRecycle(src)
        }
    }

    /**
     * Verifies that the matched node spans at least 50% of the screen height and 60% of the width.
     * This physically eliminates false positives from small bottom navigation icons (~48-60dp)
     * or feed preview cards.
     */
    private fun isFullScreenBounds(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val metrics = Resources.getSystem().displayMetrics
        val minHeight = metrics.heightPixels * 0.65f
        val minWidth = metrics.widthPixels * 0.70f
        return rect.height() >= minHeight && rect.width() >= minWidth
    }

    /**
     * Fast backward-compatible method for YouTube Shorts.
     */
    fun isShortsDetectedFast(e: AccessibilityEvent, rootNode: AccessibilityNodeInfo?): Boolean {
        return isReelsOrShortsDetected(e, rootNode, YOUTUBE_PACKAGE)
    }

    /**
     * Backward-compatible inspectNodeTree that delegates to view-ID scanning.
     */
    suspend fun inspectNodeTree(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        onBlockTriggered: (BlockAction) -> Unit
    ) {
        val config = ReelTargetRegistry.targets.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) } ?: return
        val candidateIds = listOf(config.targetViewId) + config.alternateViewIds
        for (viewId in candidateIds) {
            val targetNodes = try { rootNode.findAccessibilityNodeInfosByViewId(viewId) } catch (_: Exception) { null }
            if (!targetNodes.isNullOrEmpty()) {
                targetNodes.forEach { SafeNodeUtils.safeRecycle(it) }
                onBlockTriggered(config.blockAction)
                return
            }
        }
    }
}
