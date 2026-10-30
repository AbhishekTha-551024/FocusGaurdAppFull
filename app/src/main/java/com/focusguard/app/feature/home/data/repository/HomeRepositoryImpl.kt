package com.focusguard.app.feature.home.data.repository

import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityManager
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PermissionStatusRepository
import com.focusguard.app.data.repository.ProtectionPermissionStatus
import com.focusguard.app.feature.home.domain.repository.HomeDashboardCounts
import com.focusguard.app.feature.home.domain.repository.HomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Repository Implementation (Home Feature Data Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Data Aggregator for the Main Dashboard:
 *    - Consolidates real-time streams from [FocusGuardRepository], [PermissionStatusRepository],
 *      and [CapabilityManager] into unified StateFlows and Flows consumed by the UI.
 *
 * 2. Real-Time Security & Permission Monitoring:
 *    - [permissionStatus]: Exposes [ProtectionPermissionStatus] tracking Accessibility Service,
 *      Device Admin policy, System Alert Window overlay, and battery optimization exemptions.
 *    - [healthSummary]: Aggregates health indicators and capabilities into a unified security score.
 *    - [capabilityStates]: Maps each [Capability] (App Blocker, Keyword Blocker, VPN filter, etc.)
 *      to its current runtime health state ([CapabilityState]).
 *
 * 3. 0ms Immediate Startup Hydration ([getInitialCounts]):
 *    - Provides synchronous memory-cached counts on Frame 1 before Room database flows emit,
 *      preventing visual UI layout shifts or zero-count flickering on cold app launches.
 *
 * 4. Permission & OEM Lifecycle Triggering:
 *    - [refreshPermissions]: Re-audits system permissions on Activity resume.
 *    - [acknowledgeOemGuidance]: Persists user acknowledgment for OEM battery killing bypasses.
 * =========================================================================================
 */
@Singleton
class HomeRepositoryImpl @Inject constructor(
    private val repository: FocusGuardRepository,
    private val permissionStatusRepository: PermissionStatusRepository,
    val capabilityManager: CapabilityManager
) : HomeRepository {

    override val permissionStatus: StateFlow<ProtectionPermissionStatus>
        get() = permissionStatusRepository.status

    override val healthSummary: StateFlow<ProtectionHealthSummary>
        get() = capabilityManager.healthSummary

    override val capabilityStates: StateFlow<Map<Capability, CapabilityState>>
        get() = capabilityManager.capabilityStates

    override val dashboardCounts: Flow<HomeDashboardCounts> = combine(
        repository.blockedAppsCount,
        repository.blockedKeywordsCount,
        repository.activeTimeLimitsCount,
        repository.activeSchedulesCount,
        repository.todayImpulsesBlockedCount
    ) { apps: Int, keywords: Int, limits: Int, schedules: Int, impulses: Int ->
        HomeDashboardCounts(
            blockedAppsCount = apps,
            blockedKeywordsCount = keywords,
            activeTimeLimitsCount = limits,
            activeSchedulesCount = schedules,
            todayImpulsesBlockedCount = impulses
        )
    }

    override fun getInitialCounts(): HomeDashboardCounts {
        return HomeDashboardCounts(
            blockedAppsCount = repository.getFastBlockedAppsCount(),
            blockedKeywordsCount = repository.getFastBlockedKeywords().size,
            activeTimeLimitsCount = repository.getFastTimeLimits().size,
            activeSchedulesCount = repository.getFastActiveSchedules().size,
            todayImpulsesBlockedCount = 0
        )
    }

    override fun refreshPermissions() {
        permissionStatusRepository.refresh()
        capabilityManager.refresh()
    }

    override suspend fun acknowledgeOemGuidance() {
        permissionStatusRepository.acknowledgeOemGuidance()
    }
}
