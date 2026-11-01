package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.ReelBlocker
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.tracker.BrowserHeartbeatTracker
import com.focusguard.app.tracker.BrowserUrlTracker
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates browser URL, YouTube, Reels, and keyword background heartbeat sweeps
 * when foreground package transitions occur.
 *
 * Rules:
 * - Stateless @Singleton: Holds no per-service state or Context references.
 * - Exact logic, eager evaluation order, strings, and logs preserved from FocusGuardAccessibilityService.
 */
@Singleton
class HeartbeatCoordinator @Inject constructor(
    private val browserUrlTracker: BrowserUrlTracker,
    private val reelBlocker: ReelBlocker,
    private val repository: FocusGuardRepository,
    private val browserHeartbeatTracker: BrowserHeartbeatTracker
) {

    /**
     * Evaluates package capabilities and starts or stops the heartbeat tracker.
     * Evaluated only upon package change.
     */
    fun updateHeartbeat(
        pkg: String,
        scope: CoroutineScope,
        isOverlayShowing: () -> Boolean,
        rootProvider: () -> AccessibilityNodeInfo?,
        getCachedAppLabel: (String) -> String,
        onTriggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit
    ) {
        val isBrowser = browserUrlTracker.isSupportedBrowser(pkg)
        val isYt = browserUrlTracker.isYouTubePackage(pkg)
        val isReelTarget = reelBlocker.isTargetPackage(pkg)
        val isReelBlockedForPkg = when (pkg) {
            ReelBlocker.INSTAGRAM_PACKAGE -> repository.isFastInstagramReelsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
            ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> repository.isFastFacebookReelsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
            ReelBlocker.YOUTUBE_PACKAGE -> repository.isFastYouTubeShortsBlockEnabled() || repository.isFastShortsReelsBlockEnabled()
            else -> repository.isFastShortsReelsBlockEnabled()
        }
        val hasKeywords = repository.getFastBlockedKeywords().isNotEmpty()
        val shouldStartHeartbeat = (isBrowser || isYt || (isReelTarget && isReelBlockedForPkg) || hasKeywords) &&
                !browserUrlTracker.isMessagingOrChatPackage(pkg)

        if (shouldStartHeartbeat) {
            browserHeartbeatTracker.startHeartbeat(
                scope = scope,
                packageName = pkg,
                isOverlayShowing = isOverlayShowing,
                rootProvider = rootProvider,
                onViolation = { sourcePkg, matchedKeyword, _ ->
                    val appLabel = getCachedAppLabel(sourcePkg)
                    val isYtViol = browserUrlTracker.isYouTubePackage(sourcePkg)
                    val isBrowserViol = browserUrlTracker.isSupportedBrowser(sourcePkg)

                    if (matchedKeyword == "YouTube Shorts" || matchedKeyword == "Instagram Reels" || matchedKeyword == "Facebook Reels") {
                        Log.i(TAG, "Heartbeat caught active short-form reel in $sourcePkg -> displaying overlay and closing app")
                        val desc = reelDescription(matchedKeyword)
                        onTriggerBlock(
                            sourcePkg,
                            "$matchedKeyword Blocked",
                            OverlayBlockType.SHORT_OR_REEL_BLOCK,
                            matchedKeyword,
                            desc
                        )
                        return@startHeartbeat
                    }

                    val (blockReason, blockDesc) = buildKeywordViolationText(
                        matchedKeyword = matchedKeyword,
                        appLabel = appLabel,
                        isYtViol = isYtViol,
                        isBrowserViol = isBrowserViol
                    )
                    onTriggerBlock(
                        sourcePkg,
                        blockReason,
                        OverlayBlockType.KEYWORD_BLOCK,
                        matchedKeyword,
                        blockDesc
                    )
                }
            )
        } else {
            browserHeartbeatTracker.stopHeartbeat()
        }
    }

    companion object {
        private const val TAG = "FocusGuardService"

        /**
         * Builds the description text for social reel/short violations.
         */
        fun reelDescription(matchedKeyword: String): String = when (matchedKeyword) {
            "Instagram Reels" -> "Instagram Reels is blocked to protect your focus. Feed, profile, and direct messages remain accessible."
            "Facebook Reels" -> "Facebook Reels is blocked to protect your focus. Feed, groups, and messages remain accessible."
            else -> "YouTube Shorts is blocked to protect your focus. Normal videos and search remain accessible."
        }

        /**
         * Builds the reason and description pair for keyword violations across YouTube, browsers, and general apps.
         */
        fun buildKeywordViolationText(
            matchedKeyword: String,
            appLabel: String,
            isYtViol: Boolean,
            isBrowserViol: Boolean
        ): Pair<String, String> {
            val blockReason = when {
                isYtViol -> "YouTube Search Blocked: '$matchedKeyword'"
                isBrowserViol -> "Keyword Blocked: '$matchedKeyword'"
                else -> "Keyword Blocked in $appLabel: '$matchedKeyword'"
            }
            val blockDesc = when {
                isYtViol -> "Searching for '$matchedKeyword' is blocked in YouTube to protect your focus."
                isBrowserViol -> "Access to '$matchedKeyword' is blocked across all browsers."
                else -> "Searching for or typing '$matchedKeyword' is blocked in $appLabel."
            }
            return Pair(blockReason, blockDesc)
        }
    }
}

