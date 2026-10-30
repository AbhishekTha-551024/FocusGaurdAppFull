package com.focusguard.app.core.model

/**
 * Pure domain representation of the verdict produced by the decision engine.
 * Completely decoupled from Android UI or WindowManager implementations.
 */
sealed interface BlockDecision {
    /**
     * Target application or window action is allowed without friction.
     */
    data object Allow : BlockDecision

    /**
     * Target application is blocked and should mount an overlay followed by HOME action.
     */
    data class Block(
        val reason: BlockReason,
        val targetPackage: String,
        val customMessage: String? = null
    ) : BlockDecision

    /**
     * Target application requires an intentional mindful breathing pause & conscious allowance.
     */
    data class Mindful(
        val targetPackage: String,
        val allowanceMinutes: Int = 5,
        val isReturnSession: Boolean = false
    ) : BlockDecision

    /**
     * A tamper attempt, cloner, or clock rollback was detected. Immediate security lockdown.
     */
    data class TamperLock(
        val reason: String,
        val detectedAtMonotonicMs: Long
    ) : BlockDecision

    /**
     * A short-form video feed (Reels/Shorts) node was matched inside a normally allowed app.
     */
    data class FeedBlock(
        val platform: FeedPlatform,
        val matchedViewId: String,
        val targetPackage: String
    ) : BlockDecision
}
