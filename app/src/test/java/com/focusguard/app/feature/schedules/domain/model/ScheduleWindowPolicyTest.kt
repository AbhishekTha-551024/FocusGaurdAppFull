package com.focusguard.app.feature.schedules.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWindowPolicyTest {

    @Test
    fun validOvernightWindowIsAccepted() {
        assertTrue(isScheduleWindowValid(127, 22 * 60, 6 * 60))
    }

    @Test
    fun equalTimesAreRejected() {
        assertFalse(isScheduleWindowValid(127, 8 * 60, 8 * 60))
    }

    @Test
    fun invalidDaysAndMinutesAreRejected() {
        assertFalse(isScheduleWindowValid(0, 60, 120))
        assertFalse(isScheduleWindowValid(1, -1, 120))
        assertFalse(isScheduleWindowValid(1, 60, 1440))
    }
}
