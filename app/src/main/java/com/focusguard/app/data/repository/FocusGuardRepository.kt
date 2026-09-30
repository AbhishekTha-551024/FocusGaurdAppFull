package com.focusguard.app.data.repository

import android.content.Context
import android.util.Log
import com.focusguard.app.blockers.TimeTamperGuard
import com.focusguard.app.data.datastore.FocusGuardPreferences
import com.focusguard.app.data.db.*
import com.focusguard.app.security.SupportCodeEngine
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.usage.LimitStatus
import com.focusguard.app.usage.ScheduleBlockResult
import com.focusguard.app.usage.ScheduleEvaluator
import com.focusguard.app.usage.TimeLimitEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val prefs: FocusGuardPreferences,
    private val eventDao: SecurityEventDao,
    private val blockedAppDao: BlockedAppDao,
    private val blockedKeywordDao: BlockedKeywordDao,
    private val appTimeLimitDao: AppTimeLimitDao,
    private val scheduleDao: ScheduleDao,
    private val pendingChangeDao: PendingChangeDao,
    private val timeLimitEvaluator: TimeLimitEvaluator,
    private val scheduleEvaluator: ScheduleEvaluator,
    private val supportCodeEngine: SupportCodeEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // In-memory zero-latency fast lookup caches (< 0.01 ms)
    private val cachedActiveSchedules = CopyOnWriteArrayList<ScheduleEntity>()
    private val cachedBlockedPackages = ConcurrentHashMap.newKeySet<String>()
    private val cachedBlockedKeywords = ConcurrentHashMap.newKeySet<String>()
    private val cachedTimeLimits = ConcurrentHashMap<String, AppTimeLimitEntity>()
    private val cachedExceededTimeLimits = ConcurrentHashMap.newKeySet<String>()
    private val cachedOnboardingComplete = AtomicBoolean(false)
    // STARTUP RACE FIX: Initialize with synchronous prefs cache (`prefs.syncUninstallProtectionEnabled`)
    // so on cold boot / device reboot, isFastUninstallProtectionEnabled() immediately returns the real state
    // with 0ms delay, closing the reboot uninstall vulnerability window.
    private val cachedUninstallProtection = AtomicBoolean(prefs.syncUninstallProtectionEnabled)
    private val cachedTimedLockExpiry = java.util.concurrent.atomic.AtomicLong(prefs.syncUninstallProtectionExpiry)
    private val cachedTimedLockDays = java.util.concurrent.atomic.AtomicInteger(prefs.syncUninstallProtectionDays)
    private val cachedShortsReelsBlockEnabled = AtomicBoolean(false)
    private val cachedInstagramReelsBlockEnabled = AtomicBoolean(false)
    private val cachedFacebookReelsBlockEnabled = AtomicBoolean(false)
    private val cachedYouTubeShortsBlockEnabled = AtomicBoolean(false)
    private val cachedAutoBlockNewApps = AtomicBoolean(false)
    private val cachedAutoBlockEnabledTimestamp = java.util.concurrent.atomic.AtomicLong(0L)
    private val cachedCustomBlockMessage = java.util.concurrent.atomic.AtomicReference<String>("")
    private val cachedBlockCountdownSeconds = java.util.concurrent.atomic.AtomicInteger(5)
    private val cachedOverlayPresetId = java.util.concurrent.atomic.AtomicInteger(0)
    private val cachedOverlayDimAlpha = java.util.concurrent.atomic.AtomicReference<Float>(0.55f)
    private val cachedOverlayWallpaperPath = java.util.concurrent.atomic.AtomicReference<String>("")
    private val cachedReviewedPackages = ConcurrentHashMap.newKeySet<String>()
    private val cachedIncognitoBlockEnabled = AtomicBoolean(prefs.syncIncognitoBlockEnabled)
    private val cachedBlockUnsupportedBrowsersEnabled = AtomicBoolean(prefs.syncBlockUnsupportedBrowsersEnabled)
    private val cachedAdultContentBlockEnabled = AtomicBoolean(prefs.syncAdultContentBlockEnabled)
    private val cachedAntiCloningShieldEnabled = AtomicBoolean(prefs.syncAntiCloningShieldEnabled)

    init {
        // 1. Synchronize onboarding state
        scope.launch {
            prefs.isOnboardingComplete.collect { complete ->
                cachedOnboardingComplete.set(complete)
            }
        }

        // 2. Synchronize active schedules for 0ms lookup
        scope.launch {
            scheduleDao.watchEnabledSchedules().collect { list ->
                cachedActiveSchedules.clear()
                cachedActiveSchedules.addAll(list)
                prefs.updateSyncCounts(schedules = list.size)
                Log.d(TAG, "Synchronized active schedules cache: ${list.size} active schedules")
            }
        }

        // 3. Synchronize blocked apps set for 0ms lookup
        scope.launch {
            blockedAppDao.watchBlockedApps().collect { list ->
                cachedBlockedPackages.clear()
                cachedBlockedPackages.addAll(list.map { it.packageName.lowercase() })
                prefs.updateSyncCounts(apps = list.size)
                Log.d(TAG, "Synchronized blocked apps cache: ${list.size} apps")
            }
        }

        // 4. Synchronize time limits for 0ms lookup
        scope.launch {
            appTimeLimitDao.watchEnabledTimeLimits().collect { list ->
                cachedTimeLimits.clear()
                list.forEach { cachedTimeLimits[it.packageName.lowercase()] = it }
                prefs.updateSyncCounts(limits = list.size)
                refreshExceededTimeLimits()
                Log.d(TAG, "Synchronized time limits cache: ${list.size} limits")
            }
        }

        // 5. Periodic & Startup auto-reconciliation for past-due pending changes
        scope.launch {
            while (isActive) {
                try {
                    reconcilePendingChanges()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in pending changes auto-reconciliation: ${e.message}")
                }
                delay(5_000L) // Auto-reconcile every 5 seconds
            }
        }

        // 6. Synchronize uninstall protection toggle & timed lock
        // Safe default of false (above) is corrected immediately by this collector.
        // The accessibility service additionally calls refreshUninstallProtectionNow() on connect
        // to guarantee the value is correct before the first event is processed.
        scope.launch {
            prefs.isUninstallProtectionEnabled.collect { enabled ->
                cachedUninstallProtection.set(enabled)
                Log.d(TAG, "Synchronized uninstall protection cache: $enabled")
            }
        }
        scope.launch {
            prefs.uninstallProtectionExpiryTimestamp.collect { expiry ->
                cachedTimedLockExpiry.set(expiry)
            }
        }
        scope.launch {
            prefs.uninstallProtectionDays.collect { days ->
                cachedTimedLockDays.set(days)
            }
        }
        scope.launch {
            // DATA LOSS FIX: Only clean up already applied or cancelled changes on startup.
            // PENDING changes (e.g. 24h delay challenge) must survive app restarts and reboots.
            pendingChangeDao.deleteApplied()
            if (prefs.syncChallengeType != ChallengeType.TYPING) {
                prefs.setChallengeType(ChallengeType.TYPING)
            }
        }

        // 7. Synchronize Shorts & Reels blocking toggle
        scope.launch {
            prefs.isShortsReelsBlockEnabled.collect { enabled ->
                cachedShortsReelsBlockEnabled.set(enabled)
                Log.d(TAG, "Synchronized Shorts/Reels block cache: $enabled")
            }
        }
        scope.launch {
            prefs.isInstagramReelsBlockEnabled.collect { enabled ->
                cachedInstagramReelsBlockEnabled.set(enabled)
            }
        }
        scope.launch {
            prefs.isFacebookReelsBlockEnabled.collect { enabled ->
                cachedFacebookReelsBlockEnabled.set(enabled)
            }
        }
        scope.launch {
            prefs.isYouTubeShortsBlockEnabled.collect { enabled ->
                cachedYouTubeShortsBlockEnabled.set(enabled)
            }
        }

        // 8. Synchronize Auto-Block New Apps toggle
        scope.launch {
            prefs.isAutoBlockNewAppsEnabled.collect { enabled ->
                cachedAutoBlockNewApps.set(enabled)
                Log.d(TAG, "Synchronized auto-block new apps cache: $enabled")
            }
        }

        // 9. Synchronize Auto-Block timestamp
        scope.launch {
            prefs.autoBlockEnabledTimestamp.collect { ts ->
                cachedAutoBlockEnabledTimestamp.set(ts)
            }
        }

        // 10. Synchronize Custom Block Message
        scope.launch {
            prefs.customBlockMessage.collect { msg ->
                cachedCustomBlockMessage.set(msg)
            }
        }

        // 10.5 Synchronize Incognito Block
        scope.launch {
            prefs.isIncognitoBlockEnabled.collect { enabled ->
                cachedIncognitoBlockEnabled.set(enabled)
                Log.d(TAG, "Synchronized incognito block cache: $enabled")
            }
        }

        // 10.6 Synchronize Block Unsupported Browsers
        scope.launch {
            prefs.isBlockUnsupportedBrowsersEnabled.collect { enabled ->
                cachedBlockUnsupportedBrowsersEnabled.set(enabled)
                Log.d(TAG, "Synchronized block unsupported browsers cache: $enabled")
            }
        }

        // 10.7 Synchronize Anti-Cloning & Dual Apps Shield
        scope.launch {
            prefs.isAntiCloningShieldEnabled.collect { enabled ->
                cachedAntiCloningShieldEnabled.set(enabled)
                Log.d(TAG, "Synchronized anti-cloning shield cache: $enabled")
            }
        }

        // 10.8 Synchronize Strict Adult Blocker & SafeSearch Shield
        scope.launch {
            prefs.isAdultContentBlockEnabled.collect { enabled ->
                cachedAdultContentBlockEnabled.set(enabled)
                Log.d(TAG, "Synchronized adult content block cache: $enabled")
            }
        }

        // 11. Synchronize Block Countdown Seconds
        scope.launch {
            prefs.blockCountdownSeconds.collect { sec ->
                cachedBlockCountdownSeconds.set(sec)
            }
        }

        // 12. Synchronize Overlay Wallpaper & Preset
        scope.launch {
            prefs.overlayPresetId.collect { id ->
                cachedOverlayPresetId.set(id)
            }
        }
        scope.launch {
            prefs.overlayDimAlpha.collect { dim ->
                cachedOverlayDimAlpha.set(dim)
            }
        }
        scope.launch {
            prefs.overlayWallpaperPath.collect { path ->
                cachedOverlayWallpaperPath.set(path)
            }
        }

        // 14. Synchronize blocked keywords for 0ms lookup & auto-sanitize legacy entries
        scope.launch {
            blockedKeywordDao.watchBlockedKeywords().collect { list ->
                val cleanList = mutableListOf<String>()
                for (item in list) {
                    val clean = sanitizeKeyword(item.keyword)
                    if (clean.isNotBlank()) {
                        cleanList.add(clean)
                        if (clean != item.keyword) {
                            scope.launch {
                                try {
                                    blockedKeywordDao.insert(item.copy(keyword = clean))
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
                cachedBlockedKeywords.clear()
                cachedBlockedKeywords.addAll(cleanList)
                prefs.updateSyncCounts(keywords = cleanList.size)
                Log.d(TAG, "Synchronized blocked keywords cache: ${cleanList.size} keywords")
            }
        }

        // 15. Eagerly synchronize challenge preferences to syncPrefs RAM cache
        scope.launch {
            prefs.challengeType.collect { /* triggered mapping to syncPrefs */ }
        }
        scope.launch {
            prefs.commitmentSentence.collect { /* triggered mapping to syncPrefs */ }
        }
        scope.launch {
            prefs.timeDelayHours.collect { /* triggered mapping to syncPrefs */ }
        }
        scope.launch {
            prefs.isChallengeActive.collect { /* triggered mapping to syncPrefs */ }
        }

        // 16. Synchronize reviewed apps from syncPrefs & DataStore
        cachedReviewedPackages.addAll(prefs.syncReviewedPackages.map { it.lowercase() })
        scope.launch {
            prefs.reviewedPackages.collect { set ->
                cachedReviewedPackages.clear()
                cachedReviewedPackages.addAll(set.map { it.lowercase() })
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Zero-Latency Fast In-Memory Checks (< 0.01 ms)
    // ──────────────────────────────────────────────────────────────────────────

    fun isFastOnboardingComplete(): Boolean = cachedOnboardingComplete.get()

    fun isFastBlockedApp(packageName: String): Boolean = cachedBlockedPackages.contains(packageName.lowercase())

    fun getFastBlockedPackages(): Set<String> = cachedBlockedPackages

    fun getFastBlockedAppsCount(): Int = cachedBlockedPackages.size

    fun getFastBlockedKeywords(): Set<String> = cachedBlockedKeywords

    fun getFastBlockedKeywordsEntities(): List<BlockedKeywordEntity> {
        return cachedBlockedKeywords.map { BlockedKeywordEntity(keyword = it) }
    }

    fun getFastActiveSchedules(): List<ScheduleEntity> {
        return cachedActiveSchedules.toList()
    }

    fun getFastTimeLimits(): Map<String, AppTimeLimitEntity> = cachedTimeLimits

    fun hasFastTimeLimit(packageName: String): Boolean = cachedTimeLimits.containsKey(packageName.lowercase())

    fun isFastTimeLimitExceeded(packageName: String): Boolean =
        cachedExceededTimeLimits.contains(packageName.lowercase())

    fun markTimeLimitExceeded(packageName: String) {
        cachedExceededTimeLimits.add(packageName.lowercase())
    }

    fun clearTimeLimitExceeded(packageName: String) {
        cachedExceededTimeLimits.remove(packageName.lowercase())
    }

    fun refreshExceededTimeLimits() {
        scope.launch(Dispatchers.IO) {
            try {
                val limits = cachedTimeLimits.values.toList()
                val exceeded = timeLimitEvaluator.getExceededPackages(limits)
                cachedExceededTimeLimits.clear()
                cachedExceededTimeLimits.addAll(exceeded)
                Log.d(TAG, "Refreshed exceeded time limits: $exceeded")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing exceeded time limits", e)
            }
        }
    }

    fun isFastUninstallProtectionEnabled(): Boolean {
        if (!cachedUninstallProtection.get()) return false
        val expiry = cachedTimedLockExpiry.get()
        if (expiry <= 0L) return true // Indefinite protection if enabled without expiry
        // AGENTS Rule 9 & Security Fix #5: Check monotonic hardware clock to prevent rollback bypass
        if (TimeTamperGuard.isTampered() || TimeTamperGuard.checkAndValidateTime()) {
            return true // Lock remains unconditionally active if clock manipulation is detected
        }
        return System.currentTimeMillis() < expiry
    }

    /**
     * Force-reads uninstall protection state from DataStore (canonical source) into the in-memory
     * cache. Called by FocusGuardAccessibilityService on connect to close the startup race window
     * where the service might read a stale cache value before the init {} coroutine completes.
     */
    fun refreshUninstallProtectionNow() {
        scope.launch {
            try {
                val canonical = prefs.isUninstallProtectionEnabled.first()
                cachedUninstallProtection.set(canonical)
                Log.d(TAG, "refreshUninstallProtectionNow: synced from DataStore -> $canonical")
            } catch (e: Exception) {
                Log.e(TAG, "refreshUninstallProtectionNow: DataStore read failed, defaulting to OFF", e)
                cachedUninstallProtection.set(false)
            }
        }
    }

    fun isFastShortsReelsBlockEnabled(): Boolean = cachedShortsReelsBlockEnabled.get()

    fun isFastInstagramReelsBlockEnabled(): Boolean = cachedInstagramReelsBlockEnabled.get()

    fun isFastFacebookReelsBlockEnabled(): Boolean = cachedFacebookReelsBlockEnabled.get()

    fun isFastYouTubeShortsBlockEnabled(): Boolean = cachedYouTubeShortsBlockEnabled.get()

    fun isFastAutoBlockNewAppsEnabled(): Boolean = cachedAutoBlockNewApps.get()

    fun isFastIncognitoBlockEnabled(): Boolean = cachedIncognitoBlockEnabled.get()

    fun isFastBlockUnsupportedBrowsersEnabled(): Boolean = cachedBlockUnsupportedBrowsersEnabled.get()
    
    fun isFastAdultContentBlockEnabled(): Boolean = cachedAdultContentBlockEnabled.get()
    
    fun isFastAntiCloningShieldEnabled(): Boolean = cachedAntiCloningShieldEnabled.get()
    
    fun isFastAppBlocked(packageName: String): Boolean = cachedBlockedPackages.contains(packageName.lowercase())

    fun getFastAutoBlockEnabledTimestamp(): Long = cachedAutoBlockEnabledTimestamp.get()

    fun isFastAppReviewed(packageName: String): Boolean = cachedReviewedPackages.contains(packageName.lowercase())

    val isUninstallProtectionEnabled: Flow<Boolean> get() = prefs.isUninstallProtectionEnabled
    val uninstallProtectionExpiryTimestamp: Flow<Long> get() = prefs.uninstallProtectionExpiryTimestamp
    val uninstallProtectionDays: Flow<Int> get() = prefs.uninstallProtectionDays

    fun isFastTimedLockActive(): Boolean {
        return isFastUninstallProtectionEnabled()
    }

    fun getFastTimedLockRemainingDays(): Long {
        val expiry = cachedTimedLockExpiry.get()
        if (expiry <= 0L) return 0L
        // If clock was tampered with, lock remains locked; reflect configured days
        if (TimeTamperGuard.isTampered() || TimeTamperGuard.checkAndValidateTime()) {
            return cachedTimedLockDays.get().toLong().coerceAtLeast(1L)
        }
        val diffMs = expiry - System.currentTimeMillis()
        return if (diffMs > 0) java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs) + 1 else 0L
    }

    suspend fun startTimedUninstallProtection(days: Int) {
        prefs.startTimedUninstallProtection(days)
        val durationMs = java.util.concurrent.TimeUnit.DAYS.toMillis(days.toLong().coerceAtLeast(1L))
        val expiry = System.currentTimeMillis() + durationMs
        cachedTimedLockExpiry.set(expiry)
        cachedTimedLockDays.set(days)
        cachedUninstallProtection.set(true)
        TimeTamperGuard.checkAndValidateTime() // Synchronize monotonic hardware baseline
        sendRefreshCachesBroadcast() // FIX 4: sync :blocker_service cache immediately
        logEvent(EventType.UNINSTALL_ATTEMPT, "Timed uninstall protection activated for $days days")
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        prefs.setUninstallProtectionEnabled(enabled)
        cachedUninstallProtection.set(enabled)
        if (!enabled) {
            cachedTimedLockExpiry.set(0L)
            cachedTimedLockDays.set(0)
        }
        // FIX 4: Notify :blocker_service process immediately so its AtomicBoolean cache
        // reflects the new state without waiting for DataStore Flow propagation (~30s lag).
        sendRefreshCachesBroadcast()
    }

    suspend fun disableUninstallProtection() {
        setUninstallProtectionEnabled(false)
    }

    /** Broadcasts ACTION_REFRESH_CACHES to the :blocker_service process. */
    private fun sendRefreshCachesBroadcast() {
        try {
            val intent = android.content.Intent("com.focusguard.app.ACTION_REFRESH_CACHES").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            android.util.Log.d(TAG, "ACTION_REFRESH_CACHES broadcast sent to :blocker_service")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to send REFRESH_CACHES broadcast", e)
        }
    }

    // ── Phase 7: Overlay Wallpaper Prefs (delegates to DataStore) ─────────────
    fun getFastOverlayPresetId(): Int = cachedOverlayPresetId.get()
    fun getFastOverlayDimAlpha(): Float = cachedOverlayDimAlpha.get() ?: 0.55f
    fun getFastOverlayWallpaperPath(): String = cachedOverlayWallpaperPath.get() ?: ""

    val overlayWallpaperPath get() = prefs.overlayWallpaperPath
    val overlayPresetId      get() = prefs.overlayPresetId
    val overlayDimAlpha      get() = prefs.overlayDimAlpha

    suspend fun setOverlayWallpaperPath(path: String) {
        prefs.setOverlayWallpaperPath(path)
        cachedOverlayWallpaperPath.set(path)
    }
    suspend fun setOverlayPresetId(id: Int) {
        prefs.setOverlayPresetId(id)
        cachedOverlayPresetId.set(id)
    }
    suspend fun setOverlayDimAlpha(alpha: Float) {
        prefs.setOverlayDimAlpha(alpha)
        cachedOverlayDimAlpha.set(alpha)
    }
    suspend fun clearOverlayWallpaper() {
        prefs.clearOverlayWallpaper()
        cachedOverlayWallpaperPath.set("")
    }

    // ── Block Overlay Customization & Behavior ─────────────────────────────────
    fun getFastCustomBlockMessage(): String = cachedCustomBlockMessage.get()
    fun getFastBlockCountdownSeconds(): Int = cachedBlockCountdownSeconds.get()

    val customBlockMessage: Flow<String> get() = prefs.customBlockMessage
    suspend fun setCustomBlockMessage(message: String) {
        prefs.setCustomBlockMessage(message)
        cachedCustomBlockMessage.set(message)
    }

    val blockCountdownSeconds: Flow<Int> get() = prefs.blockCountdownSeconds
    suspend fun setBlockCountdownSeconds(seconds: Int) {
        prefs.setBlockCountdownSeconds(seconds)
        cachedBlockCountdownSeconds.set(seconds)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Phase 6: Removal Challenges & Pending Changes Engine
    // ──────────────────────────────────────────────────────────────────────────

    data class ChallengeSettings(
        val type: ChallengeType = ChallengeType.TYPING,
        val commitmentSentence: String = "",
        val timeDelayHours: Int = 24,
        val isChallengeActive: Boolean = false
    )

    val challengeType: Flow<ChallengeType> = prefs.challengeType
    val commitmentSentence: Flow<String> = prefs.commitmentSentence
    val timeDelayHours: Flow<Int> = prefs.timeDelayHours
    val challengeSettings: Flow<ChallengeSettings> = combine(
        prefs.challengeType,
        prefs.commitmentSentence,
        prefs.timeDelayHours,
        prefs.isChallengeActive
    ) { type, sentence, hours, active ->
        ChallengeSettings(type, sentence, hours, active)
    }
    val isChallengeActive: Flow<Boolean> = prefs.isChallengeActive
    val settingsSnapshot: Flow<FocusGuardPreferences.SettingsSnapshot> = prefs.settingsSnapshot
    val pendingChanges: Flow<List<PendingChangeEntity>> = pendingChangeDao.watchPendingChanges()
        .transform { list ->
            val now = System.currentTimeMillis()
            val due = list.filter { it.scheduledApplyAt != null && it.scheduledApplyAt <= now }
            if (due.isNotEmpty()) {
                scope.launch {
                    reconcilePendingChanges()
                }
            }
            emit(list.filter { it.scheduledApplyAt == null || it.scheduledApplyAt > now })
        }

    suspend fun setChallengeType(type: ChallengeType) = prefs.setChallengeType(type)
    suspend fun setCommitmentSentence(sentence: String) = prefs.setCommitmentSentence(sentence)
    suspend fun setTimeDelayHours(hours: Int) = prefs.setTimeDelayHours(hours)
    suspend fun setChallengeActive(active: Boolean) = prefs.setChallengeActive(active)

    suspend fun createPendingChange(
        changeType: ChangeType,
        targetId: String,
        targetName: String = "",
        targetPayload: String? = null,
        scheduledApplyAt: Long? = null
    ): PendingChangeEntity {
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
        return entity.copy(id = id)
    }

    suspend fun applyPendingChange(pendingChange: PendingChangeEntity) {
        try {
            when (pendingChange.changeType) {
                ChangeType.UNBLOCK_APP -> {
                    unblockApp(pendingChange.targetId)
                }
                ChangeType.DELETE_KEYWORD -> {
                    val id = pendingChange.targetId.toLongOrNull()
                    if (id != null) unblockKeyword(id)
                }
                ChangeType.DISABLE_SCHEDULE -> {
                    val id = pendingChange.targetId.toLongOrNull()
                    if (id != null) {
                        scheduleDao.updateEnabled(id, false)
                    }
                }
                ChangeType.DELETE_SCHEDULE -> {
                    val id = pendingChange.targetId.toLongOrNull()
                    if (id != null) deleteSchedule(id)
                }
                ChangeType.DISABLE_LIMIT -> {
                    removeTimeLimit(pendingChange.targetId)
                }
                ChangeType.LOWER_LIMIT -> {
                    val limit = pendingChange.targetPayload?.toIntOrNull() ?: 60
                    setTimeLimit(pendingChange.targetId, pendingChange.targetName, limit, true)
                }
                ChangeType.CHANGE_CHALLENGE_TYPE -> {
                    val newType = pendingChange.targetPayload?.let { runCatching { ChallengeType.valueOf(it) }.getOrDefault(ChallengeType.TYPING) } ?: ChallengeType.TYPING
                    setChallengeType(newType)
                }
                ChangeType.UNINSTALL_REQUEST -> {
                    setUninstallProtectionEnabled(false)
                    logEvent(EventType.UNINSTALL_ATTEMPT, "Uninstall protection disabled via challenge approval")
                }
                ChangeType.DISABLE_DISTRACTION_FEED -> {
                    when (pendingChange.targetId) {
                        "instagram" -> setInstagramReelsBlockEnabled(false)
                        "facebook" -> setFacebookReelsBlockEnabled(false)
                        "youtube" -> setYouTubeShortsBlockEnabled(false)
                        "auto_block" -> setAutoBlockNewAppsEnabled(false)
                        "unsupported_browsers" -> setBlockUnsupportedBrowsersEnabled(false)
                        "adult_content" -> setAdultContentBlockEnabled(false)
                        "incognito" -> setIncognitoBlockEnabled(false)
                        "shorts_reels" -> setShortsReelsBlockEnabled(false)
                        else -> {
                            setInstagramReelsBlockEnabled(false)
                            setFacebookReelsBlockEnabled(false)
                            setYouTubeShortsBlockEnabled(false)
                        }
                    }
                    logEvent(EventType.APP_UNBLOCKED, "Distraction feed control disabled via challenge approval: ${pendingChange.targetId}")
                }
                ChangeType.LOWER_COUNTDOWN -> {
                    val newSeconds = pendingChange.targetPayload?.toIntOrNull() ?: 5
                    setBlockCountdownSeconds(newSeconds)
                    logEvent(EventType.APP_UNBLOCKED, "Block countdown lowered to ${newSeconds}s via challenge approval")
                }
                ChangeType.LOGOUT_REQUEST -> {
                    logEvent(EventType.UNINSTALL_ATTEMPT, "Logout request approved via challenge/partner")
                }
                ChangeType.ENABLE_UNINSTALL_PROTECTION -> {
                    /* handled immediately via commitment challenge and duration dialog in SettingsViewModel */
                }
                ChangeType.REMOVE_MINDFUL_APP -> {
                    logEvent(EventType.APP_UNBLOCKED, "Mindful app removed via challenge approval: ${pendingChange.targetName}")
                }
            }

            pendingChangeDao.updateStatus(pendingChange.id, PendingStatus.APPLIED)
            pendingChangeDao.deleteById(pendingChange.id)
            logEvent(EventType.APP_UNBLOCKED, "Pending change applied: ${pendingChange.changeType} on ${pendingChange.targetName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply pending change: ${e.message}", e)
        }
    }

    suspend fun cancelPendingChange(id: Long) {
        pendingChangeDao.deleteById(id)
        logEvent(EventType.APP_BLOCKED, "Pending change cancelled (id=$id)")
    }

    suspend fun reconcilePendingChanges() {
        val now = System.currentTimeMillis()
        val dueList = pendingChangeDao.getDuePendingChanges(now)
        Log.i(TAG, "Reconciling pending changes: found ${dueList.size} due changes")
        for (change in dueList) {
            applyPendingChange(change)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Onboarding
    // ──────────────────────────────────────────────────────────────────────────

    val isOnboardingComplete: Flow<Boolean> = prefs.isOnboardingComplete

    suspend fun markOnboardingComplete() {
        prefs.setOnboardingComplete(true)
        cachedOnboardingComplete.set(true)
        logEvent(EventType.ONBOARDING_COMPLETE)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // App Blocking (P1)
    // ──────────────────────────────────────────────────────────────────────────

    val blockedApps: Flow<List<BlockedAppEntity>> = blockedAppDao.watchBlockedApps()
    val blockedAppsCount: Flow<Int> = blockedAppDao.count()

    suspend fun blockApp(
        packageName: String,
        appName: String,
        isAutoBlockedUnsupportedBrowser: Boolean = false,
        isAutoBlockedNewInstall: Boolean = false
    ) {
        val clean = packageName.trim().lowercase()
        val entity = BlockedAppEntity(
            packageName = clean,
            appName = appName,
            isAutoBlockedUnsupportedBrowser = isAutoBlockedUnsupportedBrowser,
            isAutoBlockedNewInstall = isAutoBlockedNewInstall
        )
        blockedAppDao.insert(entity)
        cachedBlockedPackages.add(clean)
        logEvent(EventType.APP_BLOCKED, "Package: $packageName ($appName)")
    }

    suspend fun unblockApp(packageName: String) {
        val clean = packageName.trim().lowercase()
        blockedAppDao.deleteByPackageName(clean)
        blockedAppDao.deleteByPackageName(packageName)
        cachedBlockedPackages.remove(clean)
        cachedBlockedPackages.remove(packageName)
        markAppReviewed(clean)
        logEvent(EventType.APP_UNBLOCKED, "Package: $packageName")
    }

    suspend fun unblockAllAutoBlockedNewApps() {
        val apps = blockedAppDao.getAutoBlockedNewInstallApps()
        for (app in apps) {
            val clean = app.packageName.lowercase()
            cachedBlockedPackages.remove(clean)
            markAppReviewed(clean)
            logEvent(EventType.APP_UNBLOCKED, "Auto-unblocked package on setting disabled: ${app.packageName}")
        }
        blockedAppDao.deleteAutoBlockedNewInstallApps()
    }

    suspend fun markAppReviewed(packageName: String) {
        val clean = packageName.lowercase()
        cachedReviewedPackages.add(clean)
        prefs.markAppReviewed(clean)
    }

    suspend fun clearAppReviewed(packageName: String) {
        val clean = packageName.lowercase()
        cachedReviewedPackages.remove(clean)
        prefs.clearAppReviewed(clean)
    }

    suspend fun isAppBlocked(packageName: String): Boolean {
        return cachedBlockedPackages.contains(packageName.lowercase())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Keyword Blocking (P2)
    // ──────────────────────────────────────────────────────────────────────────

    val blockedKeywords: Flow<List<BlockedKeywordEntity>> = blockedKeywordDao.watchBlockedKeywords()
    val blockedKeywordsCount: Flow<Int> = blockedKeywordDao.count()
    val blockedKeywordsSet: Flow<Set<String>> = blockedKeywordDao.watchBlockedKeywords().map { list ->
        list.map { it.keyword.lowercase() }.toSet()
    }

    fun sanitizeKeyword(raw: String): String {
        return raw.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/', '.', ',', ' ', '?', '#')
            .trim()
    }

    suspend fun blockKeyword(keyword: String): Boolean {
        val sanitized = sanitizeKeyword(keyword)
        if (sanitized.isNotBlank()) {
            cachedBlockedKeywords.add(sanitized)
            return try {
                blockedKeywordDao.insert(BlockedKeywordEntity(keyword = sanitized))
                true
            } catch (e: Exception) {
                cachedBlockedKeywords.remove(sanitized)
                false
            }
        }
        return false
    }

    suspend fun unblockKeyword(id: Long) {
        val kw = blockedKeywordDao.getById(id)
        if (kw != null) {
            cachedBlockedKeywords.remove(kw.keyword.lowercase())
        }
        blockedKeywordDao.deleteById(id)
    }

    suspend fun unblockKeywordById(id: Long) {
        val kw = blockedKeywordDao.getById(id)
        if (kw != null) {
            cachedBlockedKeywords.remove(kw.keyword.lowercase())
        }
        blockedKeywordDao.deleteById(id)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Time Limits & Focus Schedules (Phase 5)
    // ──────────────────────────────────────────────────────────────────────────

    val allTimeLimits: Flow<List<AppTimeLimitEntity>> = appTimeLimitDao.watchAllTimeLimits()
    val activeTimeLimitsCount: Flow<Int> = appTimeLimitDao.countActive()
    val allSchedules: Flow<List<ScheduleEntity>> = scheduleDao.watchAllSchedules()
    val activeSchedulesCount: Flow<Int> = scheduleDao.countActive()

    suspend fun setTimeLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean = true
    ) {
        val entity = AppTimeLimitEntity(
            packageName = packageName,
            appName = appName,
            dailyLimitMinutes = dailyLimitMinutes,
            isEnabled = isEnabled
        )
        appTimeLimitDao.upsert(entity)
        if (isEnabled) {
            cachedTimeLimits[packageName.lowercase()] = entity
        } else {
            cachedTimeLimits.remove(packageName.lowercase())
        }
        refreshExceededTimeLimits()
    }

    suspend fun removeTimeLimit(packageName: String) {
        appTimeLimitDao.delete(packageName)
        cachedTimeLimits.remove(packageName.lowercase())
        cachedExceededTimeLimits.remove(packageName.lowercase())
    }

    suspend fun saveSchedule(schedule: ScheduleEntity): Long {
        val id = scheduleDao.insert(schedule)
        return id
    }

    suspend fun deleteSchedule(id: Long) {
        scheduleDao.deleteById(id)
    }

    suspend fun updateScheduleEnabled(id: Long, enabled: Boolean) {
        scheduleDao.updateEnabled(id, enabled)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Master Unified Access Evaluation Engine (High Priority Fast-Path)
    // ──────────────────────────────────────────────────────────────────────────

    fun hasActiveSchedulesOrLimits(packageName: String): Boolean {
        if (cachedActiveSchedules.isNotEmpty()) return true
        val limit = cachedTimeLimits[packageName.lowercase()]
        return limit != null && limit.isEnabled
    }

    suspend fun evaluateAccess(
        packageName: String,
        appLabel: String = packageName,
        liveSessionMs: Long = 0L
    ): AccessDecision {
        val isAppInBlocklist = cachedBlockedPackages.contains(packageName.lowercase())

        // 1. HARD APP BLOCKLIST EVALUATION (0ms instant memory check)
        if (isAppInBlocklist) {
            Log.i(TAG, "evaluateAccess -> BLOCKED_BY_APP_BLOCKER: $packageName")
            return AccessDecision.BlockedByAppBlocker(appLabel = appLabel)
        }

        // 2. FOCUS SCHEDULE EVALUATION (0ms instant memory check)
        val scheduleBlock = scheduleEvaluator.isBlockedBySchedule(
            packageName = packageName,
            schedules = cachedActiveSchedules,
            isAppBlockedInBlocklist = isAppInBlocklist
        )

        if (scheduleBlock != null) {
            Log.i(TAG, "evaluateAccess -> BLOCKED_BY_SCHEDULE: $packageName by '${scheduleBlock.scheduleTitle}' until ${scheduleBlock.endTimeFormatted}")
            return AccessDecision.BlockedBySchedule(
                appLabel = appLabel,
                scheduleTitle = scheduleBlock.scheduleTitle,
                endTimeFormatted = scheduleBlock.endTimeFormatted
            )
        }

        // 3. DAILY TIME LIMIT EVALUATION (Sub-second millisecond accuracy)
        val limitEntity = cachedTimeLimits[packageName.lowercase()]
        if (limitEntity != null && limitEntity.isEnabled) {
            val status = timeLimitEvaluator.evaluate(packageName, limitEntity, liveSessionMs)
            if (status is LimitStatus.Exceeded) {
                markTimeLimitExceeded(packageName)
                Log.i(TAG, "evaluateAccess -> BLOCKED_BY_TIME_LIMIT: $packageName (${status.usedMinutes}/${status.limitMinutes}m)")
                return AccessDecision.BlockedByTimeLimit(
                    appLabel = appLabel,
                    usedMinutes = status.usedMinutes,
                    limitMinutes = status.limitMinutes
                )
            }
        }

        return AccessDecision.Allowed
    }

    fun recordSessionUsage(packageName: String, elapsedMs: Long) {
        timeLimitEvaluator.recordSessionUsage(packageName, elapsedMs)
        val limit = cachedTimeLimits[packageName.lowercase()]
        if (limit != null && limit.isEnabled) {
            val usedMs = timeLimitEvaluator.getTodayUsageMillis(packageName)
            if (usedMs >= limit.dailyLimitMinutes * 60_000L) {
                markTimeLimitExceeded(packageName)
            }
        }
    }

    fun getTodayUsageMillis(packageName: String, liveSessionMs: Long = 0L): Long {
        return timeLimitEvaluator.getTodayUsageMillis(packageName, liveSessionMs)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Security Audit Log (P3)
    // ──────────────────────────────────────────────────────────────────────────

    val securityEvents: Flow<List<SecurityEvent>> = eventDao.observeAll()

    val isShortsReelsBlockEnabled: Flow<Boolean> = prefs.isShortsReelsBlockEnabled

    suspend fun setShortsReelsBlockEnabled(enabled: Boolean) {
        cachedShortsReelsBlockEnabled.set(enabled)
        prefs.setShortsReelsBlockEnabled(enabled)
    }

    val isInstagramReelsBlockEnabled: Flow<Boolean> = prefs.isInstagramReelsBlockEnabled

    suspend fun setInstagramReelsBlockEnabled(enabled: Boolean) {
        cachedInstagramReelsBlockEnabled.set(enabled)
        prefs.setInstagramReelsBlockEnabled(enabled)
    }

    val isFacebookReelsBlockEnabled: Flow<Boolean> = prefs.isFacebookReelsBlockEnabled

    suspend fun setFacebookReelsBlockEnabled(enabled: Boolean) {
        cachedFacebookReelsBlockEnabled.set(enabled)
        prefs.setFacebookReelsBlockEnabled(enabled)
    }

    val isYouTubeShortsBlockEnabled: Flow<Boolean> = prefs.isYouTubeShortsBlockEnabled

    suspend fun setYouTubeShortsBlockEnabled(enabled: Boolean) {
        cachedYouTubeShortsBlockEnabled.set(enabled)
        prefs.setYouTubeShortsBlockEnabled(enabled)
    }

    val isAutoBlockNewAppsEnabled: Flow<Boolean> = prefs.isAutoBlockNewAppsEnabled

    suspend fun setAutoBlockNewAppsEnabled(enabled: Boolean) {
        cachedAutoBlockNewApps.set(enabled)
        prefs.setAutoBlockNewApps(enabled)
    }

    suspend fun isAutoBlockNewAppsEnabled(): Boolean = prefs.isAutoBlockNewAppsEnabled.first()

    val isIncognitoBlockEnabled: Flow<Boolean> = prefs.isIncognitoBlockEnabled

    suspend fun setIncognitoBlockEnabled(enabled: Boolean) {
        cachedIncognitoBlockEnabled.set(enabled)
        prefs.setIncognitoBlockEnabled(enabled)
    }

    val isBlockUnsupportedBrowsersEnabled: Flow<Boolean> = prefs.isBlockUnsupportedBrowsersEnabled

    suspend fun setBlockUnsupportedBrowsersEnabled(enabled: Boolean) {
        cachedBlockUnsupportedBrowsersEnabled.set(enabled)
        prefs.setBlockUnsupportedBrowsersEnabled(enabled)
    }

    val isAdultContentBlockEnabled: Flow<Boolean> = prefs.isAdultContentBlockEnabled

    suspend fun setAdultContentBlockEnabled(enabled: Boolean) {
        cachedAdultContentBlockEnabled.set(enabled)
        prefs.setAdultContentBlockEnabled(enabled)
    }

    val isAntiCloningShieldEnabled: Flow<Boolean> = prefs.isAntiCloningShieldEnabled

    suspend fun setAntiCloningShieldEnabled(enabled: Boolean) {
        cachedAntiCloningShieldEnabled.set(enabled)
        prefs.setAntiCloningShieldEnabled(enabled)
    }

    private fun getStartOfDayMillis(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // FIX NH7: getStartOfDayMillis() was previously captured ONCE at repository construction.
    // After midnight the "today" boundary never updated until process restart, mixing yesterday's
    // blocks into today's impulse counter indefinitely.
    // channelFlow restarts the Room query with the new day's boundary after each midnight,
    // ensuring the count correctly resets at 00:00 without requiring a process restart.
    val todayImpulsesBlockedCount: Flow<Int> = kotlinx.coroutines.flow.channelFlow {
        while (!isClosedForSend) {
            val startOfDay = getStartOfDayMillis()
            val nextMidnight = startOfDay + 24L * 60L * 60L * 1000L
            val remainingMs = (nextMidnight - System.currentTimeMillis()).coerceAtLeast(60_000L)
            try {
                kotlinx.coroutines.withTimeoutOrNull(remainingMs) {
                    eventDao.observeTodayBlockCount(startOfDay).collect { send(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "todayImpulsesBlockedCount collection interrupted: ${e.message}")
            }
            // Brief buffer after midnight before restarting with the new day's boundary
            kotlinx.coroutines.delay(1_500L)
        }
    }

    suspend fun logEvent(type: EventType, detail: String = "") {
        try {
            eventDao.insert(SecurityEvent(type = type, detail = detail))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log security event: ${e.message}")
        }
    }

    // ── Synchronous 0ms RAM Snapshots for Instant UI Rendering ──

    fun getCurrentSettingsSnapshot(): com.focusguard.app.ui.settings.SettingsUiState {
        val path = prefs.syncOverlayWallpaperPath
        return com.focusguard.app.ui.settings.SettingsUiState(
            challengeType = prefs.syncChallengeType,
            commitmentSentence = prefs.syncCommitmentSentence,
            timeDelayHours = prefs.syncTimeDelayHours,
            isChallengeActive = prefs.syncIsChallengeActive,
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
            activeChallenge = null,
            overlayWallpaperPath = path,
            overlayPresetId = prefs.syncOverlayPresetId,
            overlayDimAlpha = prefs.syncOverlayDimAlpha,
            hasCustomWallpaper = path.isNotEmpty(),
            customBlockMessage = prefs.syncCustomBlockMessage,
            blockCountdownSeconds = prefs.syncBlockCountdownSeconds
        )
    }

    fun getCurrentHomeSnapshot(permissions: ProtectionPermissionStatus = ProtectionPermissionStatus()): com.focusguard.app.ui.home.HomeUiState {
        val apps = if (cachedBlockedPackages.isNotEmpty()) cachedBlockedPackages.size else prefs.syncBlockedAppsCount
        val kws = if (cachedBlockedKeywords.isNotEmpty()) cachedBlockedKeywords.size else prefs.syncBlockedKeywordsCount
        val limits = if (cachedTimeLimits.isNotEmpty()) cachedTimeLimits.size else prefs.syncActiveTimeLimitsCount
        val schedules = if (cachedActiveSchedules.isNotEmpty()) cachedActiveSchedules.size else prefs.syncActiveSchedulesCount

        return com.focusguard.app.ui.home.HomeUiState(
            isAccessibilityEnabled = permissions.isAccessibilityGranted,
            isDeviceAdminActive = permissions.isDeviceAdminGranted,
            isUsageAccessGranted = permissions.isUsageAccessGranted,
            permissionStatus = permissions,
            blockedAppsCount = apps,
            blockedKeywordsCount = kws,
            activeTimeLimitsCount = limits,
            activeSchedulesCount = schedules
        )
    }

    // ── Support Secret Code System (Offline Targeted Verification) ──

    fun getDeviceId(): String = prefs.getOrCreateDeviceId()

    fun getSupportCooldownRemainingSeconds(): Long = prefs.getSupportCooldownRemainingSeconds()

    fun verifySupportCode(code: String): SupportVerificationResult {
        val cooldownSecs = prefs.getSupportCooldownRemainingSeconds()
        if (cooldownSecs > 0) {
            val remainingMins = (cooldownSecs / 60) + 1
            return SupportVerificationResult.RateLimited(remainingMins)
        }

        val result = supportCodeEngine.verifySupportCode(
            inputCode = code,
            deviceId = getDeviceId(),
            activeBlockedPackages = cachedBlockedPackages,
            activeBlockedKeywords = cachedBlockedKeywords,
            activeLimitPackages = cachedTimeLimits.keys
        )

        if (result is SupportVerificationResult.Success) {
            prefs.resetSupportCodeWrongAttempts()
        } else if (result is SupportVerificationResult.InvalidCode) {
            val cooldownEnd = prefs.recordSupportCodeWrongAttempt()
            if (cooldownEnd > 0) {
                return SupportVerificationResult.RateLimited(15)
            }
        }

        return result
    }

    suspend fun executeSupportAction(actionCode: String, targetPayload: String): Boolean {
        Log.i(TAG, "Executing support action: Action $actionCode, payload: $targetPayload")
        return when (actionCode) {
            SupportCodeEngine.ACTION_DISABLE_UNINSTALL -> {
                setUninstallProtectionEnabled(false)
                true
            }
            SupportCodeEngine.ACTION_RESET_CHALLENGE -> {
                setChallengeActive(false)
                pendingChangeDao.deleteAllPending()
                true
            }
            SupportCodeEngine.ACTION_RESET_TIME_TAMPER -> {
                com.focusguard.app.blockers.TimeTamperGuard.resetTamperFlag()
                Log.i(TAG, "TimeTamperGuard flag reset via support code.")
                true
            }
            SupportCodeEngine.ACTION_UNBLOCK_APP -> {
                if (targetPayload.isNotBlank()) {
                    unblockApp(targetPayload)
                    true
                } else false
            }
            SupportCodeEngine.ACTION_DELETE_KEYWORD -> {
                if (targetPayload.isNotBlank()) {
                    blockedKeywordDao.deleteByKeyword(targetPayload)
                    true
                } else false
            }
            SupportCodeEngine.ACTION_DISABLE_FEED -> {
                when (targetPayload) {
                    "instagram" -> setInstagramReelsBlockEnabled(false)
                    "youtube" -> setYouTubeShortsBlockEnabled(false)
                    "facebook" -> setFacebookReelsBlockEnabled(false)
                    "all" -> {
                        setInstagramReelsBlockEnabled(false)
                        setYouTubeShortsBlockEnabled(false)
                        setFacebookReelsBlockEnabled(false)
                        setShortsReelsBlockEnabled(false)
                    }
                }
                true
            }
            SupportCodeEngine.ACTION_RESET_LIMIT -> {
                if (targetPayload.isNotBlank()) {
                    val limit = cachedTimeLimits[targetPayload.lowercase()]
                    if (limit != null) {
                        appTimeLimitDao.upsert(limit.copy(isEnabled = false))
                    }
                    true
                } else false
            }
            SupportCodeEngine.ACTION_FULL_RESET -> {
                setUninstallProtectionEnabled(false)
                setShortsReelsBlockEnabled(false)
                setInstagramReelsBlockEnabled(false)
                setFacebookReelsBlockEnabled(false)
                setYouTubeShortsBlockEnabled(false)
                setAutoBlockNewAppsEnabled(false)
                setChallengeActive(false)
                pendingChangeDao.deleteAllPending()
                // Clear all blocked apps
                val allBlocked = cachedBlockedPackages.toList()
                allBlocked.forEach { unblockApp(it) }
                // Clear all blocked keywords
                val allKw = cachedBlockedKeywords.toList()
                allKw.forEach { blockedKeywordDao.deleteByKeyword(it) }
                // FIX NC3: Also clear ALL schedules and time limits.
                // Previously, full reset left schedules and time limits active — the user
                // remained locked out of apps even after the support code was applied.
                val allSchedules = scheduleDao.watchAllSchedules().first()
                allSchedules.forEach { scheduleDao.deleteById(it.id) }
                cachedActiveSchedules.clear()
                val allLimitPkgs = cachedTimeLimits.keys.toList()
                allLimitPkgs.forEach { removeTimeLimit(it) }
                Log.i(TAG, "ACTION_FULL_RESET: cleared ${allBlocked.size} blocked apps, " +
                    "${allKw.size} keywords, ${allSchedules.size} schedules, " +
                    "${allLimitPkgs.size} time limits.")
                true
            }
            else -> false
        }
    }

    val isOemGuidanceAcknowledged: kotlinx.coroutines.flow.Flow<Boolean> = prefs.oemGuidanceAcknowledged

    suspend fun setOemGuidanceAcknowledged(acknowledged: Boolean) {
        prefs.setOemGuidanceAcknowledged(acknowledged)
    }
}
