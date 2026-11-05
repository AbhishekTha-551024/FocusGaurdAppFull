package com.focusguard.app.feature.schedules.ui

import java.util.Locale

object ScheduleFormatters {

    fun formatMinutes(minutesOfDay: Int): String {
        val h = minutesOfDay / 60
        val m = minutesOfDay % 60
        val amPm = if (h >= 12) "PM" else "AM"
        val displayH = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayH, m, amPm)
    }

    fun formatDaysOfWeek(mask: Int): String {
        return when (mask) {
            127 -> "Every day"
            31 -> "Weekdays"
            96 -> "Weekends"
            else -> {
                val list = mutableListOf<String>()
                if ((mask and 1) != 0) list.add("Mon")
                if ((mask and 2) != 0) list.add("Tue")
                if ((mask and 4) != 0) list.add("Wed")
                if ((mask and 8) != 0) list.add("Thu")
                if ((mask and 16) != 0) list.add("Fri")
                if ((mask and 32) != 0) list.add("Sat")
                if ((mask and 64) != 0) list.add("Sun")
                list.joinToString(", ")
            }
        }
    }
}
