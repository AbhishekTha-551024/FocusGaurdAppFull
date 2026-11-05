package com.focusguard.app.feature.home.ui

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: UI Events (Home Feature Presentation Layer - MVI Pattern)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Unidirectional Data Flow (UDF):
 *    - Sealed interface representing discrete user intents and lifecycle triggers emitted
 *      by the Home UI composables to [HomeViewModel.onEvent].
 *
 * 2. Event Types:
 *    - [RefreshPermissions]: Sent on Activity resume or when returning from system settings
 *      screens to force an immediate re-evaluation of permission states.
 *    - [AcknowledgeOemGuidance]: Sent when the user confirms reading device-specific OEM battery
 *      optimization instructions, preventing the guidance dialog from showing again.
 * =========================================================================================
 */
sealed interface HomeUiEvent {
    data object RefreshPermissions : HomeUiEvent
    data object AcknowledgeOemGuidance : HomeUiEvent
}
