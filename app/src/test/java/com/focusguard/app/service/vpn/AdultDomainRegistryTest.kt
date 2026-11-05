package com.focusguard.app.service.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdultDomainRegistryTest {

    private lateinit var registry: AdultDomainRegistry

    @Before
    fun setUp() {
        registry = AdultDomainRegistry()
    }

    @Test
    fun testExactDomainIsBlocked() {
        assertTrue(registry.isAdultDomain("pornhub.com"))
    }

    @Test
    fun testSubdomainIsBlocked() {
        assertTrue(registry.isAdultDomain("www.pornhub.com"))
        assertTrue(registry.isAdultDomain("m.pornhub.com"))
    }

    @Test
    fun testTrailingDotIsIgnored() {
        assertTrue(registry.isAdultDomain("pornhub.com."))
    }

    @Test
    fun testAdultTldIsBlocked() {
        assertTrue(registry.isAdultDomain("anything.xxx"))
        assertTrue(registry.isAdultDomain("anything.porn"))
    }

    @Test
    fun testLookalikeDomainIsNotBlocked() {
        // Must match on label boundary, not raw substring.
        assertFalse(registry.isAdultDomain("notpornhub.com"))
        assertFalse(registry.isAdultDomain("pornhub.com.evil.test"))
    }

    @Test
    fun testSafeDomainIsNotBlocked() {
        assertFalse(registry.isAdultDomain("google.com"))
        assertFalse(registry.isAdultDomain("wikipedia.org"))
    }
}
