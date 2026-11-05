package com.focusguard.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSyncIdTest {

    @Test
    fun scheduleIdFromCloud_prefersStoredScheduleId() {
        assertEquals(12L, scheduleIdFromCloud("other-id", 12L))
    }

    @Test
    fun scheduleIdFromCloud_usesNumericDocumentIdForLegacyData() {
        assertEquals(12L, scheduleIdFromCloud("12", null))
    }

    @Test
    fun scheduleIdFromCloud_fallsBackToAutoGenerationForNonNumericId() {
        assertEquals(0L, scheduleIdFromCloud("schedule-a", null))
    }
}
