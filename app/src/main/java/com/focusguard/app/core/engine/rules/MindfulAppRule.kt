package com.focusguard.app.core.engine.rules

import com.focusguard.app.core.engine.BlockerRule
import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Invariant Rule #10: Intercepts mindful apps to present breathing pause & conscious allowance.
 */
@Singleton
class MindfulAppRule @Inject constructor() : BlockerRule {
    override val ruleName: String = "MindfulAppRule"
    override val priority: Int = 30

    override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
        if (snapshot.mindfulPackages.contains(context.packageName)) {
            return BlockDecision.Mindful(
                targetPackage = context.packageName,
                allowanceMinutes = 5
            )
        }
        return null
    }
}
