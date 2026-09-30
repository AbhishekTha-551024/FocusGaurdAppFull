package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.focusguard.app.data.model.PartnershipStatus

@Entity(tableName = "partnerships")
data class PartnershipEntity(
    @PrimaryKey
    val partnershipId: String,
    val ownerUserId: String,
    val ownerEmail: String,
    val partnerEmail: String,
    val partnerUserId: String?,
    val status: PartnershipStatus,
    val createdAt: Long = System.currentTimeMillis()
)
