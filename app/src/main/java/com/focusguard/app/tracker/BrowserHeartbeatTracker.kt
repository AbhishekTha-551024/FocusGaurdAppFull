package com.focusguard.app.tracker

import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.blockers.KeywordBlocker
import com.focusguard.app.data.repository.isShortFormContentBlocked
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BrowserHeartbeat"

/**
 * BrowserHeartbeatTracker
 * =======================
 * High-performance, active 1-second background scanner for supported mobile browsers and YouTube.
 * Eliminates "event dropping" in privacy browsers (Brave, Firefox, DuckDuckGo) where
 * TYPE_WINDOW_CONTENT_CHANGED events are suppressed or delayed during tab transitions.
 *
 * Energy-efficient: Checks [PowerManager.isInteractive] to immediately skip and conserve battery
 * whenever the screen is locked or turned off.
 *
 * Runs strictly on Dispatchers.Default and automatically terminates when the user exits the browser.
 */
@Singleton
class BrowserHeartbeatTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val browserUrlTracker: BrowserUrlTracker,
    private val keywordBlocker: KeywordBlocker,
    private val repository: com.focusguard.app.data.repository.FocusGuardRepository,
    private val reelBlocker: com.focusguard.app.blockers.ReelBlocker,
    private val adultContentShield: com.focusguard.app.blockers.AdultContentShield
) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var heartbeatJob: Job? = null
    private var activePackage: String? = null

    /**
     * Starts continuous sweep (500ms for YouTube, 1000ms for browsers) for [packageName] while it remains in foreground.
     */
    fun startHeartbeat(
        scope: CoroutineScope,
        packageName: String,
        isOverlayShowing: () -> Boolean,
        rootProvider: () -> AccessibilityNodeInfo?,
        onViolation: (sourcePackage: String, matchedKeyword: String, urlOrQuery: String) -> Unit
    ) {
        if (heartbeatJob?.isActive == true && activePackage == packageName) {
            return
        }

        stopHeartbeat()
        activePackage = packageName

        val isReelsOrShorts = reelBlocker.isTargetPackage(packageName)
        val sweepInterval = if (isReelsOrShorts) 500L else 1000L

        heartbeatJob = scope.launch(Dispatchers.Default) {
            Log.d(TAG, "Started ${sweepInterval}ms active heartbeat for $packageName")
            while (isActive && activePackage == packageName) {
                delay(sweepInterval)

                // Battery optimization: If screen is off/locked or overlay is visible, pause scan
                if (powerManager?.isInteractive == false || isOverlayShowing()) {
                    continue
                }

                // CRITICAL FIX for Vivo FuntouchOS SELinux AVC denial:
                // rootInActiveWindow MUST be called from the Main thread only.
                // Calling it from Dispatchers.Default triggers /proc/fas/render ioctl
                // (Vivo FAS kernel driver) which is SELinux-denied → continuous binder
                // thread crashes → AccessibilityService unbind after ~30s.
                val rootNode = try {
                    withContext(Dispatchers.Main) { rootProvider() }
                } catch (e: Exception) {
                    null
                } ?: continue

                try {
                    // 0. Instant YouTube Shorts & Reels Detection during active playback
                    val isPlatformBlocked = repository.isShortFormContentBlocked(packageName)
                    if (reelBlocker.isTargetPackage(packageName) && isPlatformBlocked) {
                        val isShortsOrReels = reelBlocker.isReelsOrShortsDetected(
                            e = null,
                            rootNode = rootNode,
                            targetPackage = packageName
                        )

                        if (isShortsOrReels) {
                            val violationLabel = when (packageName) {
                                com.focusguard.app.blockers.ReelBlocker.INSTAGRAM_PACKAGE -> "Instagram Reels"
                                com.focusguard.app.blockers.ReelBlocker.FACEBOOK_PACKAGE,
                                com.focusguard.app.blockers.ReelBlocker.FACEBOOK_LITE_PACKAGE -> "Facebook Reels"
                                else -> "YouTube Shorts"
                            }
                            Log.i(TAG, "Heartbeat caught active $violationLabel during playback in $packageName!")
                            withContext(Dispatchers.Main) {
                                onViolation(packageName, violationLabel, violationLabel)
                            }
                            return@launch
                        }
                    }

                    if (browserUrlTracker.isYouTubePackage(packageName)) {
                        val ytItems = browserUrlTracker.extractYouTubeMetadata(rootNode, null)
                        for (item in ytItems) {
                            if (item.text.length >= 2) {
                                if (repository.isFastAdultContentBlockEnabled()) {
                                    val adultViolation = adultContentShield.checkViolation(item.text)
                                    if (adultViolation != null) {
                                        Log.i(TAG, "Heartbeat caught adult content violation in YouTube ($packageName)")
                                        withContext(Dispatchers.Main) {
                                            onViolation(packageName, "Adult Content", item.text)
                                        }
                                        return@launch
                                    }
                                }
                                val matched = keywordBlocker.isViolationDetectedSync(item.text)
                                if (matched != null) {
                                    Log.i(TAG, "Heartbeat caught keyword violation in YouTube ($packageName)")
                                    withContext(Dispatchers.Main) {
                                        onViolation(packageName, matched, item.text)
                                    }
                                    return@launch
                                }
                            }
                        }
                    } else if (browserUrlTracker.isSupportedBrowser(packageName)) {
                        val browserStrings = browserUrlTracker.extractBrowserStrings(rootNode, null, packageName)
                        for (str in browserStrings) {
                            if (str.length >= 2) {
                                if (repository.isFastAdultContentBlockEnabled()) {
                                    val adultViolation = adultContentShield.checkViolation(str)
                                    if (adultViolation != null) {
                                        Log.i(TAG, "Heartbeat caught adult content violation in browser ($packageName)")
                                        withContext(Dispatchers.Main) {
                                            onViolation(packageName, "Adult Content", str)
                                        }
                                        return@launch
                                    }
                                }
                                val matched = keywordBlocker.isViolationDetectedSync(str)
                                if (matched != null) {
                                    Log.i(TAG, "Heartbeat caught keyword violation in browser ($packageName)")
                                    withContext(Dispatchers.Main) {
                                        onViolation(packageName, matched, str)
                                    }
                                    return@launch
                                }
                            }
                        }
                    } else {
                        val extracted = browserUrlTracker.extractUniversalSearchOrInputQuery(rootNode, null)
                        if (!extracted.isNullOrBlank() && extracted.length >= 2) {
                            if (repository.isFastAdultContentBlockEnabled()) {
                                val adultViolation = adultContentShield.checkViolation(extracted)
                                if (adultViolation != null) {
                                    Log.i(TAG, "Heartbeat caught universal adult content violation in $packageName")
                                    withContext(Dispatchers.Main) {
                                        onViolation(packageName, "Adult Content", extracted)
                                    }
                                    return@launch
                                }
                            }
                            val matched = keywordBlocker.isViolationDetectedSync(extracted)
                            if (matched != null) {
                                Log.i(TAG, "Heartbeat caught universal keyword violation in $packageName")
                                withContext(Dispatchers.Main) {
                                    onViolation(packageName, matched, extracted)
                                }
                                return@launch
                            }
                        }
                    }
                } finally {
                    com.focusguard.app.utils.SafeNodeUtils.safeRecycle(rootNode)
                }
            }
        }
    }

    /**
     * Stops the running heartbeat sweep immediately.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        activePackage = null
    }
}
