package com.focusguard.app.feature.mindful.domain.model

data class MindfulApp(
    val packageName: String,
    val appName: String,
    val defaultAllowanceMinutes: Int = 5,
    val createdAt: Long = System.currentTimeMillis()
)
