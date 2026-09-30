package com.focusguard.app.ui.blocker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.db.PendingChangeEntity
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.worker.PendingChangeApplyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveChallengeState(
    val changeType: ChangeType,
    val targetId: String,
    val targetName: String,
    val targetPayload: String? = null,
    val description: String
)

data class AppBlockerUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val blockedApps: List<AppInfo> = emptyList(),
    val pendingReviewApps: List<AppInfo> = emptyList(),
    val unblockedApps: List<AppInfo> = emptyList(),
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilityApp: AppInfo? = null
)

@HiltViewModel
class AppBlockerViewModel @Inject constructor(
    private val repository: FocusGuardRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilityApp = MutableStateFlow<AppInfo?>(null)

    private data class LocalBlockerState(
        val searchQuery: String,
        val activeChallenge: ActiveChallengeState?,
        val pendingAccountabilityApp: AppInfo?
    )

    private val localBlockerState = combine(
        _searchQuery,
        _activeChallenge,
        _pendingAccountabilityApp
    ) { query, challenge, pendingApp ->
        LocalBlockerState(query, challenge, pendingApp)
    }

    val uiState: StateFlow<AppBlockerUiState> = combine(
        getInstalledAppsUseCase.execute(),
        localBlockerState,
        repository.pendingChanges,
        repository.challengeSettings
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

        val appPending = pendingChanges.filter { it.changeType == ChangeType.UNBLOCK_APP }

        AppBlockerUiState(
            isLoading = false,
            searchQuery = query,
            blockedApps = blocked,
            pendingReviewApps = pending,
            unblockedApps = unblocked,
            pendingChanges = appPending,
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
        // isLoading = true so the skeleton shows immediately before Room emits the first list
        AppBlockerUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppBlock(app: AppInfo, shouldBlock: Boolean) {
        viewModelScope.launch {
            if (shouldBlock) {
                // Blocking is instant — no friction
                repository.blockApp(
                    packageName = app.packageName,
                    appName = app.appName
                )
            } else {
                if (uiState.value.isChallengeActive) {
                    // Unblocking requires completing removal challenge!
                    _activeChallenge.value = ActiveChallengeState(
                        changeType = ChangeType.UNBLOCK_APP,
                        targetId = app.packageName,
                        targetName = app.appName,
                        description = "unblock ${app.appName}"
                    )
                } else {
                    // Invariant Rule 6: Accountability Challenge is required! Prompt user to activate it.
                    _pendingAccountabilityApp.value = app
                }
            }
        }
    }

    fun onActivateChallengeForApp() {
        val app = _pendingAccountabilityApp.value ?: return
        viewModelScope.launch {
            repository.setChallengeActive(true)
            _pendingAccountabilityApp.value = null
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.UNBLOCK_APP,
                targetId = app.packageName,
                targetName = app.appName,
                description = "unblock ${app.appName}"
            )
        }
    }

    fun onDismissAccountabilityRequired() {
        _pendingAccountabilityApp.value = null
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
            if (challenge.changeType == ChangeType.UNBLOCK_APP) {
                repository.unblockApp(challenge.targetId)
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
