package com.focusguard.app.usage

import android.util.Log
import com.focusguard.app.data.db.ScheduleEntity
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class ScheduleBlockResult(
    val scheduleTitle: String,
    val startMinute: Int,
    val endMinute: Int,
    val endTimeFormatted: String
)

private const val TAG = "ScheduleEvaluator"

@Singleton
class ScheduleEvaluator @Inject constructor() {

    fun isBlockedBySchedule(
        packageName: String,
        schedules: List<ScheduleEntity>,
        isAppBlockedInBlocklist: Boolean,
        calendar: Calendar = Calendar.getInstance()
    ): ScheduleBlockResult? {
        // Guard 1: Never ever block System UI, Clock, Alarm, Wellbeing, Phone, Keyboards, or Launchers
        if (isSystemOrEssentialPackage(packageName)) {
            return null
        }

        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentDayBit = dayToBitmask(currentDayOfWeek)
        val yesterdayCal = (calendar.clone() as Calendar).apply { add(Calendar.DATE, -1) }
        val yesterdayDayOfWeek = yesterdayCal.get(Calendar.DAY_OF_WEEK)
        val yesterdayBit = dayToBitmask(yesterdayDayOfWeek)
        val currentMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        for (schedule in schedules) {
            if (!schedule.isEnabled) continue

            // 1. Check Time & Day recurrence (with full overnight across-midnight support)
            val isOvernight = schedule.startMinuteOfDay > schedule.endMinuteOfDay
            val isScheduleActiveNow = if (!isOvernight) {
                val isDayActive = (schedule.daysOfWeekMask and currentDayBit) != 0
                val isTimeActive = currentMinuteOfDay in schedule.startMinuteOfDay..schedule.endMinuteOfDay
                isDayActive && isTimeActive
            } else {
                // Overnight schedule across midnight (e.g. 22:00 to 06:00)
                if (currentMinuteOfDay >= schedule.startMinuteOfDay) {
                    // Evening portion before midnight: originates on TODAY
                    (schedule.daysOfWeekMask and currentDayBit) != 0
                } else if (currentMinuteOfDay <= schedule.endMinuteOfDay) {
                    // Morning portion after midnight: originated YESTERDAY evening
                    (schedule.daysOfWeekMask and yesterdayBit) != 0
                } else {
                    false
                }
            }

            if (!isScheduleActiveNow) continue

            // 2. Check App Scope (Supports Excluded/Allowed apps and Target apps)
            val isTargetApp = when {
                // Case A: User specified which apps NOT to block (EXCLUDE Whitelist)
                schedule.packageName?.startsWith("EXCLUDE:", ignoreCase = true) == true -> {
                    val excludedList = schedule.packageName
                        .removePrefix("EXCLUDE:")
                        .split(",")
                        .map { it.trim().lowercase() }
                    
                    val isExcluded = excludedList.contains(packageName.lowercase())
                    !isExcluded // Block everything else!
                }

                // Case B: User specified which apps NOT to block (ALLOWED Whitelist)
                schedule.packageName?.startsWith("ALLOWED:", ignoreCase = true) == true -> {
                    val allowedList = schedule.packageName
                        .removePrefix("ALLOWED:")
                        .split(",")
                        .map { it.trim().lowercase() }
                    
                    val isAllowed = allowedList.contains(packageName.lowercase())
                    !isAllowed // Block everything else!
                }

                // Case C: Null or ALL_APPS -> Block all non-system distracting apps
                schedule.packageName.isNullOrBlank() || schedule.packageName.equals("ALL_APPS", ignoreCase = true) -> {
                    true
                }

                // Case D: Specific targeted app(s) to block
                else -> {
                    val packageList = schedule.packageName.split(",").map { it.trim().lowercase() }
                    packageList.contains(packageName.lowercase())
                }
            }

            if (!isTargetApp) continue

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
        return lower == "android" ||
                lower == "com.android.systemui" ||
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
                lower.contains("dialer") ||
                lower.contains("incallui") ||
                lower.contains("telecom") ||
                lower.contains("telephony") ||
                lower.contains("phone") ||
                lower.contains("settings") ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.google.android.gms") ||
                lower.startsWith("com.google.android.gsf") ||
                lower.startsWith("com.google.android.packageinstaller") ||
                lower.startsWith("com.android.packageinstaller") ||
                lower.startsWith("com.focusguard.app")
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
}
