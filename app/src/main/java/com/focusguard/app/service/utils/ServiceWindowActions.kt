package com.focusguard.app.service.utils

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

private const val TAG = "ServiceWindowActions"

/**
 * ServiceWindowActions
 * ====================
 * Encapsulates global window actions (HOME, BACK), haptic feedback,
 * home launcher intent launching, and process termination for FocusGuardAccessibilityService.
 */
class ServiceWindowActions(
    private val service: AccessibilityService,
    private val isSystemPackage: (String) -> Boolean,
    private val isLauncherPackage: (String) -> Boolean
) {

    fun triggerBlockHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = service.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(45)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Haptic vibration error: ${e.message}")
        }
    }

    fun dismissUnauthorizedAppInDeepFocus(pkg: String) {
        try {
            val isSystem = isSystemPackage(pkg)
            val isLauncher = isLauncherPackage(pkg)

            // 1. Send BACK to dismiss any open app drawers, folders, recents, or popup menus
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

            // 2. Kill background processes for 3rd-party unallowed apps to stop PiP video, audio, etc.
            if (!isSystem && !isLauncher) {
                val am = service.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(pkg)
                Log.d(TAG, "Killed background processes for $pkg during Deep Focus lockdown.")
            }

            // Invariant: NEVER send GLOBAL_ACTION_HOME or startActivity(homeIntent) in Deep Focus!
            // Deep Focus is a Kiosk. Returning HOME would hide SessionLockActivity behind the launcher.
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing unauthorized app $pkg in Deep Focus", e)
        }
    }

    fun forceCloseApp(pkg: String) {
        try {
            val isSystem = isSystemPackage(pkg)
            val isLauncher = isLauncherPackage(pkg)

            // CRITICAL: Never send GLOBAL_ACTION_BACK to system settings!
            // Sending BACK in settings navigates to a DIFFERENT settings page, which then fires
            // another TYPE_WINDOW_STATE_CHANGED event that can trigger another blocking round,
            // creating an infinite loop. Only send BACK for 3rd-party app video players.
            if (!isSystem) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }

            // ── FIX: If pkg is the launcher, we still MUST send HOME + start home intent.
            // Previously we returned early here which meant the user stayed on the launcher
            // and could immediately hold FocusGuard again. Now we always proceed to HOME.
            // We skip BACK (done above for non-system) but always send HOME.

            // 2. Return to Home screen
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

            // 3. Kill background processes for 3rd party apps only (Chess, YouTube, etc.)
            // NEVER kill system packages -- this caused the AccessibilityService to become unbound!
            if (!isSystem && !isLauncher) {
                val am = service.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(pkg)
                Log.d(TAG, "Killed background processes for $pkg to eliminate PiP window.")
            }

            // 4. Bring home launcher to top
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            service.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error force-closing $pkg", e)
        }
    }
}

