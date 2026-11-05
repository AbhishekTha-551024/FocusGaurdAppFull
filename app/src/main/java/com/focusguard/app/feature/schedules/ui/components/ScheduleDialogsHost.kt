package com.focusguard.app.feature.schedules.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.schedules.ui.ScheduleUiState
import com.focusguard.app.feature.schedules.ui.ScheduleAccountabilityAction
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog

@Composable
fun ScheduleDialogsHost(
    uiState: ScheduleUiState,
    context: Context,
    showPermissionsDialog: Boolean,
    showDisclosureDialog: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    onCloseAddDialog: () -> Unit,
    onAddSchedule: (String, String?, String?, Int, Int, Int) -> Unit,
    onClosePermissions: () -> Unit,
    onOpenDisclosure: () -> Unit,
    onCloseDisclosure: () -> Unit,
    onActivateChallenge: (ChallengeType, String) -> Unit,
    onDismissAccountability: () -> Unit,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit
) {
    if (uiState.isAddDialogOpen) {
        AddCustomScheduleDialog(
            installedApps = uiState.installedApps,
            onDismiss = onCloseAddDialog,
            onConfirm = onAddSchedule
        )
    }

    if (showPermissionsDialog) {
        FeaturePermissionsDialog(
            featureName = "Focus Schedules",
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
                    android.util.Log.d("ScheduleDialogsHost", "Launch accessibility error: ${e.message}")
                }
            },
            onDismiss = onCloseDisclosure
        )
    }

    uiState.pendingAccountabilitySchedule?.let { pendingSched ->
        val actionVerb = when (pendingSched.action) {
            ScheduleAccountabilityAction.CREATE -> "Create '${pendingSched.schedule.title}'"
            ScheduleAccountabilityAction.ENABLE -> "Enable '${pendingSched.schedule.title}'"
            ScheduleAccountabilityAction.DISABLE -> "Disable '${pendingSched.schedule.title}'"
            ScheduleAccountabilityAction.DELETE -> "Delete '${pendingSched.schedule.title}'"
        }
        ChangeChallengeTypeDialog(
            currentType = uiState.challengeType,
            currentSentence = uiState.commitmentSentence,
            title = "Set Up Accountability Challenge",
            confirmButtonText = "Activate & $actionVerb",
            onConfirmChange = { newType, sentence, _ -> onActivateChallenge(newType, sentence) },
            onDismiss = onDismissAccountability
        )
    }

    uiState.activeChallenge?.let { challenge ->
        if (uiState.challengeType == ChallengeType.PARTNER) {
            PartnerApprovalPendingDialog(
                actionDescription = challenge.description,
                partnerEmail = null,
                statusMessage = "Partner approval is not connected for Focus Schedules yet. No change has been applied.",
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
