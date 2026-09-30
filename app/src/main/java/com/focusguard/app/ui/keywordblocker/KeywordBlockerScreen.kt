package com.focusguard.app.ui.keywordblocker

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusguard.app.data.db.BlockedKeywordEntity
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.components.SearchField
import com.focusguard.app.ui.theme.FocusGuardTheme
import java.text.SimpleDateFormat
import java.util.*

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Indigo400 = Color(0xFF22C55E)
private val Amber400  = Color(0xFFFBBF24)
private val Rose400   = Color(0xFFFB7185)
private val White90   = Color(0xE6FFFFFF)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBlockerScreen(
    uiState: KeywordBlockerUiState,
    onKeywordInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (BlockedKeywordEntity) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onCancelPendingChange: (Long) -> Unit = {},
    onActivateChallenge: () -> Unit = {},
    onDismissAccountability: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isAddWebsiteDialogOpen by remember { mutableStateOf(false) }

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Websites & Keywords",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${uiState.blockedKeywords.size} websites blocked",
                                color = if (uiState.blockedKeywords.isNotEmpty()) Indigo400 else White60,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { isAddWebsiteDialogOpen = true },
                    containerColor = Indigo400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Block a Website",
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            containerColor = Navy900
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            listOf(Navy900, Color(0xFF0B1F15), Navy900)
                        )
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    if (uiState.blockedKeywords.isEmpty()) {
                        // Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(Indigo400.copy(alpha = 0.15f))
                                    .border(1.5.dp, Indigo400.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = Indigo400,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "No Websites Blocked Yet",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap the + button below to add distracting websites or search keywords (e.g. reddit.com, twitter.com) to lock them across all browsers.",
                                color = White60,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { isAddWebsiteDialogOpen = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Block", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    } else {
                        // Main Screen: Shows ONLY Blocked Websites List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text(
                                    text = "BLOCKED WEBSITES & KEYWORDS (${uiState.blockedKeywords.size})",
                                    color = White40,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                            }

                            items(uiState.blockedKeywords, key = { "kw_${it.id}_${it.keyword}" }) { item ->
                                BlockedWebsiteRow(
                                    keywordEntity = item,
                                    onDelete = { onRemoveKeyword(item) }
                                )
                            }

                            item {
                                Spacer(Modifier.height(80.dp))
                            }
                        }
                    }
                }

                // ── MODAL DIALOG: Add Website or Keyword ─
                if (isAddWebsiteDialogOpen) {
                    AddWebsiteModalDialog(
                        input = uiState.keywordInput,
                        errorMessage = uiState.errorMessage,
                        onInputChange = onKeywordInputChange,
                        onAdd = {
                            onAddKeyword()
                            if (uiState.errorMessage == null) {
                                isAddWebsiteDialogOpen = false
                            }
                        },
                        onDismiss = {
                            onKeywordInputChange("")
                            isAddWebsiteDialogOpen = false
                        }
                    )
                }

                // ── ACCOUNTABILITY REQUIRED DIALOG (Prompt to turn on challenge) ─
                val pendingKw = uiState.pendingAccountabilityKeyword
                if (pendingKw != null) {
                    com.focusguard.app.ui.components.AccountabilityRequiredDialog(
                        featureName = pendingKw.keyword,
                        subtitle = "Accountability Challenge Required",
                        message = "To unblock website '${pendingKw.keyword}', FocusGuard requires an active Accountability Challenge (Sentence Typing).\n\nTurn ON the challenge now to confirm and proceed with unblocking.",
                        confirmButtonText = "Turn ON Challenge & Unblock",
                        onSetUpChallenge = onActivateChallenge,
                        onDismiss = onDismissAccountability
                    )
                }

                // ── PHASE 6: REMOVAL CHALLENGE DIALOG ─
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
    }
}

@Composable
private fun BlockedWebsiteRow(
    keywordEntity: BlockedKeywordEntity,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo400.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Indigo400.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = keywordEntity.keyword,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Blocked in 7 browsers · 10s shield",
                    color = Indigo400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete keyword",
                    tint = Rose400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddWebsiteModalDialog(
    input: String,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val suggestions = listOf("reddit.com", "twitter.com", "instagram.com", "facebook.com", "twitch.tv", "netflix.com", "discord.com")
    var showConfirmDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Indigo400.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Block Website / Keyword",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = White60)
                    }
                }

                Text(
                    text = "Enter a website domain or search query to block it across Chrome, Firefox, Brave, and Samsung Internet.",
                    color = White60,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("e.g. reddit.com or reels", color = White40, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Navy700,
                        unfocusedContainerColor = Navy700,
                        focusedBorderColor = Indigo400,
                        unfocusedBorderColor = Color(0x33818CF8),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (input.trim().length >= 2) showConfirmDialog = true else onAdd()
                    })
                )

                if (errorMessage != null) {
                    Text(text = errorMessage, color = Rose400, fontSize = 12.sp)
                }

                Text(
                    text = "QUICK SUGGESTIONS",
                    color = White40,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.take(3).forEach { domain ->
                        SuggestionChip(
                            onClick = {
                                onInputChange(domain)
                            },
                            label = { Text(domain, fontSize = 11.sp, color = Indigo400) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Navy700),
                            border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Indigo400.copy(alpha = 0.4f))
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = White60)
                    }

                    Button(
                        onClick = {
                            if (input.trim().length >= 2) showConfirmDialog = true else onAdd()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Block", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        if (showConfirmDialog) {
            val trimmed = input.trim()
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Indigo400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("Block Keyword?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Any webpage, website URL, or browser search containing \"$trimmed\" will be blocked immediately across all browsers.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x1F22C55E),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Indigo400.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🔒 Friction Protection: Removing or unblocking this keyword later will require completing your Accountability Challenge.",
                                color = Indigo400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Text(
                            text = "Do you want to confirm blocking \"$trimmed\"?",
                            color = White60,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            onAdd()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900)
                    ) {
                        Text("Confirm Block", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel", color = White60)
                    }
                },
                containerColor = Navy800,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}
