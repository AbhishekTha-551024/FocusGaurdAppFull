package com.focusguard.app.ui.recovery

/**
 * Why the recovery shield is on screen.
 *
 * [AccessibilityRecoveryActivity] is started from five different places (boot, device-admin
 * deactivation, clone removal, tamper detection, OEM kill). Each one passes a free-form reason
 * sentence. The shield used to ignore that sentence and always say the phone turned Accessibility
 * off "during device restart", which was false for four of the five callers.
 */
enum class RecoveryCause(
    val isAdminBlock: Boolean = false,
    val requiresAdminReactivation: Boolean = false
) {
    /** Device restarted or powered on; the OS did not bind the service. */
    RESTART,

    /** Android allowed FocusGuard's Device Admin to be removed while protection was stored ON. */
    ADMIN_REMOVED(isAdminBlock = true, requiresAdminReactivation = true),

    /** Android is showing the preflight warning before a possible admin removal. */
    ADMIN_DEACTIVATION_REQUESTED(isAdminBlock = true),

    /** A cloned copy of FocusGuard was removed and took the Accessibility grant with it. */
    CLONE_REMOVED,

    /** Accessibility is off for any other reason (manual toggle, OEM kill, app update). */
    TURNED_OFF;

    companion object {
        // Cause keywords. Named so a future caller cannot silently fall through to the wrong copy.
        private const val KEY_ADMIN = "admin"
        private const val KEY_CLONE = "clon"
        private const val KEY_RESTART = "restart"
        private const val KEY_BOOT = "boot"
        private const val KEY_POWER = "power"

        /**
         * Classifies a reason string. Deliberately tolerant of the existing free-form sentences
         * ("Admin Deactivation Blocked: …", "Device Admin was removed while …",
         * "A cloned instance was removed, …",
         * "Accessibility is off (BootReceiver:BOOT_COMPLETED).") because all callers are unchanged.
         *
         * Anything unrecognised falls back to [TURNED_OFF], whose copy is neutral but still true.
         */
        fun fromReason(reason: String): RecoveryCause {
            val text = reason.lowercase()
            return when {
                text.contains("device admin was removed") || text == "device admin removed" ->
                    ADMIN_REMOVED
                text.contains(KEY_ADMIN) -> ADMIN_DEACTIVATION_REQUESTED
                text.contains(KEY_CLONE) -> CLONE_REMOVED
                text.contains(KEY_RESTART) || text.contains(KEY_BOOT) || text.contains(KEY_POWER) ->
                    RESTART
                else -> TURNED_OFF
            }
        }
    }
}
