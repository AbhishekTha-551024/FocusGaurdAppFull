package com.focusguard.app.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.settings.ui.components.*
import com.focusguard.app.security.SupportVerificationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState = SettingsUiState(),
    onRequestChangeChallenge: (ChallengeType, String, Int) -> Unit = { _, _, _ -> },
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onNavigateToOemGuidance: () -> Unit,
    onPickWallpaper: (Uri) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onSetPreset: (Int) -> Unit = {},
    onSetDimAlpha: (Float) -> Unit = {},
    onPreviewOverlay: () -> Unit = {},
    onRequestToggleUninstallProtection: (Boolean) -> Unit = {},
    onConfirmEnableUninstallProtection: (Int) -> Unit = {},
    onDismissUninstallDurationDialog: () -> Unit = {},
    onToggleShortsReelsBlock: (Boolean) -> Unit = {},
    onToggleInstagramReelsBlock: (Boolean) -> Unit = {},
    onToggleFacebookReelsBlock: (Boolean) -> Unit = {},
    onToggleYouTubeShortsBlock: (Boolean) -> Unit = {},
    onToggleIncognitoBlock: (Boolean) -> Unit = {},
    onToggleAutoBlockNewApps: (Boolean) -> Unit = {},
    onConfirmEnablePendingFeed: () -> Unit = {},
    onDismissPendingFeedWarning: () -> Unit = {},
    onDismissAccountabilityRequiredDialog: () -> Unit = {},
    onDismissPermissionsRequiredDialog: () -> Unit = {},
    onProceedToSetupChallenge: () -> Unit = {},
    onCancelChallengeSetup: () -> Unit = {},
    onDismissDeviceAdminRequiredDialog: () -> Unit = {},
    onSetCustomBlockMessage: (String) -> Unit = {},
    onSetBlockCountdownSeconds: (Int) -> Unit = {},
    onOpenSupportCodeDialog: () -> Unit = {},
    onDismissSupportCodeDialog: () -> Unit = {},
    onVerifySupportCode: (String) -> SupportVerificationResult = { SupportVerificationResult.InvalidCode },
    onApplySupportAction: suspend (SupportVerificationResult.Success) -> Boolean = { false },
    authUser: AuthUser? = null,
    isAuthLoading: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSendPartnerInvite: (String) -> Unit = {},
    onCancelPartnerInvite: (String) -> Unit = {},
    onAcceptPartnerInvite: (String) -> Unit = {},
    onDeclinePartnerInvite: (String) -> Unit = {},
    onRequestRevokePartner: (String) -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onNavigateToHomeSetup: () -> Unit = {},
    onUninstallAppClick: () -> Unit = {},
    onConfirmDirectUninstall: () -> Unit = {},
    onDismissUninstallConfirmDialog: () -> Unit = {}
) {
    val context = LocalContext.current

    val isFirstResume = remember { true }
    LifecycleResumeEffect(isFirstResume) {
        onRefreshPermissions()
        onPauseOrDispose { }
    }

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Forest950
                    )
                )
            },
            containerColor = Forest950
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusPageBackground()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Account & Profile Section
                AccountProfileSection(
                    user = authUser,
                    isLoading = isAuthLoading,
                    onSignInWithGoogle = onSignInWithGoogle,
                    onSignOut = onSignOut,
                    isSignOutLocked = uiState.isSignOutLocked,
                    signOutLockReason = uiState.signOutLockReason
                )

                // Emergency Recovery & Support
                EmergencySupportSection(
                    onOpenSupportCodeDialog = onOpenSupportCodeDialog
                )

                // About FocusGuard
                AboutFocusGuardSection()

                Spacer(Modifier.height(30.dp))
            }

            // Dialogs Container
            SettingsDialogsContainer(
                context = context,
                uiState = uiState,
                isAuthLoading = isAuthLoading,
                onChallengeSuccess = onChallengeSuccess,
                onCancelChallenge = onCancelChallenge,
                onAcceptPartnerInvite = onAcceptPartnerInvite,
                onDeclinePartnerInvite = onDeclinePartnerInvite,
                onDismissSupportCodeDialog = onDismissSupportCodeDialog,
                onVerifySupportCode = onVerifySupportCode,
                onApplySupportAction = onApplySupportAction,
                onSignInWithGoogle = onSignInWithGoogle,
                onSendPartnerInvite = onSendPartnerInvite,
                onRequestChangeChallenge = onRequestChangeChallenge,
                onDismissAccountabilityRequiredDialog = onDismissAccountabilityRequiredDialog,
                onDismissPermissionsRequiredDialog = onDismissPermissionsRequiredDialog,
                onConfirmEnablePendingFeed = onConfirmEnablePendingFeed,
                onDismissPendingFeedWarning = onDismissPendingFeedWarning,
                onConfirmDirectUninstall = onConfirmDirectUninstall,
                onDismissUninstallConfirmDialog = onDismissUninstallConfirmDialog,
                onConfirmEnableUninstallProtection = onConfirmEnableUninstallProtection,
                onDismissUninstallDurationDialog = onDismissUninstallDurationDialog
            )
        }
    }
}
