package com.focusguard.app.data.model

import com.google.firebase.Timestamp

enum class PartnershipStatus {
    PENDING_INVITE,
    ACTIVE,
    REVOKED
}

data class Partnership(
    val partnershipId: String = "",
    val ownerUserId: String = "",
    val ownerEmail: String = "",
    val partnerEmail: String = "",
    val partnerUserId: String? = null,
    val status: String = PartnershipStatus.PENDING_INVITE.name,
    val createdAt: Timestamp? = null
)
