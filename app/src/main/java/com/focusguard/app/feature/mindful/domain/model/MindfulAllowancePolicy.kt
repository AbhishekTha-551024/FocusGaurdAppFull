package com.focusguard.app.feature.mindful.domain.model

object MindfulAllowancePolicy {
    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L

    fun expiryAt(nowElapsedMs: Long, durationMinutes: Int): Long =
        nowElapsedMs + durationMinutes.coerceAtLeast(0) * SECONDS_PER_MINUTE * MILLIS_PER_SECOND

    fun isActive(expiryElapsedMs: Long, nowElapsedMs: Long): Boolean =
        nowElapsedMs < expiryElapsedMs

    fun remainingSeconds(expiryElapsedMs: Long, nowElapsedMs: Long): Long =
        ((expiryElapsedMs - nowElapsedMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
}
