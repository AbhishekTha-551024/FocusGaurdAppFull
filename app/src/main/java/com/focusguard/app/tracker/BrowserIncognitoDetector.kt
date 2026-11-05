package com.focusguard.app.tracker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BrowserIncognitoDetector
 * ========================
 * Detects if the current browser window is genuinely inside an Incognito, InPrivate,
 * Secret, or Private tab. Prevents false positives during normal browsing, search
 * queries for "incognito", and opening 3-dots menus.
 */
@Singleton
class BrowserIncognitoDetector @Inject constructor(
    private val packageRegistry: BrowserPackageRegistry,
    private val omniboxTracker: BrowserOmniboxTracker
) {

    fun isIncognitoOrPrivateTab(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        packageName: String
    ): Boolean {
        if (!packageRegistry.isSupportedBrowser(packageName)) return false

        // 1. Browser Settings Immunity: Never block settings in Chrome, Firefox, Edge, etc.
        if (omniboxTracker.isBrowserSettingsScreen(rootNode, event, packageName)) return false

        // 2. Click Intent Intercept: User explicitly clicked "New Incognito tab" from the menu
        if (event != null && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val eventTexts = event.text.map { it.toString() }
            val desc = event.contentDescription?.toString() ?: ""
            if (eventTexts.any { isNewIncognitoTabAction(it) } || isNewIncognitoTabAction(desc)) {
                return true
            }
        }

        // 3. Node-level inspection for genuine active incognito indicators
        if (rootNode != null) {
            return hasIncognitoIndicators(rootNode)
        }

        return false
    }

    private fun isNewIncognitoTabAction(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return lower.contains("new incognito tab") ||
                lower.contains("new inprivate tab") ||
                lower.contains("new private tab") ||
                lower.contains("turn on secret mode") ||
                lower.contains("close incognito tabs")
    }

    private fun hasIncognitoIndicators(
        node: AccessibilityNodeInfo?,
        maxDepth: Int = 5,
        currentDepth: Int = 0
    ): Boolean {
        if (node == null || currentDepth > maxDepth || !SafeNodeUtils.isInspectable(node)) return false

        try {
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""

            // 1. Chrome / Edge / Firefox Toolbar Tab Switcher Button (in Incognito mode)
            // When in Incognito, Chrome's top-right tab counter button has description: "1 open incognito tab", "2 open incognito tabs", etc.
            // In normal mode, it says: "1 open tab", "2 open tabs", etc. (NO "incognito").
            if (desc.contains("open incognito tab", ignoreCase = true) ||
                desc.contains("open inprivate tab", ignoreCase = true) ||
                desc.contains("switch to standard tabs", ignoreCase = true)
            ) {
                return true
            }

            // 2. Tab Switcher Toggle: ONLY matches if Incognito is actually SELECTED
            // In Chrome's tab switcher, incognito_toggle_button exists in normal mode too!
            // It is ONLY active when node.isSelected == true or node.isChecked == true!
            if (viewId.contains("incognito_toggle") || viewId.contains("incognito_tabs") ||
                viewId.contains("inprivate_toggle") || viewId.contains("secret_mode_toggle")
            ) {
                if (node.isSelected || node.isChecked) {
                    return true
                }
            }

            // Tab Switcher selection via contentDescription
            if (desc.contains("incognito tabs, selected", ignoreCase = true) ||
                desc.contains("selected, incognito tabs", ignoreCase = true) ||
                desc.contains("private tab selected", ignoreCase = true) ||
                desc.contains("inprivate tab selected", ignoreCase = true)
            ) {
                return true
            }

            // 3. Close all incognito tabs button (visible in tab switcher when incognito tabs exist)
            if (viewId.contains("close_all_incognito_tabs_button") ||
                viewId.contains("close_all_incognito") ||
                text.equals("Close all incognito tabs", ignoreCase = true) ||
                desc.equals("Close all incognito tabs", ignoreCase = true)
            ) {
                return true
            }

            // 4. Native Browser Incognito / Private Splash / New Tab Screen Headlines
            // Note: These exact phrases appear exclusively on native private new tab pages, never on normal web pages.
            if (text.contains("You've gone incognito", ignoreCase = true) ||
                text.contains("You’ve gone incognito", ignoreCase = true) ||
                text.contains("InPrivate browsing", ignoreCase = true) ||
                text.contains("You're browsing InPrivate", ignoreCase = true) ||
                text.contains("Secret mode is on", ignoreCase = true) ||
                text.contains("Turn off Secret mode", ignoreCase = true) ||
                (text.contains("Private Browsing", ignoreCase = true) &&
                        (desc.contains("Firefox clears", ignoreCase = true) || text.contains("Firefox clears", ignoreCase = true))) ||
                (text.contains("Private Tab", ignoreCase = true) &&
                        (desc.contains("Brave doesn't remember", ignoreCase = true) || text.contains("Brave doesn't remember", ignoreCase = true)))
            ) {
                return true
            }

            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                val found = hasIncognitoIndicators(child, maxDepth, currentDepth + 1)
                SafeNodeUtils.safeRecycle(child)
                if (found) return true
            }
        } catch (t: Throwable) {
            android.util.Log.v("BrowserUrlTracker", "Node traversal error: ${t.message}")
        }

        return false
    }
}

