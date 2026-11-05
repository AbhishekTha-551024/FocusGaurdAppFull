package com.focusguard.app.feature.settings.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.designsystem.theme.*

@Composable
fun DistractionFeedControlCard(
    isInstagramReelsBlocked: Boolean = false,
    onToggleInstagramReels: (Boolean) -> Unit = {},
    isFacebookReelsBlocked: Boolean = false,
    onToggleFacebookReels: (Boolean) -> Unit = {},
    isYouTubeShortsBlocked: Boolean = false,
    onToggleYouTubeShorts: (Boolean) -> Unit = {},
    isAutoBlockEnabled: Boolean = false,
    onToggleAutoBlock: (Boolean) -> Unit = {},
    isIncognitoBlocked: Boolean = false,
    onToggleIncognito: (Boolean) -> Unit = {},
    isBlockUnsupportedBrowsers: Boolean = false,
    onToggleBlockUnsupportedBrowsers: (Boolean) -> Unit = {},
    isAdultContentBlocked: Boolean = false,
    onToggleAdultContent: (Boolean) -> Unit = {}
) {
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }

    val cardBg = Forest800
    val activeGreen = Emerald500
    val inactiveTrack = Forest700
    val borderRed = Color(0x3322C55E)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.2.dp, borderRed),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, bottom = 4.dp, start = 12.dp, end = 12.dp)
            ) {
                // Row 1: Block Instagram Reels
                DistractionSettingRow(
                    title = "Block Instagram Reels",
                    isChecked = isInstagramReelsBlocked,
                    onCheckedChange = onToggleInstagramReels,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Instagram Reels"
                        infoDialogMessage = "Blocks full-screen algorithmic Instagram Reels specifically! Normal feed posts, profiles, and Direct Messages (DMs) stay fully accessible."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp
                )

                // Row 2: Block Facebook Reels
                DistractionSettingRow(
                    title = "Block Facebook Reels",
                    isChecked = isFacebookReelsBlocked,
                    onCheckedChange = onToggleFacebookReels,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Facebook Reels"
                        infoDialogMessage = "Blocks algorithmic Facebook Reels and short video players specifically! Normal news feed, groups, and Messenger stay fully accessible."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp
                )

                // Row 3: Block YouTube Shorts
                DistractionSettingRow(
                    title = "Block YouTube Shorts",
                    isChecked = isYouTubeShortsBlocked,
                    onCheckedChange = onToggleYouTubeShorts,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block YouTube Shorts"
                        infoDialogMessage = "Blocks algorithmic YouTube Shorts specifically. Regular video watch pages, search, and subscriptions stay fully accessible!"
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp
                )

                // Row 4: Auto-Block New Apps
                DistractionSettingRow(
                    title = "Auto-block newly installed apps",
                    isChecked = isAutoBlockEnabled,
                    onCheckedChange = onToggleAutoBlock,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Auto-Block Newly Installed Apps"
                        infoDialogMessage = "Any new apps downloaded from Google Play or APK are automatically placed in Pending Review & blocked by default, preventing bypass downloads."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp
                )

                // Row 5: Block Incognito Mode
                DistractionSettingRow(
                    title = "Block Incognito & Private Mode",
                    isChecked = isIncognitoBlocked,
                    onCheckedChange = onToggleIncognito,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Incognito & Private Tabs"
                        infoDialogMessage = "Prevents opening private browsing tabs and Secret mode across Chrome, Firefox, Edge, Brave, and Samsung Internet to eliminate loophole browsing."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp
                )

                // Row 6: Block Unsupported Browsers
                DistractionSettingRow(
                    title = "Block Unsupported Browsers",
                    isChecked = isBlockUnsupportedBrowsers,
                    onCheckedChange = onToggleBlockUnsupportedBrowsers,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Unsupported Browsers"
                        infoDialogMessage = "Blocks obscure, unverified, or proxy browsers where URL keyword filtering can be bypassed. Permitted safe browsers: Chrome, Firefox, Brave, Opera, DuckDuckGo, Vivaldi, Spin Safe Browser, Ecosia, Free Adblocker Browser, 1DM, Web Video Cast, Edge, and Samsung Internet."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp
                )

                // Row 7: Adult Content Shield
                DistractionSettingRow(
                    title = "🔞 Strict Adult Content & SafeSearch Shield",
                    isChecked = isAdultContentBlocked,
                    onCheckedChange = onToggleAdultContent,
                    activeColor = activeGreen,
                    inactiveTrack = inactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "🔞 Adult Content Shield"
                        infoDialogMessage = "Comprehensive 3-layer adult content protection:\n\n" +
                            "• Layer 1 — DNS Filter: Blocks 100k+ adult domains system-wide.\n\n" +
                            "• Layer 2 — Keyword Shield: Real-time explicit keyword matching in address bars and search.\n\n" +
                            "• Layer 3 — Loophole Closure: Private tab detection and unmonitored browser blocking."
                    }
                )
            }
        }

        // Pill / Badge Header positioned at top left
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = Color(0xFF334155),
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
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Distraction feed control",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }
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
                    Text("Got It", color = Indigo400, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Navy800,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun DistractionSettingRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color,
    inactiveTrack: Color,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.78f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = activeColor,
                    uncheckedThumbColor = White60,
                    uncheckedTrackColor = inactiveTrack
                )
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = White40,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
