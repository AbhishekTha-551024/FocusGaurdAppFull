package com.focusguard.app.feature.home.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusguard.app.feature.home.ui.HomeUiState
import com.focusguard.app.utils.BatteryOptimizationHelper

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Permissions Alert Banner (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Proactive Permission Deficit Warning:
 *    - Automatically displays distinct, animated warning cards when critical OS permissions
 *      are missing or were revoked by the user or operating system.
 *    - Each card animates smoothly in/out via [AnimatedVisibility] as permissions change.
 *
 * 2. Monitored Permission Layers:
 *    - 1. ACCESSIBILITY (CRITICAL - RED):
 *         FocusGuard's engine cannot detect foreground app switches or inspect UI node trees
 *         without Accessibility. Directs user to disclosure modal then system accessibility screen.
 *    - 2. SYSTEM_ALERT_WINDOW / OVERLAY (AMBER):
 *         Required to display the blocking overlay over target applications on all OEM devices.
 *    - 3. USAGE STATS (AMBER):
 *         Required by [UsageStatsReader] to compute daily screen time limits per app.
 *    - 4. OEM BATTERY RESTRICTION & AUTO-START SHIELD (AMBER):
 *         Detects OEM-specific battery killers and deep-links directly into device battery settings
 *         ([BatteryOptimizationHelper.openDirectBatterySettings]) to exempt FocusGuard from sleep kills.
 * =========================================================================================
 */
@Composable
fun MissingPermissionsCard(
    uiState: HomeUiState,
    onOpenAccessibilityDisclosure: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenOemProtectionDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isProblematicOem = remember { BatteryOptimizationHelper.isProblematicOem() }
    val oemActionSummary = remember { BatteryOptimizationHelper.getOemActionSummary() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccessibilityReadinessCard(
            uiState = uiState,
            onOpenAccessibilityDisclosure = onOpenAccessibilityDisclosure,
            onRefreshPermissions = onRefreshPermissions
        )

        // ── 2. OVERLAY PERMISSION (AMBER) ──
        AnimatedVisibility(
            visible = !uiState.isOverlayGranted && !uiState.permissionStatus.isOverlayGranted,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            SmallPermissionAlertCard(
                icon = Icons.Default.Layers,
                accentColor = Color(0xFFF59E0B),
                title = "Display Over Other Apps OFF",
                subtitle = "Block screen cannot appear over apps",
                buttonText = "Allow",
                onClick = {
                    val directIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

                    val canResolve = context.packageManager.resolveActivity(
                        directIntent, PackageManager.MATCH_DEFAULT_ONLY
                    ) != null

                    if (canResolve) {
                        try {
                            context.startActivity(directIntent)
                        } catch (e: Exception) {
                            android.util.Log.d("MissingPermissionsCard", "Direct overlay intent failed: ${e.message}")
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Find \"FocusGuard\" in the list and enable \"Display over other apps\"",
                            Toast.LENGTH_LONG
                        ).show()
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.d("MissingPermissionsCard", "ACTION_MANAGE_OVERLAY_PERMISSION failed: ${e.message}")
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            } catch (err: Exception) {
                                android.util.Log.e("MissingPermissionsCard", "App details settings fallback failed: ${err.message}")
                            }
                        }
                    }
                }
            )
        }

        // ── 2.5 USAGE ACCESS (AMBER) ──
        val isUsageGranted = uiState.isUsageAccessGranted || uiState.permissionStatus.isUsageAccessGranted
        AnimatedVisibility(
            visible = !isUsageGranted,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            SmallPermissionAlertCard(
                icon = Icons.Default.Timer,
                accentColor = Color(0xFFF59E0B),
                title = "Usage Access is OFF",
                subtitle = "Required to accurately track daily app time limits",
                buttonText = "Allow",
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        } catch (err: Exception) {
                            android.util.Log.d("MissingPermissionsCard", "Settings fallback error: ${err.message}")
                        }
                    }
                }
            )
        }

        // ── 3. OEM AUTO-START & BATTERY RESTRICTION SHIELD ──
        val isBatteryExempt = uiState.isBatteryOptimizationIgnored ||
                uiState.permissionStatus.isBatteryOptimizationIgnored ||
                BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)

        val isOemShieldNeeded = !isBatteryExempt

        AnimatedVisibility(
            visible = isOemShieldNeeded,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            SmallPermissionAlertCard(
                icon = Icons.Default.BatteryAlert,
                accentColor = Color(0xFFF59E0B),
                title = BatteryOptimizationHelper.getOemProtectionTitle(),
                subtitle = if (isProblematicOem) oemActionSummary else "Allow unrestricted background power so blocking stays active",
                buttonText = "Settings",
                onClick = {
                    BatteryOptimizationHelper.openDirectBatterySettings(context)
                }
            )
        }
    }
}
