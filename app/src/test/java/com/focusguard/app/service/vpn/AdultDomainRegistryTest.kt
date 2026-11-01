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
    fun testAdultDomainRegistryBlocksRegisteredAndSubdomainsAndAllowsBenign() {
        // 1. Registered domain is blocked
        assertTrue("pornhub.com must be blocked", registry.isAdultDomain("pornhub.com"))
        assertTrue("xvideos.com must be blocked", registry.isAdultDomain("xvideos.com"))

        // 2. Subdomains of registered domains are blocked
        assertTrue("sub.pornhub.com must be blocked", registry.isAdultDomain("sub.pornhub.com"))
        assertTrue("mobile.video.xvideos.com must be blocked", registry.isAdultDomain("mobile.video.xvideos.com"))

        // 3. Adult TLDs are blocked
        assertTrue("anything.xxx must be blocked", registry.isAdultDomain("anything.xxx"))
        assertTrue("deep.subdomain.porn must be blocked", registry.isAdultDomain("deep.subdomain.porn"))

        // 4. Benign domain is allowed
        assertFalse("example.com must be allowed", registry.isAdultDomain("example.com"))
        assertFalse("sub.example.com must be allowed", registry.isAdultDomain("sub.example.com"))
        assertFalse("google.com must be allowed", registry.isAdultDomain("google.com"))
    }
}

