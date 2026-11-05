package com.focusguard.app.service.handlers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.overlay.manager.OverlayBlockType
import com.focusguard.app.service.FocusGuardAccessibilityService
import com.focusguard.app.service.utils.TamperResponsePolicy
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
    private val featureGuardHandler: SettingsFeatureGuardHandler
) {
    private var lastSettingsContentScanTimestamp: Long = 0L
    private var lastSelfA11yContestTimestamp: Long = 0L

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
        val clickedControl = extractClickedControl(e)
        val clickedAction = SettingsTamperPolicy.classifyClickedAction(
            isClick = e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED,
            clickedText = clickedControl.text,
            checkedToggleClicked = clickedControl.checkedToggle
        )

        // ── GUARD 4.1: ACCESSIBILITY SETTINGS DEACTIVATION TAMPER ──
        if (protectionWanted) {
            val isA11y = className.contains("Accessibility", ignoreCase = true) ||
                    SafeNodeUtils.hasText(screenText, "accessibility", "screen protection")
            val targetsFocusGuardInA11y = SafeNodeUtils.hasText(screenText, "focusguard", "com.focusguard.app")
            if (SettingsTamperPolicy.shouldBlockAccessibilityDisable(
                    isAccessibilityScreen = isA11y,
                    targetsFocusGuard = targetsFocusGuardInA11y,
                    action = clickedAction
                )
            ) {
                Log.w(TAG, "BLOCKING FocusGuard Accessibility disable action (cls: $className)")
                contestSelfA11yTamper(
                    nowRealtime = nowRealtime,
                    pkg = pkg,
                    reason = "Disable FocusGuard in Accessibility Settings",
                    customToastMessage = "🚫 FocusGuard accessibility service is protected. Complete the friction challenge in the app to modify.",
                    onHandleSecurityTamper = onHandleSecurityTamper
                )
                return true
            }
        }

        // ── GUARD 4.2: DEVICE ADMIN DEACTIVATION TAMPER ──
        if (protectionWanted) {
            val isDeviceAdminScreen = className.contains("DeviceAdmin", ignoreCase = true) ||
                    SafeNodeUtils.hasText(screenText, "device admin", "admin apps", "device administrators", "this administrator is active")
            val targetsFocusGuardInAdmin = SafeNodeUtils.hasText(screenText, "focusguard", "com.focusguard.app")
            if (SettingsTamperPolicy.shouldBlockDeviceAdminDisable(
                    isDeviceAdminScreen = isDeviceAdminScreen,
                    targetsFocusGuard = targetsFocusGuardInAdmin,
                    action = clickedAction
                )
            ) {
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
            val isSpecificallyFocusGuardApp = isFocusGuardAppDetailsPage(rootNode)
            if (SettingsTamperPolicy.shouldBlockFocusGuardAppAction(
                    isFocusGuardAppDetailsPage = isSpecificallyFocusGuardApp,
                    action = clickedAction
                )
            ) {
                Log.w(TAG, "BLOCKING destructive action on FocusGuard App Info page")
                onHandleSecurityTamper(
                    pkg,
                    "FocusGuard destructive action blocked in Settings",
                    "FocusGuard App Info",
                    OverlayBlockType.UNINSTALL_ATTEMPT,
                    "🚫 Modifying FocusGuard settings or storage is restricted. Complete the friction challenge in the app."
                )
                return true
            }
        }

        if (featureGuardHandler.process(
                rootNode = rootNode,
                pkg = pkg,
                className = className,
                protectionWanted = protectionWanted,
                getCachedAppLabel = getCachedAppLabel,
                onHandleSecurityTamper = onHandleSecurityTamper
            )
        ) {
            return true
        }

        return true
    }

    private fun extractClickedControl(event: AccessibilityEvent): ClickedSettingsControl {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return ClickedSettingsControl("", false)
        val source = try {
            event.source
        } catch (_: Exception) {
            null
        }
        val text = buildString {
            append(event.text.joinToString(" "))
            append(' ')
            append(event.contentDescription ?: "")
            append(' ')
            append(source?.text ?: "")
            append(' ')
            append(source?.contentDescription ?: "")
            append(' ')
            append(source?.viewIdResourceName ?: "")
        }
        val isCheckedToggle = source?.let { it.isCheckable && it.isChecked } == true
        SafeNodeUtils.safeRecycle(source)
        return ClickedSettingsControl(text, isCheckedToggle)
    }

    /**
     * Runs the visible contest against an attempt on FocusGuard's own Accessibility Service, paced
     * by [TamperResponsePolicy.isSelfA11yContestDue].
     *
     * Detection is unchanged: the caller consumes the event either way, so a paced-out repeat can
     * never fall through to a milder Settings path. Pacing exists because the in-window response
     * ([com.focusguard.app.service.utils.TamperResponse.HEAL_AND_COVER]) no longer navigates away:
     * the user stays on the Accessibility details screen and its content stream keeps re-detecting
     * the same attempt, so without pacing each of those events would be a Toast, a haptic, a
     * secure-setting write and -- when the silent heal cannot be verified -- a fresh full-screen
     * gate several times per second. What still happens during the debounce is the setting-level
     * recovery (AccessibilityStateObserver, liveness probe), which is the layer that actually
     * re-arms the toggle.
     */
    private fun contestSelfA11yTamper(
        nowRealtime: Long,
        pkg: String,
        reason: String,
        customToastMessage: String,
        onHandleSecurityTamper: (sourcePackage: String, reason: String, targetName: String, blockType: OverlayBlockType, customToastMessage: String?) -> Unit
    ) {
        if (!TamperResponsePolicy.isSelfA11yContestDue(lastSelfA11yContestTimestamp, nowRealtime)) {
            Log.d(TAG, "Self-a11y contest paced out for a repeat attempt [$reason]")
            return
        }
        lastSelfA11yContestTimestamp = nowRealtime
        onHandleSecurityTamper(
            pkg,
            reason,
            TamperResponsePolicy.TARGET_ACCESSIBILITY_SETTINGS,
            OverlayBlockType.SETTINGS_TAMPER,
            customToastMessage
        )
    }
}

private data class ClickedSettingsControl(
    val text: String,
    val checkedToggle: Boolean
)
