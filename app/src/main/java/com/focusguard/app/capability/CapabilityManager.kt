package com.focusguard.app.capability

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.utils.BatteryOptimizationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences
) {

    private val _healthSummary = MutableStateFlow(computeHealthSummary())
    val healthSummary: StateFlow<ProtectionHealthSummary> = _healthSummary.asStateFlow()

    private val _capabilityStates = MutableStateFlow(computeAllStates())
    val capabilityStates: StateFlow<Map<Capability, CapabilityState>> = _capabilityStates.asStateFlow()

    init {
        try {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        refresh()
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.d("CapabilityManager", "registerContentObserver error: ${e.message}")
        }
    }

    fun refresh() {
        _capabilityStates.value = computeAllStates()
        _healthSummary.value = computeHealthSummary()
    }

    // ── Individual Capability Raw Access Checkers ─────────────────────────────

    fun isAccessibilityGranted(): Boolean {
        return com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(context)
    }

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isOverlayGranted(): Boolean {
        // On Vivo (OriginOS/ColorOS) and some Samsung/MIUI builds,
        // Settings.canDrawOverlays() returns true by default even before the user
        // has explicitly granted the permission. We cross-check with AppOpsManager
        // which gives the actual granted state as stored by the system.
        val canDrawViaSettings = Settings.canDrawOverlays(context)
        if (!canDrawViaSettings) return false

        // Secondary check: AppOpsManager gives the authoritative grant state
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            // If AppOpsManager check fails (shouldn't happen), trust canDrawOverlays
            canDrawViaSettings
        }
    }

    fun isDeviceAdminGranted(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun isNotificationsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        return BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
    }

    fun isOemBackgroundConfigured(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val isOemBrand = m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
                m.contains("oppo") || m.contains("realme") || m.contains("vivo") ||
                m.contains("iqoo") || m.contains("samsung") || m.contains("oneplus") ||
                m.contains("huawei") || m.contains("honor")
        return if (!isOemBrand) true else (isBatteryOptimizationIgnored() || prefs.syncOemGuidanceAcknowledged)
    }

    // ── Lifecycle-Aware Capability State Computations ─────────────────────────

    fun computeState(capability: Capability): CapabilityState {
        return when (capability) {
            Capability.ACCESSIBILITY -> {
                if (isAccessibilityGranted()) {
                    CapabilityState.ACTIVE
                } else {
                    // Needed if user has any active rules, or if onboarded
                    if (prefs.syncBlockedAppsCount > 0 || prefs.syncSessionLockActive || prefs.isOnboardingCompleteSync) {
                        CapabilityState.NEEDS_ACCESS
                    } else {
                        CapabilityState.NOT_NEEDED
                    }
                }
            }

            Capability.USAGE_ACCESS -> {
                if (isUsageAccessGranted()) {
                    CapabilityState.ACTIVE
                } else {
                    // Needed only if user configured any time limits
                    if (prefs.syncActiveTimeLimitsCount > 0) {
                        CapabilityState.NEEDS_ACCESS
                    } else {
                        CapabilityState.NOT_NEEDED
                    }
                }
            }

            Capability.OVERLAY -> {
                if (isOverlayGranted()) {
                    CapabilityState.ACTIVE
                } else if (isAccessibilityGranted()) {
                    // Accessibility overlay is sufficient for blocking, but we still
                    // recommend the explicit overlay permission for smoother UX
                    CapabilityState.NOT_NEEDED
                } else {
                    CapabilityState.NOT_NEEDED
                }
            }

            Capability.DEVICE_ADMIN -> {
                if (isDeviceAdminGranted()) {
                    CapabilityState.ACTIVE
                } else {
                    // Only needed if Uninstall Protection is explicitly requested / enabled
                    if (prefs.syncUninstallProtectionEnabled) {
                        CapabilityState.NEEDS_ACCESS
                    } else {
                        CapabilityState.NOT_NEEDED
                    }
                }
            }

            Capability.NOTIFICATIONS -> {
                if (isNotificationsGranted()) {
                    CapabilityState.ACTIVE
                } else {
                    CapabilityState.NOT_NEEDED
                }
            }

            Capability.BATTERY_OPTIMIZATION -> {
                if (isBatteryOptimizationIgnored()) {
                    CapabilityState.ACTIVE
                } else {
                    if (isAccessibilityGranted()) {
                        CapabilityState.NEEDS_ACCESS
                    } else {
                        CapabilityState.NOT_NEEDED
                    }
                }
            }

            Capability.OEM_BACKGROUND -> {
                if (isOemBackgroundConfigured()) {
                    CapabilityState.ACTIVE
                } else {
                    if (isAccessibilityGranted()) {
                        CapabilityState.NEEDS_ACCESS
                    } else {
                        CapabilityState.NOT_NEEDED
                    }
                }
            }
        }
    }

    fun computeAllStates(): Map<Capability, CapabilityState> {
        return Capability.entries.associateWith { computeState(it) }
    }

    fun computeHealthSummary(): ProtectionHealthSummary {
        val states = computeAllStates()
        val items = Capability.entries.map { getCapabilityDetails(it, states[it] ?: CapabilityState.NOT_NEEDED) }
        val active = items.count { it.isActive }
        val needed = items.count { it.isNeeded }
        val notNeeded = items.count { it.isNotNeeded }
        val coreActive = isAccessibilityGranted()

        return ProtectionHealthSummary(
            activeCount = active,
            neededCount = needed,
            notNeededCount = notNeeded,
            isCoreProtectionActive = coreActive,
            items = items
        )
    }

    // ── Rich Contextual Details for Just-in-Time Explanation Prompts ──────────

    fun getCapabilityDetails(capability: Capability, state: CapabilityState? = null): CapabilityDetails {
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

    // ── Safe System Settings Intent Providers ─────────────────────────────────

    fun getIntentForCapability(capability: Capability): Intent {
        return when (capability) {
            Capability.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Capability.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Capability.OVERLAY -> buildOverlayPermissionIntent()

            Capability.DEVICE_ADMIN -> {
                com.focusguard.app.service.FocusGuardAccessibilityService.allowAdminActivationWindow()
                val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Activate FocusGuard Device Admin to prevent impulsive uninstallation."
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            Capability.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else {
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }

            Capability.BATTERY_OPTIMIZATION -> {
                try {
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } catch (_: Exception) {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }

            Capability.OEM_BACKGROUND -> {
                // Fallback to application settings
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        }
    }

    /**
     * Builds the best-effort overlay permission intent for any Android device.
     *
     * Problem: On Vivo (ColorOS/OriginOS), Xiaomi (MIUI), and some Samsung builds,
     * `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with `package:com.focusguard.app` URI
     * either (a) opens a generic apps list without pre-selecting FocusGuard, or (b) crashes.
     *
     * Strategy:
     *  1. Try direct URI intent (works on stock Android, Pixel, most devices).
     *  2. If that fails or device is a known OEM, fall back to the generic list.
     *     The generic list still works — the user just needs to scroll and find FocusGuard.
     */
    private fun buildOverlayPermissionIntent(): Intent {
        val directIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // Check if a resolver exists for the direct-package intent.
        val pm = context.packageManager
        val canResolve = pm.resolveActivity(
            directIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        ) != null

        return if (canResolve) {
            directIntent
        } else {
            // Generic fallback: opens the full overlay-apps list
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
