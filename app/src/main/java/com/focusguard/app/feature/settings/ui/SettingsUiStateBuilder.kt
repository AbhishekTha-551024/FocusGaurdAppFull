package com.focusguard.app.feature.settings.ui

import com.focusguard.app.core.datastore.FocusGuardPreferences
import com.focusguard.app.core.model.ActiveChallengeState
import com.focusguard.app.data.repository.ProtectionPermissionStatus

object SettingsUiStateBuilder {

    /**
     * @param uptimeProvedDurationElapsed true only when the monotonic uptime ledger shows the whole
     * configured duration really ran since the lock was armed. Opening the Settings screen disarms
     * an expired lock, so that action must not be reachable by moving the wall clock from
     * Settings > Date & time — the wall clock is one half of the rule, never the whole rule.
     * @param remainingUptimeMs uptime ms still owed by the lock (required - elapsed); 0 for untimed
     * protection, which then keeps the plain wall-clock countdown.
     */
    fun buildBaseState(
        args: Array<Any?>,
        uptimeProvedDurationElapsed: Boolean,
        remainingUptimeMs: Long
    ): Pair<SettingsUiState, Boolean> {
        val settings = args[0] as FocusGuardPreferences.SettingsSnapshot
        val perms = args[1] as ProtectionPermissionStatus
        val challenge = args[2] as? ActiveChallengeState
        val showDurationDialog = args[3] as Boolean
        val showAdminRequiredDialog = args[4] as Boolean
        val showAccountabilityDialog = args[5] as Boolean
        val accountabilityFeature = args[6] as String
        val pendingFeed = args[7] as? String
        val showSupportDialog = args[8] as Boolean
        val showUninstallConfirm = args[9] as Boolean
        val permissionsRequired = args[10] as? String

        val wallClockPastExpiry = System.currentTimeMillis() >= settings.uninstallProtectionExpiryTimestamp
        val isTimedLockExpired = settings.isUninstallProtectionEnabled &&
                settings.uninstallProtectionDays >= 1 &&
                // No expiry timestamp at all stays a corrupt state: disarm, exactly as before.
                (settings.uninstallProtectionExpiryTimestamp <= 0L ||
                 (wallClockPastExpiry && uptimeProvedDurationElapsed))

        val effectiveProtectionEnabled = if (isTimedLockExpired) false else settings.isUninstallProtectionEnabled
        val remainingDays = if (effectiveProtectionEnabled && settings.uninstallProtectionExpiryTimestamp > 0L) {
            val wallRemaining = settings.uninstallProtectionExpiryTimestamp - System.currentTimeMillis()
            // The lock ends when BOTH clocks agree, so the honest countdown is the larger remainder.
            val diff = if (remainingUptimeMs > wallRemaining) remainingUptimeMs else wallRemaining
            if (diff > 0) java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff) + 1 else 0L
        } else 0L

        val state = SettingsUiState(
            isInitialSettingsSnapshotLoaded = true,
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
            showUninstallConfirmDialog = showUninstallConfirm,
            accountabilityRequiredFeature = accountabilityFeature,
            pendingFeedWarning = pendingFeed,
            showSupportCodeDialog = showSupportDialog,
            permissionsRequiredFeature = permissionsRequired,
            permissionStatus = perms,
            overlayWallpaperPath = settings.overlayWallpaperPath,
            overlayPresetId = settings.overlayPresetId,
            overlayDimAlpha = settings.overlayDimAlpha,
            hasCustomWallpaper = settings.overlayWallpaperPath.isNotEmpty(),
            customBlockMessage = settings.customBlockMessage,
            blockCountdownSeconds = settings.blockCountdownSeconds
        )

        return Pair(state, isTimedLockExpired)
    }

    fun buildFullState(
        base: SettingsUiState,
        partnerTriple: Triple<com.google.firebase.auth.FirebaseUser?, com.focusguard.app.core.database.entity.PartnershipEntity?, com.focusguard.app.core.database.entity.PartnershipEntity?>,
        partnerPair: Pair<Boolean, String?>,
        deviceId: String,
        supportCooldownSeconds: Long,
        isSignOutLocked: Boolean,
        signOutLockReason: String?
    ): SettingsUiState = base.copy(
        currentUserEmail = partnerTriple.first?.email,
        isUserLoggedIn = partnerTriple.first != null,
        partnership = partnerTriple.second,
        pendingIncomingInvite = partnerTriple.third,
        isPartnerInviteLoading = partnerPair.first,
        partnerInviteError = partnerPair.second,
        deviceId = deviceId,
        supportCooldownSeconds = supportCooldownSeconds,
        isSignOutLocked = isSignOutLocked,
        signOutLockReason = signOutLockReason
    )
}
