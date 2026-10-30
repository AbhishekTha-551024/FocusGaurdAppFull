package com.focusguard.app.feature.schedules.domain.model

data class Schedule(
    val id: Long = 0,
    val title: String = "Focus Hours",
    val packageName: String? = null,
    val appName: String? = null,
    val daysOfWeekMask: Int = 31,
    val startMinuteOfDay: Int = 540,
    val endMinuteOfDay: Int = 1020,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
