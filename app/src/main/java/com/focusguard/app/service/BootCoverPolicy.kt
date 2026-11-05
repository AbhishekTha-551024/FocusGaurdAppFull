package com.focusguard.app.service

/**
 * Pure Kotlin evaluation rules for BootCover overlay lifecycle, race conditions, and fallbacks.
 * Zero android.* dependencies.
 *
 * Startup readiness requires both an active Accessibility connection and the first unified rules
 * snapshot. A connection by itself is not evidence that the blocking rules have initialized.
 */
object BootCoverPolicy {

    const val FALLBACK_DELAY_MS: Long = 35_000L
    const val MAX_COVER_DURATION_MS: Long = 150_000L

    /** Silent heal attempts the cover may run before leaving the user on the fallback screen. */
    const val MAX_HEAL_ATTEMPTS: Int = 8

    /**
     * Determines if BootCoverService should be started.
     */
    fun shouldStartService(isProtectionReady: Boolean, canDrawOverlays: Boolean): Boolean =
        !isProtectionReady && canDrawOverlays

    /**
     * Race condition check 1 & 2: abort overlay mounting only after startup readiness is confirmed.
     */
    fun shouldAbortMount(isProtectionReady: Boolean): Boolean =
        isProtectionReady

    /**
     * Race condition check 3: Determines if overlay must be removed immediately after view attachment.
     */
    fun shouldRemoveAfterMount(isProtectionReady: Boolean): Boolean =
        isProtectionReady

    /**
     * Fallback timer condition: Determines if UI should transition to fallback action state.
     */
    fun shouldTriggerFallback(
        isProtectionReady: Boolean,
        isOverlayAttached: Boolean
    ): Boolean = !isProtectionReady && isOverlayAttached

    /**
     * The cover exists only to hold the boot gap. It leaves once startup readiness is confirmed, or
     * once there is nothing left to guard (Uninstall Protection OFF — Invariant Rule 3).
     */
    fun shouldReleaseCover(isProtectionReady: Boolean, isProtectionActive: Boolean): Boolean =
        isProtectionReady || !isProtectionActive

    fun shouldReleaseAfterMaximumDuration(
        isProtectionReady: Boolean,
        isOverlayAttached: Boolean
    ): Boolean = !isProtectionReady && isOverlayAttached

    /**
     * One more silent heal is allowed only while protection is still armed and the boot budget holds.
     */
    fun shouldHealWhileCovered(isProtectionActive: Boolean, attemptsUsed: Int): Boolean =
        isProtectionActive && attemptsUsed < MAX_HEAL_ATTEMPTS
}
