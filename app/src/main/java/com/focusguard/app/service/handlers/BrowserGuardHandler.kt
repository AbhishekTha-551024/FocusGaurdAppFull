package com.focusguard.app.service.handlers

import android.content.Context
import android.util.Log
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.tracker.BrowserUrlTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class UnsupportedBrowserAction(
    val appLabel: String
)

@Singleton
class BrowserGuardHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository,
    private val browserUrlTracker: BrowserUrlTracker
) {
    fun checkUnsupportedBrowser(
        pkg: String,
        getCachedAppLabel: (String) -> String
    ): UnsupportedBrowserAction? {
        if (repository.isFastBlockUnsupportedBrowsersEnabled()) {
            if (browserUrlTracker.isUnsupportedBrowser(pkg, context)) {
                val appLabel = getCachedAppLabel(pkg)
                Log.i(TAG, "Unsupported browser blocked: $pkg ($appLabel)")
                return UnsupportedBrowserAction(appLabel = appLabel)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}

