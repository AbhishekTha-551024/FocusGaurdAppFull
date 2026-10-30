package com.focusguard.app.feature.schedules.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.feature.schedules.domain.repository.ScheduleRepository
import com.focusguard.app.feature.schedules.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val saveScheduleUseCase: SaveScheduleUseCase,
    private val deleteScheduleUseCase: DeleteScheduleUseCase,
    private val toggleScheduleUseCase: ToggleScheduleUseCase,
    private val activateScheduleChallengeUseCase: ActivateScheduleChallengeUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val focusGuardRepository: FocusGuardRepository
) : ViewModel() {

    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilitySchedule = MutableStateFlow<PendingAccountabilitySchedule?>(null)

    private data class DialogChallengeState(
        val isAddDialogOpen: Boolean,
        val activeChallenge: ActiveChallengeState?,
        val pendingAccountability: PendingAccountabilitySchedule?
    )

    private val dialogChallengeState = combine(
        _isAddDialogOpen,
        _activeChallenge,
        _pendingAccountabilitySchedule
    ) { isAddOpen, challenge, pendingSched ->
        DialogChallengeState(isAddOpen, challenge, pendingSched)
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        getSchedulesUseCase.getAllSchedules(),
        getSchedulesUseCase.getInstalledApps(),
        dialogChallengeState,
        getSchedulesUseCase.getPendingChanges(),
        focusGuardRepository.challengeSettings
    ) { schedules, apps, dialog, pendingChanges, settings ->
        val schedulePending = pendingChanges.filter {
            it.changeType == ChangeType.DISABLE_SCHEDULE || it.changeType == ChangeType.DELETE_SCHEDULE
        }

        ScheduleUiState(
            schedules = schedules,
            installedApps = apps,
            isAddDialogOpen = dialog.isAddDialogOpen,
            isLoading = false,
            pendingChanges = schedulePending,
            activeChallenge = dialog.activeChallenge,
            isChallengeActive = settings.isChallengeActive,
            challengeType = settings.type,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            pendingAccountabilitySchedule = dialog.pendingAccountability
        )
    }.flowOn(Dispatchers.IO)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(isLoading = true)
    )

    fun onEvent(event: ScheduleUiEvent) {
        when (event) {
            is ScheduleUiEvent.OpenAddDialog -> openAddDialog()
            is ScheduleUiEvent.CloseAddDialog -> closeAddDialog()
            is ScheduleUiEvent.AddSchedule -> addSchedule(
                event.title, event.packageName, event.appName,
                event.daysOfWeekMask, event.startMinute, event.endMinute
            )
            is ScheduleUiEvent.DeleteSchedule -> deleteSchedule(event.id, event.title)
            is ScheduleUiEvent.ToggleSchedule -> toggleSchedule(event.schedule, event.isEnabled)
            is ScheduleUiEvent.OnActivateChallenge -> onActivateChallengeForSchedule(event.type, event.sentence)
            is ScheduleUiEvent.OnDismissAccountability -> onDismissAccountabilityRequired()
            is ScheduleUiEvent.OnChallengeSuccess -> onChallengeSuccess(event.context)
            is ScheduleUiEvent.OnCancelChallenge -> onCancelChallenge()
            is ScheduleUiEvent.CancelPendingChange -> cancelPendingChange(event.id)
        }
    }

    fun openAddDialog() {
        _isAddDialogOpen.value = true
    }

    fun closeAddDialog() {
        _isAddDialogOpen.value = false
    }

    fun addSchedule(
        title: String,
        packageName: String?,
        appName: String?,
        daysOfWeekMask: Int,
        startMinute: Int,
        endMinute: Int
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return

        val entity = ScheduleEntity(
            title = cleanTitle,
            packageName = packageName,
            appName = appName,
            daysOfWeekMask = daysOfWeekMask,
            startMinuteOfDay = startMinute,
            endMinuteOfDay = endMinute,
            isEnabled = true
        )

        if (!uiState.value.isChallengeActive) {
            _pendingAccountabilitySchedule.value = PendingAccountabilitySchedule(entity, isDelete = false, isAdd = true)
            _isAddDialogOpen.value = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            saveScheduleUseCase(entity)
            _isAddDialogOpen.value = false
        }
    }

    fun deleteSchedule(id: Long, title: String) {
        if (uiState.value.isChallengeActive) {
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DELETE_SCHEDULE,
                targetId = id.toString(),
                targetName = title,
                description = "delete schedule '$title'"
            )
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                deleteScheduleUseCase(id)
            }
        }
    }

    fun toggleSchedule(schedule: ScheduleEntity, isEnabled: Boolean) {
        if (isEnabled) {
            if (!uiState.value.isChallengeActive) {
                _pendingAccountabilitySchedule.value = PendingAccountabilitySchedule(schedule, isDelete = false, isAdd = false)
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                saveScheduleUseCase(schedule.copy(isEnabled = true))
            }
        } else {
            if (uiState.value.isChallengeActive) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_SCHEDULE,
                    targetId = schedule.id.toString(),
                    targetName = schedule.title,
                    description = "disable schedule '${schedule.title}'"
                )
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    saveScheduleUseCase(schedule.copy(isEnabled = false))
                }
            }
        }
    }

    fun onActivateChallengeForSchedule(newType: ChallengeType, sentence: String) {
        val pending = _pendingAccountabilitySchedule.value ?: return
        viewModelScope.launch {
            activateScheduleChallengeUseCase(newType, sentence)
            _pendingAccountabilitySchedule.value = null
            if (pending.isAdd || !pending.isDelete) {
                saveScheduleUseCase(pending.schedule.copy(isEnabled = true))
            } else {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DELETE_SCHEDULE,
                    targetId = pending.schedule.id.toString(),
                    targetName = pending.schedule.title,
                    description = "delete schedule '${pending.schedule.title}'"
                )
            }
        }
    }

    fun onDismissAccountabilityRequired() {
        _pendingAccountabilitySchedule.value = null
    }

    fun onChallengeSuccess(context: Context) {
        val challenge = _activeChallenge.value ?: return
        viewModelScope.launch {
            val pending = focusGuardRepository.createPendingChange(
                changeType = challenge.changeType,
                targetId = challenge.targetId,
                targetName = challenge.targetName
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
            scheduleRepository.cancelPendingChange(id)
        }
    }
}
