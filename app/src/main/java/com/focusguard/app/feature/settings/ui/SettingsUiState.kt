package com.focusguard.app.feature.settings.ui

import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.PartnershipEntity
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.data.repository.ProtectionPermissionStatus

/**
 * Immutable UI State for FocusGuard Settings screen.
 */
data class SettingsUiState(
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val isChallengeActive: Boolean = false,
    val isUninstallProtectionEnabled: Boolean = false,
    val uninstallProtectionExpiryTimestamp: Long = 0L,
    val uninstallProtectionDays: Int = 0,
    val remainingProtectionDays: Long = 0L,
    val isShortsReelsBlockEnabled: Boolean = false,
    val isInstagramReelsBlockEnabled: Boolean = false,
    val isFacebookReelsBlockEnabled: Boolean = false,
    val isYouTubeShortsBlockEnabled: Boolean = false,
    val isAutoBlockNewAppsEnabled: Boolean = false,
    val isIncognitoBlockEnabled: Boolean = false,
    val isBlockUnsupportedBrowsersEnabled: Boolean = false,
    val isAdultContentBlockEnabled: Boolean = false,
    val isAntiCloningShieldEnabled: Boolean = true,
    val activeChallenge: ActiveChallengeState? = null,
    val showUninstallDurationDialog: Boolean = false,
    val showDeviceAdminRequiredDialog: Boolean = false,
    val showAccountabilityRequiredDialog: Boolean = false,
    val showUninstallConfirmDialog: Boolean = false,
    val accountabilityRequiredFeature: String = "Uninstall Protection",
    val pendingFeedWarning: String? = null,
    val permissionsRequiredFeature: String? = null,
    // Support Secret Code System
    val deviceId: String = "",
    val showSupportCodeDialog: Boolean = false,
    val supportCooldownSeconds: Long = 0L,
    val permissionStatus: ProtectionPermissionStatus = ProtectionPermissionStatus(),
    // Overlay Wallpaper
    val overlayWallpaperPath: String = "",
    val overlayPresetId: Int = 0,
    val overlayDimAlpha: Float = 0.55f,
    val hasCustomWallpaper: Boolean = false,
    // Block Overlay Behavior & Customization
    val customBlockMessage: String = "",
    val blockCountdownSeconds: Int = 5,
    // Accountability Partner & Firebase Auth
    val currentUserEmail: String? = null,
    val isUserLoggedIn: Boolean = false,
    val showAuthDialog: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val partnership: PartnershipEntity? = null,
    val pendingIncomingInvite: PartnershipEntity? = null,
    val isPartnerInviteLoading: Boolean = false,
    val partnerInviteError: String? = null,
    val isSignOutLocked: Boolean = false,
    val signOutLockReason: String? = null
)
