package com.focusguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "Focus Hours",
    val packageName: String? = null,
    val appName: String? = null,
    val daysOfWeekMask: Int = 31, // Default: Mon-Fri (1+2+4+8+16 = 31)
    val startMinuteOfDay: Int = 540, // 09:00 AM
    val endMinuteOfDay: Int = 1020, // 05:00 PM
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
