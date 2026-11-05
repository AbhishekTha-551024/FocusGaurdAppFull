package com.focusguard.app.service.utils

/**
 * Pure Kotlin string-matching utilities for SystemUI shade/recents window classes
 * and OEM settings/accessibility package identifiers.
 *
 * Rules:
 * - Domain/Pure logic: No android.* dependencies.
 * - Logic copied verbatim from FocusGuardAccessibilityService guards.
 */
object ClassNameMatchers {

    /**
     * Returns true if [className] corresponds to the SystemUI notification shade
     * or quick settings panel (GUARD 1.1).
     */
    fun isShadeClass(className: String): Boolean {
        return className.contains("NotificationShade", ignoreCase = true) ||
                className.contains("NotificationPanel", ignoreCase = true) ||
                className.contains("QuickSettings", ignoreCase = true) ||
                className.contains("Shade", ignoreCase = true)
    }

    /**
     * Returns true if [className] corresponds to the SystemUI notification shade,
     * quick settings, or recents/overview multitasking screen (GUARD 1.145).
     *
     * Evaluates the 4 shade checks first, then Recents, then Overview (exact order preserved).
     */
    fun isShadeOrRecentsClass(className: String): Boolean {
        return isShadeClass(className) ||
                className.contains("Recents", ignoreCase = true) ||
                className.contains("Overview", ignoreCase = true)
    }

    /**
     * Returns true if [className] corresponds to the recents / overview multitasking screen.
     * Used to release a held block shield when the user switches apps from recents.
     */
    fun isRecentsClass(className: String): Boolean {
        return className.contains("Recents", ignoreCase = true) ||
                className.contains("Overview", ignoreCase = true)
    }

    /**
     * Returns true if [pkg] matches known Android/OEM settings, security center,
     * or permission management package substrings (sweepActiveWindowOnConnect).
     */
    fun isSettingsOrA11yPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("settings") ||
                lower.contains("imanager") ||
                lower.contains("safecenter") ||
                lower.contains("permissionmanager") ||
                lower.contains("permissioncontroller") ||
                lower.contains("securitycenter")
    }
}

