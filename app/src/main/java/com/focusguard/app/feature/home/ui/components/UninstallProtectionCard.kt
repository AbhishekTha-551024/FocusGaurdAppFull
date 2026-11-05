package com.focusguard.app.feature.home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*

/**
 * =========================================================================================
 * ARCHITECTURE COMPONENT: Anti-Tamper UI Card (Home Feature Presentation Layer)
 * =========================================================================================
 *
 * PURPOSE & RESPONSIBILITIES:
 * 1. Anti-Tamper & Uninstall Defense Indicator:
 *    - Prominently showcases the status of Android Device Administrator and Timed Lock Protection
 *      on the Home Dashboard.
 *    - Prevents impulsive deletion of FocusGuard when the user experiences strong urges or cravings
 *      to access restricted content.
 *
 * 2. Visual State Matrix:
 *    - [!isDeviceAdminActive]: Amber warning indicating Device Admin is OFF; tapping prompts activation.
 *    - [isLocked && expiry > 0]: Rose accent showing a timed lock is active (e.g., "7D LOCKED"),
 *      displaying formatted remaining time (e.g., "4d - 6h 32m left") and a live linear countdown progress bar.
 *    - [isLocked && expiry == 0]: Indefinite lock requiring an Accountability Challenge completion to turn off.
 *    - [Unlocked]: Green accent indicating protection is off and the app can be modified or uninstalled.
 *
 * 3. User Interaction & Guard Rails:
 *    - [onRequestToggleUninstallProtection]: Invoked when the user toggles the switch or taps the card.
 *      Enforces challenge solving or duration selection before enabling or disabling.
 * =========================================================================================
 */
@Composable
fun UninstallProtectionCard(
    isDeviceAdminActive: Boolean,
    isUninstallProtectionEnabled: Boolean,
    uninstallProtectionExpiryTimestamp: Long,
    uninstallProtectionDays: Int,
    currentTimeMs: Long,
    onRequestToggleUninstallProtection: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Forest800),
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                if (!isDeviceAdminActive) Amber400.copy(alpha = 0.55f)
                else if (isUninstallProtectionEnabled) Rose400.copy(alpha = 0.45f)
                else Color(0x2E22C55E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable {
                    if (!isUninstallProtectionEnabled) {
                        onRequestToggleUninstallProtection(true)
                    }
                }
        ) {
            val isLocked = isUninstallProtectionEnabled && isDeviceAdminActive &&
                    uninstallProtectionDays >= 1 && uninstallProtectionExpiryTimestamp > 0L
            val expiry = uninstallProtectionExpiryTimestamp
            val totalDays = if (uninstallProtectionDays > 0) uninstallProtectionDays else 1
            val totalDurationMs = totalDays * 24L * 60L * 60L * 1000L
            val startMs = (expiry - totalDurationMs).coerceAtLeast(0L)
            val elapsedMs = (currentTimeMs - startMs).coerceIn(0L, totalDurationMs)

            val remainingMs = (expiry - currentTimeMs).coerceAtLeast(0L)
            val totalRemainingSeconds = remainingMs / 1000L
            val totalRemainingMinutes = totalRemainingSeconds / 60L
            val totalRemainingHours = totalRemainingMinutes / 60L
            val remMinutes = totalRemainingMinutes % 60L

            val remainingFormatted = if (totalRemainingHours > 24L) {
                val days = (totalRemainingHours - 1L) / 24L
                val hours = totalRemainingHours - (days * 24L)
                if (remMinutes > 0L) "${days}d - ${hours}h ${remMinutes}m left" else "${days}d - ${hours}h left"
            } else if (totalRemainingHours > 0L || remMinutes > 0L) {
                "${totalRemainingHours}h ${remMinutes}m left"
            } else {
                "0h 0m left"
            }

            Row(
                modifier = Modifier.padding(
                    top = 15.dp,
                    bottom = if (isLocked && expiry > 0L) 10.dp else 4.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Uninstall protection",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (isLocked && expiry > 0L) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Rose400.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, Rose400.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${totalDays}D LOCKED",
                                    color = Rose400,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    val subtitleText = when {
                        !isDeviceAdminActive -> "Device Admin is OFF · Tap to activate"
                        isLocked -> "Remaining: $remainingFormatted"
                        isUninstallProtectionEnabled -> "Active (Permanent) · Uninstallation Protected"
                        else -> "Disabled (Unlocked) · Can be uninstalled"
                    }
                    Text(
                        text = subtitleText,
                        color = when {
                            !isDeviceAdminActive -> Amber400
                            isLocked || isUninstallProtectionEnabled -> Rose400
                            else -> Success
                        },
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLocked && expiry > 0L) {
                        Spacer(Modifier.height(5.dp))
                        val progress = (elapsedMs.toDouble() / totalDurationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = progress.coerceAtLeast(0.02f))
                                    .fillMaxHeight()
                                    .background(Rose400)
                            )
                        }
                    }
                }

                Switch(
                    checked = isUninstallProtectionEnabled && isDeviceAdminActive,
                    onCheckedChange = { enable ->
                        onRequestToggleUninstallProtection(enable)
                    },
                    modifier = Modifier.scale(0.78f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Rose400,
                        uncheckedThumbColor = White60,
                        uncheckedTrackColor = Navy700
                    )
                )
            }
        }

        // Top-Left Badge
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = Color(0xFF0F3822),
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3322C55E)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Anti-tamper protection",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}
