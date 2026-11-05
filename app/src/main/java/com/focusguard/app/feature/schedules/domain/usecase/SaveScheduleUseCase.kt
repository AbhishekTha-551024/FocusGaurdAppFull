package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.common.result.AppError
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.common.validator.InputValidator
import com.focusguard.app.core.common.validator.ValidationResult
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.feature.schedules.domain.model.isScheduleWindowValid
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import javax.inject.Inject

class SaveScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(schedule: ScheduleEntity): AppResult<Unit> {
        when (val titleValidation = InputValidator.validateScheduleTitle(schedule.title)) {
            is ValidationResult.Invalid -> return AppResult.Failure(
                AppError.ValidationError(
                    errorMessage = titleValidation.error.message,
                    errorCode = titleValidation.error.code
                )
            )
            ValidationResult.Valid -> Unit
        }

        if (!isScheduleWindowValid(
                schedule.daysOfWeekMask,
                schedule.startMinuteOfDay,
                schedule.endMinuteOfDay
            )
        ) {
            return AppResult.Failure(
                AppError.ValidationError(
                    errorMessage = "Choose valid days and different start and end times.",
                    errorCode = "VAL-SCHEDULE-WINDOW"
                )
            )
        }

        return repository.saveSchedule(schedule)
    }
}
