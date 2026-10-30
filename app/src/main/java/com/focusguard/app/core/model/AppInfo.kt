package com.focusguard.app.core.model

/**
 * AppInfo
 * =======
 * UI model representing an installed application.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false,
    val isAutoBlockedUnsupportedBrowser: Boolean = false,
    val isAutoBlockedNewInstall: Boolean = false
)
