package com.focusguard.app.core.engine

import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot

/**
 * Single-responsibility rule contract for the evaluation engine.
 * Implementing classes must be pure Kotlin with no blocking I/O or Android UI calls.
 */
interface BlockerRule {
    /** Human-readable identifier for diagnostics and audit logging. */
    val ruleName: String

    /** Evaluation priority. Lower number = evaluated first. */
    val priority: Int

    /**
     * Evaluates the event context against the rules snapshot.
     *
     * @return [BlockDecision] if this rule triggers, or null to pass to the next rule in chain.
     */
    fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision?
}
