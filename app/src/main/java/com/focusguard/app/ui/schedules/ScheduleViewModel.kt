package com.focusguard.app.ui.schedules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.db.PendingChangeEntity
import com.focusguard.app.data.db.ScheduleEntity
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.domain.usecase.GetInstalledAppsUseCase
import com.focusguard.app.ui.blocker.ActiveChallengeState
import com.focusguard.app.worker.PendingChangeApplyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingAccountabilitySchedule(
    val schedule: ScheduleEntity,
    val isDelete: Boolean
)

data class ScheduleUiState(
    val schedules: List<ScheduleEntity> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val pendingChanges: List<PendingChangeEntity> = emptyList(),
    val activeChallenge: ActiveChallengeState? = null,
    val isChallengeActive: Boolean = false,
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val pendingAccountabilitySchedule: PendingAccountabilitySchedule? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: FocusGuardRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _pendingAccountabilitySchedule = MutableStateFlow<PendingAccountabilitySchedule?>(null)

    private data class DialogChallengeState(
        val isAddDialogOpen: Boolean,
        val activeChallenge: ActiveChallengeState?,
        val pendingAccountability: PendingAccountabilitySchedule?
    )

    private val dialogChallengeState = combine(_isAddDialogOpen, _activeChallenge, _pendingAccountabilitySchedule) { isAddOpen, challenge, pendingSched ->
        DialogChallengeState(isAddOpen, challenge, pendingSched)
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.allSchedules,
        getInstalledAppsUseCase.execute(),
        dialogChallengeState,
        repository.pendingChanges,
        repository.challengeSettings
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
            // isLoading = true so skeleton shows immediately on screen open
            initialValue = ScheduleUiState(isLoading = true)
        )

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

        viewModelScope.launch(Dispatchers.IO) {
            val entity = ScheduleEntity(
                title = cleanTitle,
                packageName = packageName,
                appName = appName,
                daysOfWeekMask = daysOfWeekMask,
                startMinuteOfDay = startMinute,
                endMinuteOfDay = endMinute,
                isEnabled = true
            )
            repository.saveSchedule(entity)
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
            // Invariant Rule 6: Accountability Challenge is required! Prompt user to activate it.
            val sched = uiState.value.schedules.find { it.id == id } ?: ScheduleEntity(id = id, title = title, daysOfWeekMask = 0, startMinuteOfDay = 0, endMinuteOfDay = 0)
            _pendingAccountabilitySchedule.value = PendingAccountabilitySchedule(sched, isDelete = true)
        }
    }

    fun toggleSchedule(schedule: ScheduleEntity, isEnabled: Boolean) {
        if (isEnabled) {
            // Turning ON is instant
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveSchedule(schedule.copy(isEnabled = true))
            }
        } else {
            if (uiState.value.isChallengeActive) {
                // Turning OFF requires challenge!
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_SCHEDULE,
                    targetId = schedule.id.toString(),
                    targetName = schedule.title,
                    description = "disable schedule '${schedule.title}'"
                )
            } else {
                // Invariant Rule 6: Accountability Challenge is required! Prompt user to activate it.
                _pendingAccountabilitySchedule.value = PendingAccountabilitySchedule(schedule, isDelete = false)
            }
        }
    }

    fun onActivateChallengeForSchedule() {
        val pending = _pendingAccountabilitySchedule.value ?: return
        viewModelScope.launch {
            repository.setChallengeActive(true)
            _pendingAccountabilitySchedule.value = null
            if (pending.isDelete) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DELETE_SCHEDULE,
                    targetId = pending.schedule.id.toString(),
                    targetName = pending.schedule.title,
                    description = "delete schedule '${pending.schedule.title}'"
                )
            } else {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_SCHEDULE,
                    targetId = pending.schedule.id.toString(),
                    targetName = pending.schedule.title,
                    description = "disable schedule '${pending.schedule.title}'"
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
            val pending = repository.createPendingChange(
                changeType = challenge.changeType,
                targetId = challenge.targetId,
                targetName = challenge.targetName
            )
            repository.applyPendingChange(pending)
            val id = challenge.targetId.toLongOrNull()
            if (id != null) {
                when (challenge.changeType) {
                    ChangeType.DELETE_SCHEDULE -> repository.deleteSchedule(id)
                    ChangeType.DISABLE_SCHEDULE -> repository.updateScheduleEnabled(id, false)
                    else -> {}
                }
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
