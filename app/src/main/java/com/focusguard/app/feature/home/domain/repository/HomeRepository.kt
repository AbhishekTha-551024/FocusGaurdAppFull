package com.focusguard.app.feature.home.domain.repository

import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.data.repository.ProtectionPermissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Domain Repository Contract & Models (Home Feature)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Clean Architecture Domain Contract:
 *    - Defines the pure domain interface for the Home dashboard.
 *    - Has zero dependencies on Android frameworks, Room, DataStore, or Hilt annotations.
 *
 * 2. Immutable Metrics Model ([HomeDashboardCounts]):
 *    - Encapsulates active focus metrics: count of blocked apps, blocked keywords, active daily limits,
 *      active scheduled focus blocks, and impulses blocked today.
 *
 * 3. State & Operation Contracts:
 *    - [permissionStatus]: Real-time state of all system security permissions.
 *    - [healthSummary]: Health score of active protection shields.
 *    - [capabilityStates]: Breakdown of each subsystem's operational state.
 *    - [dashboardCounts]: Continuous reactive updates as rules change across other tabs.
 *    - [getInitialCounts]: Instant synchronous cache access for zero-flicker startup.
 *    - [refreshPermissions]: System permission audit hook.
 *    - [acknowledgeOemGuidance]: OEM kill prevention acknowledgment.
 * =========================================================================================
 */
data class HomeDashboardCounts(
    val blockedAppsCount: Int = 0,
    val blockedKeywordsCount: Int = 0,
    val activeTimeLimitsCount: Int = 0,
    val activeSchedulesCount: Int = 0,
    val todayImpulsesBlockedCount: Int = 0
)

interface HomeRepository {
    val permissionStatus: StateFlow<ProtectionPermissionStatus>
    val healthSummary: StateFlow<ProtectionHealthSummary>
    val capabilityStates: StateFlow<Map<Capability, CapabilityState>>
    val dashboardCounts: Flow<HomeDashboardCounts>

    fun getInitialCounts(): HomeDashboardCounts
    fun refreshPermissions()
    suspend fun acknowledgeOemGuidance()
}
