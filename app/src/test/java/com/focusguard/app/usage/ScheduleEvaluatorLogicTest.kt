package com.focusguard.app.usage

import com.focusguard.app.core.database.entity.ScheduleEntity
import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEvaluatorLogicTest {

    @Test
    fun overnightScheduleContinuesIntoFollowingDay() {
        val schedule = ScheduleEntity(
            daysOfWeekMask = 1,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60
        )
        val tuesdayAtTwoAm = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 6, 2, 0, 0)
        }

        assertTrue(isScheduleActiveAt(schedule, tuesdayAtTwoAm))
    }

    @Test
    fun equalStartAndEndDoesNotCreateAnAccidentalOneMinuteSchedule() {
        val schedule = ScheduleEntity(
            daysOfWeekMask = 127,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 8 * 60
        )
        val mondayAtEight = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 5, 8, 0, 0)
        }

        assertFalse(isScheduleActiveAt(schedule, mondayAtEight))
    }

    @Test
    fun lowercaseAllowedPrefixIsParsedCaseInsensitively() {
        val schedule = ScheduleEntity(packageName = "allowed:com.example.safe")

        assertFalse(scheduleTargetsPackage(schedule, "com.example.safe"))
        assertTrue(scheduleTargetsPackage(schedule, "com.example.blocked"))
    }

    @Test
    fun allAppsScheduleExemptsCallSurfaceWithoutExemptingPhonePe() {
        val schedule = ScheduleEntity(
            packageName = "ALL_APPS",
            daysOfWeekMask = 127,
            startMinuteOfDay = 0,
            endMinuteOfDay = 1439
        )
        val mondayAtNoon = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 5, 12, 0, 0)
        }
        val evaluator = ScheduleEvaluator()

        assertNull(evaluator.isBlockedBySchedule("com.truecaller", listOf(schedule), mondayAtNoon))
        assertNotNull(evaluator.isBlockedBySchedule("com.phonepe.app", listOf(schedule), mondayAtNoon))
    }
}
