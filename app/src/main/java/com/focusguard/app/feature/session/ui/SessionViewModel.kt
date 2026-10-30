package com.focusguard.app.feature.session.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.blockers.SessionBlockManager
import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.session.domain.usecase.GetSessionStateUseCase
import com.focusguard.app.feature.session.domain.usecase.StartSessionUseCase
import com.focusguard.app.feature.session.domain.usecase.UpdateSuitcaseUseCase
import com.focusguard.app.service.FocusSessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getSessionStateUseCase: GetSessionStateUseCase,
    private val startSessionUseCase: StartSessionUseCase,
    private val updateSuitcaseUseCase: UpdateSuitcaseUseCase,
    private val sessionBlockManager: SessionBlockManager,
    private val preferences: FocusGuardPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDuration = MutableStateFlow(preferences.syncSessionTotalMinutes)
    private val _showPackingDialog = MutableStateFlow(false)
    private val _showAbortDialog = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _draftSuitcase = MutableStateFlow<Set<String>>(preferences.syncSuitcasePackages)

    val uiState: StateFlow<SessionUiState> = combine(
        sessionBlockManager.sessionStateFlow,
        getSessionStateUseCase.getInstalledApps(),
        _searchQuery,
        _selectedDuration,
        _draftSuitcase,
        _showPackingDialog,
        _showAbortDialog,
        _errorMessage
    ) { args: Array<Any?> ->
        val sessionState = args[0] as SessionBlockManager.SessionUiState
        @Suppress("UNCHECKED_CAST")
        val apps = args[1] as List<AppInfo>
        val query = args[2] as String
        val duration = args[3] as Int
        @Suppress("UNCHECKED_CAST")
        val draftSuitcase = args[4] as Set<String>
        val showPacking = args[5] as Boolean
        val showAbort = args[6] as Boolean
        val err = args[7] as String?

        val filteredApps = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }

        val effectivePacked = if (sessionState.isActive) sessionState.packedPackages else draftSuitcase

        SessionUiState(
            isActive = sessionState.isActive,
            endTimeMillis = sessionState.endTimeMillis,
            totalMinutes = if (sessionState.isActive) sessionState.totalMinutes else duration,
            remainingMillis = sessionState.remainingMillis,
            selectedDurationMinutes = duration,
            packedPackages = effectivePacked,
            availableApps = filteredApps,
            searchQuery = query,
            parachutesCount = sessionState.parachutesCount,
            totalSessionsCompleted = sessionState.totalSessionsCompleted,
            totalMinutesCompleted = sessionState.totalMinutesCompleted,
            showPackingDialog = showPacking,
            showAbortDialog = showAbort,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SessionUiState()
    )

    fun onEvent(event: SessionUiEvent) {
        when (event) {
            is SessionUiEvent.SetDuration -> setDuration(event.minutes)
            is SessionUiEvent.SetSearchQuery -> setSearchQuery(event.query)
            is SessionUiEvent.TogglePackageInSuitcase -> togglePackageInSuitcase(event.packageName)
            is SessionUiEvent.QuickPackUtilities -> quickPackUtilities(event.apps)
            is SessionUiEvent.StartSession -> startSession()
            is SessionUiEvent.OpenPackingDialog -> openPackingDialog()
            is SessionUiEvent.ClosePackingDialog -> closePackingDialog()
            is SessionUiEvent.OpenAbortDialog -> openAbortDialog()
            is SessionUiEvent.CloseAbortDialog -> closeAbortDialog()
            is SessionUiEvent.ConfirmEmergencyAbort -> confirmEmergencyAbort()
        }
    }

    fun setDuration(minutes: Int) {
        _selectedDuration.value = minutes
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePackageInSuitcase(packageName: String) {
        if (sessionBlockManager.isSessionCurrentlyActive()) {
            _errorMessage.value = "Suitcase cannot be modified while Deep Focus is active."
            return
        }
        if (sessionBlockManager.isTamperOrSettingsPackage(packageName)) {
            _errorMessage.value = "Settings and app stores cannot be packed into Deep Focus."
            return
        }
        val current = _draftSuitcase.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            if (current.size >= 3) {
                _errorMessage.value = "Maximum 3 allowed tools packed! Uncheck an app to replace it."
                return
            }
            current.add(packageName)
        }
        _draftSuitcase.value = current
        viewModelScope.launch {
            updateSuitcaseUseCase(current)
        }
    }

    fun quickPackUtilities(apps: List<AppInfo>) {
        if (sessionBlockManager.isSessionCurrentlyActive()) {
            _errorMessage.value = "Suitcase cannot be modified while Deep Focus is active."
            return
        }
        val utilityPackages = mutableSetOf<String>()
        apps.forEach { app ->
            if (utilityPackages.size >= 3) return@forEach
            val lower = app.packageName.lowercase()
            val labelLower = app.appName.lowercase()
            if (sessionBlockManager.isTamperOrSettingsPackage(lower)) return@forEach
            if (lower.contains("calc") || labelLower.contains("calculator") ||
                lower.contains("calendar") || labelLower.contains("calendar") ||
                lower.contains("note") || labelLower.contains("notes") ||
                lower.contains("maps") || labelLower.contains("map")
            ) {
                utilityPackages.add(app.packageName)
            }
        }
        val current = utilityPackages.take(3).toSet()
        _draftSuitcase.value = current
        viewModelScope.launch {
            updateSuitcaseUseCase(current)
        }
    }

    fun startSession() {
        val duration = _selectedDuration.value
        val suitcase = _draftSuitcase.value
        startSessionUseCase(duration, suitcase)
        FocusSessionService.start(context)
        SessionLockActivity.start(context)
    }

    fun openPackingDialog() {
        if (sessionBlockManager.isSessionCurrentlyActive()) {
            _errorMessage.value = "Suitcase cannot be modified while Deep Focus is active."
            return
        }
        _showPackingDialog.value = true
    }

    fun closePackingDialog() {
        _showPackingDialog.value = false
    }

    fun openAbortDialog() {}

    fun closeAbortDialog() {
        _showAbortDialog.value = false
    }

    fun confirmEmergencyAbort() {}
}
