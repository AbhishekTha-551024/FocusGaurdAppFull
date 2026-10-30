package com.focusguard.app.feature.home.ui

import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.data.repository.ProtectionPermissionStatus

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Immutable UI State (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Single Source of Truth for Home Screen:
 *    - An immutable snapshot of the entire dashboard screen state emitted by [HomeViewModel].
 *    - Compose UI components observe this state via `collectAsStateWithLifecycle()` to render
 *      with predictable re-composition behavior.
 *
 * 2. Key State Properties:
 *    - [permissionStatus]: Detailed multi-tier permission matrix (Accessibility, Device Admin,
 *      Overlay window, Usage access, Battery exemption).
 *    - Convenience boolean flags ([isAccessibilityEnabled], [isOverlayGranted], [isDeviceAdminActive], etc.)
 *      for fast, clean reading in Composables.
 *    - [healthSummary]: Overall security health metrics (shields armed, watchdog status).
 *    - [capabilityStates]: Operational health map for all protection subsystems.
 *    - Focus Metrics: Counts of blocked apps, blocked keywords, active time limits, schedules, and impulses blocked.
 *
 * 3. Computed Helpers:
 *    - [hasMissingRequiredPermissions]: Quick boolean helper that flags if core permissions required
 *      for app blocking (Accessibility, Battery Ignore, System Overlay) are missing.
 * =========================================================================================
 */
data class HomeUiState(
    val permissionStatus: ProtectionPermissionStatus = ProtectionPermissionStatus(),
    val isAccessibilityEnabled: Boolean = permissionStatus.isAccessibilityGranted,
    val isOverlayGranted: Boolean = permissionStatus.isOverlayGranted,
    val isDeviceAdminActive: Boolean = permissionStatus.isDeviceAdminGranted,
    val isUsageAccessGranted: Boolean = permissionStatus.isUsageAccessGranted,
    val isBatteryOptimizationIgnored: Boolean = permissionStatus.isBatteryOptimizationIgnored,
    val healthSummary: ProtectionHealthSummary = ProtectionHealthSummary(),
    val capabilityStates: Map<Capability, CapabilityState> = emptyMap(),
    val blockedAppsCount: Int = 0,
    val blockedKeywordsCount: Int = 0,
    val activeTimeLimitsCount: Int = 0,
    val activeSchedulesCount: Int = 0,
    val todayImpulsesBlockedCount: Int = 0
) {
    val hasMissingRequiredPermissions: Boolean
        get() = !isAccessibilityEnabled || !isBatteryOptimizationIgnored || !isOverlayGranted
}
