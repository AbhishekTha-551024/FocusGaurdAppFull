package com.focusguard.app.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.utils.BatteryOptimizationHelper

/**
 * MissingPermissionsCard
 * ======================
 * Renders small, sleek individual cards at the top of the Home Screen for each essential
 * permission that is missing (Accessibility, Display Over Other Apps, Battery Optimization).
 *
 * Each card explains concisely why blocking is impacted and provides a 1-tap action to fix it.
 * Usage access is excluded here (handled on demand when user configures time limits).
 * When a permission is granted, its card immediately animates away.
 */
@Composable
fun MissingPermissionsCard(
    uiState: HomeUiState,
    onOpenAccessibilityDisclosure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 1. ACCESSIBILITY (CRITICAL - RED) ──
        AnimatedVisibility(
            visible = !uiState.isAccessibilityEnabled || !uiState.permissionStatus.isAccessibilityGranted,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            SmallPermissionAlertCard(
                icon = Icons.Default.AccessibilityNew,
                accentColor = Color(0xFFEF4444),
                title = "Accessibility is OFF",
                subtitle = "Blocking cannot work without this service",
                buttonText = "Turn ON",
                onClick = onOpenAccessibilityDisclosure
            )
        }

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
                    // Build the direct package-scoped overlay intent
                    val directIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

                    // Check if any activity can handle the direct intent (fails on Vivo/ColorOS/MIUI)
                    val canResolve = context.packageManager.resolveActivity(
                        directIntent, PackageManager.MATCH_DEFAULT_ONLY
                    ) != null

                    if (canResolve) {
                        // Stock Android / Pixel / OnePlus: opens FocusGuard's overlay toggle directly
                        try { context.startActivity(directIntent) } catch (_: Exception) { }
                    } else {
                        // Vivo / ColorOS / MIUI fallback: open the generic overlay apps list
                        // and tell the user to find FocusGuard manually
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
                        } catch (_: Exception) {
                            // Final fallback: app details page
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            } catch (_: Exception) { }
                        }
                    }
                }
            )
        }

        // ── 3. BATTERY RESTRICTION (YELLOW/AMBER) ──
        AnimatedVisibility(
            visible = !uiState.isBatteryOptimizationIgnored && !uiState.permissionStatus.isBatteryOptimizationIgnored,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            SmallPermissionAlertCard(
                icon = Icons.Default.BatteryAlert,
                accentColor = Color(0xFFEAB308),
                title = "Battery Saver Restricting App",
                subtitle = "App may be stopped in background",
                buttonText = "Fix",
                onClick = {
                    BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                }
            )
        }
    }
}

@Composable
private fun SmallPermissionAlertCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Title & Subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 14.sp,
                    maxLines = 1
                )
            }

            // Action Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}
