package com.focusguard.app.core.engine

import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot

/**
 * Pure Kotlin decision coordinator. Evaluates chained rules in priority order.
 */
interface BlockDecisionEngine {
    /**
     * Evaluates an incoming event against the active rules snapshot.
     *
     * @return [BlockDecision] verdict to be executed by the platform service layer.
     */
    fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision
}
