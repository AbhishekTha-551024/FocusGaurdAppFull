package com.focusguard.app.service.handlers

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.focusguard.app.data.repository.MindfulRepository
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.overlay.manager.OverlayBlockType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class holding statistics calculated for the mindful pause overlay.
 */
data class MindfulSessionStats(
    val isReturning: Boolean,
    val displayMinutes: Int,
    val todayCount: Int
)

@Singleton
class MindfulPauseHandler @Inject constructor(
    private val mindfulRepository: MindfulRepository,
    private val overlayManager: BlockOverlayManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mindfulExpiryRunnables = ConcurrentHashMap<String, Runnable>()

    /**
     * Determines whether the incoming event should trigger a Mindful Pause overlay.
     * Evaluated with zero allocations on the non-intercept path.
     */
    fun shouldIntercept(
        pkg: String,
        isHardBlocked: Boolean,
        isPackageChanged: Boolean,
        isWindowTransition: Boolean
    ): Boolean {
        if (isHardBlocked || !mindfulRepository.isPackageMindful(pkg)) {
            return false
        }
        if (mindfulRepository.isAllowanceActive(pkg)) {
            return false
        }
        return !overlayManager.isOverlayShowing && (isPackageChanged || isWindowTransition)
    }

    /**
     * Displays the Mindful Pause overlay and wires allowance expiry and walk-away callbacks.
     */
    fun showMindfulPause(
        pkg: String,
        appLabel: String,
        isForegroundPackage: () -> Boolean,
        onTriggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit,
        onForceClose: (String) -> Unit,
        nowWall: Long = System.currentTimeMillis()
    ) {
        Log.i(TAG, "Mindful Pause triggered for $pkg ($appLabel)")

        val sessionInfo = mindfulRepository.getSessionInfo(pkg)
        val stats = calculateSessionStats(
            lastClosedTimestamp = sessionInfo.lastClosedTimestamp,
            todayOpenCount = sessionInfo.todayOpenCount,
            nowWall = nowWall
        )

        overlayManager.showMindfulPauseOverlay(
            packageName = pkg,
            appName = appLabel,
            breathingDurationSeconds = 6,
            isReturning = stats.isReturning,
            minutesSinceLastSession = stats.displayMinutes,
            todayOpenCount = stats.todayCount,
            onAllowanceGranted = { durationMinutes, _, _ ->
                mindfulRepository.grantAllowance(pkg, durationMinutes)
                mindfulRepository.recordSessionStarted(pkg)
                scheduleAllowanceExpiry(
                    pkg = pkg,
                    appLabel = appLabel,
                    durationMinutes = durationMinutes,
                    isForegroundPackage = isForegroundPackage,
                    isOverlayShowing = { overlayManager.isOverlayShowing },
                    onTriggerBlock = onTriggerBlock
                )
            },
            onWalkAway = {
                mindfulRepository.recordSessionEnded(pkg)
                onForceClose(pkg)
            }
        )
    }

    fun scheduleAllowanceExpiry(
        pkg: String,
        appLabel: String,
        durationMinutes: Int,
        isForegroundPackage: () -> Boolean,
        isOverlayShowing: () -> Boolean,
        onTriggerBlock: (sourcePackage: String, reason: String, blockType: OverlayBlockType, targetName: String, description: String) -> Unit
    ) {
        val prevRunnable = mindfulExpiryRunnables[pkg]
        if (prevRunnable != null) {
            mainHandler.removeCallbacks(prevRunnable)
        }
        val r = Runnable {
            mindfulRepository.revokeAllowance(pkg)
            mindfulRepository.recordSessionEnded(pkg)
            if (isForegroundPackage() && !isOverlayShowing()) {
                Log.i(TAG, "Mindful allowance expired for $pkg -> Triggering conscious re-block and closing app!")
                onTriggerBlock(
                    pkg,
                    "Conscious Allowance Ended ($durationMinutes min)",
                    OverlayBlockType.APP_BLOCK,
                    appLabel,
                    "Your conscious allowance of $durationMinutes minutes for $appLabel has ended. Closing app to protect your focus."
                )
            }
            mindfulExpiryRunnables.remove(pkg)
        }
        mindfulExpiryRunnables[pkg] = r
        mainHandler.postDelayed(r, durationMinutes * 60 * 1000L)
    }

    fun cancelAll() {
        mindfulExpiryRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        mindfulExpiryRunnables.clear()
    }

    companion object {
        private const val TAG = "FocusGuardService"

        /**
         * Pure function calculating stats for session display and returning-user determination.
         */
        fun calculateSessionStats(
            lastClosedTimestamp: Long,
            todayOpenCount: Int,
            nowWall: Long
        ): MindfulSessionStats {
            val minutesSinceLast = if (lastClosedTimestamp > 0L) {
                ((nowWall - lastClosedTimestamp) / 60000L).coerceAtLeast(0L).toInt()
            } else {
                -1
            }
            // Mark as returning if app was closed previously today and within recent window
            val isReturning = lastClosedTimestamp > 0L && minutesSinceLast in 0..120
            val displayMinutes = if (minutesSinceLast >= 0) minutesSinceLast else 0
            val todayCount = todayOpenCount + 1
            return MindfulSessionStats(
                isReturning = isReturning,
                displayMinutes = displayMinutes,
                todayCount = todayCount
            )
        }
    }
}
