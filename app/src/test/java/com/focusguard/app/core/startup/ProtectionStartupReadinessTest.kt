package com.focusguard.app.core.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionStartupReadinessTest {

    @Test
    fun `service connection alone is not protection readiness`() {
        val readiness = ProtectionStartupReadiness()

        readiness.beginAccessibilityConnection()

        assertTrue(readiness.state.value.accessibilityConnected)
        assertFalse(readiness.state.value.rulesSnapshotReady)
        assertFalse(readiness.isReady())
    }

    @Test
    fun `rules snapshot alone is not protection readiness`() {
        val readiness = ProtectionStartupReadiness()

        readiness.markRulesSnapshotReady()

        assertFalse(readiness.state.value.accessibilityConnected)
        assertTrue(readiness.state.value.rulesSnapshotReady)
        assertFalse(readiness.isReady())
    }

    @Test
    fun `both connection and rules snapshot are required`() {
        val readiness = ProtectionStartupReadiness()

        readiness.beginAccessibilityConnection()
        readiness.markRulesSnapshotReady()

        assertTrue(readiness.isReady())
    }

    @Test
    fun `a new accessibility connection resets rules readiness until snapshot arrives`() {
        val readiness = ProtectionStartupReadiness()
        readiness.beginAccessibilityConnection()
        readiness.markRulesSnapshotReady()
        assertTrue(readiness.isReady())

        readiness.beginAccessibilityConnection()

        assertFalse(readiness.isReady())
        readiness.markRulesSnapshotReady()
        assertTrue(readiness.isReady())
    }

    @Test
    fun `disconnect clears complete readiness state`() {
        val readiness = ProtectionStartupReadiness()
        readiness.beginAccessibilityConnection()
        readiness.markRulesSnapshotReady()

        readiness.markAccessibilityDisconnected()

        assertFalse(readiness.isReady())
        assertFalse(readiness.state.value.accessibilityConnected)
        assertFalse(readiness.state.value.rulesSnapshotReady)
    }
}
