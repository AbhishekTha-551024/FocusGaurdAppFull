package com.focusguard.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        SecurityEvent::class,
        BlockedAppEntity::class,
        BlockedKeywordEntity::class,
        AppTimeLimitEntity::class,
        ScheduleEntity::class,
        PendingChangeEntity::class,
        PartnershipEntity::class,
        MindfulAppEntity::class,
        MindfulIntentLogEntity::class
    ],
    version = 7,
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

/** Room type converters for enums and other non-primitive types. */
class Converters {
    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = try {
        EventType.valueOf(value)
    } catch (_: Exception) {
        EventType.APP_BLOCKED
    }

    @TypeConverter
    fun fromChangeType(value: ChangeType): String = value.name

    @TypeConverter
    fun toChangeType(value: String): ChangeType = try {
        ChangeType.valueOf(value)
    } catch (_: Exception) {
        ChangeType.UNBLOCK_APP
    }

    @TypeConverter
    fun fromPendingStatus(value: PendingStatus): String = value.name

    @TypeConverter
    fun toPendingStatus(value: String): PendingStatus = try {
        PendingStatus.valueOf(value)
    } catch (_: Exception) {
        PendingStatus.PENDING
    }

    @TypeConverter
    fun fromChallengeType(value: ChallengeType): String = value.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType = try {
        ChallengeType.valueOf(value)
    } catch (_: Exception) {
        ChallengeType.TYPING
    }

    @TypeConverter
    fun fromPartnershipStatus(value: com.focusguard.app.data.model.PartnershipStatus): String = value.name

    @TypeConverter
    fun toPartnershipStatus(value: String): com.focusguard.app.data.model.PartnershipStatus = try {
        com.focusguard.app.data.model.PartnershipStatus.valueOf(value)
    } catch (_: Exception) {
        com.focusguard.app.data.model.PartnershipStatus.PENDING_INVITE
    }
}
