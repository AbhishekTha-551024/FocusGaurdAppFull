package com.focusguard.app.data.repository

import android.content.Context
import android.util.Log
import com.focusguard.app.core.database.entity.*
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.domain.policy.ProtectionPolicy
import com.focusguard.app.feature.appblocker.data.repository.AppBlockerRepositoryImpl
import com.focusguard.app.feature.keywordblocker.data.repository.KeywordBlockerRepositoryImpl
import com.focusguard.app.feature.schedules.data.repository.ScheduleRepositoryImpl
import com.focusguard.app.feature.settings.data.repository.*
import com.focusguard.app.feature.timelimits.data.repository.TimeLimitRepositoryImpl
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.usage.LimitStatus
import com.focusguard.app.usage.ScheduleEvaluator
import com.focusguard.app.usage.TimeLimitEvaluator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FocusGuardRepo"

sealed class AccessDecision {
    object Allowed : AccessDecision()
    data class BlockedByAppBlocker(val appLabel: String) : AccessDecision()
    data class BlockedBySchedule(val appLabel: String, val scheduleTitle: String, val endTimeFormatted: String) : AccessDecision()
    data class BlockedByTimeLimit(val appLabel: String, val usedMinutes: Int, val limitMinutes: Int) : AccessDecision()
}

@Singleton
class FocusGuardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val appBlockerRepository: AppBlockerRepositoryImpl,
    private val keywordBlockerRepository: KeywordBlockerRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val timeLimitRepository: TimeLimitRepositoryImpl,
    private val mindfulRepository: MindfulRepository,
    private val protectionRepository: ProtectionRepositoryImpl,
    private val distractionFeedRepository: DistractionFeedRepositoryImpl,
    private val overlaySettingsRepository: OverlaySettingsRepositoryImpl,
    private val challengeRepository: ChallengeRepositoryImpl,
    private val supportCodeRepository: SupportCodeRepositoryImpl,
    private val timeLimitEvaluator: TimeLimitEvaluator,
    private val scheduleEvaluator: ScheduleEvaluator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val cachedOnboardingComplete = AtomicBoolean(false)

    init {
        repoScope.launch {
            prefs.isOnboardingComplete.collect { cachedOnboardingComplete.set(it) }
        }
    }

    // ── Fast In-Memory Checks (< 0.01ms Hot Path for Accessibility) ──
    fun isFastOnboardingComplete(): Boolean = cachedOnboardingComplete.get()
    fun isFastBlockedApp(packageName: String): Boolean = appBlockerRepository.isFastBlockedApp(packageName)
    fun getFastBlockedPackages(): Set<String> = appBlockerRepository.getFastBlockedPackages()
    fun getFastBlockedAppsCount(): Int = appBlockerRepository.getFastBlockedAppsCount()
    fun isFastAppBlocked(packageName: String): Boolean = appBlockerRepository.isFastAppBlocked(packageName)
    fun isFastAutoBlockNewAppsEnabled(): Boolean = appBlockerRepository.isFastAutoBlockNewAppsEnabled()
    fun getFastAutoBlockEnabledTimestamp(): Long = appBlockerRepository.getFastAutoBlockEnabledTimestamp()
    fun isFastAppReviewed(packageName: String): Boolean = appBlockerRepository.isFastAppReviewed(packageName)
    fun getFastBlockedKeywords(): Set<String> = keywordBlockerRepository.getFastBlockedKeywordsSet()
    fun getFastBlockedKeywordsEntities(): List<BlockedKeywordEntity> = keywordBlockerRepository.getFastBlockedKeywordsEntities()
    fun getFastActiveSchedules(): List<ScheduleEntity> = scheduleRepository.getFastActiveSchedules()
    fun getFastTimeLimits(): Map<String, AppTimeLimitEntity> = timeLimitRepository.getFastTimeLimits()
    fun hasFastTimeLimit(packageName: String): Boolean = timeLimitRepository.hasFastTimeLimit(packageName)
    fun isFastTimeLimitExceeded(packageName: String): Boolean = timeLimitRepository.isFastTimeLimitExceeded(packageName)
    fun markTimeLimitExceeded(packageName: String) = timeLimitRepository.markTimeLimitExceeded(packageName)
    fun clearTimeLimitExceeded(packageName: String) = timeLimitRepository.clearTimeLimitExceeded(packageName)
    fun refreshExceededTimeLimits() = timeLimitRepository.refreshExceededTimeLimits()
    fun isFastUninstallProtectionEnabled(): Boolean = protectionRepository.isFastUninstallProtectionEnabled()
    fun refreshUninstallProtectionNow() = protectionRepository.refreshUninstallProtectionNow()
    fun isFastTimedLockActive(): Boolean = protectionRepository.isFastTimedLockActive()
    fun getFastTimedLockRemainingDays(): Long = protectionRepository.getFastTimedLockRemainingDays()
    fun isFastTimedLockDurationElapsed(): Boolean = protectionRepository.isFastTimedLockDurationElapsed()
    fun fastTimedLockRemainingUptimeMs(): Long = protectionRepository.fastTimedLockRemainingUptimeMs()
    fun isFastShortsReelsBlockEnabled(): Boolean = distractionFeedRepository.isFastShortsReelsBlockEnabled()
    fun isFastInstagramReelsBlockEnabled(): Boolean = distractionFeedRepository.isFastInstagramReelsBlockEnabled()
    fun isFastFacebookReelsBlockEnabled(): Boolean = distractionFeedRepository.isFastFacebookReelsBlockEnabled()
    fun isFastYouTubeShortsBlockEnabled(): Boolean = distractionFeedRepository.isFastYouTubeShortsBlockEnabled()
    fun isFastIncognitoBlockEnabled(): Boolean = distractionFeedRepository.isFastIncognitoBlockEnabled()
    fun isFastBlockUnsupportedBrowsersEnabled(): Boolean = distractionFeedRepository.isFastBlockUnsupportedBrowsersEnabled()
    fun isFastAdultContentBlockEnabled(): Boolean = distractionFeedRepository.isFastAdultContentBlockEnabled()
    fun isFastAntiCloningShieldEnabled(): Boolean = distractionFeedRepository.isFastAntiCloningShieldEnabled()
    fun getFastOverlayPresetId(): Int = overlaySettingsRepository.getFastOverlayPresetId()
    fun getFastOverlayDimAlpha(): Float = overlaySettingsRepository.getFastOverlayDimAlpha()
    fun getFastOverlayWallpaperPath(): String = overlaySettingsRepository.getFastOverlayWallpaperPath()
    fun getFastCustomBlockMessage(): String = overlaySettingsRepository.getFastCustomBlockMessage()
    fun getFastBlockCountdownSeconds(): Int = overlaySettingsRepository.getFastBlockCountdownSeconds()

    // ── Flows ──
    val blockedApps: Flow<List<BlockedAppEntity>> get() = appBlockerRepository.blockedApps
    val blockedAppsCount: Flow<Int> get() = appBlockerRepository.blockedAppsCount
    val isAutoBlockNewAppsEnabled: Flow<Boolean> get() = appBlockerRepository.isAutoBlockNewAppsEnabled
    val todayImpulsesBlockedCount: Flow<Int> get() = appBlockerRepository.todayImpulsesBlockedCount
    val blockedKeywords: Flow<List<BlockedKeywordEntity>> get() = keywordBlockerRepository.blockedKeywordsEntities
    val blockedKeywordsCount: Flow<Int> get() = keywordBlockerRepository.blockedKeywordsCount
    val blockedKeywordsSet: Flow<Set<String>> get() = keywordBlockerRepository.blockedKeywordsSet
    val allTimeLimits: Flow<List<AppTimeLimitEntity>> get() = timeLimitRepository.getAllTimeLimits()
    val activeTimeLimitsCount: Flow<Int> get() = timeLimitRepository.activeTimeLimitsCount
    val allSchedules: Flow<List<ScheduleEntity>> get() = scheduleRepository.getAllSchedules()
    val activeSchedulesCount: Flow<Int> get() = scheduleRepository.activeSchedulesCount
    val isUninstallProtectionEnabled: Flow<Boolean> get() = protectionRepository.isUninstallProtectionEnabled
    val uninstallProtectionExpiryTimestamp: Flow<Long> get() = protectionRepository.uninstallProtectionExpiryTimestamp
    val uninstallProtectionDays: Flow<Int> get() = protectionRepository.uninstallProtectionDays
    val isShortsReelsBlockEnabled: Flow<Boolean> get() = distractionFeedRepository.isShortsReelsBlockEnabled
    val isInstagramReelsBlockEnabled: Flow<Boolean> get() = distractionFeedRepository.isInstagramReelsBlockEnabled
    val isFacebookReelsBlockEnabled: Flow<Boolean> get() = distractionFeedRepository.isFacebookReelsBlockEnabled
    val isYouTubeShortsBlockEnabled: Flow<Boolean> get() = distractionFeedRepository.isYouTubeShortsBlockEnabled
    val isIncognitoBlockEnabled: Flow<Boolean> get() = distractionFeedRepository.isIncognitoBlockEnabled
    val isBlockUnsupportedBrowsersEnabled: Flow<Boolean> get() = distractionFeedRepository.isBlockUnsupportedBrowsersEnabled
    val isAdultContentBlockEnabled: Flow<Boolean> get() = distractionFeedRepository.isAdultContentBlockEnabled
    val isAntiCloningShieldEnabled: Flow<Boolean> get() = distractionFeedRepository.isAntiCloningShieldEnabled
    val overlayWallpaperPath: Flow<String> get() = overlaySettingsRepository.overlayWallpaperPath
    val overlayPresetId: Flow<Int> get() = overlaySettingsRepository.overlayPresetId
    val overlayDimAlpha: Flow<Float> get() = overlaySettingsRepository.overlayDimAlpha
    val customBlockMessage: Flow<String> get() = overlaySettingsRepository.customBlockMessage
    val blockCountdownSeconds: Flow<Int> get() = overlaySettingsRepository.blockCountdownSeconds
    val challengeType: Flow<ChallengeType> get() = challengeRepository.challengeType
    val commitmentSentence: Flow<String> get() = challengeRepository.commitmentSentence
    val timeDelayHours: Flow<Int> get() = challengeRepository.timeDelayHours
    val challengeSettings: Flow<ChallengeSettings> get() = challengeRepository.challengeSettings
    val isChallengeActive: Flow<Boolean> get() = challengeRepository.isChallengeActive
    val pendingChanges: Flow<List<PendingChangeEntity>> get() = challengeRepository.pendingChanges
    val securityEvents: Flow<List<SecurityEvent>> get() = challengeRepository.securityEvents
    val settingsSnapshot: Flow<FocusGuardPreferences.SettingsSnapshot> get() = prefs.settingsSnapshot
    val isOnboardingComplete: Flow<Boolean> get() = prefs.isOnboardingComplete
    val isOemGuidanceAcknowledged: Flow<Boolean> get() = prefs.oemGuidanceAcknowledged

    // ── App & Keyword Blocking Mutations ──
    suspend fun blockApp(
        packageName: String, appName: String,
        isAutoBlockedUnsupportedBrowser: Boolean = false, isAutoBlockedNewInstall: Boolean = false
    ) = appBlockerRepository.blockAppDirect(packageName, appName, isAutoBlockedUnsupportedBrowser, isAutoBlockedNewInstall)

    suspend fun unblockApp(packageName: String) = appBlockerRepository.unblockAppDirect(packageName)
    suspend fun unblockAllAutoBlockedNewApps() = appBlockerRepository.unblockAllAutoBlockedNewApps()
    suspend fun markAppReviewed(packageName: String) = appBlockerRepository.markAppReviewed(packageName)
    suspend fun clearAppReviewed(packageName: String) = appBlockerRepository.clearAppReviewed(packageName)
    suspend fun isAppBlocked(packageName: String): Boolean = appBlockerRepository.isAppBlocked(packageName)
    fun sanitizeKeyword(raw: String): String = keywordBlockerRepository.sanitizeKeyword(raw)
    suspend fun blockKeyword(keyword: String): Boolean = keywordBlockerRepository.blockKeywordDirect(keyword)
    suspend fun unblockKeyword(id: Long) = keywordBlockerRepository.unblockKeywordDirect(id)
    suspend fun unblockKeywordById(id: Long) = keywordBlockerRepository.unblockKeywordDirect(id)

    // ── Time Limits & Schedules Mutations ──
    suspend fun setTimeLimit(packageName: String, appName: String, dailyLimitMinutes: Int, isEnabled: Boolean = true) =
        timeLimitRepository.setTimeLimitDirect(packageName, appName, dailyLimitMinutes, isEnabled)
    suspend fun removeTimeLimit(packageName: String) = timeLimitRepository.removeTimeLimitDirect(packageName)
    suspend fun saveSchedule(schedule: ScheduleEntity): Long = scheduleRepository.saveScheduleDirect(schedule)
    suspend fun deleteSchedule(id: Long) = scheduleRepository.deleteScheduleDirect(id)
    suspend fun updateScheduleEnabled(id: Long, enabled: Boolean) = scheduleRepository.updateScheduleEnabledDirect(id, enabled)

    // ── Protection & Distraction Controls ──
    suspend fun startTimedUninstallProtection(days: Int) = protectionRepository.startTimedUninstallProtection(days)
    suspend fun setUninstallProtectionEnabled(enabled: Boolean, challengeAuthorized: Boolean = false) = protectionRepository.setUninstallProtectionEnabled(enabled, challengeAuthorized)
    suspend fun disableUninstallProtection() = protectionRepository.disableUninstallProtection()
    suspend fun setShortsReelsBlockEnabled(enabled: Boolean) = distractionFeedRepository.setShortsReelsBlockEnabled(enabled)
    suspend fun setInstagramReelsBlockEnabled(enabled: Boolean) = distractionFeedRepository.setInstagramReelsBlockEnabled(enabled)
    suspend fun setFacebookReelsBlockEnabled(enabled: Boolean) = distractionFeedRepository.setFacebookReelsBlockEnabled(enabled)
    suspend fun setYouTubeShortsBlockEnabled(enabled: Boolean) = distractionFeedRepository.setYouTubeShortsBlockEnabled(enabled)
    suspend fun setAutoBlockNewAppsEnabled(enabled: Boolean) = appBlockerRepository.setAutoBlockNewAppsEnabled(enabled)
    suspend fun isAutoBlockNewAppsEnabled(): Boolean = appBlockerRepository.isAutoBlockNewAppsEnabled()
    suspend fun setIncognitoBlockEnabled(enabled: Boolean) = distractionFeedRepository.setIncognitoBlockEnabled(enabled)
    suspend fun setBlockUnsupportedBrowsersEnabled(enabled: Boolean) = distractionFeedRepository.setBlockUnsupportedBrowsersEnabled(enabled)
    suspend fun setAdultContentBlockEnabled(enabled: Boolean) = distractionFeedRepository.setAdultContentBlockEnabled(enabled)
    suspend fun setAntiCloningShieldEnabled(enabled: Boolean) = distractionFeedRepository.setAntiCloningShieldEnabled(enabled)

    // ── Overlay & Challenge Mutations ──
    suspend fun setOverlayWallpaperPath(path: String) = overlaySettingsRepository.setOverlayWallpaperPath(path)
    suspend fun setOverlayPresetId(id: Int) = overlaySettingsRepository.setOverlayPresetId(id)
    suspend fun setOverlayDimAlpha(alpha: Float) = overlaySettingsRepository.setOverlayDimAlpha(alpha)
    suspend fun clearOverlayWallpaper() = overlaySettingsRepository.clearOverlayWallpaper()
    suspend fun setCustomBlockMessage(message: String) = overlaySettingsRepository.setCustomBlockMessage(message)
    suspend fun setBlockCountdownSeconds(seconds: Int) = overlaySettingsRepository.setBlockCountdownSeconds(seconds)
    suspend fun setChallengeType(type: ChallengeType) = challengeRepository.setChallengeType(type)
    suspend fun setCommitmentSentence(sentence: String) = challengeRepository.setCommitmentSentence(sentence)
    suspend fun setTimeDelayHours(hours: Int) = challengeRepository.setTimeDelayHours(hours)
    suspend fun setChallengeActive(active: Boolean) = challengeRepository.setChallengeActive(active)
    suspend fun createPendingChange(
        changeType: ChangeType, targetId: String, targetName: String = "",
        targetPayload: String? = null, scheduledApplyAt: Long? = null
    ): PendingChangeEntity = challengeRepository.createPendingChange(changeType, targetId, targetName, targetPayload, scheduledApplyAt)
    suspend fun applyPendingChange(change: PendingChangeEntity) = challengeRepository.applyPendingChange(change)
    suspend fun cancelPendingChange(id: Long) = challengeRepository.cancelPendingChange(id)
    suspend fun reconcilePendingChanges() = challengeRepository.reconcilePendingChanges()
    suspend fun logEvent(type: EventType, detail: String = "") = challengeRepository.logEvent(type, detail)

    // ── Support Code System ──
    fun getDeviceId(): String = supportCodeRepository.getDeviceId()
    fun getSupportCooldownRemainingSeconds(): Long = supportCodeRepository.getSupportCooldownRemainingSeconds()
    fun verifySupportCode(code: String): SupportVerificationResult = supportCodeRepository.verifySupportCode(code)
    suspend fun executeSupportAction(verified: SupportVerificationResult.Success): Boolean = supportCodeRepository.executeSupportAction(verified)

    // ── Onboarding & OEM ──
    suspend fun markOnboardingComplete() {
        prefs.setOnboardingComplete(true)
        cachedOnboardingComplete.set(true)
        logEvent(EventType.ONBOARDING_COMPLETE)
    }
    suspend fun setOemGuidanceAcknowledged(acknowledged: Boolean) = prefs.setOemGuidanceAcknowledged(acknowledged)

    // ── Master Access Evaluation & Usage ──
    fun hasActiveSchedulesOrLimits(packageName: String): Boolean {
        if (scheduleRepository.getFastActiveSchedules().isNotEmpty()) return true
        val limit = timeLimitRepository.getFastTimeLimits()[packageName.lowercase()]
        return limit != null && limit.isEnabled
    }

    suspend fun evaluateAccess(
        packageName: String,
        appLabel: String = packageName,
        liveSessionMs: Long = 0L
    ): AccessDecision {
        val isAppInBlocklist = appBlockerRepository.isFastBlockedApp(packageName)
        if (isAppInBlocklist) {
            Log.i(TAG, "evaluateAccess -> BLOCKED_BY_APP_BLOCKER: $packageName")
            return AccessDecision.BlockedByAppBlocker(appLabel = appLabel)
        }
        val scheduleBlock = scheduleEvaluator.isBlockedBySchedule(packageName, scheduleRepository.getFastActiveSchedules())
        if (scheduleBlock != null) {
            Log.i(TAG, "evaluateAccess -> BLOCKED_BY_SCHEDULE: $packageName by '${scheduleBlock.scheduleTitle}' until ${scheduleBlock.endTimeFormatted}")
            return AccessDecision.BlockedBySchedule(appLabel, scheduleBlock.scheduleTitle, scheduleBlock.endTimeFormatted)
        }
        val limitEntity = timeLimitRepository.getFastTimeLimits()[packageName.lowercase()]
        if (limitEntity != null && limitEntity.isEnabled) {
            val status = timeLimitEvaluator.evaluate(packageName, limitEntity, liveSessionMs)
            if (status is LimitStatus.Exceeded) {
                markTimeLimitExceeded(packageName)
                Log.i(TAG, "evaluateAccess -> BLOCKED_BY_TIME_LIMIT: $packageName (${status.usedMinutes}/${status.limitMinutes}m)")
                return AccessDecision.BlockedByTimeLimit(appLabel, status.usedMinutes, status.limitMinutes)
            }
        }
        return AccessDecision.Allowed
    }

    fun recordSessionUsage(packageName: String, elapsedMs: Long) {
        timeLimitEvaluator.recordSessionUsage(packageName, elapsedMs)
        val limit = timeLimitRepository.getFastTimeLimits()[packageName.lowercase()]
        if (limit != null && limit.isEnabled) {
            val usedMs = timeLimitEvaluator.getTodayUsageMillis(packageName)
            if (usedMs >= limit.dailyLimitMinutes * 60_000L) markTimeLimitExceeded(packageName)
        }
    }

    fun getTodayUsageMillis(packageName: String, liveSessionMs: Long = 0L): Long =
        timeLimitEvaluator.getTodayUsageMillis(packageName, liveSessionMs)

    // ── Synchronous Snapshots for UI ──
    fun getCurrentSettingsSnapshot(): com.focusguard.app.feature.settings.ui.SettingsUiState {
        val path = prefs.syncOverlayWallpaperPath
        return com.focusguard.app.feature.settings.ui.SettingsUiState(
            challengeType = prefs.syncChallengeType, commitmentSentence = prefs.syncCommitmentSentence,
            timeDelayHours = prefs.syncTimeDelayHours, isChallengeActive = prefs.syncIsChallengeActive,
            isUninstallProtectionEnabled = prefs.syncUninstallProtectionEnabled,
            uninstallProtectionExpiryTimestamp = prefs.syncUninstallProtectionExpiry,
            uninstallProtectionDays = prefs.syncUninstallProtectionDays,
            remainingProtectionDays = prefs.getTimedUninstallProtectionRemainingDays(),
            isShortsReelsBlockEnabled = prefs.syncShortsReelsBlockEnabled,
            isInstagramReelsBlockEnabled = prefs.syncInstagramReelsBlockEnabled,
            isFacebookReelsBlockEnabled = prefs.syncFacebookReelsBlockEnabled,
            isYouTubeShortsBlockEnabled = prefs.syncYouTubeShortsBlockEnabled,
            isAutoBlockNewAppsEnabled = prefs.syncAutoBlockNewApps,
            isIncognitoBlockEnabled = prefs.syncIncognitoBlockEnabled,
            activeChallenge = null, overlayWallpaperPath = path,
            overlayPresetId = prefs.syncOverlayPresetId, overlayDimAlpha = prefs.syncOverlayDimAlpha,
            hasCustomWallpaper = path.isNotEmpty(), customBlockMessage = prefs.syncCustomBlockMessage,
            blockCountdownSeconds = prefs.syncBlockCountdownSeconds
        )
    }

    fun getCurrentHomeSnapshot(permissions: ProtectionPermissionStatus = ProtectionPermissionStatus()): com.focusguard.app.feature.home.ui.HomeUiState =
        com.focusguard.app.feature.home.ui.HomeUiState(
            isAccessibilityEnabled = permissions.isAccessibilityGranted,
            isDeviceAdminActive = permissions.isDeviceAdminGranted,
            isUsageAccessGranted = permissions.isUsageAccessGranted,
            permissionStatus = permissions,
            blockedAppsCount = if (appBlockerRepository.getFastBlockedAppsCount() > 0) appBlockerRepository.getFastBlockedAppsCount() else prefs.syncBlockedAppsCount,
            blockedKeywordsCount = if (keywordBlockerRepository.getFastBlockedKeywordsSet().isNotEmpty()) keywordBlockerRepository.getFastBlockedKeywordsSet().size else prefs.syncBlockedKeywordsCount,
            activeTimeLimitsCount = if (timeLimitRepository.getFastTimeLimits().isNotEmpty()) timeLimitRepository.getFastTimeLimits().size else prefs.syncActiveTimeLimitsCount,
            activeSchedulesCount = if (scheduleRepository.getFastActiveSchedules().isNotEmpty()) scheduleRepository.getFastActiveSchedules().size else prefs.syncActiveSchedulesCount
        )

    companion object {
        fun isProtectionReallyActive(enabled: Boolean, days: Int, expiry: Long, now: Long): Boolean = ProtectionPolicy.isProtectionActive(enabled, days, expiry, now)
    }
}
