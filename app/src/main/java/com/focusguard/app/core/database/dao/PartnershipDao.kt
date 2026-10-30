package com.focusguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.app.core.database.entity.PartnershipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnershipDao {
    @Query("SELECT * FROM partnerships WHERE ownerUserId = :userId OR partnerUserId = :userId OR partnerEmail = :email LIMIT 1")
    fun observeCurrentPartnership(userId: String, email: String): Flow<PartnershipEntity?>

    @Query("SELECT * FROM partnerships WHERE status = 'ACTIVE' LIMIT 1")
    fun getActivePartnership(): Flow<PartnershipEntity?>

    @Query("SELECT * FROM partnerships WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActivePartnershipSync(): PartnershipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(partnership: PartnershipEntity)

    @Query("DELETE FROM partnerships WHERE partnershipId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM partnerships")
    suspend fun clearAll()
}
