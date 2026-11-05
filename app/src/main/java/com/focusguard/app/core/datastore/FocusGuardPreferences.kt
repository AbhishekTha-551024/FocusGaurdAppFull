package com.focusguard.app.core.datastore

import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.domain.model.CommitmentLockState
import com.focusguard.app.domain.repository.LocalCommitmentStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade over the split preference files. It also *is* the [LocalCommitmentStore] the restore engine
 * talks to, so that port needs no second implementation and no delegating helper of its own.
 */
@Singleton
class FocusGuardPreferences @Inject constructor(
    private val core: PreferencesCore,
    private val protection: ProtectionPreferences,
    private val feed: FeedPreferences,
    private val overlay: OverlayPreferences,
    private val onboarding: OnboardingPreferences,
    private val session: SessionPreferences
) : LocalCommitmentStore {
    data class SettingsSnapshot(
        val challengeType: ChallengeType = ChallengeType.TYPING,
        val commitmentSentence: String = DEFAULT_COMMITMENT_SENTENCE,
        val timeDelayHours: Int = 24,
        val isChallengeActive: Boolean = false,
        val isUninstallProtectionEnabled: Boolean = false,
        val uninstallProtectionExpiryTimestamp: Long = 0L,
        val uninstallProtectionDays: Int = 0,
        val isShortsReelsBlockEnabled: Boolean = false,
        val isInstagramReelsBlockEnabled: Boolean = false,
        val isFacebookReelsBlockEnabled: Boolean = false,
        val isYouTubeShortsBlockEnabled: Boolean = false,
        val isAutoBlockNewAppsEnabled: Boolean = false,
        val isIncognitoBlockEnabled: Boolean = false,
        val isBlockUnsupportedBrowsersEnabled: Boolean = false,
        val isAdultContentBlockEnabled: Boolean = false,
        val isAntiCloningShieldEnabled: Boolean = true,
        val overlayWallpaperPath: String = "",
        val overlayPresetId: Int = 0,
        val overlayDimAlpha: Float = 0.55f,
        val customBlockMessage: String = "",
        val blockCountdownSeconds: Int = 5
    )

    // ── Flows ──
    val isOnboardingComplete: Flow<Boolean> get() = onboarding.isOnboardingComplete
    val challengeType: Flow<ChallengeType> get() = protection.challengeType
    val commitmentSentence: Flow<String> get() = protection.commitmentSentence
    val timeDelayHours: Flow<Int> get() = protection.timeDelayHours
    val isChallengeActive: Flow<Boolean> get() = protection.isChallengeActive
    val overlayWallpaperPath: Flow<String> get() = overlay.overlayWallpaperPath
    val overlayPresetId: Flow<Int> get() = overlay.overlayPresetId
    val overlayDimAlpha: Flow<Float> get() = overlay.overlayDimAlpha
    val isUninstallProtectionEnabled: Flow<Boolean> get() = protection.isUninstallProtectionEnabled
    val uninstallProtectionExpiryTimestamp: Flow<Long> get() = protection.uninstallProtectionExpiryTimestamp
    val uninstallProtectionDays: Flow<Int> get() = protection.uninstallProtectionDays
    override fun timedLockState(deviceId: String): Flow<CommitmentLockState> =
        protection.timedLockState(deviceId)
    val isShortsReelsBlockEnabled: Flow<Boolean> get() = feed.isShortsReelsBlockEnabled
    val isInstagramReelsBlockEnabled: Flow<Boolean> get() = feed.isInstagramReelsBlockEnabled
    val isFacebookReelsBlockEnabled: Flow<Boolean> get() = feed.isFacebookReelsBlockEnabled
    val isYouTubeShortsBlockEnabled: Flow<Boolean> get() = feed.isYouTubeShortsBlockEnabled
    val isAutoBlockNewAppsEnabled: Flow<Boolean> get() = feed.isAutoBlockNewAppsEnabled
    val autoBlockEnabledTimestamp: Flow<Long> get() = feed.autoBlockEnabledTimestamp
    val reviewedPackages: Flow<Set<String>> get() = feed.reviewedPackages
    val customBlockMessage: Flow<String> get() = overlay.customBlockMessage
    val blockCountdownSeconds: Flow<Int> get() = overlay.blockCountdownSeconds
    val isIncognitoBlockEnabled: Flow<Boolean> get() = feed.isIncognitoBlockEnabled
    val isBlockUnsupportedBrowsersEnabled: Flow<Boolean> get() = feed.isBlockUnsupportedBrowsersEnabled
    val isAdultContentBlockEnabled: Flow<Boolean> get() = feed.isAdultContentBlockEnabled
    val isAntiCloningShieldEnabled: Flow<Boolean> get() = protection.isAntiCloningShieldEnabled
    val currentAuthUser: Flow<AuthUser?> get() = onboarding.currentAuthUser
    val oemGuidanceAcknowledged: Flow<Boolean> get() = onboarding.oemGuidanceAcknowledged
    val settingsSnapshot: Flow<SettingsSnapshot> get() = core.settingsSnapshot

    // ── Synchronous 0ms RAM Snapshot Getters ──
    val syncShortsReelsBlockEnabled: Boolean get() = feed.syncShortsReelsBlockEnabled
    val syncInstagramReelsBlockEnabled: Boolean get() = feed.syncInstagramReelsBlockEnabled
    val syncFacebookReelsBlockEnabled: Boolean get() = feed.syncFacebookReelsBlockEnabled
    val syncYouTubeShortsBlockEnabled: Boolean get() = feed.syncYouTubeShortsBlockEnabled
    val syncAutoBlockNewApps: Boolean get() = feed.syncAutoBlockNewApps
    val syncIncognitoBlockEnabled: Boolean get() = feed.syncIncognitoBlockEnabled
    val syncBlockUnsupportedBrowsersEnabled: Boolean get() = feed.syncBlockUnsupportedBrowsersEnabled
    val syncAdultContentBlockEnabled: Boolean get() = feed.syncAdultContentBlockEnabled
    val syncAntiCloningShieldEnabled: Boolean get() = protection.syncAntiCloningShieldEnabled
    val syncIsLoggedIn: Boolean get() = onboarding.syncIsLoggedIn
    val syncAuthUser: AuthUser? get() = onboarding.syncAuthUser
    val syncReviewedPackages: Set<String> get() = feed.syncReviewedPackages
    val syncOemGuidanceAcknowledged: Boolean get() = onboarding.syncOemGuidanceAcknowledged
    val isOnboardingCompleteSync: Boolean get() = onboarding.isOnboardingCompleteSync
    val syncUninstallProtectionEnabled: Boolean get() = protection.syncUninstallProtectionEnabled
    val syncUninstallProtectionExpiry: Long get() = protection.syncUninstallProtectionExpiry
    val syncUninstallProtectionDays: Int get() = protection.syncUninstallProtectionDays
    val syncSessionLockActive: Boolean get() = session.syncSessionLockActive
    val syncSessionEndTimeMs: Long get() = session.syncSessionEndTimeMs
    val syncSessionTotalMinutes: Int get() = session.syncSessionTotalMinutes
    val syncSuitcasePackages: Set<String> get() = session.syncSuitcasePackages
    val syncEmergencyParachutesCount: Int get() = session.syncEmergencyParachutesCount
    val syncTotalFocusSessionsCompleted: Int get() = session.syncTotalFocusSessionsCompleted
    val syncTotalFocusMinutesCompleted: Long get() = session.syncTotalFocusMinutesCompleted
    val syncChallengeType: ChallengeType get() = protection.syncChallengeType
    val syncCommitmentSentence: String get() = protection.syncCommitmentSentence
    val syncTimeDelayHours: Int get() = protection.syncTimeDelayHours
    val syncIsChallengeActive: Boolean get() = protection.syncIsChallengeActive
    val syncCustomBlockMessage: String get() = overlay.syncCustomBlockMessage
    val syncBlockCountdownSeconds: Int get() = overlay.syncBlockCountdownSeconds
    val syncOverlayPresetId: Int get() = overlay.syncOverlayPresetId
    val syncOverlayDimAlpha: Float get() = overlay.syncOverlayDimAlpha
    val syncOverlayWallpaperPath: String get() = overlay.syncOverlayWallpaperPath
    val syncBlockedAppsCount: Int get() = onboarding.syncBlockedAppsCount
    val syncBlockedKeywordsCount: Int get() = onboarding.syncBlockedKeywordsCount
    val syncActiveTimeLimitsCount: Int get() = onboarding.syncActiveTimeLimitsCount
    val syncActiveSchedulesCount: Int get() = onboarding.syncActiveSchedulesCount

    // ── Synchronous Methods ──
    fun isTimedUninstallProtectionActive(): Boolean = protection.isTimedUninstallProtectionActive()
    fun getTimedUninstallProtectionRemainingDays(): Long = protection.getTimedUninstallProtectionRemainingDays()
    override fun localCommitmentState(deviceId: String): CommitmentLockState = protection.localCommitmentState(deviceId)
    fun updateSyncCounts(apps: Int? = null, keywords: Int? = null, limits: Int? = null, schedules: Int? = null) = onboarding.updateSyncCounts(apps, keywords, limits, schedules)
    fun getTodayDateString(): String = core.getTodayDateString()
    fun getOrCreateDeviceId(): String = onboarding.getOrCreateDeviceId()
    fun getSupportCooldownRemainingSeconds(): Long = onboarding.getSupportCooldownRemainingSeconds()
    fun recordSupportCodeWrongAttempt(): Long = onboarding.recordSupportCodeWrongAttempt()
    fun resetSupportCodeWrongAttempts() = onboarding.resetSupportCodeWrongAttempts()
    fun supportCodeLedger(): Set<String> = onboarding.supportCodeLedger()
    fun isSupportCodeConsumed(matchedDate: String, codeFingerprint: String): Boolean = onboarding.isSupportCodeConsumed(matchedDate, codeFingerprint)
    fun markSupportCodeConsumed(matchedDate: String, codeFingerprint: String) = onboarding.markSupportCodeConsumed(matchedDate, codeFingerprint)
    fun clearSupportCodeLedger() = onboarding.clearSupportCodeLedger()
    fun replenishDailyParachuteIfNeeded() = session.replenishDailyParachuteIfNeeded()

    // ── Suspend Methods ──
    suspend fun setOnboardingComplete(complete: Boolean) = onboarding.setOnboardingComplete(complete)
    suspend fun setChallengeType(type: ChallengeType) = protection.setChallengeType(type)
    suspend fun setCommitmentSentence(sentence: String) = protection.setCommitmentSentence(sentence)
    suspend fun setTimeDelayHours(hours: Int) = protection.setTimeDelayHours(hours)
    suspend fun setChallengeActive(active: Boolean) = protection.setChallengeActive(active)
    suspend fun setOverlayWallpaperPath(path: String) = overlay.setOverlayWallpaperPath(path)
    suspend fun setOverlayPresetId(id: Int) = overlay.setOverlayPresetId(id)
    suspend fun setOverlayDimAlpha(alpha: Float) = overlay.setOverlayDimAlpha(alpha)
    suspend fun clearOverlayWallpaper() = overlay.clearOverlayWallpaper()
    suspend fun startTimedUninstallProtection(days: Int) = protection.startTimedUninstallProtection(days)
    override suspend fun reArmRestoredTimedProtection(days: Int, expiryWallMs: Long, repinUptimeAnchor: Boolean) =
        protection.reArmRestoredTimedProtection(days, expiryWallMs, repinUptimeAnchor)
    suspend fun setUninstallProtectionEnabled(enabled: Boolean) = protection.setUninstallProtectionEnabled(enabled)
    suspend fun disableUninstallProtection() = protection.disableUninstallProtection()
    suspend fun setShortsReelsBlockEnabled(enabled: Boolean) = feed.setShortsReelsBlockEnabled(enabled)
    suspend fun setInstagramReelsBlockEnabled(enabled: Boolean) = feed.setInstagramReelsBlockEnabled(enabled)
    suspend fun setFacebookReelsBlockEnabled(enabled: Boolean) = feed.setFacebookReelsBlockEnabled(enabled)
    suspend fun setYouTubeShortsBlockEnabled(enabled: Boolean) = feed.setYouTubeShortsBlockEnabled(enabled)
    suspend fun setAutoBlockNewApps(enabled: Boolean) = feed.setAutoBlockNewApps(enabled)
    suspend fun markAppReviewed(packageName: String) = feed.markAppReviewed(packageName)
    suspend fun clearAppReviewed(packageName: String) = feed.clearAppReviewed(packageName)
    suspend fun setCustomBlockMessage(message: String) = overlay.setCustomBlockMessage(message)
    suspend fun setBlockCountdownSeconds(seconds: Int) = overlay.setBlockCountdownSeconds(seconds)
    suspend fun setIncognitoBlockEnabled(enabled: Boolean) = feed.setIncognitoBlockEnabled(enabled)
    suspend fun setBlockUnsupportedBrowsersEnabled(enabled: Boolean) = feed.setBlockUnsupportedBrowsersEnabled(enabled)
    suspend fun setAdultContentBlockEnabled(enabled: Boolean) = feed.setAdultContentBlockEnabled(enabled)
    suspend fun setAntiCloningShieldEnabled(enabled: Boolean) = protection.setAntiCloningShieldEnabled(enabled)
    suspend fun saveAuthUser(user: AuthUser) = onboarding.saveAuthUser(user)
    suspend fun clearAuthUser() = onboarding.clearAuthUser()
    suspend fun setOemGuidanceAcknowledged(acknowledged: Boolean) = onboarding.setOemGuidanceAcknowledged(acknowledged)
    suspend fun startSessionLock(durationMinutes: Int, suitcasePackages: Set<String>) = session.startSessionLock(durationMinutes, suitcasePackages)
    suspend fun stopSessionLock() = session.stopSessionLock()
    suspend fun consumeEmergencyParachute(): Boolean = session.consumeEmergencyParachute()
    suspend fun saveSuitcasePackages(packages: Set<String>) = session.saveSuitcasePackages(packages)
    suspend fun recordCompletedSession(durationMinutes: Int) = session.recordCompletedSession(durationMinutes)
}
