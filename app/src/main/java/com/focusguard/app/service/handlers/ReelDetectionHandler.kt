package com.focusguard.app.service.handlers

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.ReelBlocker
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.isShortFormContentBlocked
import javax.inject.Inject
import javax.inject.Singleton

data class ReelBlockAction(
    val targetLabel: String,
    val targetDesc: String
)

@Singleton
class ReelDetectionHandler @Inject constructor(
    private val reelBlocker: ReelBlocker,
    private val repository: FocusGuardRepository
) {
    fun isTargetPackage(pkg: String): Boolean = reelBlocker.isTargetPackage(pkg)

    fun isReelBlockedForPkg(pkg: String): Boolean = repository.isShortFormContentBlocked(pkg)

    fun detectReelOrShort(
        e: AccessibilityEvent?,
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        isDebounceElapsed: Boolean,
        isOverlayShowing: Boolean
    ): ReelBlockAction? {
        if (!reelBlocker.isTargetPackage(pkg)) return null

        val isPlatformBlocked = isReelBlockedForPkg(pkg)
        if (!isPlatformBlocked) return null

        if (reelBlocker.isReelsOrShortsDetected(e, rootNode, pkg)) {
            if (!isOverlayShowing && isDebounceElapsed) {
                val targetLabel = when (pkg) {
                    ReelBlocker.INSTAGRAM_PACKAGE -> "Instagram Reels"
                    ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> "Facebook Reels"
                    else -> "YouTube Shorts"
                }
                val targetDesc = when (pkg) {
                    ReelBlocker.INSTAGRAM_PACKAGE -> "Instagram Reels is blocked to protect your focus. Feed, profile, and direct messages remain accessible."
                    ReelBlocker.FACEBOOK_PACKAGE, ReelBlocker.FACEBOOK_LITE_PACKAGE -> "Facebook Reels is blocked to protect your focus. Feed, groups, and messages remain accessible."
                    else -> "YouTube Shorts is blocked to protect your focus. Normal videos and search remain accessible."
                }
                return ReelBlockAction(
                    targetLabel = targetLabel,
                    targetDesc = targetDesc
                )
            }
        }
        return null
    }
}
