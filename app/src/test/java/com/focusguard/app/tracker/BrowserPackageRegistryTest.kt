package com.focusguard.app.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowserPackageRegistryTest {

    private lateinit var registry: BrowserPackageRegistry

    @Before
    fun setUp() {
        registry = BrowserPackageRegistry()
    }

    @Test
    fun testBrowserRegistryContainsMajorBrowsersAndNonEmptyViewIds() {
        val chromePkg = "com.android.chrome"
        val firefoxPkg = "org.mozilla.firefox"
        val edgePkg = "com.microsoft.emmx"

        // 1. Contains Chrome, Firefox, and Edge in verified supported browsers
        assertTrue("Registry must support Chrome", registry.isSupportedBrowser(chromePkg))
        assertTrue("Registry must support Firefox", registry.isSupportedBrowser(firefoxPkg))
        assertTrue("Registry must support Edge", registry.isSupportedBrowser(edgePkg))

        // 2. Resource ID map contains Chrome, Firefox, and Edge
        assertTrue("Resource map must contain Chrome", registry.browserResourceIdMap.containsKey(chromePkg))
        assertTrue("Resource map must contain Firefox", registry.browserResourceIdMap.containsKey(firefoxPkg))
        assertTrue("Resource map must contain Edge", registry.browserResourceIdMap.containsKey(edgePkg))

        // 3. Every registered browser has at least one view ID
        assertFalse("Resource map must not be empty", registry.browserResourceIdMap.isEmpty())
        for ((pkg, viewIds) in registry.browserResourceIdMap) {
            assertTrue("Browser $pkg must have at least one view ID", viewIds.isNotEmpty())
            for (id in viewIds) {
                assertTrue("View ID in $pkg must not be blank", id.isNotBlank())
            }
        }
    }

    @Test
    fun testLinkHandlerAppsAreNotQuarantinedAsBrowsers() {
        // Apps that resolve http(s) VIEW intents (Custom Tabs hosts, deep links) must never be
        // quarantined: they do not ship a browser surface of their own.
        assertFalse(registry.isUnsupportedBrowser("com.google.android.gm"))
        assertFalse(registry.isUnsupportedBrowser("com.facebook.katana"))
        assertFalse(registry.isUnsupportedBrowser("com.whatsapp"))
        assertFalse(registry.isUnsupportedBrowser("com.google.android.apps.messaging"))
        assertFalse(registry.isUnsupportedBrowser("com.focusguard.app"))
    }

    @Test
    fun testKnownAndUnknownBrowsersStayQuarantined() {
        assertTrue(registry.isUnsupportedBrowser("org.torproject.torbrowser"))
        assertTrue(registry.isUnsupportedBrowser("com.kiwibrowser.browser"))
        assertTrue(registry.isUnsupportedBrowser("com.some.vendor.quickbrowser"))
    }

    @Test
    fun testVerifiedBrowsersAreNeverQuarantined() {
        for (pkg in BrowserPackageRegistry.VERIFIED_SUPPORTED_BROWSERS) {
            assertFalse(
                "Verified browser must not be quarantined: $pkg",
                registry.isUnsupportedBrowser(pkg)
            )
        }
    }
}

