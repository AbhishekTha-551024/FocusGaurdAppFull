package com.focusguard.app.usage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeLimitEvaluatorDayRolloverTest {

    @Test
    fun trackingDateChanged_detectsYearBoundaryWithSameDayOfYear() {
        assertTrue(trackingDateChanged(2025, 1, "UTC", 2026, 1, "UTC"))
    }

    @Test
    fun trackingDateChanged_returnsFalseForSameLocalDate() {
        assertFalse(trackingDateChanged(2026, 308, "UTC", 2026, 308, "UTC"))
    }

    @Test
    fun trackingDateChanged_detectsTimezoneChange() {
        assertTrue(trackingDateChanged(2026, 308, "UTC", 2026, 308, "Asia/Kolkata"))
    }
}
