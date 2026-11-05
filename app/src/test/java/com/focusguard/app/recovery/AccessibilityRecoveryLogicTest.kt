package com.focusguard.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRecoveryLogicTest {

    private val targetComponent = "com.focusguard.app/com.focusguard.app.service.FocusGuardAccessibilityService"
    private val shortComponent = "com.focusguard.app/.service.FocusGuardAccessibilityService"

    @Test
    fun `isComponentEnabledInSetting matches full component name`() {
        val enabled = AccessibilityStatusChecker.isComponentEnabledInSetting(
            enabledServices = targetComponent,
            targetComponent = targetComponent,
            shortComponent = shortComponent
        )
        assertTrue("Full component should match", enabled)
    }

    @Test
    fun `isComponentEnabledInSetting matches short component name`() {
        val enabled = AccessibilityStatusChecker.isComponentEnabledInSetting(
            enabledServices = shortComponent,
            targetComponent = targetComponent,
            shortComponent = shortComponent
        )
        assertTrue("Short component should match", enabled)
    }

    @Test
    fun `isComponentEnabledInSetting matches case-insensitively`() {
        val lowerTarget = targetComponent.lowercase()
        val enabled = AccessibilityStatusChecker.isComponentEnabledInSetting(
            enabledServices = lowerTarget,
            targetComponent = targetComponent,
            shortComponent = shortComponent
        )
        assertTrue("Case insensitive component match should succeed", enabled)
    }

    @Test
    fun `isComponentEnabledInSetting matches within colon-delimited list`() {
        val setting = "com.android.talkback/com.google.android.marvin.talkback.TalkBackService:$targetComponent:com.example.other/.Service"
        val enabled = AccessibilityStatusChecker.isComponentEnabledInSetting(
            enabledServices = setting,
            targetComponent = targetComponent,
            shortComponent = shortComponent
        )
        assertTrue("Component in middle of list should match", enabled)
    }

    @Test
    fun `isComponentEnabledInSetting handles whitespace around items`() {
        val setting = "com.example.other/.Service :  $shortComponent  "
        val enabled = AccessibilityStatusChecker.isComponentEnabledInSetting(
            enabledServices = setting,
            targetComponent = targetComponent,
            shortComponent = shortComponent
        )
        assertTrue("Component with whitespace should match", enabled)
    }

    @Test
    fun `isComponentEnabledInSetting returns false for null or blank`() {
        assertFalse(
            "Null setting must return false",
            AccessibilityStatusChecker.isComponentEnabledInSetting(null, targetComponent, shortComponent)
        )
        assertFalse(
            "Blank setting must return false",
            AccessibilityStatusChecker.isComponentEnabledInSetting("   ", targetComponent, shortComponent)
        )
    }

    @Test
    fun `isComponentEnabledInSetting returns false when component is not in list`() {
        val setting = "com.android.talkback/com.google.android.marvin.talkback.TalkBackService:com.other.app/.Service"
        assertFalse(
            "Different services list must return false",
            AccessibilityStatusChecker.isComponentEnabledInSetting(setting, targetComponent, shortComponent)
        )
    }

    @Test
    fun `isComponentEnabledInSetting returns false for partial name collisions`() {
        val collision = "prefix.com.focusguard.app/.service.FocusGuardAccessibilityService"
        assertFalse(
            "Prefixed partial match must return false",
            AccessibilityStatusChecker.isComponentEnabledInSetting(collision, targetComponent, shortComponent)
        )
    }

    @Test
    fun `buildEnabledServicesString returns target when current services is null or blank`() {
        assertEquals(
            targetComponent,
            ProgrammaticAccessibilityEnabler.buildEnabledServicesString(null, targetComponent)
        )
        assertEquals(
            targetComponent,
            ProgrammaticAccessibilityEnabler.buildEnabledServicesString("", targetComponent)
        )
        assertEquals(
            targetComponent,
            ProgrammaticAccessibilityEnabler.buildEnabledServicesString("   ", targetComponent)
        )
    }

    @Test
    fun `buildEnabledServicesString does not duplicate when target is already present`() {
        val current = "com.google.talkback/.Service:$targetComponent"
        assertEquals(
            current,
            ProgrammaticAccessibilityEnabler.buildEnabledServicesString(current, targetComponent)
        )
    }

    @Test
    fun `buildEnabledServicesString appends target with colon when other services exist`() {
        val current = "com.google.talkback/.Service"
        val expected = "$current:$targetComponent"
        assertEquals(
            expected,
            ProgrammaticAccessibilityEnabler.buildEnabledServicesString(current, targetComponent)
        )
    }
}

