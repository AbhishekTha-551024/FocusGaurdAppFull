package com.focusguard.app.feature.settings.domain

/**
 * Outcome of applying a challenge the user has already passed.
 *
 * Splitting "applied" from "retry me" is the whole point: a privilege change that depends on the
 * network must not silently half-happen. When the partner cannot be reached the user is still
 * accountable, still protected and still an Device Admin — the challenge simply did not succeed.
 */
sealed interface ChallengeApplyResult {

    /** The requested change was made; the caller may close the challenge. */
    data object Applied : ChallengeApplyResult

    /**
     * Nothing was changed and the same challenge can be confirmed again. [message] is shown to the
     * user as the reason, so it must never carry a raw backend error or any account identifier.
     */
    data class Retryable(val message: String) : ChallengeApplyResult
}
