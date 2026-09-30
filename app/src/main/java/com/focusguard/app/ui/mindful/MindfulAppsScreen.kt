package com.focusguard.app.ui.mindful

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.components.AccountabilityRequiredDialog
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.utils.AppIconLoader

private val Forest900 = Color(0xFF07140D)
private val CardBg    = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val TextMuted  = Color(0x99FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulAppsScreen(
    uiState: MindfulAppsUiState,
    iconLoader: AppIconLoader,
    onSearchQueryChange: (String) -> Unit,
    onToggleMindfulApp: (AppInfo, Boolean) -> Unit,
    onConfirmAddMindfulApp: (AppInfo) -> Unit = {},
    onDismissConfirmAddMindfulApp: () -> Unit = {},
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onActivateChallenge: () -> Unit = {},
    onDismissAccountability: () -> Unit = {},
    onUpdateAllowanceMinutes: (packageName: String, minutes: Int) -> Unit,
    onOpenJournal: () -> Unit,
    onDismissJournal: () -> Unit
) {
    val context = LocalContext.current

    if (uiState.showJournalDialog) {
        MindfulIntentJournalDialog(
            logs = uiState.recentLogs,
            walkedAwayCount = uiState.walkedAwayCount,
            consciousOpensCount = uiState.consciousOpensCount,
            onDismiss = onDismissJournal
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Forest900)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Daily Victory & Journal Banner ──
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0x1A22C55E),
            border = BorderStroke(1.dp, Color(0x3322C55E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x2222C55E),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SelfImprovement,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "🔥 ${uiState.walkedAwayCount} Impulses Stopped",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "⏱️ ~${uiState.walkedAwayCount * 15}m screen time saved",
                            fontSize = 11.sp,
                            color = Emerald400
                        )
                    }
                }

                Button(
                    onClick = onOpenJournal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Color(0xFF031A0F)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Journal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Pattern Insights Summary (History -> Patterns -> Insights) ──
        if (uiState.recentLogs.isNotEmpty()) {
            val repeatCount = uiState.recentLogs.count { it.intentCategory == "REFLEX" || it.intentCategory == "WAITING_REPLY" || it.intentCategory == "BOREDOM" }
            val topCategory = uiState.recentLogs
                .filter { !it.wasWalkedAway }
                .groupBy { it.intentCategory }
                .maxByOrNull { it.value.size }?.key ?: "WORK"

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                border = BorderStroke(1.dp, Color(0x2222C55E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("MIND PATTERNS & INSIGHTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald400, letterSpacing = 0.8.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Top Intent", fontSize = 10.sp, color = TextMuted)
                            Text(topCategory, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column {
                            Text("Rebound Opens", fontSize = 10.sp, color = TextMuted)
                            Text("$repeatCount today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (repeatCount > 0) Color(0xFFFBBF24) else Emerald400)
                        }
                        Column {
                            Text("Conscious Opens", fontSize = 10.sp, color = TextMuted)
                            Text("${uiState.consciousOpensCount} sessions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Search Field ──
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search communication / work apps...", color = Color(0x55FFFFFF), fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0x88FFFFFF), modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Emerald500,
                unfocusedBorderColor = Color(0x22FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Emerald400
            ),
            singleLine = true
        )

        Spacer(Modifier.height(14.dp))

        // ── Apps List ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section 1: Mindful Pause Apps
            if (uiState.mindfulApps.isNotEmpty()) {
                item {
                    Text(
                        text = "PAUSED APPS (${uiState.mindfulApps.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald400,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(uiState.mindfulApps, key = { "mindful_${it.packageName}" }) { app ->
                    val allowanceMins = uiState.appAllowanceMap[app.packageName] ?: 5
                    MindfulAppCard(
                        app = app,
                        isMindful = true,
                        allowanceMinutes = allowanceMins,
                        iconLoader = iconLoader,
                        onToggle = { onToggleMindfulApp(app, false) },
                        onUpdateAllowance = { mins -> onUpdateAllowanceMinutes(app.packageName, mins) }
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0x1AFFFFFF))
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Section 2: Available Installed Apps
            item {
                Text(
                    text = "ADD TO MINDFUL PAUSE (${uiState.availableApps.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(uiState.availableApps, key = { "avail_${it.packageName}" }) { app ->
                MindfulAppCard(
                    app = app,
                    isMindful = false,
                    allowanceMinutes = 5,
                    iconLoader = iconLoader,
                    onToggle = { onToggleMindfulApp(app, true) },
                    onUpdateAllowance = { }
                )
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }

        // ── CONFIRMATION DIALOG (When user taps to add an app to Mindful) ──
        val appToAdd = uiState.pendingConfirmAddApp
        if (appToAdd != null) {
            AlertDialog(
                onDismissRequest = onDismissConfirmAddMindfulApp,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("Add to Mindful Apps?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "When you open ${appToAdd.appName}, FocusGuard will introduce an intentional breathing pause, ask your conscious reason, and set a time allowance before granting access.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x1A22C55E),
                            border = BorderStroke(0.8.dp, Emerald400.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 Mindful Intention: Helps you break dopamine loops and pause before opening habitually.",
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Text(
                            text = "Do you want to add ${appToAdd.appName} to Mindful Pause?",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmAddMindfulApp(appToAdd) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Forest900)
                    ) {
                        Text("Add to Mindful", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissConfirmAddMindfulApp) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = CardBg,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // ── ACCOUNTABILITY REQUIRED DIALOG (Prompt to turn on challenge when disabling) ──
        val pendingApp = uiState.pendingAccountabilityApp
        if (pendingApp != null) {
            AccountabilityRequiredDialog(
                featureName = pendingApp.appName,
                subtitle = "Accountability Challenge Required",
                message = "To remove ${pendingApp.appName} from Mindful Tracking, FocusGuard requires an active Accountability Challenge (Sentence Typing).\n\nTurn ON the challenge now to confirm and proceed with removal.",
                confirmButtonText = "Turn ON Challenge & Remove",
                onSetUpChallenge = onActivateChallenge,
                onDismiss = onDismissAccountability
            )
        }

        // ── REMOVAL CHALLENGE DIALOG (Sentence Typing / Partner) ──
        val challenge = uiState.activeChallenge
        if (challenge != null) {
            if (uiState.challengeType == ChallengeType.PARTNER) {
                PartnerApprovalPendingDialog(
                    actionDescription = challenge.description,
                    partnerEmail = null,
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
}

@Composable
private fun MindfulAppCard(
    app: AppInfo,
    isMindful: Boolean,
    allowanceMinutes: Int,
    iconLoader: AppIconLoader? = null,
    onToggle: () -> Unit,
    onUpdateAllowance: (Int) -> Unit
) {
    var showAllowanceMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = BorderStroke(1.dp, if (isMindful) Color(0x3322C55E) else Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            AppIconView(
                packageName = app.packageName,
                size = 38.dp
            )

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isMindful) "Breathing pause on launch" else app.packageName,
                    color = if (isMindful) Emerald400 else TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Allowance duration picker pill (only when enabled)
            if (isMindful) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x2222C55E),
                        border = BorderStroke(1.dp, Color(0x4422C55E)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAllowanceMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${allowanceMinutes}m",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showAllowanceMenu,
                        onDismissRequest = { showAllowanceMenu = false }
                    ) {
                        listOf(1, 2, 5, 10, 15).forEach { mins ->
                            DropdownMenuItem(
                                text = { Text("${mins} Minutes Allowance", fontSize = 13.sp) },
                                onClick = {
                                    onUpdateAllowance(mins)
                                    showAllowanceMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
            }

            // Switch
            Switch(
                checked = isMindful,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF031A0F),
                    checkedTrackColor = Emerald500,
                    uncheckedThumbColor = Color(0x88FFFFFF),
                    uncheckedTrackColor = Color(0x22FFFFFF)
                )
            )
        }
    }
}
