package com.focusguard.app.core.database.dao

import androidx.room.*
import com.focusguard.app.core.database.entity.BlockedKeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedKeywordDao {

    @Query("SELECT * FROM blocked_keywords ORDER BY createdAt DESC")
    fun watchBlockedKeywords(): Flow<List<BlockedKeywordEntity>>

    @Query("SELECT * FROM blocked_keywords WHERE keyword = :keyword LIMIT 1")
    suspend fun getBlockedKeyword(keyword: String): BlockedKeywordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedKeywordEntity): Long

    @Query("DELETE FROM blocked_keywords WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    @Query("DELETE FROM blocked_keywords WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM blocked_keywords WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BlockedKeywordEntity?

    @Query("SELECT COUNT(*) FROM blocked_keywords")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BlockedKeywordEntity>)

    @Query("DELETE FROM blocked_keywords")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<BlockedKeywordEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
