package com.focusguard.app.feature.schedules.ui

import com.focusguard.app.core.database.entity.ScheduleEntity

data class PendingAccountabilitySchedule(
    val schedule: ScheduleEntity,
    val isDelete: Boolean = false,
    val isAdd: Boolean = false
)
