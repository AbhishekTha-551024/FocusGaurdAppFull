package com.focusguard.app.tracker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChatQueryExtractor
 * ==================
 * Extracts search queries and typed input from universal 3rd-party apps, search bars,
 * and text inputs (e.g. Pinterest, Instagram, system search) via fast view-ID lookup
 * and bounded node traversal.
 */
@Singleton
class ChatQueryExtractor @Inject constructor() {

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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }

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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
        }

        return searchDynamicEditableOrFocused(rootNode, maxDepth = 4, currentDepth = 0)
    }

    internal fun searchDynamicEditableOrFocused(node: AccessibilityNodeInfo?, maxDepth: Int = 4, currentDepth: Int = 0): String? {
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
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }
        return null
    }
}

