package com.focusguard.app.core.engine.rules

import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.core.engine.BlockerRule
import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.BlockReason
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Invariant Rule #7: Anti-Cloning & Dual Apps Shield.
 * Evaluated before standard app blocks to immediately intercept OEM cloner processes.
 */
@Singleton
class AntiClonerRule @Inject constructor() : BlockerRule {
    override val ruleName: String = "AntiClonerRule"
    override val priority: Int = 10

    override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
        if (AntiClonerEngine.isClonerPackage(context.packageName)) {
            return BlockDecision.Block(
                reason = BlockReason.CLONER_OR_DUAL_APP,
                targetPackage = context.packageName,
                customMessage = "Dual Apps and Cloners are permanently prohibited by FocusGuard Shield."
            )
        }
        return null
    }
}
