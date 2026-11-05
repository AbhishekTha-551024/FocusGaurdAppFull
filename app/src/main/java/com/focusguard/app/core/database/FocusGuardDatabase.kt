package com.focusguard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.focusguard.app.core.database.dao.*
import com.focusguard.app.core.database.entity.*

@Database(
    entities = [
        SecurityEvent::class,
        BlockedAppEntity::class,
        BlockedKeywordEntity::class,
        AppTimeLimitEntity::class,
        ScheduleEntity::class,
        PendingChangeEntity::class,
        PartnershipEntity::class,
        MindfulAppEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FocusGuardDatabase : RoomDatabase() {
    abstract fun securityEventDao(): SecurityEventDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockedKeywordDao(): BlockedKeywordDao
    abstract fun appTimeLimitDao(): AppTimeLimitDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun partnershipDao(): PartnershipDao
    abstract fun mindfulDao(): MindfulDao
}
