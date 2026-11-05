package com.focusguard.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingChangeScheduleTest {

    @Test
    fun scheduledApplyTimestamp_addsConfiguredHours() {
        assertEquals(36_055_000L, scheduledApplyTimestamp(55_000L, 10))
    }

    @Test
    fun scheduledApplyTimestamp_enforcesAtLeastOneHour() {
        assertEquals(3_600_000L, scheduledApplyTimestamp(0L, 0))
    }

    @Test
    fun scheduledApplyTimestamp_saturatesWhenTimestampWouldOverflow() {
        assertEquals(Long.MAX_VALUE, scheduledApplyTimestamp(Long.MAX_VALUE - 1L, 1))
    }
}
