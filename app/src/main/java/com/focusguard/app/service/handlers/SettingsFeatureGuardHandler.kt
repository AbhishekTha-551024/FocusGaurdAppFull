package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.utils.SafeNodeUtils
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Settings"
private const val SETTINGS_SCAN_NODE_LIMIT = 150

@Singleton
class SettingsFeatureGuardHandler @Inject constructor(
    private val repository: FocusGuardRepository,
    private val sessionBlockManager: SessionBlockManager
) {

    fun process(
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        className: String,
        protectionWanted: Boolean,
        getCachedAppLabel: (String) -> String,
        onHandleSecurityTamper: (sourcePackage: String, reason: String, targetName: String, blockType: OverlayBlockType, customToastMessage: String?) -> Unit
    ): Boolean {
        val hasFocusedSearchControl = hasFocusedSearchControl(rootNode)
        if (SettingsTamperPolicy.isSettingsSearchScreen(className, hasFocusedSearchControl)) {
            return false
        }

        val isHideAppsSettings = className.contains("HideApp", ignoreCase = true) ||
                className.contains("AppHide", ignoreCase = true) ||
                className.contains("HiddenApp", ignoreCase = true) ||
                className.contains("PrivateSpace", ignoreCase = true) ||
                className.contains("AppHider", ignoreCase = true) ||
                containsVisibleKeyword(
                    rootNode,
                    "Hide apps",
                    "Hidden apps",
                    "Hide app",
                    "App hider",
                    "Hide icons",
                    "Private space",
                    "Hide apps on Home and Apps screens",
                    "Hide apps on Home",
                    "App lock & hide",
                    "App lock and hide",
                    "App hiding",
                    "Privacy and app encryption",
                    "Hide desktop icons",
                    "Secret apps",
                    "Stealth mode"
                )

        if ((protectionWanted || sessionBlockManager.isSessionCurrentlyActive()) && isHideAppsSettings) {
            Log.w(TAG, "BLOCKING Hide Apps / Private Space tamper attempt in Settings ($pkg - $className)!")
            onHandleSecurityTamper(
                pkg,
                "Hide Apps / Private Space Access Blocked in Settings",
                "Hide Apps Protection",
                OverlayBlockType.SETTINGS_TAMPER,
                "Hiding apps is restricted to prevent bypassing FocusGuard protection."
            )
            return true
        }

        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = protectionWanted
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if (isProtectionActive || isSessionActive) {
                val isVivoDoubleInstance = pkg == "com.vivo.doubleinstance" ||
                        className.contains("DoubleInstance", ignoreCase = true)
                val isDualAppScreen = isVivoDoubleInstance ||
                        (containsVisibleKeyword(rootNode, "Dual apps", "App clone", "Clone apps", "App twin") &&
                                AntiClonerEngine.isDualAppsSettingsScreen(rootNode, className, pkg))
                if (isDualAppScreen) {
                    val appLabel = getCachedAppLabel(pkg)
                    Log.w(TAG, "ANTI-CLONING INTERCEPT: OEM Dual Apps Settings blocked: $pkg ($appLabel)")
                    onHandleSecurityTamper(
                        pkg,
                        "Dual Apps Settings Blocked in $appLabel",
                        "App Cloning Prohibited",
                        OverlayBlockType.SETTINGS_TAMPER,
                        "App cloning and dual apps are prohibited while FocusGuard protection is active."
                    )
                    return true
                }
            }
        }

        if (repository.isFastAdultContentBlockEnabled()) {
            val isSettingsPackage = pkg == "com.android.settings" ||
                    pkg.contains("settings", ignoreCase = true)
            val isPrivateDnsScreen = isSettingsPackage &&
                    (className.contains("PrivateDns", ignoreCase = true) ||
                            containsVisibleKeyword(rootNode, "Private DNS"))
            if (isPrivateDnsScreen) {
                Log.w(TAG, "GUARD 4.6: Private DNS settings intercepted — adult content shield active")
                onHandleSecurityTamper(
                    pkg,
                    "Private DNS Settings Blocked",
                    "Adult Content Shield",
                    OverlayBlockType.SETTINGS_TAMPER,
                    "Changing Private DNS is blocked while the Adult Content Shield is active."
                )
                return true
            }
        }

        return false
    }

    private fun hasFocusedSearchControl(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(rootNode)
        var visited = 0

        while (pending.isNotEmpty() && visited < SETTINGS_SCAN_NODE_LIMIT) {
            val node = pending.removeFirst()
            visited++
            val viewId = node.viewIdResourceName.orEmpty()
            val hint = node.hintText?.toString().orEmpty()
            val isSearchControl = viewId.contains("search", ignoreCase = true) ||
                    hint.contains("search", ignoreCase = true) ||
                    node.className?.toString()?.contains("Search", ignoreCase = true) == true
            val isActive = node.isFocused || node.text?.isNotBlank() == true
            if (isSearchControl && isActive) {
                recycleQueuedNodes(pending, rootNode)
                if (node !== rootNode) SafeNodeUtils.safeRecycle(node)
                return true
            }

            addChildren(node, pending)
            if (node !== rootNode) SafeNodeUtils.safeRecycle(node)
        }
        recycleQueuedNodes(pending, rootNode)
        return false
    }

    private fun containsVisibleKeyword(rootNode: AccessibilityNodeInfo?, vararg keywords: String): Boolean {
        if (rootNode == null) return false
        val normalizedKeywords = keywords.map { it.lowercase() }
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(rootNode)
        var visited = 0

        while (pending.isNotEmpty() && visited < SETTINGS_SCAN_NODE_LIMIT) {
            val node = pending.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                val visibleText = "${node.text ?: ""} ${node.contentDescription ?: ""}".lowercase()
                if (normalizedKeywords.any(visibleText::contains)) {
                    recycleQueuedNodes(pending, rootNode)
                    if (node !== rootNode) SafeNodeUtils.safeRecycle(node)
                    return true
                }
            }

            addChildren(node, pending)
            if (node !== rootNode) SafeNodeUtils.safeRecycle(node)
        }
        recycleQueuedNodes(pending, rootNode)
        return false
    }

    private fun addChildren(
        node: AccessibilityNodeInfo,
        pending: ArrayDeque<AccessibilityNodeInfo>
    ) {
        for (index in 0 until node.childCount.coerceAtMost(50)) {
            try {
                node.getChild(index)?.let(pending::addLast)
            } catch (_: Exception) {
                // OEM accessibility trees can become stale while a screen is transitioning.
            }
        }
    }

    private fun recycleQueuedNodes(
        pending: ArrayDeque<AccessibilityNodeInfo>,
        rootNode: AccessibilityNodeInfo
    ) {
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node !== rootNode) SafeNodeUtils.safeRecycle(node)
        }
    }
}
