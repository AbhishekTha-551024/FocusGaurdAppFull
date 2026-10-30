package com.focusguard.app.core.engine.rules

import com.focusguard.app.core.engine.BlockerRule
import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.BlockReason
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast-path (<0.01ms) rule intercepting explicitly blocked application packages.
 */
@Singleton
class BlockedAppRule @Inject constructor() : BlockerRule {
    override val ruleName: String = "BlockedAppRule"
    override val priority: Int = 20

    override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
        if (snapshot.blockedPackages.contains(context.packageName)) {
            return BlockDecision.Block(
                reason = BlockReason.APP_BLOCKED,
                targetPackage = context.packageName
            )
        }
        return null
    }
}
