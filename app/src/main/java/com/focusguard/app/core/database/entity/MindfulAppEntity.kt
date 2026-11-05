package com.focusguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mindful_apps")
data class MindfulAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val defaultAllowanceMinutes: Int = 5,
    val dateAdded: Long = System.currentTimeMillis()
)
