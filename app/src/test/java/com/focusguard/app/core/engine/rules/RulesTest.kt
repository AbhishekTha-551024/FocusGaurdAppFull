package com.focusguard.app.core.engine.rules

import com.focusguard.app.core.model.BlockDecision
import com.focusguard.app.core.model.BlockReason
import com.focusguard.app.core.model.EventContext
import com.focusguard.app.core.model.RulesSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun selfPackageExclusionRule_allowsFocusGuardPackages() {
        val rule = SelfPackageExclusionRule()
        val snapshot = RulesSnapshot()

        val selfContext = EventContext(packageName = "com.focusguard.app")
        assertEquals(BlockDecision.Allow, rule.evaluate(selfContext, snapshot))

        val subContext = EventContext(packageName = "com.focusguard.app.ui.dialog")
        assertEquals(BlockDecision.Allow, rule.evaluate(subContext, snapshot))

        val otherContext = EventContext(packageName = "com.facebook.katana")
        assertNull(rule.evaluate(otherContext, snapshot))
    }

    @Test
    fun antiClonerRule_blocksOemAndVirtualCloners() {
        val rule = AntiClonerRule()
        val snapshot = RulesSnapshot()

        val oplusContext = EventContext(packageName = "com.oplus.multiapp")
        val oplusDecision = rule.evaluate(oplusContext, snapshot)
        assertTrue(oplusDecision is BlockDecision.Block)
        assertEquals(BlockReason.CLONER_OR_DUAL_APP, (oplusDecision as BlockDecision.Block).reason)

        val parallelContext = EventContext(packageName = "com.parallel.space.lite")
        val parallelDecision = rule.evaluate(parallelContext, snapshot)
        assertTrue(parallelDecision is BlockDecision.Block)
        assertEquals(BlockReason.CLONER_OR_DUAL_APP, (parallelDecision as BlockDecision.Block).reason)

        val normalContext = EventContext(packageName = "com.google.android.apps.docs")
        assertNull(rule.evaluate(normalContext, snapshot))
    }

    @Test
    fun blockedAppRule_blocksPackageInSnapshot() {
        val rule = BlockedAppRule()
        val snapshot = RulesSnapshot(blockedPackages = setOf("com.twitter.android", "com.snapchat.android"))

        val blockedContext = EventContext(packageName = "com.twitter.android")
        val decision = rule.evaluate(blockedContext, snapshot)
        assertTrue(decision is BlockDecision.Block)
        val block = decision as BlockDecision.Block
        assertEquals(BlockReason.APP_BLOCKED, block.reason)
        assertEquals("com.twitter.android", block.targetPackage)

        val allowedContext = EventContext(packageName = "com.slack")
        assertNull(rule.evaluate(allowedContext, snapshot))
    }

    @Test
    fun mindfulAppRule_triggersMindfulPauseForTarget() {
        val rule = MindfulAppRule()
        val snapshot = RulesSnapshot(mindfulPackages = setOf("com.reddit.frontpage"))

        val mindfulContext = EventContext(packageName = "com.reddit.frontpage")
        val decision = rule.evaluate(mindfulContext, snapshot)
        assertTrue(decision is BlockDecision.Mindful)
        val mindful = decision as BlockDecision.Mindful
        assertEquals("com.reddit.frontpage", mindful.targetPackage)
        assertEquals(5, mindful.allowanceMinutes)

        val nonMindfulContext = EventContext(packageName = "com.calculator")
        assertNull(rule.evaluate(nonMindfulContext, snapshot))
    }
}
