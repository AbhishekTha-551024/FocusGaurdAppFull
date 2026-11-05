package com.focusguard.app.feature.home.domain.usecase

import com.focusguard.app.feature.home.domain.repository.HomeRepository
import javax.inject.Inject

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Domain Use Case (OEM Battery & Autostart Guidance)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. OEM Kill-Prevention Acknowledgment:
 *    - Aggressive OEM task killers (MIUI/HyperOS, ColorOS, FuntouchOS, EMUI, Samsung OneUI)
 *      frequently kill Accessibility Services in the background when the screen turns off.
 *    - FocusGuard displays OEM-specific guidance explaining how to whitelist the app, disable
 *      battery saver restrictions, and lock the app in recent apps.
 *
 * 2. State Persistence:
 *    - Once the user reads and acknowledges the guidance, this use case persists the state
 *      via [HomeRepository.acknowledgeOemGuidance] so the user is not pestered on every launch.
 * =========================================================================================
 */
class AcknowledgeOemGuidanceUseCase @Inject constructor(
    private val repository: HomeRepository
) {
    suspend operator fun invoke() {
        repository.acknowledgeOemGuidance()
    }
}
