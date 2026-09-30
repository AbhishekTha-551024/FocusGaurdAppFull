package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ScheduleEntity
 * ==============
 * Represents a scheduled focus window (e.g., Mon–Fri, 09:00–17:00).
 *
 * @param packageName Null indicates the schedule applies to ALL blocked apps.
 *                    A specific package name restricts it only to that app.
 * @param daysOfWeekMask Bitmask where Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64.
 * @param startMinuteOfDay Minute from midnight (0..1439), e.g. 09:00 = 540.
 * @param endMinuteOfDay Minute from midnight (0..1439), e.g. 17:00 = 1020.
 */
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
