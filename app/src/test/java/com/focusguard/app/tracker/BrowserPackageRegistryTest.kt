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
}

