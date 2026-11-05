package com.focusguard.app.core.model

import com.focusguard.app.core.model.ChangeType

data class ActiveChallengeState(
    val changeType: ChangeType,
    val targetId: String,
    val targetName: String,
    val targetPayload: String? = null,
    val description: String
)
