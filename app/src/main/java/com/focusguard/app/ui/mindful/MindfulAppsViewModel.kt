package com.focusguard.app.ui.mindful

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.db.MindfulAppEntity
import com.focusguard.app.data.db.MindfulIntentLogEntity
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.MindfulRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.ui.blocker.ActiveChallengeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MindfulAppsUiState(
    val searchQuery: String = "",
    val mindfulApps: List<AppInfo> = emptyList(),
    val availableApps: List<AppInfo> = emptyList(),
    val appAllowanceMap: Map<String, Int> = emptyMap(),
    val walkedAwayCount: Int = 0,
    val consciousOpensCount: Int = 0,
    val recentLogs: List<MindfulIntentLogEntity> = emptyList(),
    val showJournalDialog: Boolean = false,
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val pendingAccountabilityApp: AppInfo? = null,
    val pendingConfirmAddApp: AppInfo? = null
)

@HiltViewModel
class MindfulAppsViewModel @Inject constructor(
    private val mindfulRepository: MindfulRepository,
    private val repository: FocusGuardRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
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

    private data class MindfulLocalState(
        val activeChallenge: ActiveChallengeState?,
        val pendingAccountabilityApp: AppInfo?,
        val pendingConfirmAddApp: AppInfo?
    )

    private val localStateFlow = combine(
        _activeChallenge,
        _pendingAccountabilityApp,
        _pendingConfirmAddApp
    ) { challenge, pendingApp, confirmAdd ->
        MindfulLocalState(challenge, pendingApp, confirmAdd)
    }

    private val appsFlow = combine(
        getInstalledAppsUseCase.execute(),
        mindfulRepository.allMindfulApps,
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
        mindfulRepository.getTodayWalkedAwayCount(),
        mindfulRepository.getTodayConsciousOpensCount(),
        mindfulRepository.recentIntentLogs,
        _showJournalDialog
    ) { walkedAway, consciousOpens, logs, showJournal ->
        StatsData(walkedAway, consciousOpens, logs, showJournal)
    }

    val uiState: StateFlow<MindfulAppsUiState> = combine(
        appsFlow,
        statsFlow,
        localStateFlow,
        repository.challengeSettings
    ) { (mindful, available, pkgMap), stats, local, challengeSettings ->
        MindfulAppsUiState(
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
        initialValue = MindfulAppsUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleMindfulApp(app: AppInfo, enable: Boolean) {
        requestToggleMindfulApp(app, enable)
    }

    fun requestToggleMindfulApp(app: AppInfo, enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                // Confirmation required before adding
                _pendingConfirmAddApp.value = app
            } else {
                // Removal friction: accountability challenge
                if (uiState.value.isChallengeActive) {
                    _activeChallenge.value = ActiveChallengeState(
                        changeType = ChangeType.REMOVE_MINDFUL_APP,
                        targetId = app.packageName,
                        targetName = app.appName,
                        description = "remove ${app.appName} from Mindful Tracking"
                    )
                } else {
                    _pendingAccountabilityApp.value = app
                }
            }
        }
    }

    fun confirmAddMindfulApp(app: AppInfo) {
        viewModelScope.launch {
            mindfulRepository.addMindfulApp(app.packageName, app.appName, 5)
            _pendingConfirmAddApp.value = null
        }
    }

    fun dismissAddMindfulApp() {
        _pendingConfirmAddApp.value = null
    }

    fun onActivateChallengeForApp() {
        val app = _pendingAccountabilityApp.value ?: return
        viewModelScope.launch {
            repository.setChallengeActive(true)
            _pendingAccountabilityApp.value = null
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.REMOVE_MINDFUL_APP,
                targetId = app.packageName,
                targetName = app.appName,
                description = "remove ${app.appName} from Mindful Tracking"
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
            if (challenge.changeType == ChangeType.REMOVE_MINDFUL_APP) {
                mindfulRepository.removeMindfulApp(challenge.targetId)
            }
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }

    fun updateDefaultAllowance(packageName: String, minutes: Int) {
        viewModelScope.launch {
            mindfulRepository.updateAllowanceMinutes(packageName, minutes)
        }
    }

    fun openJournalDialog() {
        _showJournalDialog.value = true
    }

    fun dismissJournalDialog() {
        _showJournalDialog.value = false
    }
}
