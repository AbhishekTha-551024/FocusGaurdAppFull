package com.focusguard.app.core.model

/**
 * Reasons why an application, web domain, or system setting action was intercepted.
 */
enum class BlockReason {
    APP_BLOCKED,
    KEYWORD_BLOCKED,
    SCHEDULE_ACTIVE,
    TIME_LIMIT_EXCEEDED,
    SHORT_OR_REEL,
    INCOGNITO_RESTRICTED,
    CLONER_OR_DUAL_APP,
    UNINSTALL_PROTECTED,
    SETTINGS_TAMPER,
    SESSION_LOCKED
}
