package com.focusguard.app.capability

import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.domain.model.ProtectionState
import com.focusguard.app.domain.policy.ProtectionReadiness
import com.focusguard.app.domain.policy.ProtectionStatePolicy
import javax.inject.Inject

class CapabilityStateResolver @Inject constructor(
    private val accessChecker: CapabilityAccessChecker,
    private val prefs: FocusGuardPreferences
) {
    fun computeState(capability: Capability): CapabilityState = when (capability) {
        Capability.ACCESSIBILITY -> {
            if (accessChecker.isAccessibilityServiceConnected()) {
                CapabilityState.ACTIVE
            } else if (
                prefs.syncBlockedAppsCount > 0 ||
                prefs.syncSessionLockActive ||
                prefs.isOnboardingCompleteSync
            ) {
                CapabilityState.NEEDS_ACCESS
            } else {
                CapabilityState.NOT_NEEDED
            }
        }

        Capability.USAGE_ACCESS -> {
            if (accessChecker.isUsageAccessGranted()) {
                CapabilityState.ACTIVE
            } else if (prefs.syncActiveTimeLimitsCount > 0) {
                CapabilityState.NEEDS_ACCESS
            } else {
                CapabilityState.NOT_NEEDED
            }
        }

        Capability.OVERLAY -> {
            if (accessChecker.isOverlayGranted()) {
                CapabilityState.ACTIVE
            } else {
                CapabilityState.NOT_NEEDED
            }
        }

        Capability.DEVICE_ADMIN -> {
            if (accessChecker.isDeviceAdminGranted()) {
                CapabilityState.ACTIVE
            } else if (prefs.syncUninstallProtectionEnabled) {
                CapabilityState.NEEDS_ACCESS
            } else {
                CapabilityState.NOT_NEEDED
            }
        }

        Capability.NOTIFICATIONS -> if (accessChecker.isNotificationsGranted()) {
            CapabilityState.ACTIVE
        } else {
            CapabilityState.NOT_NEEDED
        }

        Capability.BATTERY_OPTIMIZATION -> {
            if (accessChecker.isBatteryOptimizationIgnored()) {
                CapabilityState.ACTIVE
            } else if (accessChecker.isAccessibilityGranted()) {
                CapabilityState.NEEDS_ACCESS
            } else {
                CapabilityState.NOT_NEEDED
            }
        }

        Capability.OEM_BACKGROUND -> {
            if (accessChecker.isOemBackgroundConfigured()) {
                CapabilityState.ACTIVE
            } else if (accessChecker.isAccessibilityGranted()) {
                CapabilityState.NEEDS_ACCESS
            } else {
                CapabilityState.NOT_NEEDED
            }
        }
    }

    fun computeAllStates(): Map<Capability, CapabilityState> =
        Capability.entries.associateWith(::computeState)

    fun resolveProtectionState(): ProtectionState {
        val hasConfiguredProtection =
            prefs.syncBlockedAppsCount > 0 ||
                    prefs.syncBlockedKeywordsCount > 0 ||
                    prefs.syncActiveTimeLimitsCount > 0 ||
                    prefs.syncActiveSchedulesCount > 0 ||
                    prefs.syncSessionLockActive ||
                    prefs.syncUninstallProtectionEnabled
        return ProtectionStatePolicy.resolve(
            ProtectionReadiness(
                isOnboardingComplete = prefs.isOnboardingCompleteSync,
                hasConfiguredProtection = hasConfiguredProtection,
                isAccessibilityEnabled = accessChecker.isAccessibilityGranted(),
                isAccessibilityServiceConnected = accessChecker.isAccessibilityServiceConnected(),
                uninstallProtectionEnabled = prefs.syncUninstallProtectionEnabled,
                isDeviceAdminActive = accessChecker.isDeviceAdminGranted()
            )
        )
    }

    fun computeHealthSummary(): ProtectionHealthSummary {
        val states = computeAllStates()
        val items = Capability.entries.map {
            getCapabilityDetails(it, states[it] ?: CapabilityState.NOT_NEEDED)
        }
        val protectionState = resolveProtectionState()
        return ProtectionHealthSummary(
            activeCount = items.count { it.isActive },
            neededCount = items.count { it.isNeeded },
            notNeededCount = items.count { it.isNotNeeded },
            isCoreProtectionActive = accessChecker.isAccessibilityServiceConnected(),
            protectionState = protectionState,
            items = items
        )
    }

    fun getCapabilityDetails(
        capability: Capability,
        state: CapabilityState? = null
    ): CapabilityDetails {
        val currentState = state ?: computeState(capability)
        return when (capability) {
            Capability.ACCESSIBILITY -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "App Blocking Protection",
                featureName = "App Blocker & Focus Sessions",
                whyNeeded = "FocusGuard uses Android Accessibility Service to detect when a blocked app is opened and display your focus block screen immediately.",
                benefits = listOf(
                    "Instantly blocks distracting apps (social media, games, shorts)",
                    "Enforces daily schedules and Mindful Pause countdowns",
                    "Enforces Deep Focus lockdown sessions"
                ),
                privacyPromise = "100% on-device. FocusGuard never reads passwords, private messages, bank details, or personal content.",
                actionButtonText = "Enable App Blocking"
            )

            Capability.USAGE_ACCESS -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "Screen Time & Daily Limits",
                featureName = "App Time Limits",
                whyNeeded = "To measure your daily app usage time and enforce limits, FocusGuard needs Usage Access from Android settings.",
                benefits = listOf(
                    "Track exact minutes spent on addictive apps",
                    "Automatically block apps when your daily cap is reached",
                    "Visual daily usage analytics and habit trends"
                ),
                privacyPromise = "Usage data is stored only in your phone's local database and never transmitted to any external server.",
                actionButtonText = "Enable Screen Time"
            )

            Capability.OVERLAY -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "Block Screen Overlay",
                featureName = "Display Over Other Apps",
                whyNeeded = "Allows FocusGuard to render the friction timer screen on top of restricted applications smoothly.",
                benefits = listOf(
                    "Smooth fullscreen block overlay",
                    "Shows custom motivational wallpapers and quotes",
                    "Provides graceful breathing pause allowance"
                ),
                privacyPromise = "Overlay is strictly used to display your custom block UI when an app is restricted.",
                actionButtonText = "Enable Overlay"
            )

            Capability.DEVICE_ADMIN -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "Tamper & Uninstall Protection",
                featureName = "Uninstall Protection",
                whyNeeded = "Device Admin privileges ensure you cannot impulsively uninstall FocusGuard or deactivate rules during moments of weakness.",
                benefits = listOf(
                    "Prevents impulsive uninstallation without commitment challenge",
                    "Protects device admin settings and auto-block shields",
                    "Respects your designated commitment delay timer"
                ),
                privacyPromise = "Device Admin is solely used for app-lock enforcement. No remote wipe or device monitoring is ever performed.",
                actionButtonText = "Activate Protection"
            )

            Capability.NOTIFICATIONS -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "Focus Reminders & Alerts",
                featureName = "Notifications",
                whyNeeded = "FocusGuard needs notification permission to alert you when daily limits are nearing, or when focus sessions finish.",
                benefits = listOf(
                    "5-minute warning before daily limit expires",
                    "Deep Focus session completion bells",
                    "Daily mindfulness check-ins"
                ),
                privacyPromise = "Zero spam or marketing. Only alerts configured directly by you.",
                actionButtonText = "Allow Notifications"
            )

            Capability.BATTERY_OPTIMIZATION -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "Background Reliability",
                featureName = "Battery Exemption",
                whyNeeded = "Prevents Android OS from putting FocusGuard to sleep in the background, ensuring 24/7 protection reliability.",
                benefits = listOf(
                    "Blocks apps reliably even after hours of inactivity",
                    "Prevents OS task-killer from stopping the blocking service",
                    "Ensures timely schedule transitions"
                ),
                privacyPromise = "FocusGuard uses lightweight event-driven hooks with minimal (< 1%) battery consumption.",
                actionButtonText = "Allow Background Running"
            )

            Capability.OEM_BACKGROUND -> CapabilityDetails(
                capability = capability,
                state = currentState,
                title = "Device Autostart & Keep-Alive",
                featureName = "OEM System Settings",
                whyNeeded = "Custom OEM interfaces (MIUI, ColorOS, OxygenOS, One UI) require enabling Autostart to prevent app killing.",
                benefits = listOf(
                    "Immunity to OEM task-killer aggressive cleanup",
                    "Persistent restart after device reboot"
                ),
                privacyPromise = "One-time hardware configuration guidance for your specific device brand.",
                actionButtonText = "Configure Device Settings"
            )
        }
    }
}
