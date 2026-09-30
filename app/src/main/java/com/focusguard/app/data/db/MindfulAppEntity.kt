package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an application designated for Mindful Intent Pause (One Sec / Opal style).
 * When launched, instead of hard blocking, it triggers a 6-10s breathing delay
 * and conscious intent prompt before granting temporary allowance.
 */
@Entity(tableName = "mindful_apps")
data class MindfulAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val defaultAllowanceMinutes: Int = 5,
    val dateAdded: Long = System.currentTimeMillis()
)
