package com.focusguard.app.overlay.standard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.focusguard.app.overlay.manager.OverlayBlockType

/**
 * Static text and styling for [BlockOverlayContent]. Kept apart from the layout so the
 * composable file stays within the project's 300-line rule.
 */
internal data class OverlayHeading(
    val headline: String,
    val accentColor: Color,
    val icon: ImageVector
)

internal fun resolveHeading(blockType: OverlayBlockType, targetName: String): OverlayHeading = when (blockType) {
    OverlayBlockType.UNINSTALL_ATTEMPT -> OverlayHeading("Uninstall Protected", Rose400, Icons.Default.Security)
    OverlayBlockType.SETTINGS_TAMPER -> OverlayHeading("Settings Protected", Rose400, Icons.Default.Lock)
    OverlayBlockType.APP_BLOCK -> OverlayHeading("App Blocked", Indigo400, Icons.Default.Block)
    OverlayBlockType.KEYWORD_BLOCK -> OverlayHeading("Keyword Blocked", Rose400, Icons.Default.Language)
    OverlayBlockType.SCHEDULE_BLOCK -> OverlayHeading("Focus Schedule", Amber400, Icons.Default.Schedule)
    OverlayBlockType.TIME_LIMIT_BLOCK -> OverlayHeading("Daily Limit Reached", Purple400, Icons.Default.HourglassTop)
    OverlayBlockType.SHORT_OR_REEL_BLOCK -> {
        val title = when {
            targetName.contains("Shorts", ignoreCase = true) -> "Shorts Blocked"
            targetName.contains("Reels", ignoreCase = true) -> "Reels Blocked"
            else -> "Shorts & Reels Blocked"
        }
        OverlayHeading(title, Rose400, Icons.Default.PlayCircle)
    }
    OverlayBlockType.INCOGNITO_BLOCK -> OverlayHeading("Incognito Blocked", Rose400, Icons.Default.VisibilityOff)
    OverlayBlockType.CLONER_BLOCK -> OverlayHeading("App Cloner Blocked", Rose400, Icons.Default.Security)
    OverlayBlockType.SESSION_LOCK_BLOCK -> OverlayHeading("Deep Focus Lockdown", Amber400, Icons.Default.Lock)
}

internal fun resolveDescription(
    blockType: OverlayBlockType,
    contextLine: String?,
    targetName: String
): String {
    if (!contextLine.isNullOrBlank()) return contextLine
    return when (blockType) {
        OverlayBlockType.UNINSTALL_ATTEMPT -> "Uninstall protection is ON, so FocusGuard can't be removed right now."
        OverlayBlockType.SETTINGS_TAMPER -> "This settings screen is protected so your blocking can't be switched off."
        OverlayBlockType.APP_BLOCK -> "This app is on your block list."
        OverlayBlockType.KEYWORD_BLOCK -> {
            if (targetName.isNotBlank()) "Keyword “$targetName” is in your blocklist."
            else "This content matched a keyword in your blocklist."
        }
        OverlayBlockType.SCHEDULE_BLOCK -> "This app is blocked during your focus schedule."
        OverlayBlockType.TIME_LIMIT_BLOCK -> "Your daily limit for this app is used up. It resets at midnight."
        OverlayBlockType.SHORT_OR_REEL_BLOCK -> "Short-form video is blocked."
        OverlayBlockType.INCOGNITO_BLOCK -> "Private browsing is blocked."
        OverlayBlockType.CLONER_BLOCK -> "App cloners are blocked."
        OverlayBlockType.SESSION_LOCK_BLOCK -> "A Deep Focus session is running."
    }
}
