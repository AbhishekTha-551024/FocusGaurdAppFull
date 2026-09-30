package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AppTimeLimitEntity
 * ==================
 * Represents a daily usage time limit cap configured for an application.
 *
 * Enforcement resets automatically at midnight because usage is queried
 * strictly from start-of-today (00:00) via UsageStatsManager.
 */
@Entity(tableName = "app_time_limits")
data class AppTimeLimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val isEnabled: Boolean = true,
    val dateConfigured: Long = System.currentTimeMillis()
)
