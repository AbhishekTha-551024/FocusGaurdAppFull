package com.focusguard.app.feature.schedules.domain.usecase

import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveScheduleUseCaseTest {

    @Test
    fun invalidWindowIsNotPersisted() = runBlocking {
        val repository = RecordingScheduleRepository()
        val result = SaveScheduleUseCase(repository)(
            ScheduleEntity(startMinuteOfDay = 60, endMinuteOfDay = 60)
        )

        assertTrue(result is AppResult.Failure)
        assertFalse(repository.saveCalled)
    }

    @Test
    fun validOvernightWindowIsPersisted() = runBlocking {
        val repository = RecordingScheduleRepository()
        val result = SaveScheduleUseCase(repository)(
            ScheduleEntity(startMinuteOfDay = 22 * 60, endMinuteOfDay = 6 * 60)
        )

        assertTrue(result is AppResult.Success)
        assertTrue(repository.saveCalled)
    }

    private class RecordingScheduleRepository : ScheduleRepository {
        var saveCalled = false

        override fun getAllSchedules(): Flow<List<ScheduleEntity>> = emptyFlow()
        override fun getInstalledApps(): Flow<List<AppInfo>> = emptyFlow()
        override fun getPendingChanges(): Flow<List<PendingChangeEntity>> = emptyFlow()

        override suspend fun saveSchedule(schedule: ScheduleEntity): AppResult<Unit> {
            saveCalled = true
            return AppResult.Success(Unit)
        }

        override suspend fun deleteSchedule(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun updateScheduleEnabled(id: Long, isEnabled: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun cancelPendingChange(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun activateChallengeForSchedule(
            type: ChallengeType,
            sentence: String
        ): AppResult<Unit> = AppResult.Success(Unit)
    }
}
