package com.focusguard.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the non-clobbering merge used by the post-boot re-assert path: the enabled-services
 * setting must gain FocusGuard without silently deleting the user's other accessibility services
 * when an OEM tool blanks the setting for a few seconds.
 */
class EnabledServicesMergeTest {

    private val target = "com.focusguard.app/com.focusguard.app.service.FocusGuardAccessibilityService"
    private val shortTarget = "com.focusguard.app/.service.FocusGuardAccessibilityService"
    private val alarmy = "com.alarmy.android/.AlarmService"
    private val talkback = "com.google.android.marvin.talkback/com.google.android.accessibility.TalkBack"

    @Test
    fun `blanked setting falls back to the preserved list instead of wiping other services`() {
        val merged = ProgrammaticAccessibilityEnabler.mergeEnabledServices(
            currentServices = "",
            preservedServices = "$alarmy:$talkback",
            targetComponent = target
        )
        assertEquals("$alarmy:$talkback:$target", merged)
    }

    @Test
    fun `whitespace-only setting is treated as blanked`() {
        val merged = ProgrammaticAccessibilityEnabler.mergeEnabledServices(
            currentServices = "   ",
            preservedServices = alarmy,
            targetComponent = target
        )
        assertEquals("$alarmy:$target", merged)
    }

    @Test
    fun `null setting with no preserved list yields only the target`() {
        assertEquals(
            target,
            ProgrammaticAccessibilityEnabler.mergeEnabledServices(null, null, target)
        )
    }

    @Test
    fun `live setting list wins and keeps its own entries`() {
        val current = "$alarmy:$talkback"
        val merged = ProgrammaticAccessibilityEnabler.mergeEnabledServices(
            currentServices = current,
            preservedServices = "stale.example/.ShouldNotAppear",
            targetComponent = target
        )
        assertEquals("$current:$target", merged)
    }

    @Test
    fun `target already present is not duplicated`() {
        val current = "$alarmy:$target"
        assertEquals(
            current,
            ProgrammaticAccessibilityEnabler.mergeEnabledServices(current, current, target)
        )
    }

    @Test
    fun `preserved list containing target is not duplicated when setting is blanked`() {
        val preserved = "$alarmy:$target"
        assertEquals(
            preserved,
            ProgrammaticAccessibilityEnabler.mergeEnabledServices("", preserved, target)
        )
    }

    @Test
    fun `legacy two-argument builder keeps its original behaviour`() {
        assertEquals(target, ProgrammaticAccessibilityEnabler.buildEnabledServicesString(null, target))
        assertEquals(target, ProgrammaticAccessibilityEnabler.buildEnabledServicesString("", target))
        assertEquals("$alarmy:$target", ProgrammaticAccessibilityEnabler.buildEnabledServicesString(alarmy, target))
        assertEquals(alarmy, ProgrammaticAccessibilityEnabler.buildEnabledServicesString(alarmy, alarmy))
    }

    @Test
    fun `removal drops the target and keeps every other service`() {
        assertEquals(
            "$alarmy:$talkback",
            ProgrammaticAccessibilityEnabler.removeComponent("$alarmy:$target:$talkback", target, shortTarget)
        )
    }

    @Test
    fun `removal matches the target case-insensitively`() {
        assertEquals(
            alarmy,
            ProgrammaticAccessibilityEnabler.removeComponent(
                "$alarmy:$target",
                target.lowercase(),
                shortTarget.lowercase()
            )
        )
    }

    @Test
    fun `removal of the only entry yields blank`() {
        assertEquals(
            "",
            ProgrammaticAccessibilityEnabler.removeComponent(target, target, shortTarget)
        )
    }

    @Test
    fun `removal on a blank list stays blank instead of inventing entries`() {
        assertEquals(
            "",
            ProgrammaticAccessibilityEnabler.removeComponent("   ", target, shortTarget)
        )
        assertEquals(
            "",
            ProgrammaticAccessibilityEnabler.removeComponent(null, target, shortTarget)
        )
    }
}
