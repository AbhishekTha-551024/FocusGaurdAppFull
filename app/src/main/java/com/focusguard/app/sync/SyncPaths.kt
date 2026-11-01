package com.focusguard.app.sync

/**
 * SyncPaths
 * =========
 * Authoritative Firestore collection, document, and field name constants for Cloud Sync.
 * Values are strictly immutable and identical to Firestore wire-schema.
 */
object SyncPaths {

    // Top-level collections & subcollections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_APP_BLOCKLIST = "app_blocklist"
    const val COLLECTION_BLOCKED_KEYWORDS = "blocked_keywords"
    const val COLLECTION_KEYWORDS_LEGACY = "keywords"
    const val COLLECTION_MINDFUL_APPS = "mindful_apps"
    const val COLLECTION_SCHEDULES = "schedules"
    const val COLLECTION_TIME_LIMITS = "time_limits"
    const val COLLECTION_ACCOUNTABILITY = "accountability"
    const val DOCUMENT_ACCOUNTABILITY_SETTINGS = "settings"

    // Common fields
    const val FIELD_PACKAGE_NAME = "packageName"
    const val FIELD_APP_NAME = "appName"
    const val FIELD_DATE_ADDED = "dateAdded"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_IS_ENABLED = "isEnabled"

    // App Blocklist fields
    const val FIELD_IS_AUTO_BLOCKED_UNSUPPORTED_BROWSER = "isAutoBlockedUnsupportedBrowser"
    const val FIELD_IS_AUTO_BLOCKED_NEW_INSTALL = "isAutoBlockedNewInstall"

    // Keyword fields
    const val FIELD_KEYWORD = "keyword"

    // Mindful Apps fields
    const val FIELD_DEFAULT_ALLOWANCE_MINUTES = "defaultAllowanceMinutes"

    // Schedule fields
    const val FIELD_SCHEDULE_ID = "id"
    const val FIELD_TITLE = "title"
    const val FIELD_DAYS_OF_WEEK_MASK = "daysOfWeekMask"
    const val FIELD_START_MINUTE_OF_DAY = "startMinuteOfDay"
    const val FIELD_END_MINUTE_OF_DAY = "endMinuteOfDay"

    // Time Limit fields
    const val FIELD_DAILY_LIMIT_MINUTES = "dailyLimitMinutes"
    const val FIELD_DATE_CONFIGURED = "dateConfigured"

    // Accountability Settings fields
    const val FIELD_IS_CHALLENGE_ACTIVE = "isChallengeActive"
    const val FIELD_CHALLENGE_TYPE = "challengeType"
    const val FIELD_COMMITMENT_SENTENCE = "commitmentSentence"
    const val FIELD_TIME_DELAY_HOURS = "timeDelayHours"
}

