package com.focusguard.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MindfulDao {

    // ── Mindful Apps Configuration ──────────────────────────────────────────

    @Query("SELECT * FROM mindful_apps ORDER BY dateAdded DESC")
    fun getAllMindfulApps(): Flow<List<MindfulAppEntity>>

    @Query("SELECT packageName FROM mindful_apps")
    suspend fun getMindfulPackagesSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMindfulApp(app: MindfulAppEntity)

    @Query("DELETE FROM mindful_apps WHERE packageName = :packageName")
    suspend fun deleteMindfulApp(packageName: String)

    @Query("UPDATE mindful_apps SET defaultAllowanceMinutes = :minutes WHERE packageName = :packageName")
    suspend fun updateAllowanceMinutes(packageName: String, minutes: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM mindful_apps WHERE packageName = :packageName)")
    suspend fun isAppMindful(packageName: String): Boolean

    // ── Intent Logs & Victory Analytics ──────────────────────────────────────

    @Insert
    suspend fun insertIntentLog(log: MindfulIntentLogEntity)

    @Query("SELECT * FROM mindful_intent_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<MindfulIntentLogEntity>>

    @Query("SELECT COUNT(*) FROM mindful_intent_logs WHERE wasWalkedAway = 1 AND timestamp >= :startOfDay")
    fun getTodayWalkedAwayCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM mindful_intent_logs WHERE wasWalkedAway = 0 AND timestamp >= :startOfDay")
    fun getTodayConsciousOpensCount(startOfDay: Long): Flow<Int>

    @Query("DELETE FROM mindful_intent_logs WHERE timestamp < :beforeTimestamp")
    suspend fun clearOldLogs(beforeTimestamp: Long)
}
