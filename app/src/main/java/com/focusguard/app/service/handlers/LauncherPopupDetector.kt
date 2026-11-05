package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.BuildConfig
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Launcher"

/**
 * LauncherPopupDetector
 * =====================
 * Encapsulates launcher popup detection, shortcut action resolution, and click classification.
 *
 * Rules:
 * - A popup is classified as FocusGuard's popup ONLY IF:
 *   (a) Target latch is active, OR
 *   (b) Popup container subtree contains "FocusGuard" / "com.focusguard.app", OR
 *   (c) Vivo: the popup is anchored to the cached FocusGuard icon slot AND that slot no longer
 *       holds a labelled icon (see LauncherTargetResolver.isPopupAnchoredToFocusGuard).
 *   A home-screen icon that merely falls inside the popup's bounding box is NOT evidence: Vivo's
 *   shortcut container spans several icon rows and overlaps other apps' icons.
 * - Clone / Uninstall clicks block ONLY when the latch is active OR an ancestor node (depth <= 5)
 *   specifically contains "FocusGuard" / "com.focusguard.app".
 */
@Singleton
class LauncherPopupDetector @Inject constructor(
    private val targetResolver: LauncherTargetResolver
) {

    fun isLauncherHideApps(className: String, rootNode: AccessibilityNodeInfo?): Boolean {
        return className.contains("HideApp", ignoreCase = true) ||
                className.contains("AppHide", ignoreCase = true) ||
                className.contains("HiddenApp", ignoreCase = true) ||
                className.contains("PrivateSpace", ignoreCase = true) ||
                (rootNode != null && SafeNodeUtils.containsKeywords(
                    rootNode,
                    "Hide apps on Home and Apps screens",
                    "Hide apps on Home",
                    "Hide apps",
                    "Hidden apps",
                    "App hider",
                    "Hide icons",
                    "Private space"
                ))
    }

    fun isCloneClick(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("app clone") ||
                lower.contains("clone app") ||
                lower.contains("clone") ||
                lower.contains("cloner") ||
                lower.contains("dual app") ||
                lower.contains("twin app") ||
                lower.contains("app twin") ||
                lower.contains("multi-app") ||
                lower.contains("द्वि-ऐप") ||
                lower.contains("क्लोन")
    }

    fun isUninstallClick(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("uninstall") ||
                lower.contains("delete") ||
                lower.contains("remove") ||
                lower.contains("अनइंस्टॉल") ||
                lower.contains("desinstalar")
    }

    fun isNodeAncestorFocusGuard(clickedNode: AccessibilityNodeInfo?): Boolean {
        if (clickedNode == null) return false
        var curr: AccessibilityNodeInfo? = clickedNode
        var foundFg = false
        var depth = 0
        while (curr != null && depth < 5) {
            val text = (curr.text?.toString() ?: "") + " " + (curr.contentDescription?.toString() ?: "")
            if (text.contains("FocusGuard", ignoreCase = true) || text.contains("com.focusguard.app", ignoreCase = true)) {
                foundFg = true
                break
            }
            val parent = try { curr.parent } catch (_: Exception) { null }
            if (curr != clickedNode) {
                SafeNodeUtils.safeRecycle(curr)
            }
            curr = parent
            depth++
        }
        if (curr != null && curr != clickedNode) {
            SafeNodeUtils.safeRecycle(curr)
        }
        return foundFg
    }

    fun findActivePopupAnchor(rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (rootNode == null) return null

        val popupNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/popup_container")
            .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/deep_shortcuts_container") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/bottom_shortcuts_scroll_view") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/system_shortcut_icons") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByViewId("com.android.launcher3:id/bubble_text") }

        if (popupNodes.isNotEmpty()) {
            val anchor = popupNodes.first()
            for (i in 1 until popupNodes.size) {
                SafeNodeUtils.safeRecycle(popupNodes[i])
            }
            return anchor
        }

        val actionNodes = rootNode.findAccessibilityNodeInfosByText("Uninstall")
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Delete") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("App info") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("App clone") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Clone") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("क्लोन") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Remove") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("अनइंस्टॉल") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("ऐप जानकारी") }
            .ifEmpty { rootNode.findAccessibilityNodeInfosByText("Desinstalar") }

        if (actionNodes.isNotEmpty()) {
            val anchor = actionNodes.first()
            for (i in 1 until actionNodes.size) {
                SafeNodeUtils.safeRecycle(actionNodes[i])
            }
            return anchor
        }

        return null
    }

    fun isPopupForFocusGuard(
        rootNode: AccessibilityNodeInfo?,
        popupAnchorNode: AccessibilityNodeInfo?,
        isLatchActive: Boolean
    ): Boolean {
        // (a) Active latch from a verified FocusGuard long-press
        if (isLatchActive) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Popup identified for FocusGuard via active latch.")
            }
            return true
        }

        if (popupAnchorNode == null) return false

        // (b) Popup container subtree text contains FocusGuard
        val popupText = SafeNodeUtils.extractScreenTextFast(popupAnchorNode)
        if (popupText.contains("FocusGuard", ignoreCase = true) || popupText.contains("com.focusguard.app", ignoreCase = true)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Popup identified for FocusGuard via container subtree text.")
            }
            return true
        }

        // (c) Vivo launcher removes the held icon's node while its popup is open. When the popup
        //     is anchored to the cached FocusGuard slot and that slot no longer holds a labelled
        //     icon, the popup is FocusGuard's. Measured, not guessed.
        if (rootNode != null && targetResolver.isPopupAnchoredToFocusGuard(rootNode, popupAnchorNode)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Popup identified for FocusGuard via anchored empty icon slot.")
            }
            return true
        }

        return false
    }

    fun isDragDropTarget(className: String, rootNode: AccessibilityNodeInfo?): Boolean {
        return className.contains("DropTarget", ignoreCase = true) ||
                className.contains("DeleteDropTarget", ignoreCase = true) ||
                (rootNode != null && SafeNodeUtils.containsKeywords(rootNode, "Drop to uninstall", "Drag to uninstall"))
    }
}

