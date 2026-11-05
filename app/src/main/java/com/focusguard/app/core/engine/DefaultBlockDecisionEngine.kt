package com.focusguard.app.core.engine

import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [BlockDecisionEngine] utilizing a Chain of Responsibility.
 * Evaluates registered [BlockerRule] instances sorted by priority (lowest integer first).
 */
@Singleton
class DefaultBlockDecisionEngine @Inject constructor(
    private val rules: Set<@JvmSuppressWildcards BlockerRule>
) : BlockDecisionEngine {

    private val sortedRules: List<BlockerRule> by lazy {
        rules.sortedBy { it.priority }
    }

    override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision {
        for (rule in sortedRules) {
            val decision = rule.evaluate(context, snapshot)
            if (decision != null) {
                return decision
            }
        }
        return BlockDecision.Allow
    }
}
