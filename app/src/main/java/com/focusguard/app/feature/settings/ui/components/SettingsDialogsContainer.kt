package com.focusguard.app.feature.settings.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.feature.auth.ui.components.PartnerInviteDialog
import com.focusguard.app.feature.settings.ui.SettingsUiState
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog
import com.focusguard.app.ui.components.FeedControlWarningDialog
import com.focusguard.app.ui.components.UninstallProtectionArmingGate

@Composable
fun SettingsDialogsContainer(
    context: Context,
    uiState: SettingsUiState,
    isAuthLoading: Boolean,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit,
    onAcceptPartnerInvite: (String) -> Unit,
    onDeclinePartnerInvite: (String) -> Unit,
    onDismissSupportCodeDialog: () -> Unit,
    onVerifySupportCode: (String) -> SupportVerificationResult,
    onApplySupportAction: suspend (SupportVerificationResult.Success) -> Boolean,
    onSignInWithGoogle: () -> Unit,
    onSendPartnerInvite: (String) -> Unit,
    onRequestChangeChallenge: (ChallengeType, String, Int) -> Unit,
    onDismissAccountabilityRequiredDialog: () -> Unit,
    onDismissPermissionsRequiredDialog: () -> Unit,
    onConfirmEnablePendingFeed: () -> Unit,
    onDismissPendingFeedWarning: () -> Unit,
    onConfirmDirectUninstall: () -> Unit,
    onDismissUninstallConfirmDialog: () -> Unit,
    onConfirmEnableUninstallProtection: (Int) -> Unit,
    onDismissUninstallDurationDialog: () -> Unit
) {
    // ── ACTIVE CHALLENGE VERIFICATION DIALOG ──
    val challenge = uiState.activeChallenge
    if (challenge != null) {
        if (uiState.challengeType == ChallengeType.PARTNER) {
            PartnerApprovalPendingDialog(
                actionDescription = challenge.description,
                partnerEmail = uiState.partnership?.partnerEmail,
                onDismiss = onCancelChallenge
            )
        } else {
            SentenceTypingChallengeDialog(
                actionDescription = challenge.description,
                targetSentence = uiState.commitmentSentence,
                onConfirm = { onChallengeSuccess(context) },
                onCancel = onCancelChallenge
            )
        }
    }

    // ── INCOMING PARTNER INVITE DIALOG ──
    val invite = uiState.pendingIncomingInvite
    if (invite != null) {
        PartnerInviteDialog(
            ownerEmail = invite.ownerEmail,
            onAccept = { onAcceptPartnerInvite(invite.partnershipId) },
            onDecline = { onDeclinePartnerInvite(invite.partnershipId) },
            isLoading = uiState.isPartnerInviteLoading
        )
    }

    // ── SUPPORT CODE DIALOG ──
    if (uiState.showSupportCodeDialog) {
        SupportCodeDialog(
            userEmail = uiState.currentUserEmail,
            cooldownRemainingSeconds = uiState.supportCooldownSeconds,
            onVerifyCode = onVerifySupportCode,
            onApplyAction = onApplySupportAction,
            onDismiss = onDismissSupportCodeDialog
        )
    }

    // ── ACCOUNTABILITY REQUIRED DIALOG ──
    if (uiState.showAccountabilityRequiredDialog) {
        ChangeChallengeTypeDialog(
            currentType = uiState.challengeType,
            currentSentence = uiState.commitmentSentence,
            isUserLoggedIn = uiState.isUserLoggedIn,
            currentUserEmail = uiState.currentUserEmail,
            isAuthLoading = isAuthLoading,
            partnership = uiState.partnership,
            isPartnerInviteLoading = uiState.isPartnerInviteLoading,
            partnerInviteError = uiState.partnerInviteError,
            title = "Set Up Accountability Challenge",
            confirmButtonText = "Activate & Enable Feature",
            onSignInWithGoogle = onSignInWithGoogle,
            onSendPartnerInvite = { email -> onSendPartnerInvite(email) },
            onConfirmChange = { newType, sentence, delay ->
                onRequestChangeChallenge(newType, sentence, delay)
            },
            onDismiss = onDismissAccountabilityRequiredDialog
        )
    }

    // ── PERMISSIONS REQUIRED DIALOG ──
    val permFeature = uiState.permissionsRequiredFeature
    if (permFeature != null) {
        FeaturePermissionsDialog(
            featureName = permFeature,
            isAccessibilityGranted = uiState.permissionStatus.isAccessibilityGranted,
            isOverlayGranted = uiState.permissionStatus.isOverlayGranted,
            isBatteryOptimizationIgnored = com.focusguard.app.utils.BatteryOptimizationHelper.isBatteryOptimizationIgnored(context),
            onEnableAccessibility = {
                onDismissPermissionsRequiredDialog()
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    android.util.Log.d("SettingsDialogs", "Launch accessibility error: ${e.message}")
                }
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

    // ── FEED CONTROL ACTIVATION WARNING DIALOG ──
    val pendingFeed = uiState.pendingFeedWarning
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

    // ── DIRECT UNINSTALL CONFIRMATION DIALOG ──
    if (uiState.showUninstallConfirmDialog) {
        AlertDialog(
            onDismissRequest = onDismissUninstallConfirmDialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Rose400,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Uninstall FocusGuard?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Uninstall Protection is currently inactive. You can proceed directly to the uninstaller.",
                    color = White90,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                var isSubmitting by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            onConfirmDirectUninstall()
                        }
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = Rose400)
                ) {
                    Text("Proceed to Uninstall", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUninstallConfirmDialog) {
                    Text("Cancel", color = White60)
                }
            },
            containerColor = Forest800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // ── UNINSTALL PROTECTION DURATION DIALOG ──
    // Phase B: arming goes through the shared gate — notification rationale + runtime
    // POST_NOTIFICATIONS request, then a fail-visible warning if both recovery channels
    // (overlay + notifications) are missing. onArm is only called after the user passes it.
    if (uiState.showUninstallDurationDialog) {
        UninstallProtectionArmingGate(
            onArm = onConfirmEnableUninstallProtection,
            onDismiss = onDismissUninstallDurationDialog
        )
    }
}
