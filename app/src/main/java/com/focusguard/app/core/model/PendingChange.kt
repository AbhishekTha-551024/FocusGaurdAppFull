package com.focusguard.app.core.model

/**
 * What a delayed protection change will do when its delay matures.
 *
 * Pure Kotlin domain concept, persisted by name through
 * [com.focusguard.app.core.database.Converters].
 */
enum class ChangeType {
    UNKNOWN,
    UNBLOCK_APP,
    DELETE_KEYWORD,
    DISABLE_SCHEDULE,
    DELETE_SCHEDULE,
    LOWER_LIMIT,
    DISABLE_LIMIT,
    CHANGE_CHALLENGE_TYPE,
    UNINSTALL_REQUEST,
    DISABLE_DISTRACTION_FEED,
    LOWER_COUNTDOWN,
    LOGOUT_REQUEST,
    ENABLE_UNINSTALL_PROTECTION,
    REMOVE_MINDFUL_APP,
    INCREASE_MINDFUL_ALLOWANCE
}

/**
 * Lifecycle of a delayed change. Only [PENDING] rows are reconciled at boot or on schedule.
 */
enum class PendingStatus {
    PENDING,
    APPLIED,
    CANCELLED
}
