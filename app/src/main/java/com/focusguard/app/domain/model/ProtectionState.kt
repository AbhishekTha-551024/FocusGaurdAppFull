package com.focusguard.app.domain.model

enum class ProtectionState {
    UNINITIALIZED,
    PROTECTED,
    DEGRADED,
    RECOVERING,
    RECOVERY_REQUIRED,
    DISABLED
}
