package com.focusguard.app.usage

import android.util.Log
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.domain.policy.CallSurfacePolicy
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class ScheduleBlockResult(
    val scheduleTitle: String,
    val startMinute: Int,
    val endMinute: Int,
    val endTimeFormatted: String
)

internal fun isScheduleActiveAt(schedule: ScheduleEntity, calendar: Calendar): Boolean {
    if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) return false

    val currentDayBit = dayToBitmask(calendar.get(Calendar.DAY_OF_WEEK))
    val currentMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val isOvernight = schedule.startMinuteOfDay > schedule.endMinuteOfDay
    if (!isOvernight) {
        return (schedule.daysOfWeekMask and currentDayBit) != 0 &&
            currentMinute in schedule.startMinuteOfDay..schedule.endMinuteOfDay
    }

    if (currentMinute >= schedule.startMinuteOfDay) {
        return (schedule.daysOfWeekMask and currentDayBit) != 0
    }
    if (currentMinute <= schedule.endMinuteOfDay) {
        val yesterday = (calendar.clone() as Calendar).apply { add(Calendar.DATE, -1) }
        val yesterdayBit = dayToBitmask(yesterday.get(Calendar.DAY_OF_WEEK))
        return (schedule.daysOfWeekMask and yesterdayBit) != 0
    }
    return false
}

internal fun scheduleTargetsPackage(schedule: ScheduleEntity, packageName: String): Boolean {
    val pkg = packageName.lowercase()
    return when {
        schedule.packageName?.startsWith("EXCLUDE:", ignoreCase = true) == true -> {
            schedule.packageName.substringAfter(':').split(',').none { it.trim().lowercase() == pkg }
        }
        schedule.packageName?.startsWith("ALLOWED:", ignoreCase = true) == true -> {
            schedule.packageName.substringAfter(':').split(',').none { it.trim().lowercase() == pkg }
        }
        schedule.packageName.isNullOrBlank() ||
            schedule.packageName.equals("ALL_APPS", ignoreCase = true) -> true
        else -> schedule.packageName.split(',').any { it.trim().lowercase() == pkg }
    }
}

private const val TAG = "ScheduleEvaluator"

@Singleton
class ScheduleEvaluator @Inject constructor() {

    fun isBlockedBySchedule(
        packageName: String,
        schedules: List<ScheduleEntity>,
        calendar: Calendar = Calendar.getInstance()
    ): ScheduleBlockResult? {
        // Guard 1: Never ever block System UI, Clock, Alarm, Wellbeing, Phone, Keyboards, or Launchers
        if (isSystemOrEssentialPackage(packageName)) {
            return null
        }

        for (schedule in schedules) {
            if (!schedule.isEnabled) continue
            if (!isScheduleActiveAt(schedule, calendar)) continue
            if (!scheduleTargetsPackage(schedule, packageName)) continue

            val endHour = schedule.endMinuteOfDay / 60
            val endMin = schedule.endMinuteOfDay % 60
            val amPm = if (endHour >= 12) "PM" else "AM"
            val displayHour = when {
                endHour == 0 -> 12
                endHour > 12 -> endHour - 12
                else -> endHour
            }
            val formattedEnd = String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, endMin, amPm)

            Log.i(TAG, "BLOCKED BY SCHEDULE! '${schedule.title}' for $packageName until $formattedEnd")
            return ScheduleBlockResult(
                scheduleTitle = schedule.title,
                startMinute = schedule.startMinuteOfDay,
                endMinute = schedule.endMinuteOfDay,
                endTimeFormatted = formattedEnd
            )
        }

        return null
    }

    private fun isSystemOrEssentialPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return CallSurfacePolicy.isNamedCallSurface(lower) ||
                lower == "android" ||
                lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("trebuchet") ||
                lower.contains("nexuslauncher") ||
                lower.contains("keyboard") ||
                lower.contains("inputmethod") ||
                lower.contains("clock") ||
                lower.contains("alarm") ||
                lower.contains("wellbeing") ||
                lower.contains("wellness") ||
                lower.contains("settings") ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.google.android.gms") ||
                lower.startsWith("com.google.android.gsf") ||
                lower.startsWith("com.google.android.packageinstaller") ||
                lower.startsWith("com.android.packageinstaller")
    }

}

private fun dayToBitmask(dayOfWeek: Int): Int = when (dayOfWeek) {
    Calendar.MONDAY -> 1
    Calendar.TUESDAY -> 2
    Calendar.WEDNESDAY -> 4
    Calendar.THURSDAY -> 8
    Calendar.FRIDAY -> 16
    Calendar.SATURDAY -> 32
    Calendar.SUNDAY -> 64
    else -> 0
}
