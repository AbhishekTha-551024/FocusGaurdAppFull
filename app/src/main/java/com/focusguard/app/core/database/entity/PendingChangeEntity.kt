package com.focusguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.core.model.PendingStatus

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
