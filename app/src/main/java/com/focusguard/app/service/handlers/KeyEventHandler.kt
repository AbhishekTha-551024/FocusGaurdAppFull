package com.focusguard.app.service.handlers

import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.overlay.manager.BlockOverlayManager
import com.focusguard.app.utils.AudioManagerHelper
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FG:Keys"

/**
 * KeyEventHandler
 * ===============
 * Specialized hardware key event filter for FocusGuard.
 * - Enforces zero-latency fast-paths for volume keys.
 * - Protects active siren audio streams during focus violations.
 * - Isolates overlay windows and deep focus lockdowns against hardware navigation escapes.
 */
@Singleton
class KeyEventHandler @Inject constructor(
    private val overlayManager: BlockOverlayManager,
    private val audioManagerHelper: AudioManagerHelper,
    private val sessionBlockManager: SessionBlockManager
) {

    /**
     * Intercepts hardware keys.
     * @return true if consumed, null if unhandled (caller must invoke super.onKeyEvent).
     */
    fun handleKeyEvent(event: KeyEvent?, currentForegroundPackage: String?): Boolean? {
        if (event == null) return false

        val keyCode = event.keyCode
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE

        // ⚡ ZERO-LATENCY FAST-PATH FOR HARDWARE VOLUME KEYS (AGENTS Rule 5 Invariant)
        if (isVolumeKey) {
            val isOverlayShowing = overlayManager.isOverlayShowing
            val isAlertActive = audioManagerHelper.isAlertActive()
            if (!isOverlayShowing && !isAlertActive) {
                return null // Instant unhandled -> pass to super.onKeyEvent(event)
            }
        }

        // 🚨 EMERGENCY & IN-CALL IMMUNITY: Never intercept keys during calls
        if (sessionBlockManager.isInPhoneCall() || audioManagerHelper.isInCall()) {
            return null
        }

        val isOverlayShowing = overlayManager.isOverlayShowing
        val isDeepFocus = sessionBlockManager.isSessionCurrentlyActive()
        val currentPkg = currentForegroundPackage
        val isAllowedTool = currentPkg != null && (
            sessionBlockManager.isAlwaysAllowedPackage(currentPkg) ||
            sessionBlockManager.suitcasePackages.contains(currentPkg.lowercase())
        )

        // 🚨 AUDIO STREAM PROTECTION: Prevent muting alert sirens or focus alarms
        if (audioManagerHelper.isAlertActive()) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    audioManagerHelper.ensureAudibleAlertVolume(AudioManager.STREAM_ALARM)
                    Log.d(TAG, "Hardware volume-down consumed & alarm volume locked during active alert")
                }
                return true
            }
        }

        // 🚨 OVERLAY KEY ISOLATION: Prevent volume key combinations from interacting with background apps
        if (isOverlayShowing && isVolumeKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    audioManagerHelper.ensureAudibleAlertVolume(AudioManager.STREAM_ALARM)
                }
                Log.d(TAG, "Hardware volume key $keyCode consumed to isolate block overlay window")
            }
            return true
        }

        // 🚨 NAVIGATION KEY ISOLATION: Back, App-Switch, Home
        if ((isOverlayShowing || (isDeepFocus && !isAllowedTool)) &&
            (keyCode == KeyEvent.KEYCODE_BACK ||
             keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
             keyCode == KeyEvent.KEYCODE_HOME)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "Hardware key $keyCode strictly consumed (overlayShowing=$isOverlayShowing, deepFocus=$isDeepFocus)")
            }
            return true
        }

        return null
    }
}

