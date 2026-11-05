package com.focusguard.app.feature.settings.ui

import android.content.Context
import android.net.Uri
import com.focusguard.app.core.model.ChallengeType

/**
 * UI Events for Settings Screen.
 */
sealed interface SettingsUiEvent {
    data class ToggleUninstallProtection(val enable: Boolean) : SettingsUiEvent
    data class ConfirmEnableUninstallProtection(val days: Int) : SettingsUiEvent
    data object DismissUninstallDurationDialog : SettingsUiEvent
    data object DismissDeviceAdminRequiredDialog : SettingsUiEvent
    data object DismissAccountabilityRequiredDialog : SettingsUiEvent
    data object DismissPermissionsRequiredDialog : SettingsUiEvent
    data object ConfirmEnablePendingFeed : SettingsUiEvent
    data object DismissPendingFeedWarning : SettingsUiEvent
    data class ToggleShortsReels(val enable: Boolean) : SettingsUiEvent
    data class ToggleInstagramReels(val enable: Boolean) : SettingsUiEvent
    data class ToggleFacebookReels(val enable: Boolean) : SettingsUiEvent
    data class ToggleYouTubeShorts(val enable: Boolean) : SettingsUiEvent
    data class ToggleIncognito(val enable: Boolean) : SettingsUiEvent
    data class ToggleBlockUnsupportedBrowsers(val enable: Boolean) : SettingsUiEvent
    data class ToggleAutoBlockNewApps(val enable: Boolean) : SettingsUiEvent
    data class ToggleAdultContent(val enable: Boolean) : SettingsUiEvent
    data object RequestRemoveChallenge : SettingsUiEvent
    data class RequestChangeChallenge(val type: ChallengeType, val sentence: String, val delay: Int) : SettingsUiEvent
    data object ActivateChallenge : SettingsUiEvent
    data class SetCommitmentSentence(val sentence: String) : SettingsUiEvent
    data class ChallengeSuccess(val context: Context) : SettingsUiEvent
    data object CancelChallenge : SettingsUiEvent
    data class PickWallpaper(val uri: Uri) : SettingsUiEvent
    data object ClearWallpaper : SettingsUiEvent
    data class SetPreset(val presetId: Int) : SettingsUiEvent
    data class SetDimAlpha(val alpha: Float) : SettingsUiEvent
    data class SetCustomBlockMessage(val message: String) : SettingsUiEvent
    data class SetBlockCountdownSeconds(val seconds: Int) : SettingsUiEvent
    data object PreviewOverlay : SettingsUiEvent
    data object RefreshPermissions : SettingsUiEvent
}
