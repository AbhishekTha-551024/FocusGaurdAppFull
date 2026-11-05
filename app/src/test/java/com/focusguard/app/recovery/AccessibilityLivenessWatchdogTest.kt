package com.focusguard.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the deliberate-removal half of the forced rebind: FocusGuard must disappear from the
 * enabled-services setting without taking any other accessibility service with it.
 */
class AccessibilityLivenessWatchdogTest {

    private val target = "com.focusguard.app/com.focusguard.app.service.FocusGuardAccessibilityService"
    private val shortTarget = "com.focusguard.app/.service.FocusGuardAccessibilityService"
    private val alarmy = "com.alarmy.android/.AlarmService"
    private val talkback = "com.google.android.marvin.talkback/com.google.android.accessibility.TalkBack"

    @Test
    fun `removes only focusguard and keeps other services`() {
        val current = "$alarmy:$target:$talkback"
        assertEquals(
            "$alarmy:$talkback",
            AccessibilityLivenessWatchdog.removeComponent(current, target, shortTarget)
        )
    }

    @Test
    fun `removes the short component variant`() {
        assertEquals(
            talkback,
            AccessibilityLivenessWatchdog.removeComponent("$shortTarget:$talkback", target, shortTarget)
        )
    }

    @Test
    fun `focusguard as the only entry yields an empty list`() {
        assertEquals("", AccessibilityLivenessWatchdog.removeComponent(target, target, shortTarget))
    }

    @Test
    fun `blank or null setting yields an empty list`() {
        assertEquals("", AccessibilityLivenessWatchdog.removeComponent("", target, shortTarget))
        assertEquals("", AccessibilityLivenessWatchdog.removeComponent(null, target, shortTarget))
    }

    @Test
    fun `entries are trimmed and empties dropped`() {
        val current = " $alarmy : $target : "
        assertEquals(
            alarmy,
            AccessibilityLivenessWatchdog.removeComponent(current, target, shortTarget)
        )
    }

    @Test
    fun `similar package names are not touched`() {
        val lookalike = "com.focusguard.app2/com.focusguard.app2.OtherService"
        assertEquals(
            "$lookalike:$alarmy",
            AccessibilityLivenessWatchdog.removeComponent("$lookalike:$alarmy:$target", target, shortTarget)
        )
    }
}
