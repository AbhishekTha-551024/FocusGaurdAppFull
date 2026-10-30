package com.focusguard.app.feature.timelimits.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PermissionStatusRepository
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import com.focusguard.app.feature.timelimits.domain.usecase.ActivateTimeLimitChallengeUseCase
import com.focusguard.app.feature.timelimits.domain.usecase.GetTimeLimitsUseCase
import com.focusguard.app.feature.timelimits.domain.usecase.RemoveTimeLimitUseCase
import com.focusguard.app.feature.timelimits.domain.usecase.SetTimeLimitUseCase
import com.focusguard.app.usage.UsageStatsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =====================================================================
 * TimeLimitViewModel.kt - Presentation / State Management Layer
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh ViewModel Time Limits feature ka central processing engine hai. Yeh device ke installed
 * apps, Room database ke active time limits, Android UsageStatsManager ke historical usage,
 * aur FocusGuard ke in-memory live session metrics ko combine karke single reactive [TimeLimitUiState]
 * produce karta hai.
 *
 * ## 2. Core Business Logic & Algorithms:
 * - **Multi-Stream State Combining:** 5 reactive flows ko combine karta hai:
 *   1. `installedApps` (device par installed apps)
 *   2. `allTimeLimits` (Room DB configured caps)
 *   3. `localState` (search text, spinners, active typing challenges)
 *   4. `pendingChanges` (cooldown delays)
 *   5. `challengeSettings` (accountability settings)
 * - **Smart Sorting Algorithm:** Apps ko 3 levels par sort karta hai:
 *   1. Jo apps pehle se limited/locked hain wo top par aayengi.
 *   2. Jinka used screen time aaj sabse zyada hai wo pehle dikhengi.
 *   3. Alphabetically app name se tie-break hota hai.
 * - **Asymmetric Friction Enforcement (Senior Dev Design):**
 *   - Agar user limit ko **TIGHTEN** karta hai (e.g. 60m se 30m karta hai) -> Koi challenge nahi!
 *   - Agar user limit ko **LOOSEN** ya **DELETE** karta hai (e.g. 30m se 60m karta hai ya delete karta hai) ->
 *     Commitment sentence type karna padta hai ya partner approval chahiye!
 * - **Real-time Lag Compensation:** `maxOf(batchUsed, liveMinutes)` use karta hai taaki
 *   Android UsageStatsManager ke 5-minute buffering lag ke bawajood exact live time reflect ho.
 * =====================================================================
 */
@HiltViewModel
class TimeLimitViewModel @Inject constructor(
    private val getTimeLimitsUseCase: GetTimeLimitsUseCase,
    private val setTimeLimitUseCase: SetTimeLimitUseCase,
    private val removeTimeLimitUseCase: RemoveTimeLimitUseCase,
    private val activateTimeLimitChallengeUseCase: ActivateTimeLimitChallengeUseCase,
    private val timeLimitRepository: TimeLimitRepository,
    private val focusGuardRepository: FocusGuardRepository,
    private val usageStatsReader: UsageStatsReader,
    private val permissionStatusRepository: PermissionStatusRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _refreshTrigger = MutableStateFlow(0L)
    private val _pendingAccountabilityLimit = MutableStateFlow<PendingAccountabilityLimit?>(null)

    private data class LocalState(
        val searchQuery: String,
        val isLoading: Boolean,
        val activeChallenge: ActiveChallengeState?,
        val refreshTrigger: Long,
        val pendingAccountability: PendingAccountabilityLimit?
    )

    private val localState = combine(
        _searchQuery,
        _isLoading,
        _activeChallenge,
        _refreshTrigger,
        _pendingAccountabilityLimit
    ) { query, loading, challenge, trigger, pendingLimit ->
        LocalState(query, loading, challenge, trigger, pendingLimit)
    }

    init {
        viewModelScope.launch {
            permissionStatusRepository.status.collect {
                _refreshTrigger.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshPermissions() {
        permissionStatusRepository.refresh()
        _refreshTrigger.value = System.currentTimeMillis()
    }

    val uiState: StateFlow<TimeLimitUiState> = combine(
        getTimeLimitsUseCase.getInstalledApps(),
        getTimeLimitsUseCase.getAllTimeLimits(),
        localState,
        getTimeLimitsUseCase.getPendingChanges(),
        focusGuardRepository.challengeSettings
    ) { installedApps, limits, local, pendingChanges, settings ->
        val hasPermission = usageStatsReader.hasUsageAccessPermission()
        val limitMap = limits.associateBy { it.packageName }
        val usageMap = if (hasPermission) usageStatsReader.getTodayForegroundMinutesBatch() else emptyMap()

        val models = installedApps.map { app ->
            val limit = limitMap[app.packageName]
            val batchUsed = usageMap[app.packageName] ?: 0
            val liveMinutes = if (limit != null) getTimeLimitsUseCase.getTodayForegroundMinutes(app.packageName) else 0
            val usedToday = maxOf(batchUsed, liveMinutes)

            AppTimeLimitUiModel(
                appInfo = app,
                dailyLimitMinutes = limit?.dailyLimitMinutes ?: 30,
                usedMinutesToday = usedToday,
                isLimitConfigured = limit != null,
                isEnabled = limit?.isEnabled ?: true
            )
        }.sortedWith(
            compareByDescending<AppTimeLimitUiModel> { it.isLimitConfigured }
                .thenByDescending { it.usedMinutesToday }
                .thenBy { it.appInfo.appName.lowercase() }
        )

        val filtered = if (local.searchQuery.isBlank()) {
            models
        } else {
            models.filter {
                it.appInfo.appName.contains(local.searchQuery, ignoreCase = true) ||
                        it.appInfo.packageName.contains(local.searchQuery, ignoreCase = true)
            }
        }

        val limitPending = pendingChanges.filter {
            it.changeType == ChangeType.DISABLE_LIMIT || it.changeType == ChangeType.LOWER_LIMIT
        }

        TimeLimitUiState(
            apps = models,
            filteredApps = filtered,
            searchQuery = local.searchQuery,
            hasUsagePermission = hasPermission,
            isLoading = local.isLoading,
            pendingChanges = limitPending,
            activeChallenge = local.activeChallenge,
            isChallengeActive = settings.isChallengeActive,
            challengeType = settings.type,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            pendingAccountabilityLimit = local.pendingAccountability
        )
    }.flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TimeLimitUiState(
            isLoading = true,
            hasUsagePermission = usageStatsReader.hasUsageAccessPermission()
        )
    )

    fun onEvent(event: TimeLimitUiEvent) {
        when (event) {
            is TimeLimitUiEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is TimeLimitUiEvent.ToggleTimeLimit -> toggleTimeLimit(event.app, event.defaultMinutes, event.isEnabled)
            is TimeLimitUiEvent.SetTimeLimit -> setTimeLimit(event.app, event.newMinutes)
            is TimeLimitUiEvent.RefreshPermissions -> refreshPermissions()
            is TimeLimitUiEvent.OnActivateChallenge -> onActivateChallengeForLimit(event.type, event.sentence)
            is TimeLimitUiEvent.OnDismissAccountability -> onDismissAccountabilityRequired()
            is TimeLimitUiEvent.OnChallengeSuccess -> onChallengeSuccess(event.context)
            is TimeLimitUiEvent.OnCancelChallenge -> onCancelChallenge()
            is TimeLimitUiEvent.CancelPendingChange -> cancelPendingChange(event.id)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleTimeLimit(app: AppInfo, defaultMinutes: Int, isEnabled: Boolean) {
        if (isEnabled) {
            if (!uiState.value.isChallengeActive) {
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.LOWER_LIMIT,
                    newMinutes = defaultMinutes,
                    currentMinutes = 0
                )
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                setTimeLimitUseCase(
                    packageName = app.packageName,
                    appName = app.appName,
                    dailyLimitMinutes = defaultMinutes,
                    isEnabled = true
                )
            }
        } else {
            if (uiState.value.isChallengeActive) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_LIMIT,
                    targetId = app.packageName,
                    targetName = app.appName,
                    description = "remove daily limit for ${app.appName}"
                )
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    removeTimeLimitUseCase(app.packageName)
                }
            }
        }
    }

    fun setTimeLimit(app: AppInfo, newMinutes: Int) {
        val currentModel = uiState.value.apps.find { it.appInfo.packageName == app.packageName }
        val currentMinutes = currentModel?.dailyLimitMinutes ?: Int.MAX_VALUE

        if (currentModel?.isLimitConfigured == true && newMinutes > currentMinutes) {
            if (uiState.value.isChallengeActive) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.LOWER_LIMIT,
                    targetId = app.packageName,
                    targetName = app.appName,
                    targetPayload = newMinutes.toString(),
                    description = "increase daily limit for ${app.appName} from ${currentMinutes}m to ${newMinutes}m"
                )
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    setTimeLimitUseCase(app.packageName, app.appName, newMinutes, true)
                }
            }
        } else {
            if (currentModel?.isLimitConfigured != true && !uiState.value.isChallengeActive) {
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.LOWER_LIMIT,
                    newMinutes = newMinutes,
                    currentMinutes = 0
                )
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                setTimeLimitUseCase(app.packageName, app.appName, newMinutes, true)
            }
        }
    }

    fun onActivateChallengeForLimit(newType: ChallengeType, sentence: String) {
        val pending = _pendingAccountabilityLimit.value ?: return
        viewModelScope.launch {
            activateTimeLimitChallengeUseCase(newType, sentence)
            _pendingAccountabilityLimit.value = null
            if (pending.currentMinutes == 0) {
                val limit = pending.newMinutes ?: 30
                setTimeLimitUseCase(pending.app.packageName, pending.app.appName, limit, true)
            } else if (pending.actionType == ChangeType.DISABLE_LIMIT) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_LIMIT,
                    targetId = pending.app.packageName,
                    targetName = pending.app.appName,
                    description = "remove daily limit for ${pending.app.appName}"
                )
            } else {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.LOWER_LIMIT,
                    targetId = pending.app.packageName,
                    targetName = pending.app.appName,
                    targetPayload = pending.newMinutes.toString(),
                    description = "increase daily limit for ${pending.app.appName} to ${pending.newMinutes}m"
                )
            }
        }
    }

    fun onDismissAccountabilityRequired() {
        _pendingAccountabilityLimit.value = null
    }

    fun onChallengeSuccess(context: Context) {
        val challenge = _activeChallenge.value ?: return
        viewModelScope.launch {
            val pending = focusGuardRepository.createPendingChange(
                changeType = challenge.changeType,
                targetId = challenge.targetId,
                targetName = challenge.targetName,
                targetPayload = challenge.targetPayload
            )
            focusGuardRepository.applyPendingChange(pending)
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun cancelPendingChange(id: Long) {
        viewModelScope.launch {
            timeLimitRepository.cancelPendingChange(id)
        }
    }
}
