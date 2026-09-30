package com.focusguard.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingChangeDao {

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' ORDER BY requestedAt DESC")
    fun watchPendingChanges(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' AND scheduledApplyAt IS NOT NULL AND scheduledApplyAt <= :currentTime")
    suspend fun getDuePendingChanges(currentTime: Long): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingChangeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingChangeEntity): Long

    @Update
    suspend fun update(entity: PendingChangeEntity)

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_changes SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: PendingStatus)

    @Query("DELETE FROM pending_changes WHERE status = 'PENDING'")
    suspend fun deleteAllPending()

    /**
     * DATA LOSS FIX: Deletes only APPLIED and CANCELLED rows on startup cleanup.
     * PENDING rows (e.g. 24-hour TIME_DELAY challenge approvals) are intentionally preserved
     * so they survive app restarts, phone reboots, and OS process kills.
     */
    @Query("DELETE FROM pending_changes WHERE status = 'APPLIED' OR status = 'CANCELLED'")
    suspend fun deleteApplied()
}
