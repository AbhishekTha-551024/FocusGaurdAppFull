package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a security event logged to the local Room database.
 */
@Entity(tableName = "security_events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: EventType,
    val detail: String = ""
)

enum class EventType {
    ADMIN_ENABLED,
    ADMIN_DISABLED,
    UNINSTALL_ATTEMPT,
    PIN_WRONG,
    PIN_COOLDOWN_STARTED,
    PIN_CORRECT,
    ONBOARDING_COMPLETE,
    APP_BLOCKED,
    APP_UNBLOCKED,
    SERVICE_ERROR,
    SERVICE_CONNECTED,
    SERVICE_RESTARTED_UNEXPECTEDLY,
    ADMIN_REMOVED_WHILE_PROTECTED
}
