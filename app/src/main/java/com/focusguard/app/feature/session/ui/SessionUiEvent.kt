package com.focusguard.app.feature.session.ui

import com.focusguard.app.core.model.AppInfo

sealed interface SessionUiEvent {
    data class SetDuration(val minutes: Int) : SessionUiEvent
    data class SetSearchQuery(val query: String) : SessionUiEvent
    data class TogglePackageInSuitcase(val packageName: String) : SessionUiEvent
    data class QuickPackUtilities(val apps: List<AppInfo>) : SessionUiEvent
    data object StartSession : SessionUiEvent
    data object OpenPackingDialog : SessionUiEvent
    data object ClosePackingDialog : SessionUiEvent
    data object OpenAbortDialog : SessionUiEvent
    data object CloseAbortDialog : SessionUiEvent
    data object ConfirmEmergencyAbort : SessionUiEvent
}
