package com.focusguard.app.core.database.dao

import androidx.room.*
import com.focusguard.app.core.database.entity.MindfulAppEntity
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
}
