package com.focusguard.app.service.handlers

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.AntiClonerEngine
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.data.repository.FocusGuardRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ClonedAppAction(
    val appLabel: String
)

@Singleton
class ClonedAppScanHandler @Inject constructor(
    private val repository: FocusGuardRepository,
    private val sessionBlockManager: SessionBlockManager
) {
    fun checkClonedTarget(
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        isProtectionWanted: Boolean,
        getCachedAppLabel: (String) -> String
    ): ClonedAppAction? {
        if (repository.isFastAntiCloningShieldEnabled()) {
            val isProtectionActive = isProtectionWanted
            val isSessionActive = sessionBlockManager.isSessionCurrentlyActive()
            if (isProtectionActive || isSessionActive) {
                if (AntiClonerEngine.isClonedTargetViewDetected(rootNode, pkg) { target -> repository.isFastAppBlocked(target) }) {
                    val appLabel = getCachedAppLabel(pkg)
                    Log.w(TAG, "ANTI-CLONING INTERCEPT: Cloned Distraction App Blocked: $pkg ($appLabel)")
                    return ClonedAppAction(appLabel = appLabel)
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "FocusGuardService"
    }
}

