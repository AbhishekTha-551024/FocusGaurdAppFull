package com.focusguard.app.data.db

import androidx.room.*
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
}
