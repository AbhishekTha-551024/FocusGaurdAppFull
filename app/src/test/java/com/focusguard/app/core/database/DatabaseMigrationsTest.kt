package com.focusguard.app.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Unit test for DatabaseMigrations
 * Verifies that all migrations define valid versions and execute without throwing exceptions.
 */
class DatabaseMigrationsTest {

    @Test
    fun testAllMigrationsCountAndVersions() {
        val migrations = DatabaseMigrations.ALL_MIGRATIONS
        assertEquals(7, migrations.size)
        assertEquals(1, migrations[0].startVersion)
        assertEquals(2, migrations[0].endVersion)
        assertEquals(2, migrations[1].startVersion)
        assertEquals(3, migrations[1].endVersion)
        assertEquals(3, migrations[2].startVersion)
        assertEquals(4, migrations[2].endVersion)
        assertEquals(4, migrations[3].startVersion)
        assertEquals(5, migrations[3].endVersion)
        assertEquals(5, migrations[4].startVersion)
        assertEquals(6, migrations[4].endVersion)
        assertEquals(6, migrations[5].startVersion)
        assertEquals(7, migrations[5].endVersion)
        assertEquals(7, migrations[6].startVersion)
        assertEquals(8, migrations[6].endVersion)
    }

    @Test
    fun testMigrationsExecuteSqlStatements() {
        val executedSql = mutableListOf<String>()

        val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                val sql = args?.get(0) as? String
                if (sql != null) executedSql.add(sql)
            }
            null
        } as SupportSQLiteDatabase

        for (migration in DatabaseMigrations.ALL_MIGRATIONS) {
            migration.migrate(dummyDb)
        }

        assertTrue(executedSql.isNotEmpty())
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `blocked_keywords`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `app_time_limits`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `schedules`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `pending_changes`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `partnerships`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `mindful_apps`") })
        assertTrue(executedSql.any { it == "DROP TABLE IF EXISTS `mindful_intent_logs`" })
        assertTrue(executedSql.any { it.contains("ALTER TABLE `blocked_apps` ADD COLUMN") })
    }
}
