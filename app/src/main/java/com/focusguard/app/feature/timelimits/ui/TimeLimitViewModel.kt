package com.focusguard.app.feature.timelimits.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.core.model.scheduledApplyTimestamp
import com.focusguard.app.core.common.result.AppResult
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PermissionStatusRepository
import com.focusguard.app.feature.timelimits.domain.repository.TimeLimitRepository
import com.focusguard.app.feature.timelimits.domain.usecase.ActivateTimeLimitChallengeUseCase
import com.focusguard.app.feature.timelimits.domain.usecase.GetTimeLimitsUseCase
import com.focusguard.app.feature.timelimits.domain.usecase.SetTimeLimitUseCase
import com.focusguard.app.usage.UsageStatsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Combines Limits state and routes protected mutations through accountability. */
@HiltViewModel
class TimeLimitViewModel @Inject constructor(
    private val getTimeLimitsUseCase: GetTimeLimitsUseCase,
    private val setTimeLimitUseCase: SetTimeLimitUseCase,
    private val activateTimeLimitChallengeUseCase: ActivateTimeLimitChallengeUseCase,
    private val timeLimitRepository: TimeLimitRepository,
    private val focusGuardRepository: FocusGuardRepository,
    private val usageStatsReader: UsageStatsReader,
    private val permissionStatusRepository: PermissionStatusRepository,
    private val uiStateFactory: TimeLimitUiStateFactory
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _refreshTrigger = MutableStateFlow(0L)
    private val _pendingAccountabilityLimit = MutableStateFlow<PendingAccountabilityLimit?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private data class LocalState(
        val searchQuery: String,
        val isLoading: Boolean,
        val activeChallenge: ActiveChallengeState?,
        val refreshTrigger: Long,
        val pendingAccountability: PendingAccountabilityLimit?,
        val errorMessage: String? = null
    )

    private val baseLocalState = combine(
        _searchQuery,
        _isLoading,
        _activeChallenge,
        _refreshTrigger,
        _pendingAccountabilityLimit
    ) { query, loading, challenge, trigger, pendingLimit ->
        LocalState(query, loading, challenge, trigger, pendingLimit)
    }
    private val localState = combine(baseLocalState, _errorMessage) { state, error ->
        state.copy(errorMessage = error)
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
        uiStateFactory.create(
            installedApps = installedApps,
            limits = limits,
            query = local.searchQuery,
            isLoading = local.isLoading,
            activeChallenge = local.activeChallenge,
            pendingAccountabilityLimit = local.pendingAccountability,
            pendingChanges = pendingChanges,
            settings = settings,
            errorMessage = local.errorMessage
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
                recordOperationResult(setTimeLimitUseCase(
                    packageName = app.packageName,
                    appName = app.appName,
                    dailyLimitMinutes = defaultMinutes,
                    isEnabled = true
                ))
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
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.DISABLE_LIMIT,
                    currentMinutes = uiState.value.apps
                        .find { it.appInfo.packageName == app.packageName }
                        ?.dailyLimitMinutes
                        ?: 0
                )
            }
        }
    }

    fun setTimeLimit(app: AppInfo, newMinutes: Int) {
        val currentModel = uiState.value.apps.find { it.appInfo.packageName == app.packageName }
        val currentMinutes = currentModel?.dailyLimitMinutes ?: Int.MAX_VALUE

        if (currentModel?.isLimitConfigured == true) {
            if (uiState.value.isChallengeActive) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.LOWER_LIMIT,
                    targetId = app.packageName,
                    targetName = app.appName,
                    targetPayload = newMinutes.toString(),
                    description = "change daily limit for ${app.appName} from ${currentMinutes}m to ${newMinutes}m"
                )
            } else {
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.LOWER_LIMIT,
                    newMinutes = newMinutes,
                    currentMinutes = currentMinutes
                )
            }
        } else {
            if (!uiState.value.isChallengeActive) {
                _pendingAccountabilityLimit.value = PendingAccountabilityLimit(
                    app = app,
                    actionType = ChangeType.LOWER_LIMIT,
                    newMinutes = newMinutes,
                    currentMinutes = 0
                )
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                recordOperationResult(setTimeLimitUseCase(app.packageName, app.appName, newMinutes, true))
            }
        }
    }

    fun onActivateChallengeForLimit(newType: ChallengeType, sentence: String) {
        val pending = _pendingAccountabilityLimit.value ?: return
        viewModelScope.launch {
            val activation = activateTimeLimitChallengeUseCase(newType, sentence)
            recordOperationResult(activation)
            when (activation) {
                is AppResult.Failure -> return@launch
                is AppResult.Success -> Unit
            }
            _pendingAccountabilityLimit.value = null
            if (pending.currentMinutes == 0) {
                val limit = pending.newMinutes ?: 30
                recordOperationResult(setTimeLimitUseCase(pending.app.packageName, pending.app.appName, limit, true))
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
                    description = "change daily limit for ${pending.app.appName} to ${pending.newMinutes}m"
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
            try {
                focusGuardRepository.createPendingChange(
                    changeType = challenge.changeType,
                    targetId = challenge.targetId,
                    targetName = challenge.targetName,
                    targetPayload = challenge.targetPayload,
                    scheduledApplyAt = scheduledApplyTimestamp(
                        System.currentTimeMillis(),
                        uiState.value.timeDelayHours
                    )
                )
                _activeChallenge.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Could not queue the limit change."
            }
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun cancelPendingChange(id: Long) {
        viewModelScope.launch {
            recordOperationResult(timeLimitRepository.cancelPendingChange(id))
        }
    }

    private fun recordOperationResult(result: AppResult<*>) {
        _errorMessage.value = result.errorOrNull()?.message
    }
}
