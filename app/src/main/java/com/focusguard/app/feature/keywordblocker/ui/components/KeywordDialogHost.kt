package com.focusguard.app.feature.keywordblocker.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.feature.keywordblocker.ui.KeywordBlockerUiState
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog

/**
 * =====================================================================
 * KeywordDialogHost.kt - Modal Dialog Orchestration Host
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose component Keyword Blocker screen ke saare floating dialogs aur popups
 * ka central manager/orchestrator hai. Screen composable ko clean aur readable rakhne ke liye
 * saare dialog states ko is ek file me consolidate kiya gaya hai.
 *
 * ## 2. Hosted Dialogs & Flows:
 * 1. `AddWebsiteModalDialog`: Input dialog jisme user keyword ya domain type karke block karta hai.
 * 2. `FeaturePermissionsDialog`: Accessibility aur Overlay permission missing hone par settings guide popup.
 * 3. `AccessibilityDisclosureDialog`: Google Play Store policy compliant accessibility disclosure statement.
 * 4. `ChangeChallengeTypeDialog`: First-time keyword block karte waqt accountability challenge setup popup.
 * 5. `SentenceTypingChallengeDialog` / `PartnerApprovalPendingDialog`: Unblock karte waqt security friction challenges.
 *
 * ## 3. Android Intent Handling (Senior Dev Perspective):
 * - `ACTION_MANAGE_OVERLAY_PERMISSION`: Package-specific direct URI intent banata hai
 *   (`Uri.parse("package:${context.packageName}")`). Agar device direct package settings support nahi karta
 *   (kuch OEM ROMs jaise Xiaomi/MIUI), toh graceful fallback generic intent par redirect hota hai.
 * - `ACTION_ACCESSIBILITY_SETTINGS`: Try-catch wrapper me launch hota hai taaki kisi non-standard
 *   Android fork par app crash na ho.
 * =====================================================================
 */
@Composable
fun KeywordDialogHost(
    uiState: KeywordBlockerUiState,
    isAddDialogOpen: Boolean,
    showPermissionsDialog: Boolean,
    showDisclosureDialog: Boolean,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    context: Context,
    onKeywordInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onActivateChallenge: (ChallengeType, String) -> Unit,
    onDismissAccountability: () -> Unit,
    onChallengeSuccess: (Context) -> Unit,
    onCancelChallenge: () -> Unit,
    onCloseAddDialog: () -> Unit,
    onClosePermissions: () -> Unit,
    onOpenDisclosure: () -> Unit,
    onCloseDisclosure: () -> Unit,
    onRequirePermissions: () -> Unit
) {
    if (isAddDialogOpen) {
        AddWebsiteModalDialog(
            input = uiState.keywordInput,
            errorMessage = uiState.errorMessage,
            onInputChange = onKeywordInputChange,
            onAdd = {
                if (!isAccessibilityEnabled || !isOverlayGranted) {
                    onRequirePermissions()
                } else {
                    onAddKeyword()
                    if (uiState.errorMessage == null) onCloseAddDialog()
                }
            },
            onDismiss = {
                onKeywordInputChange("")
                onCloseAddDialog()
            }
        )
    }

    if (showPermissionsDialog) {
        FeaturePermissionsDialog(
            featureName = "Websites & Keywords",
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
                    android.util.Log.d("KeywordDialogHost", "Launch accessibility error: ${e.message}")
                }
            },
            onDismiss = onCloseDisclosure
        )
    }

    val pendingKw = uiState.pendingAccountabilityKeyword
    if (pendingKw != null) {
        ChangeChallengeTypeDialog(
            currentType = uiState.challengeType,
            currentSentence = uiState.commitmentSentence,
            title = "Set Up Accountability Challenge",
            confirmButtonText = "Activate & Block '${pendingKw.keyword}'",
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
