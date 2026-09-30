package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChangeType {
    UNBLOCK_APP,
    DELETE_KEYWORD,
    DISABLE_SCHEDULE,
    DELETE_SCHEDULE,
    LOWER_LIMIT,
    DISABLE_LIMIT,
    CHANGE_CHALLENGE_TYPE,
    UNINSTALL_REQUEST,
    DISABLE_DISTRACTION_FEED,
    LOWER_COUNTDOWN,          // Reducing block-screen countdown requires challenge
    LOGOUT_REQUEST,
    ENABLE_UNINSTALL_PROTECTION,
    REMOVE_MINDFUL_APP
}

enum class PendingStatus {
    PENDING,
    APPLIED,
    CANCELLED
}

enum class ChallengeType {
    TYPING,
    PARTNER
}

@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val changeType: ChangeType,
    val targetId: String,
    val targetName: String = "",
    val targetPayload: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val scheduledApplyAt: Long? = null,
    val status: PendingStatus = PendingStatus.PENDING
)
