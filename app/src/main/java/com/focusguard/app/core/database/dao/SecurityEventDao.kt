package com.focusguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.SecurityEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SecurityEvent)

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SecurityEvent>>

    @Query("SELECT * FROM security_events WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: EventType, limit: Int = 50): List<SecurityEvent>

    @Query("SELECT COUNT(*) FROM security_events WHERE timestamp >= :sinceMidnight AND (type = 'APP_BLOCKED' OR type = 'UNINSTALL_ATTEMPT')")
    fun observeTodayBlockCount(sinceMidnight: Long): Flow<Int>
}
