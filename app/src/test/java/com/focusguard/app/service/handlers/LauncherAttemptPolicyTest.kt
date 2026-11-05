package com.focusguard.app.service.handlers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAttemptPolicyTest {

    @Test
    fun `coalesces duplicate events while overlay is attaching`() {
        val policy = LauncherAttemptPolicy()

        assertTrue(policy.begin(eventTime = 100L, overlayShowing = false))
        assertFalse(policy.begin(eventTime = 101L, overlayShowing = false))
    }

    @Test
    fun `allows a new attempt immediately after overlay dismissal`() {
        val policy = LauncherAttemptPolicy()

        assertTrue(policy.begin(eventTime = 100L, overlayShowing = false))
        policy.onOverlayAttached()
        policy.onOverlayDismissed()

        assertFalse(policy.shouldRetry(overlayShowing = false))
        assertTrue(policy.begin(eventTime = 101L, overlayShowing = false))
    }

    @Test
    fun `allows a new attempt after overlay attachment failure`() {
        val policy = LauncherAttemptPolicy()

        assertTrue(policy.begin(eventTime = 100L, overlayShowing = false))
        assertTrue(policy.shouldRetry(overlayShowing = false))
        policy.onAttachFailed()

        assertTrue(policy.begin(eventTime = 101L, overlayShowing = false))
    }

    @Test
    fun `does not attempt while another overlay is already showing`() {
        val policy = LauncherAttemptPolicy()

        assertFalse(policy.begin(eventTime = 100L, overlayShowing = true))
        assertTrue(policy.begin(eventTime = 100L, overlayShowing = false))
    }

    @Test
    fun `does not accept duplicate or older event timestamps`() {
        val policy = LauncherAttemptPolicy()

        assertTrue(policy.begin(eventTime = 100L, overlayShowing = false))
        policy.onAttachFailed()

        assertFalse(policy.begin(eventTime = 100L, overlayShowing = false))
        assertFalse(policy.begin(eventTime = 99L, overlayShowing = false))
    }
}
