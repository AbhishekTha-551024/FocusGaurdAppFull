package com.focusguard.app.feature.settings.data.repository

import android.content.Context
import android.util.Log
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.core.database.dao.PendingChangeDao
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.di.IoDispatcher
import com.focusguard.app.feature.appblocker.data.repository.AppBlockerRepositoryImpl
import com.focusguard.app.feature.keywordblocker.data.repository.KeywordBlockerRepositoryImpl
import com.focusguard.app.feature.schedules.data.repository.ScheduleRepositoryImpl
import com.focusguard.app.feature.timelimits.data.repository.TimeLimitRepositoryImpl
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.security.SupportCodeEngine
import com.focusguard.app.security.SupportVerificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SupportCodeRepo"

@Singleton
class SupportCodeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val supportCodeEngine: SupportCodeEngine,
    private val appBlockerRepository: AppBlockerRepositoryImpl,
    private val keywordBlockerRepository: KeywordBlockerRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val timeLimitRepository: TimeLimitRepositoryImpl,
    private val protectionRepository: ProtectionRepositoryImpl,
    private val distractionFeedRepository: DistractionFeedRepositoryImpl,
    private val challengeRepository: ChallengeRepositoryImpl,
    private val pendingChangeDao: PendingChangeDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun getDeviceId(): String = prefs.getOrCreateDeviceId()

    fun getSupportCooldownRemainingSeconds(): Long = prefs.getSupportCooldownRemainingSeconds()

    fun verifySupportCode(code: String): SupportVerificationResult {
        val cooldownSecs = prefs.getSupportCooldownRemainingSeconds()
        if (cooldownSecs > 0) {
            val remainingMins = (cooldownSecs / 60) + 1
            return SupportVerificationResult.RateLimited(remainingMins)
        }

        val result = runCatching {
            supportCodeEngine.verifySupportCode(
                inputCode = code,
                deviceId = getDeviceId(),
                activeBlockedPackages = appBlockerRepository.getFastBlockedPackages(),
                activeBlockedKeywords = keywordBlockerRepository.getFastBlockedKeywordsSet(),
                activeLimitPackages = timeLimitRepository.getFastTimeLimits().keys
            )
        }.getOrElse { failure ->
            Log.e(TAG, "Support code verification aborted by ${failure.javaClass.simpleName}: ${failure.message}")
            return SupportVerificationResult.InvalidCode
        }

        if (result is SupportVerificationResult.Success &&
            prefs.isSupportCodeConsumed(result.matchedDate, result.codeFingerprint)
        ) {
            Log.i(TAG, "Support code replay rejected for action ${result.actionCode} (already applied)")
            return SupportVerificationResult.AlreadyUsed
        }

        when (result) {
            is SupportVerificationResult.Success -> prefs.resetSupportCodeWrongAttempts()
            is SupportVerificationResult.InvalidCode -> {
                val cooldownEnd = prefs.recordSupportCodeWrongAttempt()
                if (cooldownEnd > 0L) {
                    val remainingMins = ((cooldownEnd - System.currentTimeMillis()) / 60_000L) + 1
                    return SupportVerificationResult.RateLimited(remainingMins.coerceAtLeast(1L))
                }
            }
            is SupportVerificationResult.AlreadyUsed -> Unit
            is SupportVerificationResult.RateLimited -> Unit
        }

        return result
    }

    suspend fun executeSupportAction(verified: SupportVerificationResult.Success): Boolean = withContext(ioDispatcher) {
        val actionCode = verified.actionCode
        val targetPayload = verified.targetPayload
        Log.i(TAG, "Executing support action: Action $actionCode, payload: $targetPayload")

        val applied = runCatching { applySupportAction(actionCode, targetPayload) }
            .getOrElse { failure ->
                Log.e(TAG, "Support action $actionCode failed: ${failure.javaClass.simpleName} - ${failure.message}")
                false
            }

        if (applied) {
            prefs.markSupportCodeConsumed(verified.matchedDate, verified.codeFingerprint)
        }
        applied
    }

    private suspend fun applySupportAction(actionCode: String, targetPayload: String): Boolean =
        when (actionCode) {
            SupportCodeEngine.ACTION_DISABLE_UNINSTALL -> {
                protectionRepository.setUninstallProtectionEnabled(false)
                val adminRemoved = FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
                Log.i(TAG, "ACTION_DISABLE_UNINSTALL: Device Admin deactivated=$adminRemoved")
                true
            }
            SupportCodeEngine.ACTION_RESET_CHALLENGE -> {
                challengeRepository.setChallengeActive(false)
                pendingChangeDao.deleteAllPending()
                true
            }
            SupportCodeEngine.ACTION_RESET_TIME_TAMPER -> {
                TimeTamperGuard.resetTamperFlag()
                Log.i(TAG, "TimeTamperGuard flag reset via support code.")
                true
            }
            SupportCodeEngine.ACTION_UNBLOCK_APP -> {
                if (targetPayload.isNotBlank()) {
                    appBlockerRepository.unblockAppDirect(targetPayload)
                    true
                } else false
            }
            SupportCodeEngine.ACTION_DELETE_KEYWORD -> {
                if (targetPayload.isNotBlank()) {
                    keywordBlockerRepository.deleteByKeyword(targetPayload)
                    true
                } else false
            }
            SupportCodeEngine.ACTION_DISABLE_FEED -> when (targetPayload) {
                "instagram" -> {
                    distractionFeedRepository.setInstagramReelsBlockEnabled(false)
                    true
                }
                "youtube" -> {
                    distractionFeedRepository.setYouTubeShortsBlockEnabled(false)
                    true
                }
                "facebook" -> {
                    distractionFeedRepository.setFacebookReelsBlockEnabled(false)
                    true
                }
                "all" -> {
                    distractionFeedRepository.setInstagramReelsBlockEnabled(false)
                    distractionFeedRepository.setYouTubeShortsBlockEnabled(false)
                    distractionFeedRepository.setFacebookReelsBlockEnabled(false)
                    distractionFeedRepository.setShortsReelsBlockEnabled(false)
                    true
                }
                else -> false
            }
            SupportCodeEngine.ACTION_RESET_LIMIT -> {
                val limit = timeLimitRepository.getFastTimeLimits()[targetPayload.lowercase()]
                if (limit != null) {
                    timeLimitRepository.setTimeLimitDirect(limit.packageName, limit.appName, limit.dailyLimitMinutes, false)
                    true
                } else {
                    Log.w(TAG, "ACTION_RESET_LIMIT: no stored limit for '$targetPayload' — nothing applied")
                    false
                }
            }
            SupportCodeEngine.ACTION_FULL_RESET -> {
                protectionRepository.setUninstallProtectionEnabled(false)
                val adminRemoved = FocusGuardDeviceAdminReceiver.deactivateDeviceAdmin(context)
                Log.i(TAG, "ACTION_FULL_RESET: Device Admin deactivated=$adminRemoved")
                distractionFeedRepository.setShortsReelsBlockEnabled(false)
                distractionFeedRepository.setInstagramReelsBlockEnabled(false)
                distractionFeedRepository.setFacebookReelsBlockEnabled(false)
                distractionFeedRepository.setYouTubeShortsBlockEnabled(false)
                appBlockerRepository.setAutoBlockNewAppsEnabled(false)
                challengeRepository.setChallengeActive(false)
                pendingChangeDao.deleteAllPending()
                val allBlocked = appBlockerRepository.getFastBlockedPackages().toList()
                allBlocked.forEach { appBlockerRepository.unblockAppDirect(it) }
                keywordBlockerRepository.clearAllKeywords()
                val allSchedulesCount = scheduleRepository.getFastActiveSchedules().size
                scheduleRepository.clearAllSchedules()
                val allLimitPkgs = timeLimitRepository.getFastTimeLimits().keys.toList()
                allLimitPkgs.forEach { timeLimitRepository.removeTimeLimitDirect(it) }
                Log.i(TAG, "ACTION_FULL_RESET: cleared ${allBlocked.size} blocked apps, " +
                    "keywords, $allSchedulesCount schedules, ${allLimitPkgs.size} time limits.")
                true
            }
            else -> false
        }
}

