package com.focusguard.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DatabaseMigrations
 * ==================
 * Non-destructive schema migrations for Room database across versions 1 to 7.
 * Preserves user settings, block rules, and focus records during app updates.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `blocked_keywords` (" +
                    "`keyword` TEXT NOT NULL, " +
                    "`dateAdded` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`keyword`))"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_time_limits` (" +
                    "`packageName` TEXT NOT NULL, " +
                    "`appName` TEXT NOT NULL, " +
                    "`dailyLimitMinutes` INTEGER NOT NULL, " +
                    "`isEnabled` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`packageName`))"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `schedules` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`daysOfWeek` TEXT NOT NULL, " +
                    "`startHour` INTEGER NOT NULL, " +
                    "`startMinute` INTEGER NOT NULL, " +
                    "`endHour` INTEGER NOT NULL, " +
                    "`endMinute` INTEGER NOT NULL, " +
                    "`isEnabled` INTEGER NOT NULL, " +
                    "`blockedPackageNames` TEXT NOT NULL)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_changes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`changeType` TEXT NOT NULL, " +
                    "`targetId` TEXT NOT NULL, " +
                    "`targetName` TEXT NOT NULL, " +
                    "`targetPayload` TEXT, " +
                    "`requestedAt` INTEGER NOT NULL, " +
                    "`scheduledApplyAt` INTEGER, " +
                    "`status` TEXT NOT NULL, " +
                    "`reason` TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `partnerships` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`partnerUserId` TEXT NOT NULL, " +
                    "`partnerEmail` TEXT, " +
                    "`inviteCode` TEXT, " +
                    "`status` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mindful_apps` (" +
                    "`packageName` TEXT NOT NULL, " +
                    "`appName` TEXT NOT NULL, " +
                    "`defaultAllowanceMinutes` INTEGER NOT NULL, " +
                    "`dateAdded` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`packageName`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mindful_intent_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`appName` TEXT NOT NULL, " +
                    "`intentionText` TEXT NOT NULL, " +
                    "`allowanceMinutes` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`wasWalkedAway` INTEGER NOT NULL)"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE `blocked_apps` ADD COLUMN `isAutoBlockedUnsupportedBrowser` INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                android.util.Log.d("DatabaseMigrations", "Column isAutoBlockedUnsupportedBrowser exists: ${e.message}")
            }
            try {
                db.execSQL("ALTER TABLE `blocked_apps` ADD COLUMN `isAutoBlockedNewInstall` INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                android.util.Log.d("DatabaseMigrations", "Column isAutoBlockedNewInstall exists: ${e.message}")
            }
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `mindful_intent_logs`")
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )
}
