package com.focusguard.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun watchBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE LOWER(packageName) = LOWER(:packageName) LIMIT 1")
    suspend fun getBlockedApp(packageName: String): BlockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE LOWER(packageName) = LOWER(:packageName)")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT COUNT(*) FROM blocked_apps")
    fun count(): Flow<Int>

    @Query("SELECT * FROM blocked_apps WHERE isAutoBlockedNewInstall = 1")
    suspend fun getAutoBlockedNewInstallApps(): List<BlockedAppEntity>

    @Query("DELETE FROM blocked_apps WHERE isAutoBlockedNewInstall = 1")
    suspend fun deleteAutoBlockedNewInstallApps()
}
