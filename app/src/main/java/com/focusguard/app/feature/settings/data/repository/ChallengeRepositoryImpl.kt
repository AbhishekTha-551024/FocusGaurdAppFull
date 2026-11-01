package com.focusguard.app.feature.settings.data.repository

import android.util.Log
import com.focusguard.app.core.database.dao.PendingChangeDao
import com.focusguard.app.core.database.dao.SecurityEventDao
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.EventType
import com.focusguard.app.core.database.entity.PendingChangeEntity
import com.focusguard.app.core.database.entity.PendingStatus
import com.focusguard.app.core.database.entity.SecurityEvent
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.data.repository.MindfulRepository
import com.focusguard.app.feature.appblocker.data.repository.AppBlockerRepositoryImpl
import com.focusguard.app.feature.keywordblocker.data.repository.KeywordBlockerRepositoryImpl
import com.focusguard.app.feature.schedules.data.repository.ScheduleRepositoryImpl
import com.focusguard.app.feature.timelimits.data.repository.TimeLimitRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChallengeRepo"

data class ChallengeSettings(
    val type: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val isChallengeActive: Boolean = false
)

@Singleton
class ChallengeRepositoryImpl @Inject constructor(
    private val pendingChangeDao: PendingChangeDao,
    private val eventDao: SecurityEventDao,
    private val prefs: FocusGuardPreferences,
    private val appBlockerRepository: AppBlockerRepositoryImpl,
    private val keywordBlockerRepository: KeywordBlockerRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val timeLimitRepository: TimeLimitRepositoryImpl,
    private val mindfulRepository: MindfulRepository,
    private val protectionRepository: ProtectionRepositoryImpl,
    private val distractionFeedRepository: DistractionFeedRepositoryImpl,
    private val overlaySettingsRepository: OverlaySettingsRepositoryImpl,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        // Startup reconciliation and periodic check for past-due changes
        repoScope.launch {
            while (isActive) {
                try {
                    reconcilePendingChanges()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in pending changes auto-reconciliation: ${e.message}")
                }
                delay(5_000L)
            }
        }

        // Cleanup startup state
        repoScope.launch {
            pendingChangeDao.deleteApplied()
            if (prefs.syncChallengeType != ChallengeType.TYPING) {
                prefs.setChallengeType(ChallengeType.TYPING)
            }
        }

        // Eagerly sync challenge preferences to syncPrefs RAM cache
        repoScope.launch { prefs.challengeType.collect { } }
        repoScope.launch { prefs.commitmentSentence.collect { } }
        repoScope.launch { prefs.timeDelayHours.collect { } }
        repoScope.launch { prefs.isChallengeActive.collect { } }
    }

    // ── Flows ──
    val challengeType: Flow<ChallengeType> = prefs.challengeType
    val commitmentSentence: Flow<String> = prefs.commitmentSentence
    val timeDelayHours: Flow<Int> = prefs.timeDelayHours
    val isChallengeActive: Flow<Boolean> = prefs.isChallengeActive
    val securityEvents: Flow<List<SecurityEvent>> = eventDao.observeAll()

    val challengeSettings: Flow<ChallengeSettings> = combine(
        prefs.challengeType,
        prefs.commitmentSentence,
        prefs.timeDelayHours,
        prefs.isChallengeActive
    ) { type, sentence, hours, active ->
        ChallengeSettings(type, sentence, hours, active)
    }

    val pendingChanges: Flow<List<PendingChangeEntity>> = pendingChangeDao.watchPendingChanges()
        .transform { list ->
            val now = System.currentTimeMillis()
            val due = list.filter { it.scheduledApplyAt != null && it.scheduledApplyAt <= now }
            if (due.isNotEmpty()) {
                repoScope.launch {
                    reconcilePendingChanges()
                }
            }
            emit(list.filter { it.scheduledApplyAt == null || it.scheduledApplyAt > now })
        }

    // ── Challenge Preferences ──
    suspend fun setChallengeType(type: ChallengeType) = prefs.setChallengeType(type)
    suspend fun setCommitmentSentence(sentence: String) = prefs.setCommitmentSentence(sentence)
    suspend fun setTimeDelayHours(hours: Int) = prefs.setTimeDelayHours(hours)
    suspend fun setChallengeActive(active: Boolean) = prefs.setChallengeActive(active)

    // ── Pending Changes Operations ──
    suspend fun createPendingChange(
        changeType: ChangeType,
        targetId: String,
        targetName: String = "",
        targetPayload: String? = null,
        scheduledApplyAt: Long? = null
    ): PendingChangeEntity = withContext(ioDispatcher) {
        val entity = PendingChangeEntity(
            changeType = changeType,
            targetId = targetId,
            targetName = targetName,
            targetPayload = targetPayload,
            requestedAt = System.currentTimeMillis(),
            scheduledApplyAt = scheduledApplyAt,
            status = PendingStatus.PENDING
        )
        val id = pendingChangeDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun applyPendingChange(pendingChange: PendingChangeEntity) = withContext(ioDispatcher) {
        try {
            when (pendingChange.changeType) {
                ChangeType.UNKNOWN -> {
                    Log.w(TAG, "Unknown or corrupted pending change discarded (id=${pendingChange.id})")
                    pendingChangeDao.deleteById(pendingChange.id)
                    return@withContext
                }
                ChangeType.UNBLOCK_APP -> {
                    appBlockerRepository.unblockAppDirect(pendingChange.targetId)
                }
                ChangeType.DELETE_KEYWORD -> {
                    val id = pendingChange.targetId.toLongOrNull()
                    if (id != null) keywordBlockerRepository.unblockKeywordDirect(id)
                }
                ChangeType.DISABLE_SCHEDULE -> {
                    val id = pendingChange.targetId.toLongOrNull()
                    if (id != null) scheduleRepository.updateScheduleEnabledDirect(id, false)
                }
                ChangeType.DELETE_SCHEDULE -> {
                    val id = pendingChange.targetId.toLongOrNull()
                    if (id != null) scheduleRepository.deleteScheduleDirect(id)
                }
                ChangeType.DISABLE_LIMIT -> {
                    timeLimitRepository.removeTimeLimitDirect(pendingChange.targetId)
                }
                ChangeType.LOWER_LIMIT -> {
                    val limit = (pendingChange.targetPayload?.toIntOrNull() ?: 60).coerceIn(1, 1440)
                    timeLimitRepository.setTimeLimitDirect(pendingChange.targetId, pendingChange.targetName, limit, true)
                }
                ChangeType.CHANGE_CHALLENGE_TYPE -> {
                    val newType = pendingChange.targetPayload?.let { runCatching { ChallengeType.valueOf(it) }.getOrDefault(ChallengeType.TYPING) } ?: ChallengeType.TYPING
                    setChallengeType(newType)
                }
                ChangeType.UNINSTALL_REQUEST -> {
                    protectionRepository.setUninstallProtectionEnabled(false)
                    logEvent(EventType.UNINSTALL_ATTEMPT, "Uninstall protection disabled via challenge approval")
                }
                ChangeType.DISABLE_DISTRACTION_FEED -> {
                    when (pendingChange.targetId) {
                        "instagram" -> distractionFeedRepository.setInstagramReelsBlockEnabled(false)
                        "facebook" -> distractionFeedRepository.setFacebookReelsBlockEnabled(false)
                        "youtube" -> distractionFeedRepository.setYouTubeShortsBlockEnabled(false)
                        "auto_block" -> appBlockerRepository.setAutoBlockNewAppsEnabled(false)
                        "unsupported_browsers" -> distractionFeedRepository.setBlockUnsupportedBrowsersEnabled(false)
                        "adult_content" -> distractionFeedRepository.setAdultContentBlockEnabled(false)
                        "incognito" -> distractionFeedRepository.setIncognitoBlockEnabled(false)
                        "shorts_reels" -> distractionFeedRepository.setShortsReelsBlockEnabled(false)
                        else -> {
                            distractionFeedRepository.setInstagramReelsBlockEnabled(false)
                            distractionFeedRepository.setFacebookReelsBlockEnabled(false)
                            distractionFeedRepository.setYouTubeShortsBlockEnabled(false)
                        }
                    }
                    logEvent(EventType.APP_UNBLOCKED, "Distraction feed control disabled via challenge approval: ${pendingChange.targetId}")
                }
                ChangeType.LOWER_COUNTDOWN -> {
                    val newSeconds = (pendingChange.targetPayload?.toIntOrNull() ?: 5).coerceIn(1, 60)
                    overlaySettingsRepository.setBlockCountdownSeconds(newSeconds)
                    logEvent(EventType.APP_UNBLOCKED, "Block countdown lowered to ${newSeconds}s via challenge approval")
                }
                ChangeType.LOGOUT_REQUEST -> {
                    logEvent(EventType.UNINSTALL_ATTEMPT, "Logout request approved via challenge/partner")
                }
                ChangeType.ENABLE_UNINSTALL_PROTECTION -> {
                    /* handled immediately via commitment challenge and duration dialog in SettingsViewModel */
                }
                ChangeType.REMOVE_MINDFUL_APP -> {
                    mindfulRepository.removeMindfulApp(pendingChange.targetId)
                    logEvent(EventType.APP_UNBLOCKED, "Mindful app removed via challenge approval: ${pendingChange.targetName}")
                }
                ChangeType.INCREASE_MINDFUL_ALLOWANCE -> {
                    val minutes = (pendingChange.targetPayload?.toIntOrNull() ?: 5).coerceIn(1, 120)
                    mindfulRepository.updateAllowanceMinutes(pendingChange.targetId, minutes)
                }
            }

            pendingChangeDao.updateStatus(pendingChange.id, PendingStatus.APPLIED)
            pendingChangeDao.deleteById(pendingChange.id)
            logEvent(EventType.APP_UNBLOCKED, "Pending change applied: ${pendingChange.changeType} on ${pendingChange.targetName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply pending change: ${e.message}", e)
        }
    }

    suspend fun cancelPendingChange(id: Long) = withContext(ioDispatcher) {
        pendingChangeDao.deleteById(id)
        logEvent(EventType.APP_BLOCKED, "Pending change cancelled (id=$id)")
    }

    suspend fun reconcilePendingChanges() = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val dueList = pendingChangeDao.getDuePendingChanges(now)
        Log.i(TAG, "Reconciling pending changes: found ${dueList.size} due changes")
        for (change in dueList) {
            applyPendingChange(change)
        }
    }

    suspend fun logEvent(type: EventType, detail: String = "") = withContext(ioDispatcher) {
        try {
            eventDao.insert(SecurityEvent(type = type, detail = detail))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log security event: ${e.message}")
        }
    }
}

