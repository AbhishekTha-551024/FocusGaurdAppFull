package com.focusguard.app.ui.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shield is the only screen a locked-out user sees, so its wording is pinned here.
 *
 * The regression this guards: every cause used to render "PROTECTION PAUSED BY RESTART" and
 * "Your phone turned off Accessibility during device restart" even when the real trigger was a
 * Device Admin deactivation or a clone removal.
 */
class RecoveryShieldCopyTest {

    @Test
    fun `restart copy blames the restart`() {
        val copy = recoveryShieldCopy(RecoveryCause.RESTART)
        assertTrue(copy.badge.contains("RESTART", ignoreCase = true))
        assertTrue(copy.body.contains("restart", ignoreCase = true))
    }

    @Test
    fun `completed admin removal is described honestly`() {
        val copy = recoveryShieldCopy(RecoveryCause.ADMIN_REMOVED)
        assertFalse(copy.badge.contains("RESTART", ignoreCase = true))
        assertFalse(copy.title.contains("Accessibility", ignoreCase = true))
        assertTrue(copy.body.contains("Android allowed", ignoreCase = true))
        assertTrue(copy.body.contains("cannot veto", ignoreCase = true))
    }

    @Test
    fun `admin deactivation warning does not claim removal was blocked`() {
        val copy = recoveryShieldCopy(RecoveryCause.ADMIN_DEACTIVATION_REQUESTED)
        assertTrue(copy.body.contains("may allow", ignoreCase = true))
        assertTrue(copy.body.contains("cannot prevent", ignoreCase = true))
    }

    @Test
    fun `clone copy blames the clone`() {
        val copy = recoveryShieldCopy(RecoveryCause.CLONE_REMOVED)
        assertTrue(copy.badge.contains("CLONE", ignoreCase = true))
        assertTrue(copy.body.contains("cloned", ignoreCase = true))
        assertFalse(copy.body.contains("restart", ignoreCase = true))
    }

    @Test
    fun `turned off copy is neutral and never blames a restart`() {
        val copy = recoveryShieldCopy(RecoveryCause.TURNED_OFF)
        assertFalse(copy.badge.contains("RESTART", ignoreCase = true))
        assertFalse(copy.body.contains("restart", ignoreCase = true))
    }

    @Test
    fun `every cause has distinct badge and non blank text`() {
        val all = RecoveryCause.entries.map { recoveryShieldCopy(it) }
        assertEquals(RecoveryCause.entries.size, all.map { it.badge }.toSet().size)
        all.forEach { copy ->
            assertTrue(copy.badge.isNotBlank())
            assertTrue(copy.title.isNotBlank())
            assertTrue(copy.body.isNotBlank())
        }
    }

    @Test
    fun `no copy claims a one tap restore`() {
        RecoveryCause.entries.forEach { cause ->
            val copy = recoveryShieldCopy(cause)
            assertFalse(copy.title.contains("1 TAP", ignoreCase = true))
            assertFalse(copy.body.contains("one tap", ignoreCase = true))
        }
    }
}
