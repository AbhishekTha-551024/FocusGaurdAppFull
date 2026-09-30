package com.focusguard.app.ui.timelimits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.AppTimeLimitEntity
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.db.PendingChangeEntity
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.ui.blocker.ActiveChallengeState
import com.focusguard.app.usage.TimeLimitEvaluator
import com.focusguard.app.usage.UsageStatsReader
import com.focusguard.app.worker.PendingChangeApplyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppTimeLimitUiModel(
    val appInfo: AppInfo,
    val dailyLimitMinutes: Int = 30,
    val usedMinutesToday: Int = 0,
    val isLimitConfigured: Boolean = false,
    val isEnabled: Boolean = true
)

data class PendingAccountabilityLimit(
    val app: AppInfo,
    val actionType: ChangeType,
    val newMinutes: Int? = null,
    val currentMinutes: Int = 0
)

data class TimeLimitUiState(
    val apps: List<AppTimeLimitUiModel> = emptyList(),
    val filteredApps: List<AppTimeLimitUiModel> = emptyList(),
    val searchQuery: String = "",
    val hasUsagePermission: Boolean = false,
    val isLoading: Boolean = false,
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilityLimit: PendingAccountabilityLimit? = null
)

@HiltViewModel
class TimeLimitViewModel @Inject constructor(
    private val repository: FocusGuardRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val usageStatsReader: UsageStatsReader,
    private val timeLimitEvaluator: TimeLimitEvaluator,
    private val permissionStatusRepository: com.focusguard.app.data.repository.PermissionStatusRepository
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
        getInstalledAppsUseCase.execute(),
        repository.allTimeLimits,
        localState,
        repository.pendingChanges,
        repository.challengeSettings
    ) { installedApps, limits, local, pendingChanges, settings ->
        val hasPermission = usageStatsReader.hasUsageAccessPermission()
        val limitMap = limits.associateBy { it.packageName }
        val usageMap = if (hasPermission) usageStatsReader.getTodayForegroundMinutesBatch() else emptyMap()

        val models = installedApps.map { app ->
            val limit = limitMap[app.packageName]
            val batchUsed = usageMap[app.packageName] ?: 0
            val liveMinutes = if (limit != null) timeLimitEvaluator.getTodayForegroundMinutes(app.packageName) else 0
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
            // isLoading = true so skeleton shows immediately on screen open, but hasUsagePermission is pre-populated
            initialValue = TimeLimitUiState(
                isLoading = true,
                hasUsagePermission = usageStatsReader.hasUsageAccessPermission()
            )
        )


    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleTimeLimit(app: AppInfo, defaultMinutes: Int, isEnabled: Boolean) {
        if (isEnabled) {
            // Enabling limit is instant
            viewModelScope.launch(Dispatchers.IO) {
                repository.setTimeLimit(
                    packageName = app.packageName,
                    appName = app.appName,
                    dailyLimitMinutes = defaultMinutes,
                    isEnabled = true
                )
            }
        } else {
            if (uiState.value.isChallengeActive) {
                // Disabling limit requires challenge!
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_LIMIT,
                    targetId = app.packageName,
                    targetName = app.appName,
                    description = "remove daily limit for ${app.appName}"
                )
            } else {
                // Invariant Rule 6: Accountability Challenge is required! Prompt user to activate it.
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.DISABLE_LIMIT
                )
            }
        }
    }

    fun setTimeLimit(app: AppInfo, newMinutes: Int) {
        val currentModel = uiState.value.apps.find { it.appInfo.packageName == app.packageName }
        val currentMinutes = currentModel?.dailyLimitMinutes ?: Int.MAX_VALUE

        if (currentModel?.isLimitConfigured == true && newMinutes > currentMinutes) {
            if (uiState.value.isChallengeActive) {
                // INCREASING the limit (more lenient / giving extra screen time) → requires challenge!
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.LOWER_LIMIT, // Represents time limit modification
                    targetId = app.packageName,
                    targetName = app.appName,
                    targetPayload = newMinutes.toString(),
                    description = "increase daily limit for ${app.appName} from ${currentMinutes}m to ${newMinutes}m"
                )
            } else {
                // Invariant Rule 6: Accountability Challenge is required! Prompt user to activate it.
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.LOWER_LIMIT,
                    newMinutes = newMinutes,
                    currentMinutes = currentMinutes
                )
            }
        } else {
            // LOWERING the limit (more restrictive) or initial setup → instant, user wants to focus!
            viewModelScope.launch(Dispatchers.IO) {
                repository.setTimeLimit(
                    packageName = app.packageName,
                    appName = app.appName,
                    dailyLimitMinutes = newMinutes,
                    isEnabled = true
                )
            }
        }
    }

    fun onActivateChallengeForLimit() {
        val pending = _pendingAccountabilityLimit.value ?: return
        viewModelScope.launch {
            repository.setChallengeActive(true)
            _pendingAccountabilityLimit.value = null
            if (pending.actionType == ChangeType.DISABLE_LIMIT) {
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
            val pending = repository.createPendingChange(
                changeType = challenge.changeType,
                targetId = challenge.targetId,
                targetName = challenge.targetName,
                targetPayload = challenge.targetPayload
            )
            repository.applyPendingChange(pending)
            when (challenge.changeType) {
                ChangeType.DISABLE_LIMIT -> repository.removeTimeLimit(challenge.targetId)
                ChangeType.LOWER_LIMIT -> {
                    val limit = challenge.targetPayload?.toIntOrNull() ?: 60
                    repository.setTimeLimit(challenge.targetId, challenge.targetName, limit, true)
                }
                else -> {}
            }
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun cancelPendingChange(id: Long) {
        viewModelScope.launch {
            repository.cancelPendingChange(id)
        }
    }
}
