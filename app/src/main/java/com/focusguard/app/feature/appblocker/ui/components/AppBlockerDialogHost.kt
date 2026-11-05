package com.focusguard.app.feature.appblocker.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.appblocker.ui.AppBlockerUiState
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog

@Composable
fun AppBlockerDialogHost(
    uiState: AppBlockerUiState,
    isAppPickerOpen: Boolean,
    showPermissionsDialog: Boolean,
    showDisclosureDialog: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    context: Context,
    onRefreshPermissions: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleBlock: (AppInfo, Boolean) -> Unit,
    onActivateChallenge: (ChallengeType, String) -> Unit,
    onDismissAccountability: () -> Unit,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit,
    onClosePicker: () -> Unit,
    onClosePermissions: () -> Unit,
    onOpenDisclosure: () -> Unit,
    onCloseDisclosure: () -> Unit
) {
    if (isAppPickerOpen) {
        AppPickerDialog(
            uiState = uiState,
            isAccessibilityEnabled = isAccessibilityEnabled,
            isOverlayGranted = isOverlayGranted,
            onRefreshPermissions = onRefreshPermissions,
            onRequestPermissions = onRequestPermissions,
            onSearchQueryChange = onSearchQueryChange,
            onToggleBlock = onToggleBlock,
            onDismiss = onClosePicker
        )
    }

    if (showPermissionsDialog) {
        FeaturePermissionsDialog(
            featureName = "App Blocker",
            isAccessibilityGranted = isAccessibilityEnabled,
            isOverlayGranted = isOverlayGranted,
            onEnableAccessibility = onOpenDisclosure,
            onEnableOverlay = {
                onClosePermissions()
                val directIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                if (context.packageManager.resolveActivity(directIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    context.startActivity(directIntent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
            },
            onDismiss = onClosePermissions
        )
    }

    if (showDisclosureDialog) {
        AccessibilityDisclosureDialog(
            onAccept = {
                onCloseDisclosure()
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                } catch (e: Exception) {
                    android.util.Log.d("AppBlockerDialogHost", "Launch accessibility error: ${e.message}")
                }
            },
            onDismiss = onCloseDisclosure
        )
    }

    val pendingApp = uiState.pendingAccountabilityApp
    if (pendingApp != null) {
        ChangeChallengeTypeDialog(
            currentType = uiState.challengeType,
            currentSentence = uiState.commitmentSentence,
            title = "Set Up Accountability Challenge",
            confirmButtonText = "Activate & Block ${pendingApp.appName}",
            onConfirmChange = { newType, sentence, _ -> onActivateChallenge(newType, sentence) },
            onDismiss = onDismissAccountability
        )
    }

    val challenge = uiState.activeChallenge
    if (challenge != null) {
        if (uiState.challengeType == ChallengeType.PARTNER) {
            PartnerApprovalPendingDialog(actionDescription = challenge.description, partnerEmail = null, onDismiss = onCancelChallenge)
        } else {
            SentenceTypingChallengeDialog(actionDescription = challenge.description, targetSentence = uiState.commitmentSentence, onConfirm = { onChallengeSuccess(context) }, onCancel = onCancelChallenge)
        }
    }
}
