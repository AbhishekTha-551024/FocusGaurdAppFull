package com.focusguard.app.feature.mindful.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.mindful.ui.MindfulAppsUiState
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog

@Composable
fun MindfulDialogsHost(
    uiState: MindfulAppsUiState,
    context: Context,
    showPermissionsDialog: Boolean,
    showDisclosureDialog: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    onRefreshPermissions: () -> Unit,
    onOpenPermissions: () -> Unit,
    onClosePermissions: () -> Unit,
    onOpenDisclosure: () -> Unit,
    onCloseDisclosure: () -> Unit,
    onConfirmAddMindfulApp: (AppInfo) -> Unit,
    onDismissConfirmAddMindfulApp: () -> Unit,
    onActivateChallenge: (ChallengeType, String) -> Unit,
    onDismissAccountability: () -> Unit,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit
) {
    uiState.pendingConfirmAddApp?.let { appToAdd ->
        MindfulConfirmAddDialog(
            app = appToAdd,
            onConfirm = {
                onRefreshPermissions()
                if (!isAccessibilityEnabled || !isOverlayGranted) {
                    onOpenPermissions()
                    onDismissConfirmAddMindfulApp()
                } else {
                    onConfirmAddMindfulApp(appToAdd)
                }
            },
            onDismiss = onDismissConfirmAddMindfulApp
        )
    }

    if (showPermissionsDialog) {
        FeaturePermissionsDialog(
            featureName = "Mindful Pause",
            isAccessibilityGranted = isAccessibilityEnabled,
            isOverlayGranted = isOverlayGranted,
            onEnableAccessibility = {
                onClosePermissions()
                onOpenDisclosure()
            },
            onEnableOverlay = {
                onClosePermissions()
                val directIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
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
                    android.util.Log.d("MindfulDialogsHost", "Launch accessibility error: ${e.message}")
                }
            },
            onDismiss = onCloseDisclosure
        )
    }

    uiState.pendingAccountabilityApp?.let { pendingApp ->
        ChangeChallengeTypeDialog(
            currentType = uiState.challengeType,
            currentSentence = uiState.commitmentSentence,
            title = "Set Up Accountability Challenge",
            confirmButtonText = "Activate & Track ${pendingApp.appName}",
            onConfirmChange = { newType, sentence, _ -> onActivateChallenge(newType, sentence) },
            onDismiss = onDismissAccountability
        )
    }

    uiState.activeChallenge?.let { challenge ->
        if (uiState.challengeType == ChallengeType.PARTNER) {
            PartnerApprovalPendingDialog(
                actionDescription = challenge.description,
                partnerEmail = null,
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
}
