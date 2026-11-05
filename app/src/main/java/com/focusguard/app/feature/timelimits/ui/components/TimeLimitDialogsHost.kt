package com.focusguard.app.feature.timelimits.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityDetails
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.timelimits.ui.TimeLimitUiState
import com.focusguard.app.ui.capability.CapabilityExplanationDialog
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog

/**
 * =====================================================================
 * TimeLimitDialogsHost.kt - Modal Dialog Orchestration Host
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose component Time Limits screen ke saare modal dialogs, permission alerts,
 * aur friction challenge screens ka central coordinator hai.
 *
 * ## 2. Hosted Dialogs & Flows:
 * 1. `CapabilityExplanationDialog`: Android "Usage Access" permission maangne se pehle
 *    user ko clearly explain karta hai ki privacy safe hai aur usage data device par hi rehta hai.
 * 2. `FeaturePermissionsDialog`: Accessibility aur System Alert Window (Overlay) permissions guide popup.
 * 3. `AccessibilityDisclosureDialog`: Google Play policy compliant disclosure statement.
 * 4. `AddLimitPickerDialog`: Full-screen app selector dialog with search and minute picker.
 * 5. `ChangeChallengeTypeDialog`: First-time limit setup accountability popup.
 * 6. `SentenceTypingChallengeDialog` / `PartnerApprovalPendingDialog`: Unblocking/Limit increase friction challenges.
 * =====================================================================
 */
@Composable
fun TimeLimitDialogsHost(
    uiState: TimeLimitUiState,
    context: Context,
    isAddLimitPickerOpen: Boolean,
    showUsageExplanation: Boolean,
    showPermissionsDialog: Boolean,
    showDisclosureDialog: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSetTimeLimit: (AppInfo, Int) -> Unit,
    onRequestUsagePermission: () -> Unit,
    onCloseAddPicker: () -> Unit,
    onCloseUsageExplanation: () -> Unit,
    onOpenAddPicker: () -> Unit,
    onOpenDisclosure: () -> Unit,
    onClosePermissions: () -> Unit,
    onCloseDisclosure: () -> Unit,
    onActivateChallenge: (ChallengeType, String) -> Unit,
    onDismissAccountability: () -> Unit,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit
) {
    if (showUsageExplanation) {
        CapabilityExplanationDialog(
            details = CapabilityDetails(
                capability = Capability.USAGE_ACCESS,
                state = CapabilityState.NEEDS_ACCESS,
                title = "Screen Time & Daily Limits",
                featureName = "App Time Limits",
                whyNeeded = "To measure your daily app usage and enforce your custom time limits, FocusGuard needs Usage Access.",
                benefits = listOf(
                    "Track exact minutes spent inside addictive apps",
                    "Automatically block apps when your daily cap is reached",
                    "Daily usage trends and habit improvement"
                ),
                privacyPromise = "Usage data stays strictly on your phone and is never sent to external servers.",
                actionButtonText = "Enable Screen Time"
            ),
            onEnable = {
                onCloseUsageExplanation()
                onRequestUsagePermission()
            },
            onDismiss = {
                onCloseUsageExplanation()
            }
        )
    }

    if (showPermissionsDialog) {
        FeaturePermissionsDialog(
            featureName = "Daily Time Limits",
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
                    android.util.Log.d("TimeLimitDialogsHost", "Launch accessibility error: ${e.message}")
                }
            },
            onDismiss = onCloseDisclosure
        )
    }

    if (isAddLimitPickerOpen) {
        AddLimitPickerDialog(
            uiState = uiState,
            onSearchQueryChange = onSearchQueryChange,
            onSetLimit = { app, minutes ->
                onSetTimeLimit(app, minutes)
                onCloseAddPicker()
            },
            onDismiss = {
                onSearchQueryChange("")
                onCloseAddPicker()
            }
        )
    }

    uiState.pendingAccountabilityLimit?.let { pendingLimit ->
        val featureName = pendingLimit.app.appName
        val actionVerb = when {
            pendingLimit.actionType == com.focusguard.app.core.model.ChangeType.DISABLE_LIMIT ->
                "Remove Limit from $featureName"
            pendingLimit.currentMinutes == 0 -> "Set Limit on $featureName"
            else -> "Modify Limit for $featureName"
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
                statusMessage = "Partner approval is not connected for Daily Limits yet. No change has been applied.",
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
