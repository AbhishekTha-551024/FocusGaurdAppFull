package com.focusguard.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppTimeLimitDao {

    @Query("SELECT * FROM app_time_limits ORDER BY appName ASC")
    fun watchAllTimeLimits(): Flow<List<AppTimeLimitEntity>>

    @Query("SELECT * FROM app_time_limits WHERE isEnabled = 1 ORDER BY appName ASC")
    fun watchEnabledTimeLimits(): Flow<List<AppTimeLimitEntity>>

    @Query("SELECT * FROM app_time_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getTimeLimit(packageName: String): AppTimeLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppTimeLimitEntity)

    @Query("DELETE FROM app_time_limits WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM app_time_limits WHERE isEnabled = 1")
    fun countActive(): Flow<Int>
}
