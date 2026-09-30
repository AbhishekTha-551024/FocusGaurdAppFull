package com.focusguard.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityManager
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PermissionStatusRepository
import com.focusguard.app.data.repository.ProtectionPermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val permissionStatus: ProtectionPermissionStatus = ProtectionPermissionStatus(),
    val isAccessibilityEnabled: Boolean = permissionStatus.isAccessibilityGranted,
    val isOverlayGranted: Boolean = permissionStatus.isOverlayGranted,
    val isDeviceAdminActive: Boolean = permissionStatus.isDeviceAdminGranted,
    val isUsageAccessGranted: Boolean = permissionStatus.isUsageAccessGranted,
    val isBatteryOptimizationIgnored: Boolean = permissionStatus.isBatteryOptimizationIgnored,
    val healthSummary: ProtectionHealthSummary = ProtectionHealthSummary(),
    val capabilityStates: Map<Capability, CapabilityState> = emptyMap(),
    val blockedAppsCount: Int = 0,
    val blockedKeywordsCount: Int = 0,
    val activeTimeLimitsCount: Int = 0,
    val activeSchedulesCount: Int = 0,
    val todayImpulsesBlockedCount: Int = 0
) {
    val hasMissingRequiredPermissions: Boolean
        get() = !isAccessibilityEnabled || !isBatteryOptimizationIgnored || !isOverlayGranted
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusGuardRepository,
    private val permissionStatusRepository: PermissionStatusRepository,
    val capabilityManager: CapabilityManager
) : ViewModel() {

    // FIX NH3: Private typed state holders for the nested combine() refactor.
    // Kotlin's combine() is type-safe for up to 5 flows; beyond that it falls back to
    // Array<Any> requiring unchecked casts that crash at runtime on any type mismatch.
    // Splitting 8 flows into (3 + 5) nested combines makes the whole chain compiler-verified.
    private data class PermHealthState(
        val perms: ProtectionPermissionStatus,
        val health: ProtectionHealthSummary,
        val states: Map<Capability, CapabilityState>
    )

    private data class CountsState(
        val apps: Int,
        val keywords: Int,
        val limits: Int,
        val schedules: Int,
        val impulses: Int
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            permissionStatusRepository.status,
            capabilityManager.healthSummary,
            capabilityManager.capabilityStates
        ) { perms: ProtectionPermissionStatus,
            health: ProtectionHealthSummary,
            states: Map<Capability, CapabilityState> ->
            PermHealthState(perms, health, states)
        },
        combine(
            repository.blockedAppsCount,
            repository.blockedKeywordsCount,
            repository.activeTimeLimitsCount,
            repository.activeSchedulesCount,
            repository.todayImpulsesBlockedCount
        ) { apps: Int, keywords: Int, limits: Int, schedules: Int, impulses: Int ->
            CountsState(apps, keywords, limits, schedules, impulses)
        }
    ) { ph, c ->
        HomeUiState(
            permissionStatus = ph.perms,
            isAccessibilityEnabled = ph.perms.isAccessibilityGranted,
            isOverlayGranted = ph.perms.isOverlayGranted,
            isDeviceAdminActive = ph.perms.isDeviceAdminGranted,
            isUsageAccessGranted = ph.perms.isUsageAccessGranted,
            isBatteryOptimizationIgnored = ph.perms.isBatteryOptimizationIgnored,
            healthSummary = ph.health,
            capabilityStates = ph.states,
            blockedAppsCount = c.apps,
            blockedKeywordsCount = c.keywords,
            activeTimeLimitsCount = c.limits,
            activeSchedulesCount = c.schedules,
            todayImpulsesBlockedCount = c.impulses
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        run {
            val initialPerms = permissionStatusRepository.status.value
            HomeUiState(
                permissionStatus = initialPerms,
                isAccessibilityEnabled = initialPerms.isAccessibilityGranted,
                isOverlayGranted = initialPerms.isOverlayGranted,
                isDeviceAdminActive = initialPerms.isDeviceAdminGranted,
                isUsageAccessGranted = initialPerms.isUsageAccessGranted,
                isBatteryOptimizationIgnored = initialPerms.isBatteryOptimizationIgnored,
                healthSummary = capabilityManager.healthSummary.value,
                capabilityStates = capabilityManager.capabilityStates.value,
                blockedAppsCount = repository.getFastBlockedAppsCount(),
                blockedKeywordsCount = repository.getFastBlockedKeywords().size,
                activeTimeLimitsCount = repository.getFastTimeLimits().size,
                activeSchedulesCount = repository.getFastActiveSchedules().size
            )
        }
    )

    fun refreshPermissions() {
        permissionStatusRepository.refresh()
        capabilityManager.refresh()
    }

    fun acknowledgeOemGuidance() {
        viewModelScope.launch {
            permissionStatusRepository.acknowledgeOemGuidance()
        }
    }
}
