package com.focusguard.app.tracker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BrowserOmniboxTracker
 * =====================
 * High-performance, zero-leak URL, search query, autocomplete suggestions,
 * and webpage DOM visible text extractor for supported mobile browsers.
 */
@Singleton
class BrowserOmniboxTracker @Inject constructor(
    private val packageRegistry: BrowserPackageRegistry
) {

    companion object {
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
    }

    fun isBrowserSettingsScreen(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?, pkg: String): Boolean {
        if (!packageRegistry.isSupportedBrowser(pkg)) return false

        val className = event?.className?.toString() ?: ""
        if (className.contains("Settings", ignoreCase = true) ||
            className.contains("Preference", ignoreCase = true)
        ) {
            return true
        }

        val winTitle = rootNode?.window?.title?.toString()?.trim() ?: ""
        if (winTitle.equals("Settings", ignoreCase = true) ||
            winTitle.contains("Chrome Settings", ignoreCase = true) ||
            winTitle.contains("Browser Settings", ignoreCase = true)
        ) {
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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
        val candidateIds = packageRegistry.browserResourceIdMap[pkg] ?: emptyList()
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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
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
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }

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
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }
    }

    fun extractUrlOrQuery(rootNode: AccessibilityNodeInfo?, pkg: String): String? {
        if (rootNode == null) return null

        val candidateIds = packageRegistry.browserResourceIdMap[pkg] ?: emptyList()
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
            } catch (t: Throwable) {
                android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
            }
        }

        return boundedSearchForUrl(rootNode, maxDepth = 4, currentDepth = 0)
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
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }

        return null
    }
}

