package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.LauncherDetector
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Installer"

/**
 * PackageInstallerHandler
 * =======================
 * Dedicated handler for PackageInstaller & Google Play Store uninstall screens.
 * - Detects uninstall confirmation dialogs across Android versions and OEM skins.
 * - Confirms whether FocusGuard specifically is the target of the uninstall dialog.
 * - Ensures third-party applications can be uninstalled without interference.
 */
@Singleton
class PackageInstallerHandler @Inject constructor(
    private val launcherDetector: LauncherDetector
) {

    /**
     * Evaluates package installer events.
     * @return true if intercepted/handled, false if allowed.
     */
    fun processInstallerEvent(
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        protectionWanted: Boolean,
        onTamperTriggered: (sourcePkg: String, reason: String, targetName: String, customToastMessage: String) -> Unit
    ): Boolean {
        if (!protectionWanted) {
            Log.i(TAG, "Uninstall allowed: Uninstall Protection is OFF.")
            return false
        }

        val isPlayStore = pkg == "com.android.vending"
        val eventText = e.text.joinToString(" ")
        val isFastFgTarget = eventText.contains("FocusGuard", ignoreCase = true) ||
                eventText.contains("com.focusguard.app", ignoreCase = true)

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isFastFgTarget) {
            Log.w(TAG, "PREEMPTIVE BLOCK: Instant pre-render kill for PackageInstaller targeting FocusGuard ($pkg)")
            onTamperTriggered(
                pkg,
                if (isPlayStore) "Google Play Store Uninstall Targeting FocusGuard" else "Package Installer Targeting FocusGuard",
                "FocusGuard Uninstall",
                "🚫 Uninstalling FocusGuard is protected. Complete the friction challenge in the app."
            )
            return true
        }

        if (rootNode != null && (isUninstallConfirmationForFocusGuard(rootNode) ||
                    SafeNodeUtils.containsKeywords(rootNode, "FocusGuard", "com.focusguard.app"))) {
            onTamperTriggered(
                pkg,
                if (isPlayStore) "Google Play Store Uninstall Targeting FocusGuard" else "Package Installer Targeting FocusGuard",
                "FocusGuard Uninstall",
                "🚫 Uninstalling FocusGuard is protected. Complete the friction challenge in the app."
            )
            return true
        }

        return false
    }

    /**
     * Confirms whether the active dialog is an uninstall confirmation dialog targeting FocusGuard.
     * Works natively across all Android versions and OEM skins (Vivo, Samsung, Xiaomi, Pixel, etc.).
     */
    fun isUninstallConfirmationForFocusGuard(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        // CRITICAL GUARD 1: Accessibility settings & service activation screens must NEVER be treated as uninstall prompts!
        val isA11yScreen = SafeNodeUtils.containsKeywords(
            rootNode,
            "Accessibility",
            "Screen Protection",
            "Use FocusGuard",
            "Turn on FocusGuard",
            "Allow FocusGuard",
            "Installed apps",
            "Downloaded apps",
            "full control of your device"
        )
        if (isA11yScreen) {
            return false
        }

        val pkg = rootNode.packageName?.toString()?.lowercase() ?: ""
        val isLauncher = launcherDetector.isLauncherPackage(pkg) || pkg.contains("launcher")

        // Must have confirmation buttons ("Uninstall", "Delete")
        val hasActionBtn = SafeNodeUtils.containsKeywords(rootNode, "Uninstall", "Delete")
        if (!hasActionBtn) return false

        // Check if there is a Cancel/negative button (essential to distinguish dialogs from menus)
        val hasCancelBtn = SafeNodeUtils.containsKeywords(rootNode, "Cancel", "Keep", "No")
        if (!hasCancelBtn) return false

        // 1. Direct title/message/app_name node check across all Android versions and OEMs
        val targetNodeIds = listOf(
            "com.android.packageinstaller:id/app_name",
            "com.google.android.packageinstaller:id/app_name",
            "com.android.packageinstaller:id/message",
            "com.google.android.packageinstaller:id/message",
            "android:id/alertTitle",
            "android:id/message",
            "android:id/title",
            ":id/txt_uninstall_main_title",
            ":id/alertTitle",
            ":id/dialog_title",
            ":id/message"
        )

        for (id in targetNodeIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val targetsFg = nodes.any {
                        val t = (it.text?.toString() ?: "") + " " + (it.contentDescription?.toString() ?: "")
                        t.contains("FocusGuard", ignoreCase = true) || t.contains("com.focusguard.app", ignoreCase = true)
                    }
                    nodes.forEach { SafeNodeUtils.safeRecycle(it) }
                    if (targetsFg) return true
                }
            } catch (_: Exception) {
                // Defensive catch for view ID query crashes on OEM skins
            }
        }

        // 2. Strict text-based verification: Must explicitly pair "uninstall" or "delete" with "FocusGuard"
        val fullText = SafeNodeUtils.extractScreenTextFast(rootNode)
        val mentionsFg = fullText.contains("focusguard") || fullText.contains("com.focusguard.app")
        val mentionsUninstall = fullText.contains("uninstall") || fullText.contains("delete") || fullText.contains("अनइंस्टॉल")

        return mentionsFg && mentionsUninstall
    }
}

