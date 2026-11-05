package com.focusguard.app.overlay.manager

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import com.focusguard.app.utils.SafeNodeUtils

/**
 * ActiveWindowProbe:
 * Answers whether a package still owns an application window on screen.
 *
 * Used by held block shields to know when the user actually left the blocked app. System
 * overlay noise (Vivo smart card, Play services dialogs) does not remove the blocked app's
 * window, so it cannot trick the shield into releasing.
 *
 * Window PRESENCE is the signal, not isActive/isFocused: while the shield is attached it owns
 * input focus, so the application window underneath reports active=false and focused=false even
 * though the blocked app is still on screen. As soon as the user goes Home or switches to
 * another app, the blocked app's window is removed from [AccessibilityService.getWindows].
 *
 * Fail-safe: when the window list is unavailable or no application window can be read, the
 * package is reported as present so a shield is never dropped by mistake.
 */
object ActiveWindowProbe {

    fun isPackageInForeground(service: AccessibilityService, pkg: String): Boolean {
        val windows = try {
            service.windows
        } catch (_: Exception) {
            null
        } ?: return true

        val target = pkg.lowercase()
        var sawReadableApplicationWindow = false

        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = try { w.root } catch (_: Exception) { null } ?: continue
            val windowPkg = try { root.packageName?.toString()?.lowercase() } catch (_: Exception) { null }
            SafeNodeUtils.safeRecycle(root)
            if (windowPkg == null) continue
            sawReadableApplicationWindow = true
            if (windowPkg == target) return true
        }

        return !sawReadableApplicationWindow
    }
}
