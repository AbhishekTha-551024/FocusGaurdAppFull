package com.focusguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents an application actively blocked by FocusGuard.
 */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val isAutoBlockedUnsupportedBrowser: Boolean = false,
    val isAutoBlockedNewInstall: Boolean = false
)
