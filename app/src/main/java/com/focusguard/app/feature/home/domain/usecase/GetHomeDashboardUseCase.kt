package com.focusguard.app.feature.home.domain.usecase

import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.data.repository.ProtectionPermissionStatus
import com.focusguard.app.feature.home.domain.repository.HomeDashboardCounts
import com.focusguard.app.feature.home.domain.repository.HomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Domain Use Case (Home Dashboard Data Stream Provider)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Single Responsibility:
 *    - Serves as the primary query gateway for [com.focusguard.app.feature.home.ui.HomeViewModel].
 *    - Shields presentation logic from directly referencing data-layer specifics.
 *
 * 2. Exposed Reactive Observables:
 *    - [permissionStatus]: Real-time state of Accessibility, Device Admin, Overlay, Battery, and Usage.
 *    - [healthSummary]: Overall security health metrics and shield levels.
 *    - [capabilityStates]: Operational status for individual blockers.
 *    - [dashboardCounts]: Reactive counts for active blocked apps, keywords, limits, and schedules.
 *
 * 3. Frame 1 Instant Rendering:
 *    - [getInitialCounts]: Enables synchronous cache reading to eliminate UI blinking on cold starts.
 * =========================================================================================
 */
class GetHomeDashboardUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    val permissionStatus: StateFlow<ProtectionPermissionStatus> get() = repository.permissionStatus
    val healthSummary: StateFlow<ProtectionHealthSummary> get() = repository.healthSummary
    val capabilityStates: StateFlow<Map<Capability, CapabilityState>> get() = repository.capabilityStates
    val dashboardCounts: Flow<HomeDashboardCounts> get() = repository.dashboardCounts

    fun getInitialCounts(): HomeDashboardCounts = repository.getInitialCounts()
}
