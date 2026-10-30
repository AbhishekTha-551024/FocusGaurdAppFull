package com.focusguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mindful_intent_logs")
data class MindfulIntentLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val intentText: String,
    val intentCategory: String = "CUSTOM",
    val allowanceMinutes: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val wasWalkedAway: Boolean = false
)
