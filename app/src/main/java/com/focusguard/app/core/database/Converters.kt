package com.focusguard.app.core.database

import androidx.room.TypeConverter
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.PendingStatus
import com.focusguard.app.core.model.PartnershipStatus

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
        ChangeType.UNKNOWN
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
    fun fromPartnershipStatus(value: PartnershipStatus): String = value.name

    @TypeConverter
    fun toPartnershipStatus(value: String): PartnershipStatus = try {
        PartnershipStatus.valueOf(value)
    } catch (_: Exception) {
        PartnershipStatus.PENDING_INVITE
    }
}
