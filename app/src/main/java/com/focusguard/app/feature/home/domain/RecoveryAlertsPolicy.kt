package com.focusguard.app.feature.home.domain

/**
 * RecoveryAlertsPolicy
 * ====================
 * Decides when FocusGuard asks for the notification permission that makes auto-recovery visible.
 *
 * Recovery itself is silent by design (it must not depend on the user). But when the silent path
 * is unavailable, the only remaining channels are the recovery screen and a notification. The
 * notification is the one that survives a background activity-launch block, so Uninstall
 * Protection is only fully "visible" when notifications are allowed.
 *
 * Pure Kotlin on purpose (no android.* imports) so the rule is unit-testable.
 */
object RecoveryAlertsPolicy {

    /**
     * Prompt once, at the moment Uninstall Protection flips from off to on. Users who already had
     * protection armed are warned by [shouldShowWarning] instead of a modal on every app launch.
     */
    fun shouldPromptOnArm(
        wasProtectionActive: Boolean,
        isProtectionActive: Boolean,
        notificationsGranted: Boolean
    ): Boolean = !wasProtectionActive && isProtectionActive && !notificationsGranted

    /**
     * Keep a compact warning visible for as long as protection is on and alerts cannot reach the
     * user, so a later revocation of the permission does not silently re-open the blind spot.
     */
    fun shouldShowWarning(
        isProtectionActive: Boolean,
        notificationsGranted: Boolean
    ): Boolean = isProtectionActive && !notificationsGranted

    /**
     * Ask for the notification permission at the moment Uninstall Protection is armed: that is
     * the one instant the user is paying attention to what recovery means.
     */
    fun shouldRequestNotificationsOnArm(
        notificationsGranted: Boolean
    ): Boolean = !notificationsGranted

    /**
     * Fail-visible rule for arming. The recovery screen needs overlay permission and the recovery
     * notification needs notification permission. When BOTH are missing, recovery still runs but
     * can produce no user-visible output — arming must then be preceded by an explicit warning
     * instead of a silent gap (Phase B, plan G5).
     */
    fun shouldWarnBeforeArming(
        overlayGranted: Boolean,
        notificationsGranted: Boolean
    ): Boolean = !overlayGranted && !notificationsGranted
}
