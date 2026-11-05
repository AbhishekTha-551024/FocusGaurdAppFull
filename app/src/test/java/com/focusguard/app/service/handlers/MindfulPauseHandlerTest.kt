package com.focusguard.app.service.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindfulPauseHandlerTest {

    private val nowWall = 1_700_000_000_000L

    @Test
    fun calculateSessionStats_firstOpenOfTheDay_returnsNotReturningAndCountOne() {
        val stats = MindfulPauseHandler.calculateSessionStats(
            lastClosedTimestamp = 0L,
            todayOpenCount = 0,
            nowWall = nowWall
        )
        assertFalse(stats.isReturning)
        assertEquals(0, stats.displayMinutes)
        assertEquals(1, stats.todayCount)
    }

    @Test
    fun calculateSessionStats_fiveMinutesAgo_returnsReturningAndFiveMinutes() {
        val fiveMinutesMs = 5 * 60_000L
        val stats = MindfulPauseHandler.calculateSessionStats(
            lastClosedTimestamp = nowWall - fiveMinutesMs,
            todayOpenCount = 2,
            nowWall = nowWall
        )
        assertTrue(stats.isReturning)
        assertEquals(5, stats.displayMinutes)
        assertEquals(3, stats.todayCount)
    }

    @Test
    fun calculateSessionStats_exactly120MinutesAgo_returnsReturning() {
        val twoHoursMs = 120 * 60_000L
        val stats = MindfulPauseHandler.calculateSessionStats(
            lastClosedTimestamp = nowWall - twoHoursMs,
            todayOpenCount = 4,
            nowWall = nowWall
        )
        assertTrue(stats.isReturning)
        assertEquals(120, stats.displayMinutes)
        assertEquals(5, stats.todayCount)
    }

    @Test
    fun calculateSessionStats_121MinutesAgo_returnsNotReturningWith121DisplayMinutes() {
        val pastTwoHoursMs = 121 * 60_000L
        val stats = MindfulPauseHandler.calculateSessionStats(
            lastClosedTimestamp = nowWall - pastTwoHoursMs,
            todayOpenCount = 1,
            nowWall = nowWall
        )
        assertFalse(stats.isReturning)
        assertEquals(121, stats.displayMinutes)
        assertEquals(2, stats.todayCount)
    }

    @Test
    fun calculateSessionStats_negativeDiffClockSkew_isCoercedToZero() {
        val futureTimestamp = nowWall + 60_000L // 1 minute in the future
        val stats = MindfulPauseHandler.calculateSessionStats(
            lastClosedTimestamp = futureTimestamp,
            todayOpenCount = 3,
            nowWall = nowWall
        )
        assertTrue(stats.isReturning) // 0 in 0..120
        assertEquals(0, stats.displayMinutes)
        assertEquals(4, stats.todayCount)
    }
}

