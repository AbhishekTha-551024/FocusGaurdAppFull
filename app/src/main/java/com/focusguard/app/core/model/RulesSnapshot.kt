package com.focusguard.app.core.model

/**
 * Immutable in-memory snapshot of all active blocking and protection rules.
 * Updated atomically via AtomicReference, eliminating multi-cache race conditions.
 */
data class RulesSnapshot(
    val blockedPackages: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val mindfulPackages: Set<String> = emptySet(),
    val timeLimitsMinutes: Map<String, Int> = emptyMap(),
    val isUninstallProtectionEnabled: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val isShortsBlockingEnabled: Boolean = false,
    val isReelsBlockingEnabled: Boolean = false,
    val isIncognitoBlockingEnabled: Boolean = false,
    val isDeepFocusActive: Boolean = false,
    val deepFocusSuitcasePackages: Set<String> = emptySet(),
    val isChallengeActive: Boolean = false,
    val autoBlockNewApps: Boolean = false,
    val timestampMs: Long = 0L
)
