package com.focusguard.app.service.utils

import com.focusguard.app.core.common.constants.Timings
import com.focusguard.app.overlay.manager.OverlayBlockType

/**
 * The one response FocusGuard is allowed to perform for a single intercepted tamper attempt.
 *
 * The distinction that matters is not "should we care" but "which action is safe right now":
 * Vivo's anti-tapjacking watchdog punishes `GLOBAL_ACTION_HOME` / `GLOBAL_ACTION_BACK` while its
 * security scan window is open, so a response that navigates would destroy the very service that
 * is trying to protect itself. Healing the secure setting and covering the screen with our own
 * Activity carries no such risk — an Activity start is not an accessibility global action.
 */
enum class TamperResponse {
    /** Haptic, HUD Toast, BACK then HOME, block overlay over Home. The normal full response. */
    FULL_RESPONSE,

    /**
     * Haptic, HUD Toast, re-assert the accessibility setting in place, and show the full-screen
     * recovery gate only when that silent re-assert could not be verified. Performs no global
     * accessibility action, so it is safe inside an OEM scan window.
     */
    HEAL_AND_COVER,

    /** Ignore the attempt entirely. Kept for non-self tamper classes inside an OEM scan window. */
    SILENT
}

/**
 * TamperResponsePolicy
 * ====================
 * Pure decision table separating "is this an attack on FocusGuard's own protection" from
 * "which response is safe right now", plus the pacing rule for the one response that no longer
 * navigates away. No android.* imports (AGENTS Rule 4) so the whole rule is
 * unit-testable without a device. Executed only by
 * [com.focusguard.app.service.handlers.TamperResponseHandler].
 */
object TamperResponsePolicy {

    /** Shield title used by every GUARD 4.1 accessibility self-protection intercept. */
    const val TARGET_ACCESSIBILITY_SETTINGS = "Accessibility Settings"

    /** Shield title used by GUARD 4.2 and the admin-loss paths. */
    const val TARGET_DEVICE_ADMINISTRATOR = "Device Administrator"

    /** Shield title used by the Hide Apps / Private Space path. */
    const val TARGET_HIDE_APPS_PROTECTION = "Hide Apps Protection"

    /**
     * True when the attempt targets FocusGuard's own Accessibility Service.
     *
     * This class is special because the OEM blanking that opens a scan window is triggered by the
     * user entering Settings — so "we are inside a scan window" and "the user is attacking the
     * accessibility toggle" are routinely the SAME moment. Suppressing that attempt means handing
     * the user the exact window in which the toggle can be flipped.
     */
    fun isSelfA11yProtectionTamper(targetName: String, blockType: OverlayBlockType): Boolean =
        targetName == TARGET_ACCESSIBILITY_SETTINGS &&
                blockType == OverlayBlockType.SETTINGS_TAMPER

    /**
     * Attempts that must never be swallowed by the scan window at all: they are the ones that
     * disable protection outright (Device Admin removal, uninstall, hidden-apps probing).
     */
    fun isExemptFromSuppression(
        targetName: String,
        blockType: OverlayBlockType,
        reason: String
    ): Boolean {
        return targetName == TARGET_DEVICE_ADMINISTRATOR ||
                targetName == TARGET_HIDE_APPS_PROTECTION ||
                blockType == OverlayBlockType.UNINSTALL_ATTEMPT ||
                reason.contains("Device Admin", ignoreCase = true)
    }

    /**
     * The single decision every tamper response goes through.
     *
     * Outside a scan window, or for an exempt class, the historical full response runs unchanged.
     * Inside one, a self-accessibility attempt switches to the tapjacking-safe heal-and-cover
     * response instead of disappearing, and everything else keeps its legacy suppression.
     */
    fun decide(
        isOemScanWindowActive: Boolean,
        isExemptFromSuppression: Boolean,
        isSelfA11yProtection: Boolean
    ): TamperResponse {
        if (!isOemScanWindowActive || isExemptFromSuppression) return TamperResponse.FULL_RESPONSE
        return if (isSelfA11yProtection) TamperResponse.HEAL_AND_COVER else TamperResponse.SILENT
    }

    /**
     * True when the visible self-accessibility contest may run again.
     *
     * [TamperResponse.HEAL_AND_COVER] deliberately does not navigate, so the user stays on the
     * Accessibility details screen and its `TYPE_WINDOW_CONTENT_CHANGED` stream keeps re-detecting
     * the same attempt (~4 per second while it scrolls). Un-paced, each of those is a Toast, a
     * haptic and a secure-setting write — and when the silent heal cannot be verified, a fresh
     * full-screen gate every 250 ms. Pacing the contest keeps the first response instant and lets
     * the setting-level detectors (ContentObserver, liveness probe) handle anything that happens
     * after it.
     *
     * Both arguments are `SystemClock.elapsedRealtime()` values (AGENTS invariant 4); a non-positive
     * [lastContestElapsed] means "never contested yet" and is always due.
     */
    fun isSelfA11yContestDue(lastContestElapsed: Long, nowElapsed: Long): Boolean {
        if (lastContestElapsed <= 0L) return true
        if (nowElapsed < lastContestElapsed) return true
        return nowElapsed - lastContestElapsed >= Timings.SELF_A11Y_CONTEST_DEBOUNCE_MS
    }
}
