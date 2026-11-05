package com.focusguard.app.core.database.dao

import androidx.room.*
import com.focusguard.app.core.database.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM focus_schedules ORDER BY startMinuteOfDay ASC")
    fun watchAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    fun watchEnabledSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduleEntity): Long

    @Update
    suspend fun update(entity: ScheduleEntity)

    @Query("SELECT * FROM focus_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduleEntity?

    @Query("UPDATE focus_schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, isEnabled: Boolean)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM focus_schedules WHERE isEnabled = 1")
    fun countActive(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScheduleEntity>)

    @Query("DELETE FROM focus_schedules")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<ScheduleEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
