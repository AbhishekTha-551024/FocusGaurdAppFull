package com.focusguard.app.core.engine.rules

import com.focusguard.app.core.engine.BlockerRule
import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Invariant Rule #2: FocusGuard must never inspect or block its own UI activities,
 * dialogs, or overlays.
 */
@Singleton
class SelfPackageExclusionRule @Inject constructor() : BlockerRule {
    override val ruleName: String = "SelfPackageExclusionRule"
    override val priority: Int = 0 // Evaluated FIRST before any block checks

    override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
        val pkg = context.packageName
        if (pkg == "com.focusguard.app" || pkg.startsWith("com.focusguard.app.")) {
            return BlockDecision.Allow
        }
        return null
    }
}
