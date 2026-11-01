package com.focusguard.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SyncPathsTest
 * =============
 * Snapshot unit test ensuring Firestore collection, document, and field path constants
 * strictly equal their original wire-format values and can never be altered silently.
 */
class SyncPathsTest {

    @Test
    fun testFirestoreCollectionsAndDocumentsMatchOriginals() {
        assertEquals("users", SyncPaths.COLLECTION_USERS)
        assertEquals("app_blocklist", SyncPaths.COLLECTION_APP_BLOCKLIST)
        assertEquals("blocked_keywords", SyncPaths.COLLECTION_BLOCKED_KEYWORDS)
        assertEquals("keywords", SyncPaths.COLLECTION_KEYWORDS_LEGACY)
        assertEquals("mindful_apps", SyncPaths.COLLECTION_MINDFUL_APPS)
        assertEquals("schedules", SyncPaths.COLLECTION_SCHEDULES)
        assertEquals("time_limits", SyncPaths.COLLECTION_TIME_LIMITS)
        assertEquals("accountability", SyncPaths.COLLECTION_ACCOUNTABILITY)
        assertEquals("settings", SyncPaths.DOCUMENT_ACCOUNTABILITY_SETTINGS)
    }

    @Test
    fun testCommonAndEntityFieldNamesMatchOriginals() {
        // Common
        assertEquals("packageName", SyncPaths.FIELD_PACKAGE_NAME)
        assertEquals("appName", SyncPaths.FIELD_APP_NAME)
        assertEquals("dateAdded", SyncPaths.FIELD_DATE_ADDED)
        assertEquals("updatedAt", SyncPaths.FIELD_UPDATED_AT)
        assertEquals("createdAt", SyncPaths.FIELD_CREATED_AT)
        assertEquals("isEnabled", SyncPaths.FIELD_IS_ENABLED)

        // App Blocklist
        assertEquals("isAutoBlockedUnsupportedBrowser", SyncPaths.FIELD_IS_AUTO_BLOCKED_UNSUPPORTED_BROWSER)
        assertEquals("isAutoBlockedNewInstall", SyncPaths.FIELD_IS_AUTO_BLOCKED_NEW_INSTALL)

        // Keywords
        assertEquals("keyword", SyncPaths.FIELD_KEYWORD)

        // Mindful Apps
        assertEquals("defaultAllowanceMinutes", SyncPaths.FIELD_DEFAULT_ALLOWANCE_MINUTES)

        // Schedules
        assertEquals("id", SyncPaths.FIELD_SCHEDULE_ID)
        assertEquals("title", SyncPaths.FIELD_TITLE)
        assertEquals("daysOfWeekMask", SyncPaths.FIELD_DAYS_OF_WEEK_MASK)
        assertEquals("startMinuteOfDay", SyncPaths.FIELD_START_MINUTE_OF_DAY)
        assertEquals("endMinuteOfDay", SyncPaths.FIELD_END_MINUTE_OF_DAY)

        // Time Limits
        assertEquals("dailyLimitMinutes", SyncPaths.FIELD_DAILY_LIMIT_MINUTES)
        assertEquals("dateConfigured", SyncPaths.FIELD_DATE_CONFIGURED)

        // Accountability
        assertEquals("isChallengeActive", SyncPaths.FIELD_IS_CHALLENGE_ACTIVE)
        assertEquals("challengeType", SyncPaths.FIELD_CHALLENGE_TYPE)
        assertEquals("commitmentSentence", SyncPaths.FIELD_COMMITMENT_SENTENCE)
        assertEquals("timeDelayHours", SyncPaths.FIELD_TIME_DELAY_HOURS)
    }
}

