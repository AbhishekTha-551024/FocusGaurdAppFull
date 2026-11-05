package com.focusguard.app.service.handlers

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.BuildConfig
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "FG:Launcher"

/**
 * Measured Vivo launcher (V2355) anchoring of a shortcut popup to the icon it belongs to:
 * popup right edge = icon right edge - 51 px, and the popup sits 16 px above the icon's top
 * (or 16 px below its bottom when the icon is not in the bottom row).
 */
private const val SLOT_BOUNDS_TOLERANCE_PX = 16
private const val SLOT_SCAN_NODE_LIMIT = 400

enum class LauncherTarget {
    FOCUSGUARD,
    OTHER,
    UNKNOWN
}

/**
 * LauncherTargetResolver
 * ======================
 * Classifies launcher interaction targets strictly as FOCUSGUARD, OTHER, or UNKNOWN.
 *
 * Rules:
 * 1. Checks sourceNode and up to 4 ancestors. A node is "icon-sized" only if:
 *    - bounds area <= 12% of screen area
 *    - width < 60% of screen width
 *    - height < 35% of screen height
 *    Containers (Workspace, CellLayout, DragLayer) are NOT icon-sized -> never trusted.
 * 2. Text match (on icon-sized node or descendants up to depth 2):
 *    - FOCUSGUARD if text or contentDescription equals "focusguard" or contains "com.focusguard.app".
 *    - OTHER if an icon-sized node has a different non-empty label.
 * 3. Bounds match (secondary): center point lies inside lastFocusGuardIconBounds.
 * 4. lastFocusGuardIconBounds is refreshed ONLY from an icon-sized node with text "FocusGuard".
 */
@Singleton
class LauncherTargetResolver @Inject constructor() {

    var lastFocusGuardIconBounds: Rect? = null
        private set
    var lastFocusGuardIconTimestamp: Long = 0L
        private set

    fun isIconSized(r: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        if (r.width() <= 0 || r.height() <= 0) return false
        val screenArea = screenWidth.toLong() * screenHeight.toLong()
        if (screenArea <= 0L) return false

        val nodeArea = r.width().toLong() * r.height().toLong()
        val areaFits = nodeArea <= (screenArea * 0.12)
        val widthFits = r.width() < (screenWidth * 0.60)
        val heightFits = r.height() < (screenHeight * 0.35)

        return areaFits && widthFits && heightFits
    }

    fun refreshFocusGuardDesktopBounds(
        rootNode: AccessibilityNodeInfo?,
        screenWidth: Int,
        screenHeight: Int,
        nowRealtime: Long
    ) {
        if (rootNode == null) return
        val nodes = rootNode.findAccessibilityNodeInfosByText("FocusGuard")
        for (node in nodes) {
            val text = (node.text?.toString() ?: "").trim()
            val desc = (node.contentDescription?.toString() ?: "").trim()
            val isFgText = text.equals("FocusGuard", ignoreCase = true) || desc.equals("FocusGuard", ignoreCase = true)
            if (isFgText) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (isIconSized(r, screenWidth, screenHeight)) {
                    lastFocusGuardIconBounds = r
                    lastFocusGuardIconTimestamp = nowRealtime
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Refreshed lastFocusGuardIconBounds: $r at $nowRealtime")
                    }
                    break
                }
            }
        }
        nodes.forEach { SafeNodeUtils.safeRecycle(it) }
    }

    /**
     * Vivo's launcher replaces the held icon with its shortcut popup: the icon's own labelled
     * node disappears from the accessibility tree (all other icon labels stay), and the popup is
     * anchored to the vacated slot.
     *
     * Returns true only when BOTH measured facts hold for the cached FocusGuard slot:
     * 1. the popup geometry matches a measured edge of that slot,
     * 2. no labelled icon occupies the slot any more.
     * A missing cache or an occupied slot returns false, so OTHER/UNKNOWN stay ignored.
     */
    fun isPopupAnchoredToFocusGuard(
        rootNode: AccessibilityNodeInfo,
        popupAnchor: AccessibilityNodeInfo
    ): Boolean {
        val icon = lastFocusGuardIconBounds ?: return false
        val popup = Rect()
        popupAnchor.getBoundsInScreen(popup)
        if (popup.width() <= 0 || popup.height() <= 0) return false

        if (!LauncherPopupAnchorPolicy.matches(
                popupLeft = popup.left,
                popupTop = popup.top,
                popupRight = popup.right,
                popupBottom = popup.bottom,
                iconLeft = icon.left,
                iconTop = icon.top,
                iconRight = icon.right,
                iconBottom = icon.bottom
            )) {
            return false
        }

        val slotOccupied = isIconLabelOccupying(rootNode, icon)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Popup anchor check: popup=$popup icon=$icon slotOccupied=$slotOccupied")
        }
        return !slotOccupied
    }

    /**
     * True when a labelled node still occupies [slot]. The launcher removes the held icon's
     * node, so an occupied slot proves the open popup belongs to a different app.
     */
    private fun isIconLabelOccupying(rootNode: AccessibilityNodeInfo, slot: Rect): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(rootNode)
        var visited = 0
        while (pending.isNotEmpty()) {
            if (visited >= SLOT_SCAN_NODE_LIMIT) return true
            val node = pending.removeLast()
            visited++
            if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (boundsMatchesSlot(bounds, slot)) return true
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) pending.addLast(child)
            }
        }
        return false
    }

    private fun boundsMatchesSlot(bounds: Rect, slot: Rect): Boolean {
        return abs(bounds.left - slot.left) <= SLOT_BOUNDS_TOLERANCE_PX &&
                abs(bounds.top - slot.top) <= SLOT_BOUNDS_TOLERANCE_PX &&
                abs(bounds.right - slot.right) <= SLOT_BOUNDS_TOLERANCE_PX &&
                abs(bounds.bottom - slot.bottom) <= SLOT_BOUNDS_TOLERANCE_PX
    }

    fun resolve(
        e: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        screenWidth: Int,
        screenHeight: Int,
        nowRealtime: Long
    ): LauncherTarget {
        refreshFocusGuardDesktopBounds(rootNode, screenWidth, screenHeight, nowRealtime)

        val isBoundsFresh = lastFocusGuardIconBounds != null && (nowRealtime - lastFocusGuardIconTimestamp < 5_000L)
        val sourceNode = try { e.source } catch (_: Exception) { null }

        // 1. Walk sourceNode and up to 4 ancestors to find an icon-sized element
        var iconNode: AccessibilityNodeInfo? = null
        var iconBounds = Rect()
        var curr: AccessibilityNodeInfo? = sourceNode
        var depth = 0
        while (curr != null && depth < 5) {
            val r = Rect()
            curr.getBoundsInScreen(r)
            if (isIconSized(r, screenWidth, screenHeight)) {
                iconNode = curr
                iconBounds = r
                break
            }
            val parent = try { curr.parent } catch (_: Exception) { null }
            if (curr != sourceNode) {
                SafeNodeUtils.safeRecycle(curr)
            }
            curr = parent
            depth++
        }

        if (iconNode != null) {
            // 2. Text match on icon-sized node or descendants (depth <= 2)
            val extractedText = extractNodeTextUpToDepth(iconNode, 2).trim().lowercase()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Icon-sized node found: bounds=$iconBounds, text='$extractedText'")
            }

            if (extractedText.contains("focusguard") || extractedText.contains("com.focusguard.app")) {
                SafeNodeUtils.safeRecycle(iconNode)
                if (curr != null && curr != iconNode && curr != sourceNode) SafeNodeUtils.safeRecycle(curr)
                SafeNodeUtils.safeRecycle(sourceNode)
                Log.i(TAG, "TargetResolver: FOCUSGUARD (text match in icon node: '$extractedText')")
                return LauncherTarget.FOCUSGUARD
            }

            // 3. Center point bounds match (secondary)
            if (isBoundsFresh) {
                val cx = iconBounds.centerX()
                val cy = iconBounds.centerY()
                if (lastFocusGuardIconBounds!!.contains(cx, cy)) {
                    SafeNodeUtils.safeRecycle(iconNode)
                    if (curr != null && curr != iconNode && curr != sourceNode) SafeNodeUtils.safeRecycle(curr)
                    SafeNodeUtils.safeRecycle(sourceNode)
                    Log.i(TAG, "TargetResolver: FOCUSGUARD (center point [$cx, $cy] inside FG bounds $lastFocusGuardIconBounds)")
                    return LauncherTarget.FOCUSGUARD
                }
            }

            // Icon-sized node has text and it is NOT FocusGuard -> OTHER
            if (extractedText.isNotBlank()) {
                SafeNodeUtils.safeRecycle(iconNode)
                if (curr != null && curr != iconNode && curr != sourceNode) SafeNodeUtils.safeRecycle(curr)
                SafeNodeUtils.safeRecycle(sourceNode)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "TargetResolver: OTHER (icon has label '$extractedText')")
                }
                return LauncherTarget.OTHER
            }

            SafeNodeUtils.safeRecycle(iconNode)
            if (curr != null && curr != iconNode && curr != sourceNode) SafeNodeUtils.safeRecycle(curr)
        }

        // 4. Fallback check on AccessibilityEvent text/contentDescription
        val eventTexts = e.text.map { it.toString().trim().lowercase() }
        val eventDesc = e.contentDescription?.toString()?.trim()?.lowercase() ?: ""

        val hasFgText = eventTexts.any { it == "focusguard" || it.contains("com.focusguard.app") } ||
                eventDesc == "focusguard" || eventDesc.contains("com.focusguard.app")

        SafeNodeUtils.safeRecycle(sourceNode)

        if (hasFgText) {
            Log.i(TAG, "TargetResolver: FOCUSGUARD (matched event text/desc: '$eventTexts' / '$eventDesc')")
            return LauncherTarget.FOCUSGUARD
        }

        val hasOtherText = eventTexts.any { it.isNotBlank() && !it.contains("focusguard") } ||
                (eventDesc.isNotBlank() && !eventDesc.contains("focusguard"))

        if (hasOtherText) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "TargetResolver: OTHER (event has other label: '$eventTexts' / '$eventDesc')")
            }
            return LauncherTarget.OTHER
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TargetResolver: UNKNOWN (no conclusive evidence)")
        }
        return LauncherTarget.UNKNOWN
    }

    private fun extractNodeTextUpToDepth(node: AccessibilityNodeInfo, maxDepth: Int): String {
        val sb = StringBuilder()
        fun traverse(curr: AccessibilityNodeInfo, currentDepth: Int) {
            val text = curr.text?.toString() ?: ""
            val desc = curr.contentDescription?.toString() ?: ""
            if (text.isNotBlank()) sb.append(text).append(" ")
            if (desc.isNotBlank()) sb.append(desc).append(" ")
            if (currentDepth < maxDepth) {
                val childCount = curr.childCount
                for (i in 0 until childCount) {
                    val child = try { curr.getChild(i) } catch (_: Exception) { null }
                    if (child != null) {
                        traverse(child, currentDepth + 1)
                        SafeNodeUtils.safeRecycle(child)
                    }
                }
            }
        }
        traverse(node, 0)
        return sb.toString()
    }
}
