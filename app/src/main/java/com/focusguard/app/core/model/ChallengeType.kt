package com.focusguard.app.core.model

/**
 * How a friction challenge is completed.
 *
 * Pure Kotlin domain concept: Room stores it by `name` through
 * [com.focusguard.app.core.database.Converters], so UI and domain code can use it without
 * depending on the database package.
 */
enum class ChallengeType {
    TYPING,
    PARTNER
}
