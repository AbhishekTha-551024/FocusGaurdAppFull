package com.focusguard.app.capability

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessChecker: CapabilityAccessChecker,
    private val stateResolver: CapabilityStateResolver
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

    fun isAccessibilityGranted(): Boolean = accessChecker.isAccessibilityGranted()

    fun isAccessibilityServiceConnected(): Boolean = accessChecker.isAccessibilityServiceConnected()

    fun isUsageAccessGranted(): Boolean = accessChecker.isUsageAccessGranted()

    fun isOverlayGranted(): Boolean = accessChecker.isOverlayGranted()

    fun isDeviceAdminGranted(): Boolean = accessChecker.isDeviceAdminGranted()

    fun isNotificationsGranted(): Boolean = accessChecker.isNotificationsGranted()

    fun isBatteryOptimizationIgnored(): Boolean = accessChecker.isBatteryOptimizationIgnored()

    fun isOemBackgroundConfigured(): Boolean = accessChecker.isOemBackgroundConfigured()

    // ── Lifecycle-Aware Capability State Computations ─────────────────────────

    fun computeState(capability: Capability): CapabilityState =
        stateResolver.computeState(capability)

    fun computeAllStates(): Map<Capability, CapabilityState> =
        stateResolver.computeAllStates()

    fun computeHealthSummary(): ProtectionHealthSummary =
        stateResolver.computeHealthSummary()

    // ── Rich Contextual Details for Just-in-Time Explanation Prompts ──────────

    fun getCapabilityDetails(capability: Capability, state: CapabilityState? = null): CapabilityDetails =
        stateResolver.getCapabilityDetails(capability, state)

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
