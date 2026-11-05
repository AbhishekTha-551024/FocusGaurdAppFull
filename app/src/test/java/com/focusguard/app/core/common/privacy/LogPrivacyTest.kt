package com.focusguard.app.core.common.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LogPrivacyTest
 * ==============
 * Unit tests for [LogPrivacy].
 *
 * FocusGuard's logs leave the phone (bug reports, `adb logcat` during support), so a line must be able
 * to correlate one device with another without carrying the account email or a replayable device id.
 */
class LogPrivacyTest {

    private val email = "Someone.Example@FocusGuard.App"

    @Test
    fun emailRefCarriesNoAddress() {
        val ref = LogPrivacy.emailRef(email)
        assertFalse("The address itself must never reach a log", ref.contains("focusguard", ignoreCase = true))
        assertFalse("An '@' would still identify a mailbox", ref.contains("@"))
        assertTrue(ref.startsWith("mail:"))
    }

    @Test
    fun emailRefIsStableSoTwoLinesCorrelate() {
        assertEquals(LogPrivacy.emailRef(email), LogPrivacy.emailRef("someone.example@focusguard.app"))
        assertEquals(LogPrivacy.emailRef(email), LogPrivacy.emailRef("  " + email + "  "))
    }

    @Test
    fun differentAccountsGetDifferentRefs() {
        assertNotEquals(LogPrivacy.emailRef("a@example.com"), LogPrivacy.emailRef("b@example.com"))
    }

    @Test
    fun blankEmailGetsANeutralRef() {
        assertEquals("mail:unknown", LogPrivacy.emailRef("   "))
    }

    @Test
    fun deviceRefShowsOnlyTheTail() {
        val deviceId = "9f8e7d6c-5b4a-3210-fedc-ba9876543210"
        val ref = LogPrivacy.deviceRef(deviceId)
        assertEquals("dev:…3210", ref)
        assertFalse("The head of the id must not be loggable", ref.contains("9f8e"))
    }

    @Test
    fun shortDeviceIdIsHiddenCompletely() {
        assertEquals("dev:hidden", LogPrivacy.deviceRef("AB2"))
        assertEquals("dev:hidden", LogPrivacy.deviceRef(""))
    }

    @Test
    fun refsStayShortEnoughForOneLogLine() {
        assertTrue(LogPrivacy.emailRef(email).length <= 13)
        assertTrue(LogPrivacy.deviceRef("x".repeat(64)).length <= 9)
    }
}
