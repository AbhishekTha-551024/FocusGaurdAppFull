package com.focusguard.app.overlay.manager

/**
 * OverlayHoldPolicy:
 * Pure decision that answers one question: should a held block shield be released because the
 * user moved to a different foreground package?
 *
 * Extracted from ActiveOverlayGuard so the rule is unit-testable without Android.
 */
object OverlayHoldPolicy {

    /**
     * @param holdPackage package that owns the held shield (lower-case), or null for transient shields.
     * @param isWindowStateChanged true for AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED.
     * @param foregroundPackage package reported by the latest accessibility event.
     * @param isKeyboard event came from an IME (typing must not release the shield).
     * @param isSettings event came from Settings / OEM security (protection path handles it).
     * @param isInstaller event came from a package installer / store (protection path handles it).
     * @param isCloner event came from an app cloner / virtual container (protection path handles it).
     * @param isRecents SystemUI event is the recents / overview screen (user is switching apps).
     * @param heldPackageIsActive the blocked package still owns the active application window.
     *        System overlay noise (Vivo card, Play services dialogs) does not change this, so it
     *        keeps the shield mounted.
     */
    fun shouldReleaseForForeground(
        holdPackage: String?,
        isWindowStateChanged: Boolean,
        foregroundPackage: String,
        isKeyboard: Boolean,
        isSettings: Boolean,
        isInstaller: Boolean,
        isCloner: Boolean,
        isRecents: Boolean,
        heldPackageIsActive: Boolean
    ): Boolean {
        if (holdPackage.isNullOrBlank()) return false
        if (!isWindowStateChanged) return false
        if (foregroundPackage.equals(holdPackage, ignoreCase = true)) return false

        val lower = foregroundPackage.lowercase()
        // System dialogs are not a user-initiated app switch.
        if (lower == "android") return false
        // SystemUI events are noisy (nav bar, shade, volume panel). Only the recents screen
        // means the user is actually leaving the blocked app.
        if (lower == "com.android.systemui") return isRecents
        // These packages are handled by dedicated protection paths while the shield is up.
        if (isKeyboard || isSettings || isInstaller || isCloner) return false
        // A transient system overlay (com.vivo.card, play services dialog, ...) can report a
        // window-state change without the blocked app ever leaving the foreground.
        if (heldPackageIsActive) return false

        // Launcher or any other app: the user left the blocked app, so the shield must not
        // float over the new screen.
        return true
    }
}
