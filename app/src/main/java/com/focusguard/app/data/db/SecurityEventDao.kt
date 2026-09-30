package com.focusguard.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SecurityEvent)

    /** Returns all events ordered by most recent first, as a live Flow. */
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SecurityEvent>>

    /** Returns events of a specific type — used for uninstall attempt history. */
    @Query("SELECT * FROM security_events WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByType(type: EventType, limit: Int = 50): List<SecurityEvent>

    /** Returns count of blocked events since midnight (today's prevented distraction impulses). */
    @Query("SELECT COUNT(*) FROM security_events WHERE timestamp >= :sinceMidnight AND (type = 'APP_BLOCKED' OR type = 'UNINSTALL_ATTEMPT')")
    fun observeTodayBlockCount(sinceMidnight: Long): Flow<Int>
}
