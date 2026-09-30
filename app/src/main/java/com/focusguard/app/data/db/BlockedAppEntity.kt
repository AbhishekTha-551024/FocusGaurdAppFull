package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * BlockedAppEntity
 * ================
 * Represents an application actively blocked by FocusGuard.
 *
 * Strictness is HARD: Whenever the app enters foreground, the 10-second
 * friction countdown overlay is immediately triggered.
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
