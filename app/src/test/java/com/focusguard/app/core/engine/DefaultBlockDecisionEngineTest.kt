package com.focusguard.app.core.engine

import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.BlockReason
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBlockDecisionEngineTest {

    @Test
    fun whenRulesEmpty_returnsAllow() {
        val engine = DefaultBlockDecisionEngine(emptySet())
        val context = EventContext(packageName = "com.example.app")
        val snapshot = RulesSnapshot()

        val decision = engine.evaluate(context, snapshot)

        assertEquals(BlockDecision.Allow, decision)
    }

    @Test
    fun whenRuleTriggers_returnsDecision() {
        val testRule = object : BlockerRule {
            override val ruleName: String = "TestBlocker"
            override val priority: Int = 10
            override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
                return if (context.packageName == "com.distracting.app") {
                    BlockDecision.Block(
                        reason = BlockReason.APP_BLOCKED,
                        targetPackage = context.packageName
                    )
                } else null
            }
        }

        val engine = DefaultBlockDecisionEngine(setOf(testRule))
        val context = EventContext(packageName = "com.distracting.app")
        val snapshot = RulesSnapshot()

        val decision = engine.evaluate(context, snapshot)

        assertTrue(decision is BlockDecision.Block)
        val block = decision as BlockDecision.Block
        assertEquals("com.distracting.app", block.targetPackage)
        assertEquals(BlockReason.APP_BLOCKED, block.reason)
    }

    @Test
    fun whenMultipleRules_evaluatesByPriorityOrder() {
        val lowPriorityRule = object : BlockerRule {
            override val ruleName: String = "LowPriorityRule"
            override val priority: Int = 100
            override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
                return BlockDecision.Block(
                    reason = BlockReason.APP_BLOCKED,
                    targetPackage = "low"
                )
            }
        }

        val highPriorityRule = object : BlockerRule {
            override val ruleName: String = "HighPriorityRule"
            override val priority: Int = 10
            override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
                return BlockDecision.Block(
                    reason = BlockReason.CLONER_OR_DUAL_APP,
                    targetPackage = "high"
                )
            }
        }

        // Pass in unordered set (low priority first)
        val engine = DefaultBlockDecisionEngine(setOf(lowPriorityRule, highPriorityRule))
        val context = EventContext(packageName = "any.package")
        val snapshot = RulesSnapshot()

        val decision = engine.evaluate(context, snapshot)

        assertTrue(decision is BlockDecision.Block)
        val block = decision as BlockDecision.Block
        // High priority must win because priority 10 < 100
        assertEquals("high", block.targetPackage)
        assertEquals(BlockReason.CLONER_OR_DUAL_APP, block.reason)
    }

    @Test
    fun whenHighPriorityReturnsNull_evaluatesNextRule() {
        val highPriorityNoMatch = object : BlockerRule {
            override val ruleName: String = "HighPriorityNoMatch"
            override val priority: Int = 5
            override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? = null
        }

        val secondPriorityMatch = object : BlockerRule {
            override val ruleName: String = "SecondPriorityMatch"
            override val priority: Int = 15
            override fun evaluate(context: EventContext, snapshot: RulesSnapshot): BlockDecision? {
                return BlockDecision.Mindful(targetPackage = context.packageName, allowanceMinutes = 5)
            }
        }

        val engine = DefaultBlockDecisionEngine(setOf(highPriorityNoMatch, secondPriorityMatch))
        val context = EventContext(packageName = "com.mindful.app")
        val snapshot = RulesSnapshot()

        val decision = engine.evaluate(context, snapshot)

        assertTrue(decision is BlockDecision.Mindful)
        val mindful = decision as BlockDecision.Mindful
        assertEquals("com.mindful.app", mindful.targetPackage)
        assertEquals(5, mindful.allowanceMinutes)
    }
}
