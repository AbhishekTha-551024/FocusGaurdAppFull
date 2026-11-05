package com.focusguard.app.feature.schedules.ui

import com.focusguard.app.core.database.entity.ScheduleEntity

enum class ScheduleAccountabilityAction {
    CREATE,
    ENABLE,
    DISABLE,
    DELETE
}

data class PendingAccountabilitySchedule(
    val schedule: ScheduleEntity,
    val action: ScheduleAccountabilityAction
)
