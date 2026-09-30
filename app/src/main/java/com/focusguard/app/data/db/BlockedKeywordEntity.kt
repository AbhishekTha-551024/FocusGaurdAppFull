package com.focusguard.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BlockedKeywordEntity
 * ====================
 * Represents a blocked keyword or website domain.
 * Keywords are stored lowercase and indexed uniquely.
 */
@Entity(
    tableName = "blocked_keywords",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class BlockedKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val createdAt: Long = System.currentTimeMillis()
)
