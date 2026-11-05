package com.focusguard.app.service.handlers

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundSessionTrackerTest {

    @Test
    fun calculateLiveSessionMs_whenStartTimeZero_returnsZero() {
        val result = calculateLiveSessionMs(startTimeMs = 0L, nowRealtimeMs = 10_000L)
        assertEquals(0L, result)
    }

    @Test
    fun calculateLiveSessionMs_whenActive_returnsElapsed() {
        val result = calculateLiveSessionMs(startTimeMs = 5_000L, nowRealtimeMs = 12_500L)
        assertEquals(7_500L, result)
    }

    @Test
    fun calculateLiveSessionMs_whenNowBeforeStart_coercesToZero() {
        val result = calculateLiveSessionMs(startTimeMs = 15_000L, nowRealtimeMs = 10_000L)
        assertEquals(0L, result)
    }

    @Test
    fun computeAdaptiveDelay_withoutLimit_returnsFiveSeconds() {
        val delay = computeAdaptiveDelay(
            hasLimit = false,
            sessionBaselineMs = 0L,
            limitMs = 60_000L,
            liveElapsedMs = 10_000L
        )
        assertEquals(5000L, delay)
    }

    @Test
    fun computeAdaptiveDelay_withMoreThanOneSecondRemaining_returnsOneSecond() {
        val delay = computeAdaptiveDelay(
            hasLimit = true,
            sessionBaselineMs = 10_000L,
            limitMs = 60_000L,
            liveElapsedMs = 10_000L
        )
        // remaining = 60000 - 20000 = 40000 > 1000 => 1000L
        assertEquals(1000L, delay)
    }

    @Test
    fun computeAdaptiveDelay_withSubsecondRemaining_returnsExactRemaining() {
        val delay = computeAdaptiveDelay(
            hasLimit = true,
            sessionBaselineMs = 50_000L,
            limitMs = 60_000L,
            liveElapsedMs = 9_450L
        )
        // remaining = 60000 - 59450 = 550 => 550L
        assertEquals(550L, delay)
    }

    @Test
    fun computeAdaptiveDelay_withUnderHundredMsRemaining_coercesToHundredMs() {
        val delay = computeAdaptiveDelay(
            hasLimit = true,
            sessionBaselineMs = 50_000L,
            limitMs = 60_000L,
            liveElapsedMs = 9_960L
        )
        // remaining = 60000 - 59960 = 40 => coerced to 100L
        assertEquals(100L, delay)
    }

    @Test
    fun computeAdaptiveDelay_whenExceeded_coercesToHundredMs() {
        val delay = computeAdaptiveDelay(
            hasLimit = true,
            sessionBaselineMs = 60_000L,
            limitMs = 60_000L,
            liveElapsedMs = 5_000L
        )
        // remaining = 60000 - 65000 = -5000 => coerced to 100L
        assertEquals(100L, delay)
    }
}

