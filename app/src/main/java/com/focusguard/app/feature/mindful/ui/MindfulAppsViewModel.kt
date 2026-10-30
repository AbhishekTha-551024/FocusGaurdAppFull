package com.focusguard.app.feature.mindful.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.MindfulIntentLogEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.feature.mindful.domain.repository.MindfulAppsRepository
import com.focusguard.app.feature.mindful.domain.usecase.ActivateMindfulChallengeUseCase
import com.focusguard.app.feature.mindful.domain.usecase.GetMindfulAppsUseCase
import com.focusguard.app.feature.mindful.domain.usecase.ToggleMindfulAppUseCase
import com.focusguard.app.feature.mindful.domain.usecase.UpdateMindfulAllowanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MindfulAppsViewModel @Inject constructor(
    private val getMindfulAppsUseCase: GetMindfulAppsUseCase,
    private val toggleMindfulAppUseCase: ToggleMindfulAppUseCase,
    private val updateMindfulAllowanceUseCase: UpdateMindfulAllowanceUseCase,
    private val activateMindfulChallengeUseCase: ActivateMindfulChallengeUseCase,
    private val mindfulAppsRepository: MindfulAppsRepository,
    private val focusGuardRepository: FocusGuardRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _showJournalDialog = MutableStateFlow(false)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilityApp = MutableStateFlow<AppInfo?>(null)
    private val _pendingConfirmAddApp = MutableStateFlow<AppInfo?>(null)

    private data class StatsData(
        val walkedAway: Int,
        val consciousOpens: Int,
        val logs: List<MindfulIntentLogEntity>,
        val showJournal: Boolean
    )

    private data class LocalMindfulState(
        val activeChallenge: ActiveChallengeState?,
        val pendingAccountabilityApp: AppInfo?,
        val pendingConfirmAddApp: AppInfo?
    )

    private val localStateFlow = combine(
        _activeChallenge,
        _pendingAccountabilityApp,
        _pendingConfirmAddApp
    ) { challenge, pendingApp, confirmAdd ->
        LocalMindfulState(challenge, pendingApp, confirmAdd)
    }

    private val appsFlow = combine(
        getMindfulAppsUseCase.getInstalledApps(),
        getMindfulAppsUseCase.getMindfulApps(),
        _searchQuery
    ) { installedApps, mindfulEntities, query ->
        val mindfulPkgMap = mindfulEntities.associate { it.packageName to it.defaultAllowanceMinutes }

        val filteredApps = if (query.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        val mindful = filteredApps.filter { mindfulPkgMap.containsKey(it.packageName) }
            .sortedBy { it.appName.lowercase() }

        val available = filteredApps.filter { !mindfulPkgMap.containsKey(it.packageName) }
            .sortedBy { it.appName.lowercase() }

        Triple(mindful, available, mindfulPkgMap)
    }

    private val statsFlow = combine(
        getMindfulAppsUseCase.getTodayWalkedAwayCount(),
        getMindfulAppsUseCase.getTodayConsciousOpensCount(),
        getMindfulAppsUseCase.getRecentIntentLogs(),
        _showJournalDialog
    ) { walkedAway, consciousOpens, logs, showJournal ->
        StatsData(walkedAway, consciousOpens, logs, showJournal)
    }

    val uiState: StateFlow<MindfulAppsUiState> = combine(
        appsFlow,
        statsFlow,
        localStateFlow,
        focusGuardRepository.challengeSettings
    ) { (mindful, available, pkgMap), stats, local, challengeSettings ->
        MindfulAppsUiState(
            isLoading = false,
            searchQuery = _searchQuery.value,
            mindfulApps = mindful,
            availableApps = available,
            appAllowanceMap = pkgMap,
            walkedAwayCount = stats.walkedAway,
            consciousOpensCount = stats.consciousOpens,
            recentLogs = stats.logs,
            showJournalDialog = stats.showJournal,
            activeChallenge = local.activeChallenge,
            isChallengeActive = challengeSettings.isChallengeActive,
            challengeType = challengeSettings.type,
            commitmentSentence = challengeSettings.commitmentSentence,
            pendingAccountabilityApp = local.pendingAccountabilityApp,
            pendingConfirmAddApp = local.pendingConfirmAddApp
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MindfulAppsUiState(isLoading = true)
    )

    fun onEvent(event: MindfulAppsUiEvent) {
        when (event) {
            is MindfulAppsUiEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is MindfulAppsUiEvent.ToggleMindfulApp -> toggleMindfulApp(event.app, event.enable)
            is MindfulAppsUiEvent.ConfirmAddMindfulApp -> confirmAddMindfulApp(event.app)
            is MindfulAppsUiEvent.DismissAddMindfulApp -> dismissAddMindfulApp()
            is MindfulAppsUiEvent.OnActivateChallenge -> onActivateChallengeForApp(event.type, event.sentence)
            is MindfulAppsUiEvent.OnDismissAccountability -> onDismissAccountabilityRequired()
            is MindfulAppsUiEvent.OnChallengeSuccess -> onChallengeSuccess(event.context)
            is MindfulAppsUiEvent.OnCancelChallenge -> onCancelChallenge()
            is MindfulAppsUiEvent.UpdateDefaultAllowance -> updateDefaultAllowance(event.packageName, event.minutes)
            is MindfulAppsUiEvent.OpenJournalDialog -> openJournalDialog()
            is MindfulAppsUiEvent.DismissJournalDialog -> dismissJournalDialog()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleMindfulApp(app: AppInfo, enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                if (!uiState.value.isChallengeActive) {
                    _pendingAccountabilityApp.value = app
                    return@launch
                }
                _pendingConfirmAddApp.value = app
            } else {
                if (uiState.value.isChallengeActive) {
                    _activeChallenge.value = ActiveChallengeState(
                        changeType = ChangeType.REMOVE_MINDFUL_APP,
                        targetId = app.packageName,
                        targetName = app.appName,
                        description = "remove ${app.appName} from Mindful Tracking"
                    )
                } else {
                    toggleMindfulAppUseCase.remove(app.packageName)
                }
            }
        }
    }

    fun confirmAddMindfulApp(app: AppInfo) {
        viewModelScope.launch {
            toggleMindfulAppUseCase.add(app, 5)
            _pendingConfirmAddApp.value = null
        }
    }

    fun dismissAddMindfulApp() {
        _pendingConfirmAddApp.value = null
    }

    fun onActivateChallengeForApp(newType: ChallengeType, sentence: String) {
        val app = _pendingAccountabilityApp.value ?: return
        viewModelScope.launch {
            activateMindfulChallengeUseCase(
                packageName = app.packageName,
                appName = app.appName,
                type = newType,
                sentence = sentence
            )
            _pendingAccountabilityApp.value = null
            _pendingConfirmAddApp.value = app
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
            if (challenge.changeType == ChangeType.REMOVE_MINDFUL_APP) {
                toggleMindfulAppUseCase.remove(challenge.targetId)
            }
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun updateDefaultAllowance(packageName: String, minutes: Int) {
        val currentMinutes = uiState.value.appAllowanceMap[packageName] ?: 5
        val appName = uiState.value.mindfulApps.find { it.packageName == packageName }?.appName ?: packageName

        if (minutes > currentMinutes && uiState.value.isChallengeActive) {
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.INCREASE_MINDFUL_ALLOWANCE,
                targetId = packageName,
                targetName = appName,
                targetPayload = minutes.toString(),
                description = "increase mindful allowance for $appName from ${currentMinutes}m to ${minutes}m"
            )
        } else {
            viewModelScope.launch {
                updateMindfulAllowanceUseCase(packageName, minutes)
            }
        }
    }

    fun openJournalDialog() {
        _showJournalDialog.value = true
    }

    fun dismissJournalDialog() {
        _showJournalDialog.value = false
    }
}
