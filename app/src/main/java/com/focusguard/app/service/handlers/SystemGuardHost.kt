package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.overlay.manager.OverlayBlockType

/**
 * Host interface providing service-bound capabilities to service guard components.
 * Implemented directly by [com.focusguard.app.service.FocusGuardAccessibilityService].
 */
interface SystemGuardHost {
    fun context(): Context
    fun service(): AccessibilityService
    fun currentRoot(): AccessibilityNodeInfo?
    fun isLauncherPackage(pkg: String): Boolean
    fun isOemScanWindowActive(): Boolean
    /**
     * Sends a global accessibility action through the host's navigation-action budget.
     *
     * Guards must use this instead of `service().performGlobalAction(...)`: the framework method is
     * final and unguarded, and an unpaced burst of HOME/BACK is what makes Vivo unbind the service.
     */
    fun sendGlobalAction(action: Int): Boolean
    fun cachedAppLabel(pkg: String): String
    fun isDeviceAdminActive(): Boolean
    fun triggerBlock(
        sourcePackage: String,
        reason: String,
        blockType: OverlayBlockType,
        targetName: String,
        description: String
    )
    fun handleSecurityTamper(
        sourcePackage: String,
        reason: String,
        targetName: String,
        blockType: OverlayBlockType = OverlayBlockType.SETTINGS_TAMPER,
        customToastMessage: String? = null
    )
}
