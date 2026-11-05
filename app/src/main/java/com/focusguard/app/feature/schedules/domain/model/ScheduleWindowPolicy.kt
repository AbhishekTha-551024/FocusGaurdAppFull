package com.focusguard.app.feature.schedules.domain.model

fun isScheduleWindowValid(daysOfWeekMask: Int, startMinute: Int, endMinute: Int): Boolean =
    daysOfWeekMask in 1..127 &&
        startMinute in 0..1439 &&
        endMinute in 0..1439 &&
        startMinute != endMinute
