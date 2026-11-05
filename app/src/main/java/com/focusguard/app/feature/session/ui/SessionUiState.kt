package com.focusguard.app.feature.session.ui

import com.focusguard.app.core.model.AppInfo

data class SessionUiState(
    val isActive: Boolean = false,
    val endTimeMillis: Long = 0L,
    val totalMinutes: Int = 25,
    val remainingMillis: Long = 0L,
    val selectedDurationMinutes: Int = 25,
    val packedPackages: Set<String> = emptySet(),
    val availableApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val parachutesCount: Int = 2,
    val totalSessionsCompleted: Int = 0,
    val totalMinutesCompleted: Long = 0L,
    val showPackingDialog: Boolean = false,
    val showAbortDialog: Boolean = false,
    val errorMessage: String? = null
)
