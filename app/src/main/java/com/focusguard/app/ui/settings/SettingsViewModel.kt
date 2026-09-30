package com.focusguard.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.overlay.BlockOverlayManager
import com.focusguard.app.overlay.OverlayBlockType
import com.focusguard.app.overlay.OverlayWallpaperManager
import com.focusguard.app.ui.blocker.ActiveChallengeState
import com.focusguard.app.worker.PendingChangeApplyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val challengeType: ChallengeType = ChallengeType.TYPING,
    val commitmentSentence: String = "",
    val timeDelayHours: Int = 24,
    val isChallengeActive: Boolean = false,
    // UX FIX: Default to false so UI doesn't briefly flash "ON" before real state loads from DataStore.
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
    val accountabilityRequiredFeature: String = "Uninstall Protection",
    val pendingFeedWarning: String? = null,
    // Support Secret Code System
    val deviceId: String = "",
    val showSupportCodeDialog: Boolean = false,
    val supportCooldownSeconds: Long = 0L,
    val permissionStatus: com.focusguard.app.data.repository.ProtectionPermissionStatus = com.focusguard.app.data.repository.ProtectionPermissionStatus(),
    // Phase 7: Overlay Wallpaper
    val overlayWallpaperPath: String = "",
    val overlayPresetId: Int = 0,
    val overlayDimAlpha: Float = 0.55f,
    val hasCustomWallpaper: Boolean = false,
    // Block Overlay Behavior & Customization
    val customBlockMessage: String = "",
    val blockCountdownSeconds: Int = 5,
    // Stage 1: Accountability Partner & Firebase Auth
    val currentUserEmail: String? = null,
    val isUserLoggedIn: Boolean = false,
    val showAuthDialog: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val partnership: com.focusguard.app.data.db.PartnershipEntity? = null,
    val pendingIncomingInvite: com.focusguard.app.data.db.PartnershipEntity? = null,
    val isPartnerInviteLoading: Boolean = false,
    val partnerInviteError: String? = null,
    val isSignOutLocked: Boolean = false,
    val signOutLockReason: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: FocusGuardRepository,
    private val wallpaperManager: OverlayWallpaperManager,
    private val overlayManager: BlockOverlayManager,
    private val partnershipRepository: com.focusguard.app.data.repository.PartnershipRepository,
    private val permissionStatusRepository: com.focusguard.app.data.repository.PermissionStatusRepository,
    private val authRepository: com.focusguard.app.data.repository.AuthRepository
) : ViewModel() {

    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _showUninstallDurationDialog = MutableStateFlow(false)
    private val _showDeviceAdminRequiredDialog = MutableStateFlow(false)
    private val _showAccountabilityRequiredDialog = MutableStateFlow(false)
    private val _accountabilityRequiredFeature = MutableStateFlow("Uninstall Protection")
    private val _pendingFeedWarning = MutableStateFlow<String?>(null)
    private val _showSupportCodeDialog = MutableStateFlow(false)

    // Stage 1: Auth & Partner state flows
    private val _showAuthDialog = MutableStateFlow(false)
    private val _isAuthLoading = MutableStateFlow(false)
    private val _authErrorMessage = MutableStateFlow<String?>(null)
    private val _isPartnerInviteLoading = MutableStateFlow(false)
    private val _partnerInviteError = MutableStateFlow<String?>(null)

    val deviceId: String = repository.getDeviceId()

    fun openSupportCodeDialog() {
        _showSupportCodeDialog.value = true
    }

    fun dismissSupportCodeDialog() {
        _showSupportCodeDialog.value = false
    }

    fun verifySupportCode(code: String): com.focusguard.app.security.SupportVerificationResult {
        return repository.verifySupportCode(code)
    }

    fun applySupportAction(actionCode: String, targetPayload: String) {
        viewModelScope.launch {
            repository.executeSupportAction(actionCode, targetPayload)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val partnerDataFlow = partnershipRepository.currentUser.flatMapLatest { user ->
        if (user != null && !user.email.isNullOrBlank()) {
            partnershipRepository.observePartnershipForUser(user.uid, user.email!!.lowercase())
                .map { entity ->
                    val isIncoming = entity != null && entity.partnerEmail == user.email!!.lowercase() && entity.status == com.focusguard.app.data.model.PartnershipStatus.PENDING_INVITE
                    val incoming = if (isIncoming) entity else null
                    val current = if (!isIncoming) entity else null
                    Triple(user, current, incoming)
                }
        } else {
            flowOf(Triple(null, null, null))
        }
    }

    private val partnerFormState = combine(
        _showAuthDialog,
        _isAuthLoading,
        _authErrorMessage,
        _isPartnerInviteLoading,
        _partnerInviteError
    ) { showAuth, authLoading, authErr, inviteLoading, inviteErr ->
        listOf(showAuth, authLoading, authErr, inviteLoading, inviteErr)
    }

    private val baseSettingsFlow = combine(
        repository.settingsSnapshot,
        permissionStatusRepository.status,
        _activeChallenge,
        _showUninstallDurationDialog,
        _showDeviceAdminRequiredDialog,
        _showAccountabilityRequiredDialog,
        _accountabilityRequiredFeature,
        _pendingFeedWarning,
        _showSupportCodeDialog
    ) { args: Array<Any?> ->
        val settings = args[0] as com.focusguard.app.data.datastore.FocusGuardPreferences.SettingsSnapshot
        val perms = args[1] as com.focusguard.app.data.repository.ProtectionPermissionStatus
        val challenge = args[2] as? ActiveChallengeState
        val showDurationDialog = args[3] as Boolean
        val showAdminRequiredDialog = args[4] as Boolean
        val showAccountabilityDialog = args[5] as Boolean
        val accountabilityFeature = args[6] as String
        val pendingFeed = args[7] as? String
        val showSupportDialog = args[8] as Boolean

        val isTimedLockExpired = settings.isUninstallProtectionEnabled &&
                settings.uninstallProtectionExpiryTimestamp > 0L &&
                System.currentTimeMillis() >= settings.uninstallProtectionExpiryTimestamp

        if (isTimedLockExpired) {
            viewModelScope.launch {
                repository.setUninstallProtectionEnabled(false)
            }
        }

        val effectiveProtectionEnabled = if (isTimedLockExpired) false else settings.isUninstallProtectionEnabled
        val remainingDays = if (effectiveProtectionEnabled && settings.uninstallProtectionExpiryTimestamp > 0L) {
            val diff = settings.uninstallProtectionExpiryTimestamp - System.currentTimeMillis()
            if (diff > 0) java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff) + 1 else 0L
        } else 0L

        SettingsUiState(
            challengeType = settings.challengeType,
            commitmentSentence = settings.commitmentSentence,
            timeDelayHours = settings.timeDelayHours,
            isChallengeActive = settings.isChallengeActive,
            isUninstallProtectionEnabled = effectiveProtectionEnabled,
            uninstallProtectionExpiryTimestamp = if (effectiveProtectionEnabled) settings.uninstallProtectionExpiryTimestamp else 0L,
            uninstallProtectionDays = if (effectiveProtectionEnabled) settings.uninstallProtectionDays else 0,
            remainingProtectionDays = remainingDays,
            isShortsReelsBlockEnabled = settings.isShortsReelsBlockEnabled,
            isInstagramReelsBlockEnabled = settings.isInstagramReelsBlockEnabled,
            isFacebookReelsBlockEnabled = settings.isFacebookReelsBlockEnabled,
            isYouTubeShortsBlockEnabled = settings.isYouTubeShortsBlockEnabled,
            isAutoBlockNewAppsEnabled = settings.isAutoBlockNewAppsEnabled,
            isIncognitoBlockEnabled = settings.isIncognitoBlockEnabled,
            isBlockUnsupportedBrowsersEnabled = settings.isBlockUnsupportedBrowsersEnabled,
            isAdultContentBlockEnabled = settings.isAdultContentBlockEnabled,
            isAntiCloningShieldEnabled = settings.isAntiCloningShieldEnabled,
            activeChallenge = challenge,
            showUninstallDurationDialog = showDurationDialog,
            showDeviceAdminRequiredDialog = showAdminRequiredDialog,
            showAccountabilityRequiredDialog = showAccountabilityDialog,
            accountabilityRequiredFeature = accountabilityFeature,
            pendingFeedWarning = pendingFeed,
            deviceId = repository.getDeviceId(),
            showSupportCodeDialog = showSupportDialog,
            supportCooldownSeconds = repository.getSupportCooldownRemainingSeconds(),
            permissionStatus = perms,
            overlayWallpaperPath = settings.overlayWallpaperPath,
            overlayPresetId = settings.overlayPresetId,
            overlayDimAlpha = settings.overlayDimAlpha,
            hasCustomWallpaper = settings.overlayWallpaperPath.isNotEmpty(),
            customBlockMessage = settings.customBlockMessage,
            blockCountdownSeconds = settings.blockCountdownSeconds
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        baseSettingsFlow,
        partnerDataFlow,
        partnerFormState
    ) { base, (user, current, incoming), formList ->
        base.copy(
            currentUserEmail = user?.email,
            isUserLoggedIn = user != null,
            partnership = current,
            pendingIncomingInvite = incoming,
            showAuthDialog = formList[0] as Boolean,
            isAuthLoading = formList[1] as Boolean,
            authErrorMessage = formList[2] as? String,
            isPartnerInviteLoading = formList[3] as Boolean,
            partnerInviteError = formList[4] as? String,
            isSignOutLocked = authRepository.isSignOutLocked(),
            signOutLockReason = authRepository.getSignOutLockReason()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        repository.getCurrentSettingsSnapshot().copy(
            deviceId = repository.getDeviceId(),
            showSupportCodeDialog = false,
            supportCooldownSeconds = repository.getSupportCooldownRemainingSeconds(),
            permissionStatus = permissionStatusRepository.status.value
        )
    )

    fun refreshPermissions() {
        permissionStatusRepository.refresh()
    }

    fun toggleShortsReelsBlock(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "shorts_reels"
                _accountabilityRequiredFeature.value = "Shorts & Reels Blocker"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "shorts_reels"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:shorts_reels"
                _accountabilityRequiredFeature.value = "Shorts & Reels Blocker"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "shorts_reels",
                targetName = "Shorts & Reels Blocker",
                targetPayload = "false",
                description = "unblock Shorts and Reels"
            )
        }
    }

    fun toggleIncognitoBlock(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "incognito"
                _accountabilityRequiredFeature.value = "Incognito Mode Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "incognito"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:incognito"
                _accountabilityRequiredFeature.value = "Incognito Mode Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "incognito",
                targetName = "Incognito Mode Block",
                targetPayload = "false",
                description = "unblock Incognito and Private Browsing tabs"
            )
        }
    }

    // ── Distraction Feed: Warning before Turn ON, Challenge to Turn OFF ──
    fun toggleInstagramReelsBlock(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "instagram"
                _accountabilityRequiredFeature.value = "Instagram Reels Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "instagram"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:instagram"
                _accountabilityRequiredFeature.value = "Instagram Reels Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "instagram",
                targetName = "Instagram Reels Block",
                targetPayload = "false",
                description = "unblock Instagram Reels"
            )
        }
    }

    fun toggleFacebookReelsBlock(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "facebook"
                _accountabilityRequiredFeature.value = "Facebook Reels Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "facebook"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:facebook"
                _accountabilityRequiredFeature.value = "Facebook Reels Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "facebook",
                targetName = "Facebook Reels Block",
                targetPayload = "false",
                description = "unblock Facebook Reels"
            )
        }
    }

    fun toggleYouTubeShortsBlock(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "youtube"
                _accountabilityRequiredFeature.value = "YouTube Shorts Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "youtube"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:youtube"
                _accountabilityRequiredFeature.value = "YouTube Shorts Block"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "youtube",
                targetName = "YouTube Shorts Block",
                targetPayload = "false",
                description = "unblock YouTube Shorts"
            )
        }
    }

    fun toggleAutoBlockNewApps(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "auto_block"
                _accountabilityRequiredFeature.value = "Auto-Block Newly Installed Apps"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "auto_block"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:auto_block"
                _accountabilityRequiredFeature.value = "Auto-Block Newly Installed Apps"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "auto_block",
                targetName = "Auto-Block Newly Installed Apps",
                targetPayload = "false",
                description = "turn off auto-blocking for newly installed apps"
            )
        }
    }

    fun toggleBlockUnsupportedBrowsers(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unsupported_browsers"
                _accountabilityRequiredFeature.value = "Block Unsupported Browsers"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "unsupported_browsers"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:unsupported_browsers"
                _accountabilityRequiredFeature.value = "Block Unsupported Browsers"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "unsupported_browsers",
                targetName = "Block Unsupported Browsers",
                targetPayload = "false",
                description = "allow unsupported or obscure browsers that bypass keyword filtering"
            )
        }
    }

    fun toggleAdultContentBlock(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "adult_content"
                _accountabilityRequiredFeature.value = "Strict Adult Blocker & SafeSearch"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "adult_content"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:adult_content"
                _accountabilityRequiredFeature.value = "Strict Adult Blocker & SafeSearch"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "adult_content",
                targetName = "Strict Adult Blocker & SafeSearch",
                targetPayload = "false",
                description = "turn off Strict Adult Blocker & SafeSearch Shield"
            )
        }
    }

    fun toggleAllDistractionFeeds(enable: Boolean) {
        if (enable) {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "shorts_reels"
                _accountabilityRequiredFeature.value = "All Distraction Feeds"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = "shorts_reels"
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock:shorts_reels"
                _accountabilityRequiredFeature.value = "All Distraction Feeds"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                targetId = "shorts_reels",
                targetName = "All Distraction Feeds",
                targetPayload = "false",
                description = "unblock all Shorts & Reels feeds"
            )
        }
    }

    fun confirmEnablePendingFeed() {
        val target = _pendingFeedWarning.value ?: return
        _pendingFeedWarning.value = null
        viewModelScope.launch {
            when (target) {
                "shorts_reels" -> {
                    repository.setShortsReelsBlockEnabled(true)
                    repository.setInstagramReelsBlockEnabled(true)
                    repository.setFacebookReelsBlockEnabled(true)
                    repository.setYouTubeShortsBlockEnabled(true)
                }
                "incognito" -> repository.setIncognitoBlockEnabled(true)
                "instagram" -> repository.setInstagramReelsBlockEnabled(true)
                "facebook" -> repository.setFacebookReelsBlockEnabled(true)
                "youtube" -> repository.setYouTubeShortsBlockEnabled(true)
                "auto_block" -> repository.setAutoBlockNewAppsEnabled(true)
                "unsupported_browsers" -> repository.setBlockUnsupportedBrowsersEnabled(true)
                "adult_content" -> repository.setAdultContentBlockEnabled(true)
            }
        }
    }

    fun dismissPendingFeedWarning() {
        _pendingFeedWarning.value = null
    }

    private var pendingFeatureAfterChallenge: String? = null

    // ── Uninstall Protection: Days Dialog on Turn ON, Challenge on Turn OFF ──
    fun requestToggleUninstallProtection(enable: Boolean) {
        if (enable) {
            val isDeviceAdminActive = permissionStatusRepository.status.value.isDeviceAdminGranted
            if (!isDeviceAdminActive) {
                _showDeviceAdminRequiredDialog.value = true
                return
            }
            // Check if Accountability Challenge is chosen or not
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "uninstall_protection"
                _accountabilityRequiredFeature.value = "Uninstall Protection"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            pendingFeatureAfterChallenge = null
            _showUninstallDurationDialog.value = true
        } else {
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "unblock_uninstall"
                _accountabilityRequiredFeature.value = "Uninstall Protection"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            pendingFeatureAfterChallenge = null
            val remainingDays = repository.getFastTimedLockRemainingDays()
            val desc = if (remainingDays > 0) {
                "turn off Uninstall Protection ($remainingDays days remaining in timed lock)"
            } else {
                "turn off Uninstall Protection and allow removing FocusGuard"
            }
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.UNINSTALL_REQUEST,
                targetId = "uninstall_protection",
                targetName = "Uninstall Protection",
                targetPayload = "false",
                description = desc
            )
        }
    }

    fun dismissDeviceAdminRequiredDialog() {
        _showDeviceAdminRequiredDialog.value = false
    }

    fun proceedToSetupChallenge() {
        _showAccountabilityRequiredDialog.value = false
    }

    fun dismissAccountabilityRequiredDialog() {
        pendingFeatureAfterChallenge = null
        _showAccountabilityRequiredDialog.value = false
    }

    fun cancelChallengeSetup() {
        pendingFeatureAfterChallenge = null
    }

    fun confirmEnableUninstallProtection(days: Int) {
        _showUninstallDurationDialog.value = false
        pendingFeatureAfterChallenge = null
        viewModelScope.launch {
            repository.startTimedUninstallProtection(days)
        }
    }

    fun dismissUninstallDurationDialog() {
        pendingFeatureAfterChallenge = null
        _showUninstallDurationDialog.value = false
    }

    fun requestChangeChallenge(newType: ChallengeType, newSentence: String, newDelayHours: Int) {
        val currentType = uiState.value.challengeType
        val isAlreadyActive = uiState.value.isChallengeActive
        if (isAlreadyActive && currentType == newType && newSentence == uiState.value.commitmentSentence && newDelayHours == uiState.value.timeDelayHours) {
            return
        }

        if (isAlreadyActive) {
            // Changing challenge settings requires completing the active challenge first!
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.CHANGE_CHALLENGE_TYPE,
                targetId = newType.name,
                targetName = newType.name,
                targetPayload = "${newType.name}|${newSentence}|${newDelayHours}",
                description = "change challenge protection settings"
            )
        } else {
            // First time setup: apply directly without friction
            viewModelScope.launch {
                repository.setChallengeType(newType)
                repository.setCommitmentSentence(newSentence)
                repository.setTimeDelayHours(newDelayHours)
                repository.setChallengeActive(true)
                triggerPendingFeatureAfterChallenge()
            }
        }
    }

    fun activateChallenge() {
        viewModelScope.launch {
            repository.setChallengeActive(true)
            triggerPendingFeatureAfterChallenge()
        }
    }

    private fun triggerPendingFeatureAfterChallenge() {
        val pending = pendingFeatureAfterChallenge ?: return
        pendingFeatureAfterChallenge = null
        when (pending) {
            "uninstall_protection" -> {
                _showUninstallDurationDialog.value = true
            }
            "unblock_uninstall" -> {
                val remainingDays = repository.getFastTimedLockRemainingDays()
                val desc = if (remainingDays > 0) {
                    "turn off Uninstall Protection ($remainingDays days remaining in timed lock)"
                } else {
                    "turn off Uninstall Protection and allow removing FocusGuard"
                }
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.UNINSTALL_REQUEST,
                    targetId = "uninstall_protection",
                    targetName = "Uninstall Protection",
                    targetPayload = "false",
                    description = desc
                )
            }
            "shorts_reels", "incognito", "instagram", "facebook", "youtube",
            "auto_block", "unsupported_browsers", "adult_content" -> {
                _pendingFeedWarning.value = pending
            }
            else -> {
                if (pending.startsWith("unblock:")) {
                    val feedId = pending.removePrefix("unblock:")
                    val targetName = when (feedId) {
                        "shorts_reels" -> "Shorts & Reels Blocker"
                        "incognito" -> "Incognito Mode Block"
                        "instagram" -> "Instagram Reels Block"
                        "facebook" -> "Facebook Reels Block"
                        "youtube" -> "YouTube Shorts Block"
                        "auto_block" -> "Auto-Block Newly Installed Apps"
                        "unsupported_browsers" -> "Block Unsupported Browsers"
                        "adult_content" -> "Strict Adult Blocker & SafeSearch"
                        else -> feedId
                    }
                    _activeChallenge.value = ActiveChallengeState(
                        changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                        targetId = feedId,
                        targetName = targetName,
                        targetPayload = "false",
                        description = "unblock $targetName"
                    )
                }
            }
        }
    }

    fun requestRemoveChallenge() {
        val remainingDays = repository.getFastTimedLockRemainingDays()
        val desc = if (remainingDays > 0) {
            "remove Accountability Challenge ($remainingDays days remaining in timed lock) and disable Uninstall Protection"
        } else {
            "remove Accountability Challenge and disable Uninstall Protection"
        }
        _activeChallenge.value = ActiveChallengeState(
            changeType = ChangeType.CHANGE_CHALLENGE_TYPE,
            targetId = "remove_challenge",
            targetName = "Sentence Typing Challenge",
            targetPayload = "REMOVE_CHALLENGE",
            description = desc
        )
    }


    fun setCommitmentSentence(sentence: String) {
        viewModelScope.launch {
            repository.setCommitmentSentence(sentence)
        }
    }

    fun onChallengeSuccess(context: Context) {
        val challenge = _activeChallenge.value ?: return
        viewModelScope.launch {
            when (challenge.changeType) {
                ChangeType.UNINSTALL_REQUEST -> {
                    repository.setUninstallProtectionEnabled(false)
                }
                ChangeType.CHANGE_CHALLENGE_TYPE -> {
                    if (challenge.targetPayload == "REMOVE_CHALLENGE") {
                        repository.setChallengeActive(false)
                        repository.setUninstallProtectionEnabled(false)
                        repository.setShortsReelsBlockEnabled(false)
                        repository.setInstagramReelsBlockEnabled(false)
                        repository.setFacebookReelsBlockEnabled(false)
                        repository.setYouTubeShortsBlockEnabled(false)
                        repository.setIncognitoBlockEnabled(false)
                        repository.setAutoBlockNewAppsEnabled(false)
                        repository.unblockAllAutoBlockedNewApps()
                        repository.setBlockUnsupportedBrowsersEnabled(false)
                        repository.setAdultContentBlockEnabled(false)
                    } else if (challenge.targetPayload == "REVOKE_PARTNER") {
                        partnershipRepository.revokePartnership(challenge.targetId)
                        repository.setChallengeActive(false)
                        repository.setUninstallProtectionEnabled(false)
                        repository.setShortsReelsBlockEnabled(false)
                        repository.setInstagramReelsBlockEnabled(false)
                        repository.setFacebookReelsBlockEnabled(false)
                        repository.setYouTubeShortsBlockEnabled(false)
                        repository.setIncognitoBlockEnabled(false)
                        repository.setAutoBlockNewAppsEnabled(false)
                        repository.unblockAllAutoBlockedNewApps()
                        repository.setBlockUnsupportedBrowsersEnabled(false)
                        repository.setAdultContentBlockEnabled(false)
                    } else {
                        val payload = challenge.targetPayload?.split("|") ?: emptyList()
                        val newType = payload.getOrNull(0)?.let { runCatching { ChallengeType.valueOf(it) }.getOrNull() } ?: ChallengeType.TYPING
                        val newSentence = payload.getOrNull(1) ?: uiState.value.commitmentSentence
                        val newDelay = payload.getOrNull(2)?.toIntOrNull() ?: uiState.value.timeDelayHours
                        repository.setChallengeType(newType)
                        repository.setCommitmentSentence(newSentence)
                        repository.setTimeDelayHours(newDelay)
                    }
                }
                ChangeType.DISABLE_DISTRACTION_FEED -> {
                    when (challenge.targetId) {
                        "shorts_reels" -> {
                            repository.setShortsReelsBlockEnabled(false)
                            repository.setInstagramReelsBlockEnabled(false)
                            repository.setFacebookReelsBlockEnabled(false)
                            repository.setYouTubeShortsBlockEnabled(false)
                        }
                        "incognito" -> repository.setIncognitoBlockEnabled(false)
                        "instagram" -> repository.setInstagramReelsBlockEnabled(false)
                        "facebook" -> repository.setFacebookReelsBlockEnabled(false)
                        "youtube" -> repository.setYouTubeShortsBlockEnabled(false)
                        "auto_block" -> {
                            repository.setAutoBlockNewAppsEnabled(false)
                            repository.unblockAllAutoBlockedNewApps()
                        }
                        "unsupported_browsers" -> repository.setBlockUnsupportedBrowsersEnabled(false)
                        "adult_content" -> repository.setAdultContentBlockEnabled(false)
                    }
                }
                ChangeType.LOWER_COUNTDOWN -> {
                    val newSeconds = challenge.targetPayload?.toIntOrNull() ?: return@launch
                    repository.setBlockCountdownSeconds(newSeconds)
                }
                else -> { /* other change types handled by their own ViewModels */ }
            }
            _activeChallenge.value = null
        }
    }

    fun onCancelChallenge() {
        _activeChallenge.value = null
    }


    // ── Phase 7: Overlay Wallpaper Methods ────────────────────────────────────────

    fun saveWallpaperFromUri(uri: Uri) {
        viewModelScope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                wallpaperManager.saveFromUri(uri)
            }
            if (path != null) {
                repository.setOverlayWallpaperPath(path)
            }
        }
    }

    fun clearWallpaper() {
        viewModelScope.launch {
            wallpaperManager.clearWallpaper()
            repository.clearOverlayWallpaper()
        }
    }

    fun setPreset(id: Int) {
        viewModelScope.launch {
            repository.setOverlayPresetId(id)
            // Apply immediately to overlay manager
            overlayManager.currentPresetId = id
        }
    }

    fun setDimAlpha(alpha: Float) {
        viewModelScope.launch {
            repository.setOverlayDimAlpha(alpha)
            overlayManager.currentDimAlpha = alpha
        }
    }

    fun setCustomBlockMessage(message: String) {
        viewModelScope.launch {
            repository.setCustomBlockMessage(message)
        }
    }

    fun setBlockCountdownSeconds(seconds: Int) {
        val current = uiState.value.blockCountdownSeconds
        if (seconds < current && uiState.value.isChallengeActive) {
            // LOWERING countdown (easier to dismiss overlay) → challenge required ONLY if challenge is active
            _activeChallenge.value = ActiveChallengeState(
                changeType = ChangeType.LOWER_COUNTDOWN,
                targetId = "block_countdown",
                targetName = "Block Screen Countdown",
                targetPayload = seconds.toString(),
                description = "reduce block countdown from ${current}s to ${seconds}s"
            )
        } else {
            // RAISING countdown, or changing when no challenge is set → instant, no challenge
            viewModelScope.launch {
                repository.setBlockCountdownSeconds(seconds)
            }
        }
    }

    /** Fire a live preview of the block overlay with current wallpaper, custom message, and countdown */
    fun previewOverlay() {
        val state = uiState.value
        overlayManager.currentPresetId = state.overlayPresetId
        overlayManager.currentDimAlpha = state.overlayDimAlpha
        overlayManager.showOverlay(
            durationSeconds = state.blockCountdownSeconds.coerceAtLeast(3),
            blockType = OverlayBlockType.SCHEDULE_BLOCK,
            targetName = "Preview Mode",
            description = "This is how your block overlay looks!",
            customMessage = state.customBlockMessage.takeIf { it.isNotBlank() }
        )
    }

    // ── Stage 1: Firebase Auth & Partner Actions ──────────────────────────────

    fun openAuthDialog() {
        _authErrorMessage.value = null
        _showAuthDialog.value = true
    }

    fun dismissAuthDialog() {
        _showAuthDialog.value = false
        _authErrorMessage.value = null
    }

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = partnershipRepository.signInWithEmail(email, pass)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                _showAuthDialog.value = false
            } else {
                _authErrorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Sign in failed"
            }
        }
    }

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = partnershipRepository.signUpWithEmail(email, pass)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                _showAuthDialog.value = false
            } else {
                _authErrorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Sign up failed"
            }
        }
    }

    fun signOut() {
        val lockReason = authRepository.getSignOutLockReason()
        if (lockReason != null) {
            _authErrorMessage.value = lockReason
            return
        }
        partnershipRepository.signOut()
    }

    fun sendPartnerInvite(partnerEmail: String) {
        viewModelScope.launch {
            _isPartnerInviteLoading.value = true
            _partnerInviteError.value = null
            val result = partnershipRepository.sendInvite(partnerEmail)
            _isPartnerInviteLoading.value = false
            if (result.isFailure) {
                _partnerInviteError.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to send invite"
            }
        }
    }

    fun cancelPartnerInvite(partnershipId: String) {
        viewModelScope.launch {
            partnershipRepository.declineInvite(partnershipId)
        }
    }

    fun acceptPartnerInvite(partnershipId: String) {
        viewModelScope.launch {
            partnershipRepository.acceptInvite(partnershipId)
        }
    }

    fun declinePartnerInvite(partnershipId: String) {
        viewModelScope.launch {
            partnershipRepository.declineInvite(partnershipId)
        }
    }

    fun requestRevokePartner(partnershipId: String) {
        _activeChallenge.value = ActiveChallengeState(
            changeType = ChangeType.CHANGE_CHALLENGE_TYPE,
            targetId = partnershipId,
            targetName = "Revoke Partner",
            targetPayload = "REVOKE_PARTNER",
            description = "revoke your Accountability Partner"
        )
    }
}
