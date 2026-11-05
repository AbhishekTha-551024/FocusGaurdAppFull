package com.focusguard.app.core.model

fun scheduledApplyTimestamp(requestedAtMillis: Long, delayHours: Int): Long {
    val delayMillis = delayHours.coerceAtLeast(1).toLong() * 60L * 60L * 1_000L
    return if (requestedAtMillis > Long.MAX_VALUE - delayMillis) {
        Long.MAX_VALUE
    } else {
        requestedAtMillis + delayMillis
    }
}
