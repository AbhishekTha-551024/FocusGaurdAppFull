package com.focusguard.app.ui.recovery

/**
 * Cause-specific text for the recovery shield.
 *
 * Pure Kotlin on purpose: the shield is the one screen a locked-out user sees, so its wording is
 * unit-tested instead of being verified by hand on a device after every edit.
 */
data class RecoveryShieldCopy(
    val badge: String,
    val title: String,
    val body: String
)

fun recoveryShieldCopy(cause: RecoveryCause): RecoveryShieldCopy = when (cause) {
    RecoveryCause.RESTART -> RecoveryShieldCopy(
        badge = "PROTECTION PAUSED BY RESTART",
        title = "Turn On Accessibility",
        body = "Your phone turned off Accessibility during device restart. To protect your blocked " +
                "apps and maintain your uninstall shield, Accessibility must be restored."
    )

    RecoveryCause.ADMIN_REMOVED -> RecoveryShieldCopy(
        badge = "DEVICE ADMIN REMOVED",
        title = "Restore FocusGuard Admin",
        body = "Android allowed Device Admin to be removed. A public app cannot veto that system " +
                "action. Re-enable FocusGuard Device Admin to restore the protection you selected."
    )

    RecoveryCause.ADMIN_DEACTIVATION_REQUESTED -> RecoveryShieldCopy(
        badge = "ADMIN DEACTIVATION WARNING",
        title = "Device Admin Is Still Active",
        body = "Android may allow Device Admin to be removed. FocusGuard can warn and ask you to " +
                "restore it, but a public app cannot prevent this system action."
    )

    RecoveryCause.CLONE_REMOVED -> RecoveryShieldCopy(
        badge = "APP CLONE REMOVED",
        title = "Turn On Accessibility",
        body = "A cloned copy of FocusGuard was removed, which switched Accessibility off. Turn " +
                "Accessibility back ON to keep your device protected."
    )

    RecoveryCause.TURNED_OFF -> RecoveryShieldCopy(
        badge = "ACCESSIBILITY IS OFF",
        title = "Turn On Accessibility",
        body = "Accessibility is off while Uninstall Protection is active. FocusGuard cannot block " +
                "apps or hold your uninstall shield until it is back ON."
    )
}
