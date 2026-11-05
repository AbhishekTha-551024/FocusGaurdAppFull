package com.focusguard.app.ui.components

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusguard.app.core.designsystem.theme.Amber400
import com.focusguard.app.core.designsystem.theme.Forest800
import com.focusguard.app.core.designsystem.theme.Rose400
import com.focusguard.app.core.designsystem.theme.White60
import com.focusguard.app.core.designsystem.theme.White90
import com.focusguard.app.feature.home.domain.RecoveryAlertsPolicy

/**
 * UninstallProtectionArmingGate
 * =============================
 * Single shared arming flow for Uninstall Protection (Phase B: make escalation visible).
 * Wraps the existing duration picker so BOTH entry points (Home and Settings) inherit the
 * same behavior without duplicating logic:
 *
 *  1. [UninstallProtectionDurationDialog] — unchanged duration picker.
 *  2. Notification rationale + runtime `POST_NOTIFICATIONS` request at the moment of arming,
 *     the one instant the user is thinking about what recovery means (plan gap G2).
 *  3. Fail-visible warning when BOTH visible recovery channels are missing — overlay permission
 *     off AND notifications denied — so recovery can never become an invisible retry loop
 *     without the user knowing (plan gap G5). Arming then needs an explicit "Arm anyway".
 *
 * The gate never arms protection itself; it calls [onArm] exactly once, only after the user
 * has passed (or explicitly skipped) the visibility steps.
 */
@Composable
fun UninstallProtectionArmingGate(
    onArm: (days: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // null = still picking a duration; non-null = arming in progress, waiting on visibility steps.
    var pendingDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showVisibilityWarning by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> evaluateAndArm(context, granted, pendingDays, showRationale,
        onStateChange = { days, rationale, warning ->
            pendingDays = days; showRationale = rationale; showVisibilityWarning = warning
        }, onArm = onArm)
    }

    // Returning from Android Settings (notification or overlay) must complete or re-check the
    // arming without recreating the screen. A "null" result means: just re-read the device.
    LifecycleResumeEffect(Unit) {
        evaluateAndArm(context, null, pendingDays, showRationale,
            onStateChange = { days, rationale, warning ->
                pendingDays = days; showRationale = rationale; showVisibilityWarning = warning
            }, onArm = onArm)
        onPauseOrDispose { }
    }

    if (pendingDays == null && !showRationale && !showVisibilityWarning) {
        UninstallProtectionDurationDialog(
            onConfirm = { days ->
                pendingDays = days
                if (RecoveryAlertsPolicy.shouldRequestNotificationsOnArm(
                        RecoveryAlertPermissions.areNotificationsGranted(context)
                    )
                ) {
                    showRationale = true
                } else {
                    evaluateAndArm(context, null, days, false,
                        onStateChange = { d, rationale, warning ->
                            pendingDays = d; showRationale = rationale; showVisibilityWarning = warning
                        }, onArm = onArm)
                }
            },
            onDismiss = onDismiss
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                evaluateAndArm(context, null, pendingDays, false,
                    onStateChange = { days, rationale, warning ->
                        pendingDays = days; showRationale = rationale; showVisibilityWarning = warning
                    }, onArm = onArm)
            },
            title = {
                Text("Allow recovery alerts?", color = Color.White, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    text = "If Android turns Accessibility off while Uninstall Protection is on, " +
                        "FocusGuard restores it automatically. A notification is how you see that " +
                        "it happened while you were in another app.",
                    color = White60,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    RecoveryAlertPermissions.requestNotifications(context) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Allow", color = Amber400, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    evaluateAndArm(context, null, pendingDays, false,
                        onStateChange = { days, rationale, warning ->
                            pendingDays = days; showRationale = rationale; showVisibilityWarning = warning
                        }, onArm = onArm)
                }) {
                    Text("Not now", color = White60)
                }
            },
            containerColor = Forest800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showVisibilityWarning) {
        AlertDialog(
            onDismissRequest = {
                showVisibilityWarning = false
                pendingDays = null
                onDismiss()
            },
            title = {
                Text("Recovery may be invisible", color = Color.White, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    text = "Notifications are off and overlay permission is off. FocusGuard will " +
                        "still restore protection automatically, but you may never see how. " +
                        "Enable recovery alerts so restores are always visible.",
                    color = White90,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val days = pendingDays
                    showVisibilityWarning = false
                    pendingDays = null
                    if (days != null) onArm(days)
                }) {
                    Text("Arm anyway", color = Rose400, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    RecoveryAlertPermissions.requestNotifications(context) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Enable alerts", color = Amber400, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Forest800,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

/**
 * Shared re-evaluation after any permission round-trip. Reads the LIVE capability state (the
 * cached UiState can be stale right after returning from Settings), then either arms, escalates
 * to the fail-visible warning, or keeps waiting (rationale still showing).
 *
 * [granted] is the runtime permission result, or null when there is none and the device should
 * simply be re-read.
 */
private fun evaluateAndArm(
    context: Context,
    granted: Boolean?,
    pendingDays: Int?,
    showRationale: Boolean,
    onStateChange: (days: Int?, rationale: Boolean, warning: Boolean) -> Unit,
    onArm: (days: Int) -> Unit
) {
    if (pendingDays == null || showRationale) return
    val notificationsGranted =
        (granted ?: false) || RecoveryAlertPermissions.areNotificationsGranted(context)
    val overlayGranted = RecoveryAlertPermissions.isOverlayGranted(context)
    if (RecoveryAlertsPolicy.shouldWarnBeforeArming(overlayGranted, notificationsGranted)) {
        onStateChange(pendingDays, false, true)
    } else {
        onStateChange(null, false, false)
        onArm(pendingDays)
    }
}
