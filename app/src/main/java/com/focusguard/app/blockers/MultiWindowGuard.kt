package com.focusguard.app.blockers

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.utils.SafeNodeUtils

/**
 * MultiWindowGuard:
 * Intercepts Split-Screen and Floating Pop-up Window bypass attempts.
 *
 * Prevents users from bypassing app blocks or Deep Focus Lockdown by placing
 * a blocked app (YouTube, Instagram) side-by-side with an allowed app or launcher.
 *
 * Invariant Rule 1: Always mounts block overlay first, before closing or routing home.
 */
object MultiWindowGuard {
    private const val TAG = "MultiWindowGuard"

    /**
     * Checks active interactive windows for multi-window / split-screen bypass attempts.
     *
     * @param service The running AccessibilityService instance.
     * @param repository FocusGuard repository for fast-path block and limit lookups.
     * @param sessionBlockManager Manages Deep Focus Lockdown state.
     * @param onIntercept Callback triggered when an unauthorized or blocked package is found.
     * @return true if a violation was found and intercepted, false otherwise.
     */
    fun checkAndInterceptMultiWindow(
        service: AccessibilityService,
        repository: FocusGuardRepository,
        sessionBlockManager: SessionBlockManager,
        onIntercept: (sourcePackage: String, reason: String, targetName: String, description: String) -> Unit
    ): Boolean {
        val windows = try {
            service.windows
        } catch (e: Exception) {
            Log.w(TAG, "Unable to retrieve windows tree: ${e.message}")
            null
        } ?: return false

        // Collect application windows once. TYPE_APPLICATION also contains the launcher window
        // that sits behind EVERY normal foreground app, so counting raw application windows
        // made a single full-screen app look like split-screen on every windows-changed event.
        // That re-triggered the block and remounted the shield in a loop.
        val applicationWindows = ArrayList<AccessibilityWindowInfo>(4)
        var hasPip = false
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            applicationWindows.add(w)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val method = AccessibilityWindowInfo::class.java.getMethod("isInPictureInPicture")
                    if (method.invoke(w) == true) {
                        hasPip = true
                    }
                } catch (e: Exception) {
                    android.util.Log.d("MultiWindowGuard", "isInPictureInPicture reflection error: ${e.message}")
                }
            }
        }

        val ownPkg = service.packageName

        // Multi-window is only real when at least two DIFFERENT third-party packages have
        // TOP-LEVEL windows (or a PiP window exists). Child/embedded windows belong to the app
        // that owns them, and a null root cannot prove a second app, so neither is counted.
        val distinctAppPackages = HashSet<String>()
        for (w in applicationWindows) {
            val parent = try { w.parent } catch (_: Exception) { null }
            if (parent != null) continue
            val rootNode = try { w.root } catch (_: Exception) { null }
            val pkg = try { rootNode?.packageName?.toString() } catch (_: Exception) { null }
            SafeNodeUtils.safeRecycle(rootNode)
            if (pkg.isNullOrBlank()) continue
            if (isSkippableWindowPackage(pkg, ownPkg)) continue
            distinctAppPackages.add(pkg.lowercase())
        }

        val isMultiWindowActive = distinctAppPackages.size >= 2 || hasPip
        if (!isMultiWindowActive) {
            return false
        }

        val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()

        for (window in applicationWindows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

            val rootNode = window.root ?: continue
            try {
                val pkg = rootNode.packageName?.toString() ?: continue
                // Guard 1: Never intercept FocusGuard's own activities or overlays
                if (pkg == ownPkg || pkg.startsWith("com.focusguard.app")) continue
                // System UI (status bar / nav bar controls)
                val lowerPkg = pkg.lowercase()
                if (lowerPkg == com.focusguard.app.core.common.constants.OemPackages.SYSTEM_UI || lowerPkg == "android") continue

                // When Deep Focus is NOT active, skip launchers (launchers are handled by normal blocker)
                if (!isSessionActive && (lowerPkg.contains("launcher") || lowerPkg.contains("quickstep") ||
                    lowerPkg.contains("trebuchet") || lowerPkg == "com.bbk.launcher2")) continue

                // 1. Check Deep Focus Lockdown Violation in Split-Screen
                if (isSessionActive) {
                    val isBlocked = sessionBlockManager.isPackageBlockedInSession(pkg)
                    if (isBlocked) {
                        Log.w(TAG, "Multi-window bypass detected unauthorized app/launcher during Deep Focus: $pkg")
                        onIntercept(
                            pkg,
                            "Split-Screen App Blocked in Deep Focus: $pkg",
                            "Deep Focus Lockdown",
                            "Split-screen and floating pop-up windows cannot be used to run unapproved apps or launchers during Deep Focus."
                        )
                        return true
                    }
                }

                // 2. Check Hard Blocklist & Daily Time Limits in Split-Screen
                val isHardBlocked = repository.isFastBlockedApp(pkg)
                val isTimeLimitExceeded = repository.isFastTimeLimitExceeded(pkg)
                if (isHardBlocked || isTimeLimitExceeded) {
                    val reason = if (isTimeLimitExceeded) {
                        "Daily Limit Exceeded in Split-Screen: $pkg"
                    } else {
                        "Blocked App in Split-Screen: $pkg"
                    }
                    val desc = if (isTimeLimitExceeded) {
                        "This app has exceeded its daily limit and cannot be used in split-screen or floating window mode."
                    } else {
                        "This app is in your blocklist and cannot be run side-by-side in split-screen."
                    }
                    Log.w(TAG, "Multi-window bypass detected blocked app: $pkg ($reason)")
                    onIntercept(pkg, reason, pkg, desc)
                    return true
                }

                // 3. Check Anti-Cloning in Split-Screen
                val isProtectionActive = repository.isFastTimedLockActive() || repository.isFastUninstallProtectionEnabled()
                if ((isProtectionActive || isSessionActive) && repository.isFastAntiCloningShieldEnabled() && AntiClonerEngine.isClonerPackage(pkg)) {
                    Log.w(TAG, "Multi-window bypass detected cloner app: $pkg")
                    onIntercept(
                        pkg,
                        "Cloner App Blocked in Split-Screen",
                        "App Cloner",
                        "Virtual containers and app cloners cannot be run in split-screen or pop-up mode."
                    )
                    return true
                }
            } finally {
                SafeNodeUtils.safeRecycle(rootNode)
            }
        }

        return false
    }

    /**
     * Windows that can never represent a second, bypassing app in a split-screen pair:
     * FocusGuard itself, SystemUI, launchers and IMEs.
     */
    private fun isSkippableWindowPackage(pkg: String, ownPkg: String): Boolean {
        val lower = pkg.lowercase()
        if (pkg == ownPkg || lower.startsWith("com.focusguard.app")) return true
        if (lower == com.focusguard.app.core.common.constants.OemPackages.SYSTEM_UI || lower == "android") return true
        if (lower.contains("launcher") || lower.contains("quickstep") ||
            lower.contains("trebuchet") || lower == "com.bbk.launcher2"
        ) return true
        if (lower.contains("inputmethod") || lower.contains("keyboard")) return true
        return false
    }
}
