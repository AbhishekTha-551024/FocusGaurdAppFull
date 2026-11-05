package com.focusguard.app.feature.home.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusguard.app.core.designsystem.theme.Amber400
import com.focusguard.app.core.designsystem.theme.Forest800
import com.focusguard.app.core.designsystem.theme.White60
import com.focusguard.app.feature.home.domain.RecoveryAlertsPolicy
import com.focusguard.app.ui.components.RecoveryAlertPermissions

/**
 * RecoveryAlertsGuard
 * ===================
 * Makes the notification leg of auto-recovery visible:
 *  - prompts once when Uninstall Protection is armed without notification permission;
 *  - keeps a compact warning while protection is on and notifications are unavailable, so a later
 *    revocation cannot quietly re-open the "recovery is invisible" gap.
 *
 * The guard never changes protection state; it only asks for the channel that lets recovery reach
 * the user when the silent and in-app paths cannot.
 */
@Composable
fun RecoveryAlertsGuard(
    isProtectionActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(RecoveryAlertPermissions.areNotificationsGranted(context)) }
    var showPrompt by rememberSaveable { mutableStateOf(false) }
    // null until the first real state arrives: DataStore loads protection as `false` briefly, and
    // that must not be mistaken for the user arming protection on every cold start.
    var lastKnownProtectionActive by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted || RecoveryAlertPermissions.areNotificationsGranted(context)
    }

    // Returning from Android Settings must clear the warning without recreating the screen.
    LifecycleResumeEffect(Unit) {
        notificationsGranted = RecoveryAlertPermissions.areNotificationsGranted(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(isProtectionActive) {
        val previous = lastKnownProtectionActive
        if (previous != null &&
            RecoveryAlertsPolicy.shouldPromptOnArm(previous, isProtectionActive, notificationsGranted)
        ) {
            showPrompt = true
        }
        lastKnownProtectionActive = isProtectionActive
    }

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = {
                Text(
                    text = "Keep recovery alerts visible",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "Uninstall Protection can silently restore app blocking when Android turns " +
                        "Accessibility off. Without notification permission the only fallback is the " +
                        "in-app recovery screen, which Android may refuse to open in the background.",
                    color = White60,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrompt = false
                    RecoveryAlertPermissions.requestNotifications(context) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Allow", color = Amber400, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) {
                    Text("Not now", color = White60)
                }
            },
            containerColor = Forest800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (RecoveryAlertsPolicy.shouldShowWarning(isProtectionActive, notificationsGranted)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Amber400.copy(alpha = 0.12f),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Recovery alerts are off. If Android disables blocking, you may not see how to restore it.",
                    color = Amber400,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    RecoveryAlertPermissions.requestNotifications(context) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Enable", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
