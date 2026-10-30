package com.focusguard.app.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.data.repository.ProtectionPermissionStatus
import com.focusguard.app.feature.home.domain.repository.HomeDashboardCounts
import com.focusguard.app.feature.home.domain.usecase.AcknowledgeOemGuidanceUseCase
import com.focusguard.app.feature.home.domain.usecase.GetHomeDashboardUseCase
import com.focusguard.app.feature.home.domain.usecase.RefreshPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Jetpack ViewModel (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Central UI State Holder for Home Dashboard:
 *    - Survives Activity re-creations, configuration changes (screen rotation, foldables).
 *    - Connects Clean Architecture use cases ([GetHomeDashboardUseCase], [RefreshPermissionsUseCase],
 *      [AcknowledgeOemGuidanceUseCase]) to the Compose presentation layer.
 *
 * 2. Multi-Stream Flow Fusion ([uiState]):
 *    - Combines permission status, health summaries, capability maps, and dashboard metrics
 *      into a single, consolidated [StateFlow<HomeUiState>].
 *    - Uses [SharingStarted.Eagerly] to maintain active observation while subscribed in memory.
 *
 * 3. Zero-Flicker Cold Start Hydration:
 *    - Supplies synchronous initial values via [GetHomeDashboardUseCase.getInitialCounts] to
 *      guarantee that Frame 1 renders instant, cached numbers rather than displaying 0s and jumping.
 *
 * 4. User Intent Handling:
 *    - [onEvent]: Processes [HomeUiEvent.RefreshPermissions] and [HomeUiEvent.AcknowledgeOemGuidance].
 *    - Dispatches coroutines safely within [viewModelScope].
 * =========================================================================================
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val refreshPermissionsUseCase: RefreshPermissionsUseCase,
    private val acknowledgeOemGuidanceUseCase: AcknowledgeOemGuidanceUseCase
) : ViewModel() {

    private data class PermHealthState(
        val perms: ProtectionPermissionStatus,
        val health: ProtectionHealthSummary,
        val states: Map<Capability, CapabilityState>
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            getHomeDashboardUseCase.permissionStatus,
            getHomeDashboardUseCase.healthSummary,
            getHomeDashboardUseCase.capabilityStates
        ) { perms: ProtectionPermissionStatus,
            health: ProtectionHealthSummary,
            states: Map<Capability, CapabilityState> ->
            PermHealthState(perms, health, states)
        },
        getHomeDashboardUseCase.dashboardCounts
    ) { ph: PermHealthState, counts: HomeDashboardCounts ->
        HomeUiState(
            permissionStatus = ph.perms,
            isAccessibilityEnabled = ph.perms.isAccessibilityGranted,
            isOverlayGranted = ph.perms.isOverlayGranted,
            isDeviceAdminActive = ph.perms.isDeviceAdminGranted,
            isUsageAccessGranted = ph.perms.isUsageAccessGranted,
            isBatteryOptimizationIgnored = ph.perms.isBatteryOptimizationIgnored,
            healthSummary = ph.health,
            capabilityStates = ph.states,
            blockedAppsCount = counts.blockedAppsCount,
            blockedKeywordsCount = counts.blockedKeywordsCount,
            activeTimeLimitsCount = counts.activeTimeLimitsCount,
            activeSchedulesCount = counts.activeSchedulesCount,
            todayImpulsesBlockedCount = counts.todayImpulsesBlockedCount
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        run {
            val initialPerms = getHomeDashboardUseCase.permissionStatus.value
            val initialCounts = getHomeDashboardUseCase.getInitialCounts()
            HomeUiState(
                permissionStatus = initialPerms,
                isAccessibilityEnabled = initialPerms.isAccessibilityGranted,
                isOverlayGranted = initialPerms.isOverlayGranted,
                isDeviceAdminActive = initialPerms.isDeviceAdminGranted,
                isUsageAccessGranted = initialPerms.isUsageAccessGranted,
                isBatteryOptimizationIgnored = initialPerms.isBatteryOptimizationIgnored,
                healthSummary = getHomeDashboardUseCase.healthSummary.value,
                capabilityStates = getHomeDashboardUseCase.capabilityStates.value,
                blockedAppsCount = initialCounts.blockedAppsCount,
                blockedKeywordsCount = initialCounts.blockedKeywordsCount,
                activeTimeLimitsCount = initialCounts.activeTimeLimitsCount,
                activeSchedulesCount = initialCounts.activeSchedulesCount,
                todayImpulsesBlockedCount = initialCounts.todayImpulsesBlockedCount
            )
        }
    )

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.RefreshPermissions -> refreshPermissions()
            HomeUiEvent.AcknowledgeOemGuidance -> acknowledgeOemGuidance()
        }
    }

    fun refreshPermissions() {
        refreshPermissionsUseCase()
    }

    fun acknowledgeOemGuidance() {
        viewModelScope.launch {
            acknowledgeOemGuidanceUseCase()
        }
    }
}
