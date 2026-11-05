package com.focusguard.app.feature.settings.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*
import com.focusguard.app.feature.settings.ui.SettingsUiState

private val Violet500 = Color(0xFF22C55E)

@Composable
fun OverlayWallpaperCard(
    uiState: SettingsUiState,
    onPickGallery: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSetPreset: (Int) -> Unit,
    onSetDimAlpha: (Float) -> Unit,
    onSetCustomMessage: (String) -> Unit,
    onSetCountdown: (Int) -> Unit,
    onPreviewOverlay: () -> Unit
) {
    var activeEditDialog by remember { mutableStateOf<String?>(null) }
    var editingMessage by remember(uiState.customBlockMessage) { mutableStateOf(uiState.customBlockMessage) }
    var pendingCountdownIncrease by remember { mutableStateOf<Int?>(null) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }
    var isWallpaperDialogOpen by remember { mutableStateOf(false) }

    val cardBg = Forest800
    val borderViolet = Color(0x3322C55E)
    val badgeBg = Color(0xFF0F3822)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.2.dp, borderViolet),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, bottom = 6.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // Row 1: Blocked screen message
                OverlayBehaviorSettingRow(
                    title = "Blocked screen message",
                    subtitle = if (uiState.customBlockMessage.isBlank()) "This page is blocked." else uiState.customBlockMessage,
                    onEditClick = {
                        editingMessage = uiState.customBlockMessage
                        activeEditDialog = "MESSAGE"
                    },
                    onInfoClick = {
                        infoDialogTitle = "Blocked Screen Message"
                        infoDialogMessage = "Custom headline shown in glowing typography when a distraction or blocked app is opened."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Row 2: Blocked screen countdown
                OverlayBehaviorSettingRow(
                    title = "Blocked screen countdown",
                    subtitle = "${uiState.blockCountdownSeconds} seconds",
                    onEditClick = {
                        activeEditDialog = "COUNTDOWN"
                    },
                    onInfoClick = {
                        infoDialogTitle = "Blocked Screen Countdown"
                        infoDialogMessage = "How long the block screen stays visible before automatically exiting the blocked application."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Quick Action Bar: Wallpaper Customize & Preview Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isWallpaperDialogOpen = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, Violet500.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(15.dp), tint = Violet500)
                        Spacer(Modifier.width(6.dp))
                        Text("Wallpaper & Dim", fontSize = 12.sp, color = White90, maxLines = 1)
                    }

                    Button(
                        onClick = onPreviewOverlay,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet500),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // Pill / Badge Header positioned at top left
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = badgeBg,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
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
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Customize Blocked screen",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }

    // Wallpaper & Dim Settings Dialog
    if (isWallpaperDialogOpen) {
        OverlayWallpaperThemeDialog(
            uiState = uiState,
            onSetPreset = onSetPreset,
            onPickGallery = onPickGallery,
            onClearWallpaper = onClearWallpaper,
            onSetDimAlpha = onSetDimAlpha,
            onDismiss = { isWallpaperDialogOpen = false }
        )
    }

    if (infoDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { infoDialogTitle = null },
            title = {
                Text(infoDialogTitle ?: "", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(infoDialogMessage ?: "", color = White90, fontSize = 14.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = { infoDialogTitle = null }) {
                    Text("Got It", color = Violet500, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Navy800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (activeEditDialog == "MESSAGE") {
        OverlayCustomMessageDialog(
            initialMessage = editingMessage,
            onConfirm = {
                onSetCustomMessage(it)
                activeEditDialog = null
            },
            onDismiss = { activeEditDialog = null }
        )
    }

    if (activeEditDialog == "COUNTDOWN") {
        OverlayCountdownDialog(
            currentSeconds = uiState.blockCountdownSeconds,
            onSelectCountdown = { sec ->
                if (sec > uiState.blockCountdownSeconds) {
                    pendingCountdownIncrease = sec
                } else {
                    onSetCountdown(sec)
                    activeEditDialog = null
                }
            },
            onDismiss = { activeEditDialog = null }
        )
    }

    pendingCountdownIncrease?.let { targetSec ->
        OverlayCountdownIncreaseWarningDialog(
            currentSeconds = uiState.blockCountdownSeconds,
            targetSeconds = targetSec,
            isChallengeActive = uiState.isChallengeActive,
            onConfirm = {
                onSetCountdown(targetSec)
                pendingCountdownIncrease = null
                activeEditDialog = null
            },
            onDismiss = { pendingCountdownIncrease = null }
        )
    }
}
