package com.focusguard.app.blockers

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * TimeTamperGuard:
 * Clock Rollback & Time Manipulation Shield.
 *
 * Prevents users from bypassing daily app limits, focus schedules, and Deep Focus
 * lockdowns by manually setting the phone's clock backward or jumping it forward.
 *
 * Uses monotonic CPU hardware boot time (SystemClock.elapsedRealtime()) which
 * continuously increments and cannot be modified by user settings, timezone shifts,
 * or manual date adjustments.
 */
object TimeTamperGuard {
    private const val TAG = "TimeTamperGuard"

    // Monotonic boot elapsed realtime (immune to clock changes)
    private val lastRecordedBootRealtimeMs = AtomicLong(0L)
    // Wall clock time (System.currentTimeMillis())
    private val lastRecordedWallClockTimeMs = AtomicLong(0L)
    // Active tamper state flag
    private val isTamperFlagged = AtomicBoolean(false)

    // Thresholds:
    // Clock rollback: wall clock moved backward by more than 30 seconds
    private const val ROLLBACK_THRESHOLD_MS = -30_000L
    // Time jump forward: wall clock jumped forward without matching hardware elapsed time.
    // Set to 60s to safely accommodate cellular NTP network sync, timezone shifts, and Doze wakeups.
    private const val DRIFT_TOLERANCE_MS = 60_000L

    /**
     * Resets the hardware baseline when the system broadcasts an official time or timezone change,
     * preventing legitimate carrier time synchronization from falsely triggering the tamper flag.
     */
    fun onSystemTimeOrTimeZoneChanged() {
        val currentReal = SystemClock.elapsedRealtime()
        val currentWall = System.currentTimeMillis()
        lastRecordedBootRealtimeMs.set(currentReal)
        lastRecordedWallClockTimeMs.set(currentWall)
        Log.i(TAG, "System time or timezone change handled: resynchronized hardware baselines (real=$currentReal, wall=$currentWall).")
    }

    /**
     * Checks if system time was rolled back or artificially modified.
     *
     * @return true if clock tampering is detected, false otherwise.
     */
    fun checkAndValidateTime(): Boolean {
        val currentReal = SystemClock.elapsedRealtime()
        val currentWall = System.currentTimeMillis()

        val lastReal = lastRecordedBootRealtimeMs.get()
        val lastWall = lastRecordedWallClockTimeMs.get()

        if (lastReal == 0L || lastWall == 0L) {
            // Initialize baseline on first check
            lastRecordedBootRealtimeMs.set(currentReal)
            lastRecordedWallClockTimeMs.set(currentWall)
            return false
        }

        // Did the phone reboot since the last observation?
        if (currentReal < lastReal) {
            Log.i(TAG, "Device reboot detected: resetting time tracking baselines.")
            lastRecordedBootRealtimeMs.set(currentReal)
            lastRecordedWallClockTimeMs.set(currentWall)
            isTamperFlagged.set(false)
            return false
        }

        val deltaReal = currentReal - lastReal
        val deltaWall = currentWall - lastWall

        // 1. Rollback Detection: Wall clock moved backwards
        if (deltaWall < ROLLBACK_THRESHOLD_MS) {
            Log.e(TAG, "CRITICAL: System clock rollback detected! deltaWall=$deltaWall ms, deltaReal=$deltaReal ms")
            isTamperFlagged.set(true)
            return true
        }

        // 2. Artificial Time-Jump Detection: Wall clock jumped ahead without hardware elapsed time
        val drift = deltaWall - deltaReal
        if (drift > DRIFT_TOLERANCE_MS) {
            Log.e(TAG, "CRITICAL: System clock jump detected! drift=$drift ms (wall jumped $deltaWall ms, real elapsed $deltaReal ms)")
            isTamperFlagged.set(true)
            return true
        }

        // Normal progression: update checkpoints
        lastRecordedBootRealtimeMs.set(currentReal)
        lastRecordedWallClockTimeMs.set(currentWall)
        return isTamperFlagged.get()
    }

    fun isTampered(): Boolean = isTamperFlagged.get()

    fun resetTamperFlag() {
        lastRecordedBootRealtimeMs.set(SystemClock.elapsedRealtime())
        lastRecordedWallClockTimeMs.set(System.currentTimeMillis())
        isTamperFlagged.set(false)
        Log.i(TAG, "TimeTamperGuard flag reset.")
    }
}
