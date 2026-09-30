package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks every mindful opening attempt, the user's conscious intention,
 * and whether the user chose to Walk Away (impulse victory) or continue.
 */
@Entity(tableName = "mindful_intent_logs")
data class MindfulIntentLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val intentText: String,
    val intentCategory: String = "CUSTOM", // "WORK", "QUICK_REPLY", "OTP", "CUSTOM"
    val allowanceMinutes: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val wasWalkedAway: Boolean = false // true = User tapped Walk Away / Cancel during pause
)
