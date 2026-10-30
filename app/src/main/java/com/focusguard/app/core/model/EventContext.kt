package com.focusguard.app.core.model

/**
 * Pure Kotlin snapshot of an incoming accessibility event.
 * Contains zero references to Android AccessibilityNodeInfo or AccessibilityEvent,
 * ensuring zero memory leaks and allowing 100% JVM unit testability.
 */
data class EventContext(
    val packageName: String,
    val className: String? = null,
    val eventType: Int = 0,
    val windowType: Int? = null,
    val monotonicTimeMs: Long = 0L,
    val detectedUrl: String? = null,
    val visibleScreenTexts: Set<String> = emptySet(),
    val matchedNodeIds: Set<String> = emptySet(),
    val isSplitScreenOrMultiWindow: Boolean = false,
    val targetAppLabel: String = ""
)
