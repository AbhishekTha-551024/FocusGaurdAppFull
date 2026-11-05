package com.focusguard.app.service.handlers

import android.util.Log
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.tracker.BrowserUrlTracker
import javax.inject.Inject
import javax.inject.Singleton

data class UnsupportedBrowserAction(
    val appLabel: String
)

/**
 * Browser quarantine layer.
 *
 * Classification lives entirely in [BrowserUrlTracker]'s registry: only packages that ship
 * their own browser surface are quarantined, never Custom Tabs hosts or apps that merely
 * resolve http(s) links. The block itself is executed by the shared BlockTrigger path in
 * TargetAppPipeline, so this handler stays allocation-light on the accessibility thread.
 */
@Singleton
class BrowserGuardHandler @Inject constructor(
    private val repository: FocusGuardRepository,
    private val browserUrlTracker: BrowserUrlTracker
) {
    fun checkUnsupportedBrowser(
        pkg: String,
        getCachedAppLabel: (String) -> String
    ): UnsupportedBrowserAction? {
        if (!repository.isFastBlockUnsupportedBrowsersEnabled()) return null
        if (!browserUrlTracker.isUnsupportedBrowser(pkg)) return null

        val appLabel = getCachedAppLabel(pkg)
        Log.i(TAG, "Unsupported browser blocked: $pkg ($appLabel)")
        return UnsupportedBrowserAction(appLabel = appLabel)
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}
