package com.focusguard.app.service

import android.content.Context
import android.os.Handler
import android.util.Log
import com.focusguard.app.core.startup.ProtectionStartupReadiness
import com.focusguard.app.worker.AccessibilityHealthWorker

private const val TAG = "FG:Shield"

internal class BootCoverTimeoutController(
    private val context: Context,
    private val handler: Handler,
    private val startupReadiness: ProtectionStartupReadiness,
    private val isOverlayAttached: () -> Boolean,
    private val onFallback: () -> Unit,
    private val onMaximumDurationReached: () -> Unit
) {
    private var fallbackTask: Runnable? = null
    private var maximumDurationTask: Runnable? = null

    fun start() {
        stop()
        fallbackTask = Runnable {
            if (BootCoverPolicy.shouldTriggerFallback(
                    isProtectionReady = startupReadiness.isReady(),
                    isOverlayAttached = isOverlayAttached()
                )
            ) {
                Log.w(TAG, "Startup readiness is still incomplete after the boot grace period.")
                onFallback()
            }
        }.also { handler.postDelayed(it, BootCoverPolicy.FALLBACK_DELAY_MS) }

        maximumDurationTask = Runnable {
            if (BootCoverPolicy.shouldReleaseAfterMaximumDuration(
                    isProtectionReady = startupReadiness.isReady(),
                    isOverlayAttached = isOverlayAttached()
                )
            ) {
                Log.w(TAG, "Maximum boot-cover duration elapsed; releasing cover and posting recovery alert.")
                try {
                    AccessibilityHealthWorker.postReactivationNotification(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post recovery alert after releasing boot cover: ${e.message}", e)
                }
                onMaximumDurationReached()
            }
        }.also { handler.postDelayed(it, BootCoverPolicy.MAX_COVER_DURATION_MS) }
    }

    fun stop() {
        fallbackTask?.let(handler::removeCallbacks)
        maximumDurationTask?.let(handler::removeCallbacks)
        fallbackTask = null
        maximumDurationTask = null
    }
}
