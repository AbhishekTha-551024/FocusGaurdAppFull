package com.focusguard.app.feature.home.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusguard.app.capability.Capability
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.core.model.AuthUser
import com.focusguard.app.feature.home.ui.components.*
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.feature.settings.ui.components.DistractionFeedControlCard
import com.focusguard.app.feature.settings.ui.components.OverlayWallpaperCard
import com.focusguard.app.feature.settings.ui.SettingsUiState

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Main Screen Composable (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Primary Dashboard Hub:
 *    - The main visual landing screen in FocusGuard's tabbed navigation.
 *    - Synthesizes real-time protection status, active barriers, and quick-toggle controls.
 *
 * 2. Visual Hierarchy & Child Components:
 *    - [HomeHeader]: Top branding bar with quick-link to Deep Focus lockdown and Google Account indicator.
 *    - [MissingPermissionsCard]: Prominent animated warning banners for missing Accessibility,
 *      System Overlay, Usage Stats, or OEM battery killer exemptions.
 *    - [DistractionFeedControlCard]: Quick-toggles for blocking Instagram Reels, YouTube Shorts,
 *      Facebook Reels, Incognito browsing, and auto-blocking new apps.
 *    - [AccountabilityChallengeCard]: Visual commitment barrier status (Self-Commitment typing mode
 *      vs True Hard Mode with Accountability Partner).
 *    - [UninstallProtectionCard]: Anti-tamper Device Admin protection with real-time countdown progress.
 *    - [OverlayWallpaperCard]: Custom block screen preview, background customization, and preset styling.
 *    - [HomeDialogsContainer]: Centralized modal layer for system permission disclosures and challenge confirmations.
 *
 * 3. Lifecycle & Intent Management:
 *    - [LifecycleResumeEffect]: Automatically re-audits system permissions whenever user returns to the screen.
 *    - [rememberLauncherForActivityResult]: Manages Device Admin activation intents and gallery photo picker.
 * =========================================================================================
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    settingsUiState: SettingsUiState,
    onNavigateToTab: (Int, Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFocus: () -> Unit = {},
    onRequestToggleUninstallProtection: (Boolean) -> Unit = {},
    onConfirmEnableUninstallProtection: (Int) -> Unit = {},
    onDismissUninstallDurationDialog: () -> Unit = {},
    onDismissDeviceAdminRequiredDialog: () -> Unit = {},
    onDismissAccountabilityRequiredDialog: () -> Unit = {},
    onDismissPermissionsRequiredDialog: () -> Unit = {},
    onConfirmEnablePendingFeed: () -> Unit = {},
    onDismissPendingFeedWarning: () -> Unit = {},
    onToggleShortsReelsBlock: (Boolean) -> Unit = {},
    onToggleInstagramReelsBlock: (Boolean) -> Unit = {},
    onToggleFacebookReelsBlock: (Boolean) -> Unit = {},
    onToggleYouTubeShortsBlock: (Boolean) -> Unit = {},
    onToggleIncognitoBlock: (Boolean) -> Unit = {},
    onToggleBlockUnsupportedBrowsers: (Boolean) -> Unit = {},
    onToggleAutoBlockNewApps: (Boolean) -> Unit = {},
    onRequestRemoveChallenge: () -> Unit = {},
    onRequestChangeChallenge: (ChallengeType, String, Int) -> Unit = { _, _, _ -> },
    onActivateChallenge: () -> Unit = {},
    onSetCommitmentSentence: (String) -> Unit = {},
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onPickWallpaper: (Uri) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onSetPreset: (Int) -> Unit = {},
    onSetDimAlpha: (Float) -> Unit = {},
    onSetCustomBlockMessage: (String) -> Unit = {},
    onSetBlockCountdownSeconds: (Int) -> Unit = {},
    onPreviewOverlay: () -> Unit = {},
    authUser: AuthUser? = null,
    isAuthLoading: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSendPartnerInvite: (String) -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onNavigateToOemGuidance: () -> Unit = {},
    onRequireSignIn: (String, () -> Unit) -> Unit = { _, _ -> },
    onProceedToSetupChallenge: () -> Unit = {},
    onCancelChallengeSetup: () -> Unit = {},
    onToggleAdultContentBlock: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var explainingCapability by remember { mutableStateOf<Capability?>(null) }
    var isAddChallengeDialogOpen by remember { mutableStateOf(false) }
    var showOemProtectionDialog by remember { mutableStateOf(false) }

    val isFirstResume = remember { true }
    LifecycleResumeEffect(isFirstResume) {
        onRefreshPermissions()
        onPauseOrDispose { }
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshPermissions()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            onRequestToggleUninstallProtection(true)
        }
    }

    fun launchDeviceAdminActivation() {
        com.focusguard.app.service.FocusGuardAccessibilityService.allowAdminActivationWindow()
        val comp = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate FocusGuard Device Admin to enable Uninstall Protection."
            )
        }
        try {
            deviceAdminLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (err: Exception) {
                android.util.Log.d("HomeScreen", "Security settings fallback failed: ${err.message}")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickWallpaper(uri)
    }

    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settingsUiState.isUninstallProtectionEnabled, settingsUiState.uninstallProtectionExpiryTimestamp) {
        if (settingsUiState.isUninstallProtectionEnabled && settingsUiState.uninstallProtectionExpiryTimestamp > 0L) {
            while (true) {
                currentTimeMs = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    FocusGuardTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Forest950, Forest900, Forest950)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            HomeHeader(
                authUser = authUser,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToFocus = onNavigateToFocus
            )

            // Permissions Alert Banner
            MissingPermissionsCard(
                uiState = uiState,
                onOpenAccessibilityDisclosure = { showAccessibilityDisclosure = true },
                onOpenOemProtectionDialog = { showOemProtectionDialog = true }
            )

            // Distraction Feed Control
            DistractionFeedControlCard(
                isInstagramReelsBlocked = settingsUiState.isInstagramReelsBlockEnabled,
                onToggleInstagramReels = onToggleInstagramReelsBlock,
                isFacebookReelsBlocked = settingsUiState.isFacebookReelsBlockEnabled,
                onToggleFacebookReels = onToggleFacebookReelsBlock,
                isYouTubeShortsBlocked = settingsUiState.isYouTubeShortsBlockEnabled,
                onToggleYouTubeShorts = onToggleYouTubeShortsBlock,
                isAutoBlockEnabled = settingsUiState.isAutoBlockNewAppsEnabled,
                onToggleAutoBlock = onToggleAutoBlockNewApps,
                isIncognitoBlocked = settingsUiState.isIncognitoBlockEnabled,
                onToggleIncognito = onToggleIncognitoBlock,
                isBlockUnsupportedBrowsers = settingsUiState.isBlockUnsupportedBrowsersEnabled,
                onToggleBlockUnsupportedBrowsers = onToggleBlockUnsupportedBrowsers,
                isAdultContentBlocked = settingsUiState.isAdultContentBlockEnabled,
                onToggleAdultContent = onToggleAdultContentBlock
            )

            // Accountability Challenge Card
            AccountabilityChallengeCard(
                isChallengeActive = settingsUiState.isChallengeActive,
                challengeType = settingsUiState.challengeType,
                partnership = settingsUiState.partnership,
                onRequestRemoveChallenge = onRequestRemoveChallenge,
                onOpenAddChallengeDialog = { isAddChallengeDialogOpen = true }
            )

            // Uninstall & Tamper Protection Card
            UninstallProtectionCard(
                isDeviceAdminActive = uiState.isDeviceAdminActive,
                isUninstallProtectionEnabled = settingsUiState.isUninstallProtectionEnabled,
                uninstallProtectionExpiryTimestamp = settingsUiState.uninstallProtectionExpiryTimestamp,
                uninstallProtectionDays = settingsUiState.uninstallProtectionDays,
                currentTimeMs = currentTimeMs,
                onRequestToggleUninstallProtection = onRequestToggleUninstallProtection
            )

            // Block Overlay & Behavior
            OverlayWallpaperCard(
                uiState = settingsUiState,
                onPickGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearWallpaper = onClearWallpaper,
                onSetPreset = onSetPreset,
                onSetDimAlpha = onSetDimAlpha,
                onSetCustomMessage = onSetCustomBlockMessage,
                onSetCountdown = onSetBlockCountdownSeconds,
                onPreviewOverlay = onPreviewOverlay
            )
        }

        // Dialogs Container
        HomeDialogsContainer(
            context = context,
            uiState = uiState,
            settingsUiState = settingsUiState,
            isAddChallengeDialogOpen = isAddChallengeDialogOpen,
            onDismissAddChallengeDialog = { isAddChallengeDialogOpen = false },
            showAccessibilityDisclosure = showAccessibilityDisclosure,
            onSetShowAccessibilityDisclosure = { showAccessibilityDisclosure = it },
            showOemProtectionDialog = showOemProtectionDialog,
            onSetShowOemProtectionDialog = { showOemProtectionDialog = it },
            explainingCapability = explainingCapability,
            onSetExplainingCapability = { explainingCapability = it },
            onLaunchDeviceAdminActivation = { launchDeviceAdminActivation() },
            onDismissDeviceAdminRequiredDialog = onDismissDeviceAdminRequiredDialog,
            onDismissAccountabilityRequiredDialog = onDismissAccountabilityRequiredDialog,
            onDismissPermissionsRequiredDialog = onDismissPermissionsRequiredDialog,
            onConfirmEnableUninstallProtection = onConfirmEnableUninstallProtection,
            onDismissUninstallDurationDialog = onDismissUninstallDurationDialog,
            onConfirmEnablePendingFeed = onConfirmEnablePendingFeed,
            onDismissPendingFeedWarning = onDismissPendingFeedWarning,
            onRequestChangeChallenge = onRequestChangeChallenge,
            onChallengeSuccess = onChallengeSuccess,
            onCancelChallenge = onCancelChallenge,
            onCancelChallengeSetup = onCancelChallengeSetup,
            isAuthLoading = isAuthLoading,
            onSignInWithGoogle = onSignInWithGoogle,
            onSendPartnerInvite = onSendPartnerInvite,
            onRefreshPermissions = onRefreshPermissions
        )
    }
}
