package com.focusguard.app.core.common.constants

/**
 * Centralized registry of foreground service and status bar notification IDs.
 */
object NotificationIds {
    /** Foreground notification ID for Adult Content DNS VPN filter */
    const val ID_VPN_SERVICE = 9001

    /** Foreground notification ID for Boot Cover shield overlay */
    const val ID_BOOT_COVER_SHIELD = 9905

    /** Notification ID for Accessibility Health Worker ghost-accessibility warning */
    const val ID_ACCESSIBILITY_HEALTH_ALERT = 9905

    /** Notification ID for active Deep Focus session countdown */
    const val ID_FOCUS_SESSION_ONGOING = 4040

    /** Notification ID for completed Deep Focus session alert */
    const val ID_FOCUS_SESSION_COMPLETED = 4041
}

