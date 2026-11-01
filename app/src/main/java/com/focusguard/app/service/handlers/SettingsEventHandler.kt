package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.utils.SafeNodeUtils
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Settings"

/**
 * SettingsEventHandler
 * ====================
 * Specialized handler for Android System Settings & OEM Security Centers.
 * - Detects tampering with Accessibility Services and Device Administration.
 * - Inspects Force Stop, Clear Storage, and Uninstall buttons specifically on FocusGuard's details page.
 * - Filters out false-positive Vivo SubSettings and general settings navigation.
 * - Enforces Private DNS and OEM Dual Apps restrictions when shields are active.
 */
@Singleton
class SettingsEventHandler @Inject constructor(
    private val repository: FocusGuardRepository,
    private val sessionBlockManager: SessionBlockManager
) {
    private var lastSettingsContentScanTimestamp: Long = 0L
    private var lastA11yFocusGuardPageTimestamp: Long = 0L

    fun isFocusGuardAppDetailsPage(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val titleIds = listOf(
            "com.android.settings:id/entity_header_title",
            "android:id/title",
            "com.android.settings:id/app_title",
            ":id/entity_header_title",
            ":id/app_name",
            ":id/title"
        )
        for (id in titleIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                val matches = nodes.any { node ->
                    val text = node.text?.toString() ?: ""
                    text.equals("FocusGuard", ignoreCase = true) || text.equals("com.focusguard.app", ignoreCase = true)
                }
                nodes.forEach { SafeNodeUtils.safeRecycle(it) }
                if (matches) return true
            } catch (_: Exception) {
                // Defensive catch for view ID query crashes on OEM skins
            }
        }
        return false
    }

    fun processSettingsEvent(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo?,
        e: AccessibilityEvent,
        pkg: String,
        className: String,
        protectionWanted: Boolean,
        isDeviceAdminCurrentlyActive: Boolean,
        nowRealtime: Long,
        isOemScanWindowActive: () -> Boolean,
        getCachedAppLabel: (String) -> String,
        onHandleSecurityTamper: (sourcePackage: String, reason: String, targetName: String, blockType: OverlayBlockType, customToastMessage: String?) -> Unit
    ): Boolean {
        // Throttling: Cap TYPE_WINDOW_CONTENT_CHANGED scans to at most 1 per 150ms
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (nowRealtime - lastSettingsContentScanTimestamp < 150L) {
                return true
            }
            lastSettingsContentScanTimestamp = nowRealtime
        }

        // Check legitimate admin activation window (ONLY permitted when admin is not yet active)
        val isWithinAdminActivationWindow = !isDeviceAdminCurrentlyActive && (nowRealtime < FocusGuardAccessibilityService.adminActivationAllowedUntil)
        if (className.contains("DeviceAdminAdd", ignoreCase = true) && isWithinAdminActivationWindow) {
            Log.d(TAG, "Allowing DeviceAdminAdd screen: within legitimate activation window")
            return true
        }

        val screenText = SafeNodeUtils.extractScreenTextFast(rootNode)

        // ── GUARD 4.1: ACCESSIBILITY SETTINGS DEACTIVATION TAMPER ──
        if (protectionWanted) {
            val isA11y = className.contains("Accessibility", ignoreCase = true) ||
                    SafeNodeUtils.hasText(screenText, "accessibility", "screen protection")
            val targetsFocusGuardInA11y = SafeNodeUtils.hasText(screenText, "focusguard", "com.focusguard.app")

            if (isA11y && targetsFocusGuardInA11y && !isOemScanWindowActive()) {
                lastA11yFocusGuardPageTimestamp = nowRealtime
                Log.d(TAG, "A11y FocusGuard page stamped at $nowRealtime")
            }

            val isOnFocusGuardA11yPage = lastA11yFocusGuardPageTimestamp > 0L &&
                    (nowRealtime - lastA11yFocusGuardPageTimestamp < 8_000L)

            val clickedFocusGuard = (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) &&
                    (e.text.any { it.toString().contains("FocusGuard", ignoreCase = true) } ||
                     e.contentDescription?.contains("FocusGuard", ignoreCase = true) == true ||
                     (try { e.source } catch (_: Exception) { null })?.text?.toString()
                         ?.contains("FocusGuard", ignoreCase = true) == true)

            // Direct click on FocusGuard item in a11y list
            if (isA11y && clickedFocusGuard) {
                Log.w(TAG, "BLOCKING click on FocusGuard in Accessibility Settings! (cls: $className)")
                lastA11yFocusGuardPageTimestamp = nowRealtime
                onHandleSecurityTamper(
                    pkg,
                    "Direct Click on FocusGuard in Accessibility Settings",
                    "Accessibility Settings",
                    OverlayBlockType.SETTINGS_TAMPER,
                    "🚫 FocusGuard accessibility service is protected. Complete the friction challenge in the app to modify."
                )
                return true
            }

            // Click while on FocusGuard's details page
            if (isOnFocusGuardA11yPage && e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                Log.w(TAG, "BLOCKING click on FocusGuard Accessibility details page! (cls: $className)")
                onHandleSecurityTamper(
                    pkg,
                    "Interaction on FocusGuard Accessibility Page",
                    "Accessibility Settings",
                    OverlayBlockType.SETTINGS_TAMPER,
                    "🚫 FocusGuard accessibility service is protected. Complete the friction challenge in the app to modify."
                )
                return true
            }

            // Window change into FocusGuard's a11y details page
            val isFocusGuardA11yDetailsPage = isA11y && targetsFocusGuardInA11y && (
                    SafeNodeUtils.hasText(screenText, "turn off", "stop", "use focusguard", "allow focusguard", "shortcut") ||
                    className.contains("ToggleAccessibilityServicePreferenceFragment", ignoreCase = true) ||
                    className.contains("AccessibilityServiceWarning", ignoreCase = true)
            )

            if (isFocusGuardA11yDetailsPage && !isOemScanWindowActive()) {
                Log.w(TAG, "BLOCKING entry into FocusGuard Accessibility Details screen! (cls: $className)")
                onHandleSecurityTamper(
                    pkg,
                    "FocusGuard Accessibility Details Screen Opened",
                    "Accessibility Settings",
                    OverlayBlockType.SETTINGS_TAMPER,
                    "🚫 Disabling FocusGuard is restricted. Complete the friction challenge in the app."
                )
                return true
            }
        }

        // ── GUARD 4.2: DEVICE ADMIN DEACTIVATION TAMPER ──
        if (protectionWanted) {
            val isDeviceAdminScreen = className.contains("DeviceAdmin", ignoreCase = true) ||
                    className.contains("VivoSubSettings", ignoreCase = true) ||
                    className.contains("SubSettings", ignoreCase = true) ||
                    SafeNodeUtils.hasText(screenText, "device admin", "admin apps", "device administrators", "this administrator is active")
            val isDeactivationScreen = SafeNodeUtils.hasText(
                screenText,
                "deactivate",
                "remove",
                "uninstall",
                "turn off",
                "disable this device admin"
            )
            val targetsFocusGuardInAdmin = SafeNodeUtils.hasText(screenText, "focusguard", "com.focusguard.app")

            val clickedFocusGuardOrDeactivate = (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) &&
                    (e.text.any { it.toString().contains("focusguard", ignoreCase = true) || it.toString().contains("deactivate", ignoreCase = true) } ||
                     e.contentDescription?.contains("focusguard", ignoreCase = true) == true ||
                     e.contentDescription?.contains("deactivate", ignoreCase = true) == true ||
                     (try { e.source } catch (_: Exception) { null })?.text?.toString()?.contains("focusguard", ignoreCase = true) == true ||
                     (try { e.source } catch (_: Exception) { null })?.text?.toString()?.contains("deactivate", ignoreCase = true) == true)

            val isDirectDeactivateClick = (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) && (
                e.text.any { it.toString().contains("deactivate", ignoreCase = true) } ||
                e.contentDescription?.contains("deactivate", ignoreCase = true) == true ||
                (try { e.source } catch (_: Exception) { null })?.text?.toString()?.contains("deactivate", ignoreCase = true) == true
            )

            if (isDirectDeactivateClick ||
                (isDeviceAdminScreen && (isDeactivationScreen || clickedFocusGuardOrDeactivate) && (targetsFocusGuardInAdmin || clickedFocusGuardOrDeactivate)) ||
                (targetsFocusGuardInAdmin && isDeactivationScreen)) {
                Log.w(TAG, "BLOCKING Device Admin tamper for FocusGuard! (cls: $className, event: ${e.eventType})")
                onHandleSecurityTamper(
                    pkg,
                    "Device Admin Screen targeting FocusGuard",
                    "Device Administrator",
                    OverlayBlockType.SETTINGS_TAMPER,
                    "🚫 Deactivating FocusGuard Device Admin is protected. Complete the friction challenge in the app."
                )
                return true
            }
        }

        // ── GUARD 4.3: APP INFO / FORCE STOP / CLEAR STORAGE TAMPER ──
        if (protectionWanted) {
            val isDeactivationTamper = SafeNodeUtils.hasText(
                screenText,
                "deactivate",
                "disable this device admin",
                "device administrator"
            ) || className.contains("DeviceAdmin", ignoreCase = true)

            val targetsFocusGuardInAdmin = isDeactivationTamper && SafeNodeUtils.hasText(screenText, "focusguard", "com.focusguard.app")

            val isAppDetailsScreen = className.contains("InstalledAppDetails", ignoreCase = true) ||
                    className.contains("AppInfo", ignoreCase = true) ||
                    className.contains("AppStorageSettings", ignoreCase = true) ||
                    className.contains("ManageSpaceActivity", ignoreCase = true) ||
                    className.contains("AppButtonsPreferenceController", ignoreCase = true) ||
                    SafeNodeUtils.hasText(screenText, "force stop", "clear data", "clear storage", "manage space")

            val isSpecificallyFocusGuardApp = isAppDetailsScreen && isFocusGuardAppDetailsPage(rootNode)

            if (targetsFocusGuardInAdmin || isSpecificallyFocusGuardApp) {
                val targetName = if (isDeactivationTamper) "Device Administrator" else "Security Settings"
                val tamperBlockType = if (isDeactivationTamper) OverlayBlockType.SETTINGS_TAMPER else OverlayBlockType.UNINSTALL_ATTEMPT
                Log.w(TAG, "BLOCKING settings tamper: $targetName")
                onHandleSecurityTamper(
                    pkg,
                    "FocusGuard Tampering Detected in Settings [$targetName]",
                    targetName,
                    tamperBlockType,
                    "🚫 Modifying FocusGuard settings or storage is restricted. Complete the friction challenge in the app."
                )
                return true
            }
        }

        // ── GUARD 4.4: HIDE APPS, PRIVATE SPACE & STEALTH MODE IN SETTINGS ──
        val isHideAppsSettings = className.contains("HideApp", ignoreCase = true) ||
                className.contains("AppHide", ignoreCase = true) ||
                className.contains("HiddenApp", ignoreCase = true) ||
                className.contains("PrivateSpace", ignoreCase = true) ||
                className.contains("AppHider", ignoreCase = true) ||
                (rootNode != null && SafeNodeUtils.containsKeywords(
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
                ))

        if ((protectionWanted || sessionBlockManager.isSessionCurrentlyActive()) && isHideAppsSettings) {
            Log.w(TAG, "BLOCKING Hide Apps / Private Space tamper attempt in Settings ($pkg - $className)!")
            onHandleSecurityTamper(
                pkg,
                "Hide Apps / Private Space Access Blocked in Settings",
                "Hide Apps Protection",
                OverlayBlockType.SETTINGS_TAMPER,
                "🚫 Hiding apps is restricted to prevent bypassing FocusGuard protection."
            )
            return true
        }

        // ── GUARD 4.5: OEM DUAL APPS / CLONER SETTINGS INTERCEPT ──
        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = protectionWanted
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if (isProtectionActive || isSessionActive) {
                val isVivoDoubleInstance = pkg == "com.vivo.doubleinstance" || className.contains("DoubleInstance", ignoreCase = true)
                val isDualAppScreen = isVivoDoubleInstance || AntiClonerEngine.isDualAppsSettingsScreen(rootNode, className, pkg)
                if (isDualAppScreen) {
                    val appLabel = getCachedAppLabel(pkg)
                    Log.w(TAG, "ANTI-CLONING INTERCEPT: OEM Dual Apps Settings blocked: $pkg ($appLabel)")
                    onHandleSecurityTamper(
                        pkg,
                        "Dual Apps Settings Blocked in $appLabel",
                        "App Cloning Prohibited",
                        OverlayBlockType.SETTINGS_TAMPER,
                        "🚫 App cloning and dual apps are prohibited while FocusGuard protection is active."
                    )
                    return true
                }
            }
        }

        // ── GUARD 4.6: PRIVATE DNS SETTINGS INTERCEPTION (Layer 3 — Adult Content Shield) ──
        if (repository.isFastAdultContentBlockEnabled()) {
            val isPrivateDnsScreen = when {
                (pkg == "com.android.settings" || pkg.contains("settings", ignoreCase = true)) -> {
                    className.contains("PrivateDns", ignoreCase = true) ||
                    (rootNode != null && SafeNodeUtils.containsKeywords(rootNode, "Private DNS", "Private dns"))
                }
                else -> false
            }
            if (isPrivateDnsScreen) {
                Log.w(TAG, "GUARD 4.6: Private DNS settings intercepted — adult content shield active")
                onHandleSecurityTamper(
                    pkg,
                    "Private DNS Settings Blocked",
                    "🔞 Adult Content Shield",
                    OverlayBlockType.SETTINGS_TAMPER,
                    "⛔ Changing Private DNS is blocked while the Adult Content Shield is active."
                )
                return true
            }
        }

        return true
    }
}
