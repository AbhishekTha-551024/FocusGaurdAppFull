package com.focusguard.app.service.handlers

import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.overlay.manager.OverlayBlockType

/**
 * Host interface providing service-bound capabilities to [TargetAppPipeline].
 * Implemented directly by [com.focusguard.app.service.FocusGuardAccessibilityService].
 */
interface TargetAppHost {
    fun currentRoot(): AccessibilityNodeInfo?
    fun cachedAppLabel(pkg: String): String
    fun triggerBlock(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType = OverlayBlockType.UNINSTALL_ATTEMPT,
        targetName: String = "FocusGuard Protection",
        description: String = "Action is protected by friction delay."
    )
    fun forceCloseApp(pkg: String)
    fun isOverlayShowing(): Boolean
}
