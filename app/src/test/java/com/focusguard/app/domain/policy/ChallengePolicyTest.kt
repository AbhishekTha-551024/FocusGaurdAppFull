package com.focusguard.app.domain.policy

import com.focusguard.app.core.model.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ChallengePolicyTest
 * ===================
 * Pins the one rule that decides whether a cloud sync can quietly downgrade an accountability
 * challenge: the stricter type wins. A PARTNER challenge that regressed to TYPING is the exact
 * bypass a partner is supposed to close, so the mapping is tested in both directions plus the
 * no-op cases.
 */
class ChallengePolicyTest {

    @Test
    fun `cloud partner beats local typing`() {
        assertEquals(
            "A PARTNER challenge in the cloud must not be overwritten by a local TYPING type",
            ChallengeType.PARTNER,
            ChallengePolicy.stricter(ChallengeType.TYPING, ChallengeType.PARTNER)
        )
    }

    @Test
    fun `local partner is never downgraded to cloud typing`() {
        assertEquals(
            "A sync must never turn a local PARTNER challenge into a TYPING challenge",
            ChallengeType.PARTNER,
            ChallengePolicy.stricter(ChallengeType.PARTNER, ChallengeType.TYPING)
        )
    }

    @Test
    fun `equal types keep the local value`() {
        assertEquals(
            ChallengeType.PARTNER,
            ChallengePolicy.stricter(ChallengeType.PARTNER, ChallengeType.PARTNER)
        )
        assertEquals(
            ChallengeType.TYPING,
            ChallengePolicy.stricter(ChallengeType.TYPING, ChallengeType.TYPING)
        )
    }
}
