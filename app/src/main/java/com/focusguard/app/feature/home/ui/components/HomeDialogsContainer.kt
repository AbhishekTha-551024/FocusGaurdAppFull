package com.focusguard.app.feature.home.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityDetails
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.feature.home.ui.HomeUiState
import com.focusguard.app.ui.capability.CapabilityExplanationDialog
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.DeviceAdminRequiredDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog
import com.focusguard.app.ui.components.FeedControlWarningDialog
import com.focusguard.app.ui.components.UninstallProtectionDurationDialog
import com.focusguard.app.ui.protection.OemProtectionDialog
import com.focusguard.app.feature.settings.ui.SettingsUiState

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Central Dialogs Manager (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Modals & Interstitials Orchestrator:
 *    - Decouples modal dialog rendering from the scrolling layout of [HomeScreen].
 *    - Prevents cluttering the main screen code with complex dialog state logic.
 *
 * 2. Managed Dialog Types:
 *    - [DeviceAdminRequiredDialog]: Prompts user to activate Device Administrator when enabling Uninstall Protection.
 *    - [ChangeChallengeTypeDialog]: Modal to configure typing, time delay, or partner accountability barriers.
 *    - [FeaturePermissionsDialog]: Granular permission explanation when enabling protected features.
 *    - [UninstallProtectionDurationDialog]: Timed lock picker (1–180 days) for anti-uninstall mode.
 *    - [FeedControlWarningDialog]: Confirms blocking short-form distraction feeds (Reels, Shorts).
 *    - [AccessibilityDisclosureDialog]: Google Play policy-compliant user disclosure explaining
 *      exact Accessibility Service usage before opening System Settings.
 *    - [OemProtectionDialog]: Brand-specific instructions for surviving Xiaomi, Vivo, Oppo, and Samsung killers.
 *    - [SentenceTypingChallengeDialog]: Challenge typing verification modal before allowing rule modification.
 *    - [PartnerApprovalPendingDialog]: Displays pending unlock approval request sent to Accountability Partner.
 * =========================================================================================
 */
@Composable
fun HomeDialogsContainer(
    context: Context,
    uiState: HomeUiState,
    settingsUiState: SettingsUiState,
    isAddChallengeDialogOpen: Boolean,
    onDismissAddChallengeDialog: () -> Unit,
    showAccessibilityDisclosure: Boolean,
    onSetShowAccessibilityDisclosure: (Boolean) -> Unit,
    showOemProtectionDialog: Boolean,
    onSetShowOemProtectionDialog: (Boolean) -> Unit,
    explainingCapability: Capability?,
    onSetExplainingCapability: (Capability?) -> Unit,
    onLaunchDeviceAdminActivation: () -> Unit,
    onDismissDeviceAdminRequiredDialog: () -> Unit,
    onDismissAccountabilityRequiredDialog: () -> Unit,
    onDismissPermissionsRequiredDialog: () -> Unit,
    onConfirmEnableUninstallProtection: (Int) -> Unit,
    onDismissUninstallDurationDialog: () -> Unit,
    onConfirmEnablePendingFeed: () -> Unit,
    onDismissPendingFeedWarning: () -> Unit,
    onRequestChangeChallenge: (ChallengeType, String, Int) -> Unit,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit,
    onCancelChallengeSetup: () -> Unit,
    isAuthLoading: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSendPartnerInvite: (String) -> Unit,
    onRefreshPermissions: () -> Unit
) {
    // ── DEVICE ADMINISTRATOR REQUIRED DIALOG ──
    if (settingsUiState.showDeviceAdminRequiredDialog) {
        DeviceAdminRequiredDialog(
            onActivate = {
                onDismissDeviceAdminRequiredDialog()
                onLaunchDeviceAdminActivation()
            },
            onDismiss = onDismissDeviceAdminRequiredDialog
        )
    }

    // ── ACCOUNTABILITY REQUIRED DIALOG ──
    if (settingsUiState.showAccountabilityRequiredDialog) {
        ChangeChallengeTypeDialog(
            currentType = settingsUiState.challengeType,
            currentSentence = settingsUiState.commitmentSentence,
            isUserLoggedIn = settingsUiState.isUserLoggedIn,
            currentUserEmail = settingsUiState.currentUserEmail,
            isAuthLoading = isAuthLoading,
            partnership = settingsUiState.partnership,
            isPartnerInviteLoading = settingsUiState.isPartnerInviteLoading,
            partnerInviteError = settingsUiState.partnerInviteError,
            title = "Set Up Accountability Challenge",
            confirmButtonText = "Activate & Enable Feature",
            onSignInWithGoogle = onSignInWithGoogle,
            onSendPartnerInvite = onSendPartnerInvite,
            onConfirmChange = { newType, sentence, delay ->
                onRequestChangeChallenge(newType, sentence, delay)
            },
            onDismiss = onDismissAccountabilityRequiredDialog
        )
    }

    // ── PERMISSIONS REQUIRED DIALOG ──
    val permFeature = settingsUiState.permissionsRequiredFeature
    if (permFeature != null) {
        FeaturePermissionsDialog(
            featureName = permFeature,
            isAccessibilityGranted = settingsUiState.permissionStatus.isAccessibilityGranted,
            isOverlayGranted = settingsUiState.permissionStatus.isOverlayGranted,
            isBatteryOptimizationIgnored = com.focusguard.app.utils.BatteryOptimizationHelper.isBatteryOptimizationIgnored(context),
            onEnableAccessibility = {
                onDismissPermissionsRequiredDialog()
                onSetShowAccessibilityDisclosure(true)
            },
            onEnableOverlay = {
                onDismissPermissionsRequiredDialog()
                val directIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                if (context.packageManager.resolveActivity(directIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    context.startActivity(directIntent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            },
            onDisableBatteryOptimization = {
                com.focusguard.app.utils.BatteryOptimizationHelper.openDirectBatterySettings(context)
            },
            onDismiss = onDismissPermissionsRequiredDialog
        )
    }

    // ── ACCESSIBILITY SERVICE PROMINENT DISCLOSURE DIALOG ──
    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                onSetShowAccessibilityDisclosure(false)
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    android.util.Log.d("HomeDialogs", "Launch accessibility error: ${e.message}")
                }
            },
            onDismiss = { onSetShowAccessibilityDisclosure(false) }
        )
    }

    // ── UNINSTALL PROTECTION DURATION DIALOG ──
    if (settingsUiState.showUninstallDurationDialog) {
        UninstallProtectionDurationDialog(
            onConfirm = { days ->
                onConfirmEnableUninstallProtection(days)
            },
            onDismiss = onDismissUninstallDurationDialog
        )
    }

    // ── FEED CONTROL ACTIVATION WARNING DIALOG ──
    val pendingFeed = settingsUiState.pendingFeedWarning
    if (pendingFeed != null) {
        val featureName = when (pendingFeed) {
            "shorts_reels" -> "Shorts & Reels Blocker"
            "incognito" -> "Incognito Mode Block"
            "instagram" -> "Instagram Reels Block"
            "facebook" -> "Facebook Reels Block"
            "youtube" -> "YouTube Shorts Block"
            "auto_block" -> "Auto-Block Newly Installed Apps"
            "unsupported_browsers" -> "Block Unsupported Browsers"
            "adult_content" -> "Strict Adult Blocker & SafeSearch"
            else -> "Distraction Feed Control"
        }
        FeedControlWarningDialog(
            featureName = featureName,
            onConfirm = onConfirmEnablePendingFeed,
            onDismiss = onDismissPendingFeedWarning
        )
    }

    // ── ACTIVE CHALLENGE VERIFICATION DIALOG ──
    val challenge = settingsUiState.activeChallenge
    if (challenge != null) {
        if (settingsUiState.challengeType == ChallengeType.PARTNER) {
            PartnerApprovalPendingDialog(
                actionDescription = challenge.description,
                partnerEmail = settingsUiState.partnership?.partnerEmail,
                onDismiss = onCancelChallenge
            )
        } else {
            SentenceTypingChallengeDialog(
                actionDescription = challenge.description,
                targetSentence = settingsUiState.commitmentSentence,
                onConfirm = { onChallengeSuccess(context) },
                onCancel = onCancelChallenge
            )
        }
    }

    // ── CHOOSE ACCOUNTABILITY CHALLENGE DIALOG ──
    if (isAddChallengeDialogOpen) {
        ChangeChallengeTypeDialog(
            currentType = settingsUiState.challengeType,
            currentSentence = settingsUiState.commitmentSentence,
            isUserLoggedIn = settingsUiState.isUserLoggedIn,
            currentUserEmail = settingsUiState.currentUserEmail,
            isAuthLoading = isAuthLoading,
            partnership = settingsUiState.partnership,
            isPartnerInviteLoading = settingsUiState.isPartnerInviteLoading,
            partnerInviteError = settingsUiState.partnerInviteError,
            title = "Choose Accountability Challenge",
            confirmButtonText = "Activate Challenge",
            onSignInWithGoogle = onSignInWithGoogle,
            onSendPartnerInvite = onSendPartnerInvite,
            onConfirmChange = { newType, sentence, _ ->
                onRequestChangeChallenge(newType, sentence, 0)
                onDismissAddChallengeDialog()
            },
            onDismiss = {
                onDismissAddChallengeDialog()
                onCancelChallengeSetup()
            }
        )
    }

    explainingCapability?.let { cap ->
        val details = remember(cap) {
            uiState.healthSummary.items.find { it.capability == cap } ?: CapabilityDetails(
                capability = cap,
                state = CapabilityState.NEEDS_ACCESS,
                title = cap.name,
                featureName = cap.name,
                whyNeeded = "FocusGuard needs this access to power this feature.",
                benefits = emptyList(),
                privacyPromise = "100% on-device. No personal data collected.",
                actionButtonText = "Enable"
            )
        }
        CapabilityExplanationDialog(
            details = details,
            onEnable = {
                onSetExplainingCapability(null)
                when (cap) {
                    Capability.ACCESSIBILITY -> onSetShowAccessibilityDisclosure(true)
                    Capability.DEVICE_ADMIN -> onLaunchDeviceAdminActivation()
                    Capability.USAGE_ACCESS -> {
                        safeStartActivity(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                    Capability.OVERLAY -> {
                        safeStartActivity(context, Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                    Capability.NOTIFICATIONS -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeStartActivity(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                    Capability.BATTERY_OPTIMIZATION, Capability.OEM_BACKGROUND -> {
                        onSetShowOemProtectionDialog(true)
                    }
                }
            },
            onDismiss = { onSetExplainingCapability(null) }
        )
    }

    if (showOemProtectionDialog) {
        OemProtectionDialog(
            onDismiss = { onSetShowOemProtectionDialog(false) },
            onAcknowledge = { onRefreshPermissions() }
        )
    }
}

private fun safeStartActivity(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.d("HomeDialogs", "Intent launch failed, falling back to app details: ${e.message}")
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (err: Exception) {
            android.util.Log.e("HomeDialogs", "App details fallback failed: ${err.message}")
        }
    }
}
