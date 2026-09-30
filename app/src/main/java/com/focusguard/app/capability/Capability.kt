package com.focusguard.app.capability

/**
 * Android system capabilities and special access permissions required by FocusGuard features.
 * Modeled as capabilities rather than raw runtime permissions to properly represent
 * Accessibility Services, AppOps, Device Policy, and System Overlays.
 */
enum class Capability {
    /** Core Interceptor: Detects app launches and displays block screen in real-time */
    ACCESSIBILITY,

    /** AppOps GET_USAGE_STATS: Measures daily app usage time and enforces daily caps */
    USAGE_ACCESS,

    /** SYSTEM_ALERT_WINDOW: Allows displaying friction timer overlay above other apps */
    OVERLAY,

    /** DevicePolicyManager: Prevents uninstallation or bypassing while protection is active */
    DEVICE_ADMIN,

    /** POST_NOTIFICATIONS (Android 13+): Focus reminders, session milestones, and alerts */
    NOTIFICATIONS,

    /** Battery Optimization Exemption: Keeps background blocking service alive */
    BATTERY_OPTIMIZATION,

    /** OEM Autostart & Background Protection (Xiaomi, Oppo, Vivo, OnePlus, Realme, Samsung) */
    OEM_BACKGROUND
}

/**
 * User-facing lifecycle state of a capability.
 */
enum class CapabilityState {
    /** Capability is granted and fully operational */
    ACTIVE,

    /** A configured or activated feature requires this capability, but it is not yet granted */
    NEEDS_ACCESS,

    /** The feature requiring this capability is not currently enabled or configured */
    NOT_NEEDED,

    /** User chose to dismiss or postpone granting this capability (graceful degradation) */
    DENIED,

    /** Capability is not supported by device hardware or OS version */
    UNAVAILABLE
}

/**
 * Rich contextual explanation metadata for Just-In-Time permission prompts.
 */
data class CapabilityDetails(
    val capability: Capability,
    val state: CapabilityState,
    val title: String,
    val featureName: String,
    val whyNeeded: String,
    val benefits: List<String>,
    val privacyPromise: String,
    val actionButtonText: String
) {
    val isActive: Boolean get() = state == CapabilityState.ACTIVE
    val isNeeded: Boolean get() = state == CapabilityState.NEEDS_ACCESS
    val isNotNeeded: Boolean get() = state == CapabilityState.NOT_NEEDED
}

/**
 * Summary of overall protection health for Home Screen and Settings dashboard.
 */
data class ProtectionHealthSummary(
    val activeCount: Int = 0,
    val neededCount: Int = 0,
    val notNeededCount: Int = 0,
    val isCoreProtectionActive: Boolean = false,
    val items: List<CapabilityDetails> = emptyList()
) {
    val statusHeadline: String
        get() = when {
            isCoreProtectionActive && neededCount == 0 -> "Protection Active & Ready"
            isCoreProtectionActive && neededCount > 0 -> "Active (${neededCount} feature optional)"
            neededCount > 0 -> "${neededCount} Setup Recommended"
            else -> "Ready to Configure"
        }
}
