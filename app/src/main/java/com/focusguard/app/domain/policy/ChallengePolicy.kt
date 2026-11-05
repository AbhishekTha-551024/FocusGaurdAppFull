package com.focusguard.app.domain.policy

import com.focusguard.app.core.model.ChallengeType

/**
 * ChallengePolicy
 * ===============
 * Pure rule for resolving a challenge-type disagreement between this device and the cloud copy:
 * the stricter type wins.
 *
 * PARTNER is stricter than TYPING because it needs a second human being to approve an unblock, and
 * that approval cannot be typed out, waited out or guessed. A sync that overwrites PARTNER with
 * TYPING therefore removes a real protection, while the opposite overwrite only makes the user work
 * harder. So a downgrade is never applied silently — the user drops out of PARTNER only through the
 * revoke flow, which clears the type explicitly.
 *
 * Pure Kotlin, no android.* imports (AGENTS Rule 4), so the rule is unit-testable.
 */
object ChallengePolicy {

    /**
     * Returns the stricter of the two types. Equal strength keeps [local], so a no-op comparison
     * never rewrites storage.
     */
    fun stricter(local: ChallengeType, cloud: ChallengeType): ChallengeType =
        if (strength(cloud) > strength(local)) cloud else local

    private fun strength(type: ChallengeType): Int = when (type) {
        ChallengeType.PARTNER -> STRENGTH_PARTNER
        ChallengeType.TYPING -> STRENGTH_TYPING
    }

    private const val STRENGTH_PARTNER = 2
    private const val STRENGTH_TYPING = 1
}
