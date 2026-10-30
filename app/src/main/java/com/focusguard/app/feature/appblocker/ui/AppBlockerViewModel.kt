package com.focusguard.app.feature.appblocker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.feature.appblocker.domain.repository.AppBlockerRepository
import com.focusguard.app.feature.appblocker.domain.usecase.ActivateAppChallengeUseCase
import com.focusguard.app.feature.appblocker.domain.usecase.GetBlockedAppsUseCase
import com.focusguard.app.feature.appblocker.domain.usecase.ToggleAppBlockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppBlockerViewModel @Inject constructor(
    private val getBlockedAppsUseCase: GetBlockedAppsUseCase,
    private val toggleAppBlockUseCase: ToggleAppBlockUseCase,
    private val activateAppChallengeUseCase: ActivateAppChallengeUseCase,
    private val appBlockerRepository: AppBlockerRepository,
    private val focusGuardRepository: FocusGuardRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilityApp = MutableStateFlow<AppInfo?>(null)

    private val localBlockerState = combine(
        _searchQuery,
        _activeChallenge,
        _pendingAccountabilityApp
    ) { query, challenge, pendingApp ->
        LocalBlockerState(query, challenge, pendingApp)
    }

    private data class LocalBlockerState(
        val searchQuery: String,
        val activeChallenge: ActiveChallengeState?,
        val pendingAccountabilityApp: AppInfo?
    )

    val uiState: StateFlow<AppBlockerUiState> = combine(
        getBlockedAppsUseCase(),
        localBlockerState,
        appBlockerRepository.getPendingAppChanges(),
        focusGuardRepository.challengeSettings
    ) { apps, local, pendingChanges, settings ->
        val query = local.searchQuery
        val filtered = if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        val blocked = filtered.filter { it.isBlocked && !it.isAutoBlockedUnsupportedBrowser }
        val pending = filtered.filter { it.isAutoBlockedUnsupportedBrowser }
        val unblocked = filtered.filter { !it.isBlocked }

        AppBlockerUiState(
            isLoading = false,
            searchQuery = query,
            blockedApps = blocked,
            pendingReviewApps = pending,
            unblockedApps = unblocked,
            pendingChanges = pendingChanges,
            activeChallenge = local.activeChallenge,
            isChallengeActive = settings.isChallengeActive,
            challengeType = settings.type,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            pendingAccountabilityApp = local.pendingAccountabilityApp
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppBlockerUiState(isLoading = true)
    )

    fun onEvent(event: AppBlockerUiEvent) {
        when (event) {
            is AppBlockerUiEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is AppBlockerUiEvent.ToggleAppBlock -> toggleAppBlock(event.app, event.shouldBlock)
            is AppBlockerUiEvent.OnActivateChallenge -> onActivateChallengeForApp(event.type, event.sentence)
            is AppBlockerUiEvent.OnDismissAccountability -> onDismissAccountabilityRequired()
            is AppBlockerUiEvent.OnChallengeSuccess -> onChallengeSuccess(event.context)
            is AppBlockerUiEvent.OnCancelChallenge -> onCancelChallenge()
            is AppBlockerUiEvent.OnCancelPendingChange -> cancelPendingChange(event.id)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppBlock(app: AppInfo, shouldBlock: Boolean) {
        viewModelScope.launch {
            if (shouldBlock) {
                if (!uiState.value.isChallengeActive) {
                    _pendingAccountabilityApp.value = app
                    return@launch
                }
                toggleAppBlockUseCase.block(app)
            } else {
                if (uiState.value.isChallengeActive) {
                    _activeChallenge.value = ActiveChallengeState(
                        changeType = ChangeType.UNBLOCK_APP,
                        targetId = app.packageName,
                        targetName = app.appName,
                        description = "unblock ${app.appName}"
                    )
                } else {
                    toggleAppBlockUseCase.unblock(app.packageName)
                }
            }
        }
    }

    fun onActivateChallengeForApp(newType: ChallengeType, sentence: String) {
        val app = _pendingAccountabilityApp.value ?: return
        viewModelScope.launch {
            activateAppChallengeUseCase(
                packageName = app.packageName,
                appName = app.appName,
                type = newType,
                sentence = sentence
            )
            _pendingAccountabilityApp.value = null
        }
    }

    fun onDismissAccountabilityRequired() {
        _pendingAccountabilityApp.value = null
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
            appBlockerRepository.cancelPendingChange(id)
        }
    }
}
