package com.focusguard.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioManagerHelper
 * ==================
 * Hardware Audio Stream Lock & Volume Protection Helper for FocusGuard.
 *
 * Core Objectives:
 * 1. Stream Protection: Prevents users from silencing security sirens, mindful countdown bells,
 *    or focus alerts by repeatedly tapping or holding Volume Down.
 * 2. In-Call Immunity: Strictly ensures that during active cellular or VoIP calls (MODE_IN_CALL,
 *    MODE_IN_COMMUNICATION, MODE_RINGTONE), volume key adjustments and audio routing are
 *    100% untouched for emergency and dialer safety.
 * 3. Alert / Bell Playback: Provides tamper-resistant tone and chime generation on STREAM_ALARM.
 */
@Singleton
class AudioManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioManagerHelper"
        private const val DEFAULT_MIN_VOLUME_RATIO = 0.70f // Maintain at least 70% volume during alerts
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val isAlertOrBellActive = AtomicBoolean(false)
    private var activeRingtone: Ringtone? = null

    /**
     * Checks if the device is currently in a phone call, VoIP communication, or ringing.
     * ZERO permissions required, 100% reliable across all Android versions.
     */
    fun isInCall(): Boolean {
        val am = audioManager ?: return false
        val mode = am.mode
        return mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_RINGTONE
    }

    /**
     * Sets whether a critical alert, security alarm, or mindful chime is currently playing.
     */
    fun setAlertActive(active: Boolean) {
        isAlertOrBellActive.set(active)
    }

    /**
     * Returns true if an alert or countdown audio notification is actively sounding.
     */
    fun isAlertActive(): Boolean = isAlertOrBellActive.get()

    /**
     * Re-asserts minimum audible volume on the target audio stream (default STREAM_ALARM).
     * Called when a volume-down key press is intercepted during an active alert or block countdown.
     *
     * @param streamType The audio stream (e.g. [AudioManager.STREAM_ALARM] or [AudioManager.STREAM_RING]).
     * @param minRatio The minimum acceptable volume as a fraction of max volume (default 70%).
     */
    fun ensureAudibleAlertVolume(
        streamType: Int = AudioManager.STREAM_ALARM,
        minRatio: Float = DEFAULT_MIN_VOLUME_RATIO
    ) {
        // Never manipulate volume during active phone calls
        if (isInCall()) return

        val am = audioManager ?: return
        try {
            val maxVol = am.getStreamMaxVolume(streamType)
            val currentVol = am.getStreamVolume(streamType)
            val minVol = (maxVol * minRatio).toInt().coerceAtLeast(1)

            if (currentVol < minVol) {
                Log.i(TAG, "Re-asserting volume for stream $streamType: $currentVol -> $minVol (max: $maxVol)")
                am.setStreamVolume(streamType, minVol, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to re-assert stream volume: ${e.message}")
        }
    }

    /**
     * Plays a short focus session chime or reminder tone on the ALARM stream.
     */
    fun playFocusBellTone(toneType: Int = ToneGenerator.TONE_PROP_BEEP2, durationMs: Int = 400) {
        if (isInCall()) return
        try {
            ensureAudibleAlertVolume(AudioManager.STREAM_ALARM, 0.6f)
            val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            toneGenerator.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play focus bell tone: ${e.message}")
        }
    }

    /**
     * Starts playing the default system alarm or notification ringtone for security tamper alerts.
     */
    fun startAlertRingtone() {
        if (isInCall()) return
        try {
            ensureAudibleAlertVolume(AudioManager.STREAM_ALARM, 0.8f)
            stopAlertRingtone()

            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return

            val ringtone = RingtoneManager.getRingtone(context, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
            activeRingtone = ringtone
            isAlertOrBellActive.set(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start alert ringtone: ${e.message}")
        }
    }

    /**
     * Stops any currently playing alert ringtone.
     */
    fun stopAlertRingtone() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop alert ringtone: ${e.message}")
        } finally {
            isAlertOrBellActive.set(false)
        }
    }
}
