package com.focusguard.app.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.database.entity.ChangeType
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.data.repository.FocusGuardRepository
import com.focusguard.app.data.repository.PermissionStatusRepository
import com.focusguard.app.feature.auth.domain.repository.AuthRepository
import com.focusguard.app.feature.settings.domain.SettingsFeedUseCase
import com.focusguard.app.feature.settings.domain.SettingsOverlayUseCase
import com.focusguard.app.feature.settings.domain.SettingsPartnerUseCase
import com.focusguard.app.feature.settings.domain.SettingsProtectionUseCase
import com.focusguard.app.security.SupportVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: FocusGuardRepository,
    private val feedUseCase: SettingsFeedUseCase,
    private val overlayUseCase: SettingsOverlayUseCase,
    private val protectionUseCase: SettingsProtectionUseCase,
    private val partnerUseCase: SettingsPartnerUseCase,
    private val permissionStatusRepository: PermissionStatusRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _activeChallenge = MutableStateFlow<ActiveChallengeState?>(null)
    private val _showUninstallDurationDialog = MutableStateFlow(false)
    private val _showDeviceAdminRequiredDialog = MutableStateFlow(false)
    private val _showAccountabilityRequiredDialog = MutableStateFlow(false)
    private val _showUninstallConfirmDialog = MutableStateFlow(false)
    private val _accountabilityRequiredFeature = MutableStateFlow("Uninstall Protection")
    private val _pendingFeedWarning = MutableStateFlow<String?>(null)
    private val _showSupportCodeDialog = MutableStateFlow(false)
    private val _permissionsRequiredFeature = MutableStateFlow<String?>(null)

    private val _isPartnerInviteLoading = MutableStateFlow(false)
    private val _partnerInviteError = MutableStateFlow<String?>(null)

    private var pendingFeatureAfterChallenge: String? = null
    val deviceId: String = repository.getDeviceId()

    private val partnerFormState = combine(_isPartnerInviteLoading, _partnerInviteError) { loading, err ->
        Pair(loading, err)
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
        _showSupportCodeDialog,
        _showUninstallConfirmDialog,
        _permissionsRequiredFeature
    ) { args: Array<Any?> ->
        val (state, isTimedLockExpired) = SettingsUiStateBuilder.buildBaseState(args)
        if (isTimedLockExpired) viewModelScope.launch { repository.setUninstallProtectionEnabled(false) }
        state
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        baseSettingsFlow,
        partnerUseCase.partnerDataFlow,
        partnerFormState
    ) { base, partnerTriple, formList ->
        SettingsUiStateBuilder.buildFullState(
            base, partnerTriple, formList, deviceId,
            repository.getSupportCooldownRemainingSeconds(),
            authRepository.isSignOutLocked(),
            authRepository.getSignOutLockReason()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsUiState(
            deviceId = repository.getDeviceId(),
            supportCooldownSeconds = repository.getSupportCooldownRemainingSeconds(),
            permissionStatus = permissionStatusRepository.status.value
        )
    )

    fun refreshPermissions() = permissionStatusRepository.refresh()
    fun ensurePermissionsGranted(featureName: String): Boolean {
        val status = permissionStatusRepository.status.value
        if (!status.isAccessibilityGranted || !status.isOverlayGranted) {
            _permissionsRequiredFeature.value = featureName
            return false
        }
        return true
    }
    fun dismissPermissionsRequiredDialog() { _permissionsRequiredFeature.value = null }

    private fun handleFeedToggle(id: String, name: String, enable: Boolean) {
        if (enable) {
            if (!ensurePermissionsGranted(name)) return
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "feed_$id"
                _accountabilityRequiredFeature.value = name
                _showAccountabilityRequiredDialog.value = true
                return
            }
            _pendingFeedWarning.value = id
        } else {
            if (uiState.value.isChallengeActive) {
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.DISABLE_DISTRACTION_FEED,
                    targetId = id,
                    targetName = name,
                    targetPayload = "false",
                    description = "turn off $name protection"
                )
            } else {
                viewModelScope.launch { feedUseCase.disableFeed(id) }
            }
        }
    }

    fun toggleShortsReelsBlock(enable: Boolean) = handleFeedToggle("shorts_reels", "Shorts & Reels Blocker", enable)
    fun toggleInstagramReelsBlock(enable: Boolean) = handleFeedToggle("instagram", "Instagram Reels", enable)
    fun toggleFacebookReelsBlock(enable: Boolean) = handleFeedToggle("facebook", "Facebook Reels", enable)
    fun toggleYouTubeShortsBlock(enable: Boolean) = handleFeedToggle("youtube", "YouTube Shorts", enable)
    fun toggleIncognitoBlock(enable: Boolean) = handleFeedToggle("incognito", "Incognito Mode", enable)
    fun toggleBlockUnsupportedBrowsers(enable: Boolean) = handleFeedToggle("unsupported_browsers", "Unsupported Browsers", enable)
    fun toggleAutoBlockNewApps(enable: Boolean) = handleFeedToggle("auto_block", "Auto-Block New Apps", enable)
    fun toggleNsfwBlock(enable: Boolean) = handleFeedToggle("adult_content", "Adult & NSFW Content Blocker", enable)
    fun toggleAdultContentBlock(enable: Boolean) = toggleNsfwBlock(enable)
    fun toggleSearchShield(enable: Boolean) = handleFeedToggle("search_shield", "SafeSearch Shield", enable)
    fun toggleNotificationsBlock(enable: Boolean) = handleFeedToggle("notifications", "Notification Guard", enable)

    fun confirmFeedEnable() {
        val id = _pendingFeedWarning.value ?: return
        viewModelScope.launch { feedUseCase.enableFeed(id) }
        _pendingFeedWarning.value = null
    }
    fun confirmEnablePendingFeed() = confirmFeedEnable()
    fun dismissFeedWarning() { _pendingFeedWarning.value = null }
    fun dismissPendingFeedWarning() = dismissFeedWarning()

    fun requestToggleUninstallProtection(enable: Boolean = !uiState.value.isUninstallProtectionEnabled) {
        if (enable) {
            if (!ensurePermissionsGranted("Uninstall Protection")) return
            if (!uiState.value.isChallengeActive) {
                pendingFeatureAfterChallenge = "uninstall_protection"
                _accountabilityRequiredFeature.value = "Uninstall Protection"
                _showAccountabilityRequiredDialog.value = true
                return
            }
            if (!uiState.value.permissionStatus.isDeviceAdminGranted) {
                _showDeviceAdminRequiredDialog.value = true
                return
            }
            _showUninstallDurationDialog.value = true
        } else {
            val remainingDays = repository.getFastTimedLockRemainingDays()
            if (uiState.value.isChallengeActive) {
                val desc = if (remainingDays > 0) {
                    "turn off Uninstall Protection ($remainingDays days remaining in timed lock)"
                } else {
                    "turn off Uninstall Protection"
                }
                _activeChallenge.value = ActiveChallengeState(
                    changeType = ChangeType.UNINSTALL_REQUEST,
                    targetId = "uninstall_protection",
                    targetName = "Uninstall Protection",
                    targetPayload = "false",
                    description = desc
                )
            } else {
                viewModelScope.launch { protectionUseCase.disableProtection() }
            }
        }
    }

    fun confirmUninstallProtectionDuration(days: Int) {
        viewModelScope.launch { protectionUseCase.startTimedProtection(days) }
        _showUninstallDurationDialog.value = false
    }
    fun confirmEnableUninstallProtection(days: Int) = confirmUninstallProtectionDuration(days)
    fun dismissUninstallDurationDialog() { _showUninstallDurationDialog.value = false }
    fun dismissDeviceAdminRequiredDialog() { _showDeviceAdminRequiredDialog.value = false }
    fun dismissAccountabilityRequiredDialog() {
        _showAccountabilityRequiredDialog.value = false
        pendingFeatureAfterChallenge = null
    }

    fun activateChallengeAndApplyPending() {
        viewModelScope.launch {
            repository.setChallengeActive(true)
            triggerPendingFeatureAfterChallenge()
        }
    }
    fun proceedToSetupChallenge() = activateChallengeAndApplyPending()
    fun cancelChallengeSetup() = dismissAccountabilityRequiredDialog()
    fun activateChallenge() = activateChallengeAndApplyPending()

    private fun triggerPendingFeatureAfterChallenge() {
        val pending = pendingFeatureAfterChallenge
        pendingFeatureAfterChallenge = null
        _showAccountabilityRequiredDialog.value = false
        when {
            pending == "uninstall_protection" -> {
                if (!uiState.value.permissionStatus.isDeviceAdminGranted) {
                    _showDeviceAdminRequiredDialog.value = true
                } else {
                    _showUninstallDurationDialog.value = true
                }
            }
            pending?.startsWith("feed_") == true -> {
                _pendingFeedWarning.value = pending.removePrefix("feed_")
            }
        }
    }

    fun requestDisableChallenge() {
        val remainingDays = repository.getFastTimedLockRemainingDays()
        _activeChallenge.value = SettingsChallengeHandler.createRemoveChallengeState(remainingDays)
    }
    fun requestRemoveChallenge() = requestDisableChallenge()

    fun requestChangeChallenge(type: ChallengeType, sentence: String, delayHours: Int) {
        if (uiState.value.isChallengeActive) {
            _activeChallenge.value = SettingsChallengeHandler.createChangeChallengeState(type, sentence, delayHours)
        } else {
            viewModelScope.launch {
                repository.setChallengeType(type)
                repository.setCommitmentSentence(sentence)
                repository.setTimeDelayHours(delayHours)
                repository.setChallengeActive(true)
                triggerPendingFeatureAfterChallenge()
            }
        }
    }

    fun onChallengeSuccess(context: Context) {
        val challenge = _activeChallenge.value ?: return
        viewModelScope.launch {
            SettingsChallengeHandler.resolveChallengeSuccess(challenge, context, feedUseCase, overlayUseCase, protectionUseCase)
            _activeChallenge.value = null
        }
    }
    fun cancelChallenge() { _activeChallenge.value = null }
    fun onCancelChallenge() = cancelChallenge()
    fun saveWallpaperFromUri(uri: Uri) = viewModelScope.launch { overlayUseCase.saveWallpaper(uri) }
    fun clearWallpaper() = viewModelScope.launch { overlayUseCase.clearWallpaper() }
    fun setOverlayPreset(presetId: Int) = viewModelScope.launch { overlayUseCase.setPreset(presetId) }
    fun setPreset(id: Int) = setOverlayPreset(id)
    fun setOverlayDimAlpha(dimAlpha: Float) = viewModelScope.launch { overlayUseCase.setDimAlpha(dimAlpha) }
    fun setDimAlpha(alpha: Float) = setOverlayDimAlpha(alpha)
    fun setCustomBlockMessage(message: String) = viewModelScope.launch { overlayUseCase.setCustomBlockMessage(message) }

    fun setBlockCountdownSeconds(seconds: Int) {
        val currentSeconds = uiState.value.blockCountdownSeconds
        if (seconds < currentSeconds && uiState.value.isChallengeActive) {
            _activeChallenge.value = SettingsChallengeHandler.createLowerCountdownChallenge(currentSeconds, seconds)
            return
        }
        viewModelScope.launch { overlayUseCase.setBlockCountdownSeconds(seconds) }
    }

    fun previewOverlay() = with(uiState.value) {
        overlayUseCase.previewOverlay(overlayPresetId, overlayDimAlpha, blockCountdownSeconds, customBlockMessage)
    }
    fun openSupportCodeDialog() { _showSupportCodeDialog.value = true }
    fun dismissSupportCodeDialog() { _showSupportCodeDialog.value = false }
    fun verifySupportCode(code: String): SupportVerificationResult = protectionUseCase.verifySupportCode(code)

    suspend fun applySupportAction(verified: SupportVerificationResult.Success): Boolean =
        protectionUseCase.executeSupportAction(verified)

    fun sendPartnerInvite(email: String) {
        viewModelScope.launch {
            _isPartnerInviteLoading.value = true
            _partnerInviteError.value = null
            try {
                partnerUseCase.sendInvite(email).onFailure { _partnerInviteError.value = it.localizedMessage }
            } catch (e: Exception) {
                _partnerInviteError.value = e.localizedMessage ?: "Failed to send invitation."
            } finally {
                _isPartnerInviteLoading.value = false
            }
        }
    }
    fun cancelPartnerInvite(partnershipId: String) = viewModelScope.launch { partnerUseCase.cancelInvite(partnershipId) }
    fun acceptPartnerInvite(partnershipId: String) = viewModelScope.launch { partnerUseCase.acceptInvite(partnershipId) }
    fun declinePartnerInvite(partnershipId: String) = viewModelScope.launch { partnerUseCase.declineInvite(partnershipId) }
    fun requestRevokePartner(partnershipId: String) {
        val remainingDays = repository.getFastTimedLockRemainingDays()
        _activeChallenge.value = SettingsChallengeHandler.createRevokePartnerChallenge(partnershipId, remainingDays)
    }
    fun requestUninstallApp() {
        if (uiState.value.isUninstallProtectionEnabled || uiState.value.isChallengeActive) {
            val remainingDays = repository.getFastTimedLockRemainingDays()
            _activeChallenge.value = SettingsChallengeHandler.createUninstallChallenge(remainingDays)
        } else {
            _showUninstallConfirmDialog.value = true
        }
    }
    fun confirmDirectUninstall(context: Context) {
        _showUninstallConfirmDialog.value = false
        viewModelScope.launch {
            protectionUseCase.disableProtection()
            com.focusguard.app.utils.UninstallHelper.performCleanUninstall(context)
        }
    }
    fun dismissUninstallConfirmDialog() { _showUninstallConfirmDialog.value = false }
    fun setCommitmentSentence(sentence: String) = viewModelScope.launch { repository.setCommitmentSentence(sentence) }
}
