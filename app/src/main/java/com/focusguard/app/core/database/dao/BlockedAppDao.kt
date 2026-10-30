package com.focusguard.app.core.database.dao

import androidx.room.*
import com.focusguard.app.core.database.entity.BlockedAppEntity
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(apps: List<BlockedAppEntity>) {
        deleteAll()
        insertAll(apps)
    }

    @Query("SELECT * FROM blocked_apps WHERE isAutoBlockedNewInstall = 1")
    suspend fun getAutoBlockedNewInstallApps(): List<BlockedAppEntity>

    @Query("DELETE FROM blocked_apps WHERE isAutoBlockedNewInstall = 1")
    suspend fun deleteAutoBlockedNewInstallApps()
}
