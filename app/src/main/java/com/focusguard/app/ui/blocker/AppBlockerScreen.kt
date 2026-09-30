package com.focusguard.app.ui.blocker

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.ui.components.SearchField
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityDetails
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.ui.capability.CapabilityExplanationDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.AppListSkeleton
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.AppIconLoader

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Indigo400 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val Amber400  = Color(0xFFFBBF24)
private val Success   = Color(0xFF22C55E)
private val Rose400   = Color(0xFFFB7185)
private val White90   = Color(0xE6FFFFFF)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockerScreen(
    uiState: AppBlockerUiState,
    iconLoader: AppIconLoader,
    onSearchQueryChange: (String) -> Unit,
    onToggleBlock: (AppInfo, Boolean) -> Unit,
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onCancelPendingChange: (Long) -> Unit = {},
    onActivateChallenge: () -> Unit = {},
    onDismissAccountability: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var showExplanationDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }

    fun checkAccessibility() {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        isAccessibilityEnabled = enabledServices.contains("FocusGuardAccessibilityService") ||
                enabledServices.contains(context.packageName)
    }

    LaunchedEffect(Unit) {
        checkAccessibility()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAccessibility()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showExplanationDialog) {
        CapabilityExplanationDialog(
            details = CapabilityDetails(
                capability = Capability.ACCESSIBILITY,
                state = CapabilityState.NEEDS_ACCESS,
                title = "App Blocking Protection",
                featureName = "App Blocker",
                whyNeeded = "FocusGuard needs Accessibility access to detect when a blocked app is opened and display your focus block screen.",
                benefits = listOf(
                    "Instantly blocks distracting apps (social media, games, shorts)",
                    "Shows custom block screen and friction timer",
                    "Enforces your personal focus commitments"
                ),
                privacyPromise = "100% on-device. FocusGuard never reads passwords, private messages, or personal content.",
                actionButtonText = "Enable App Blocking"
            ),
            onEnable = {
                showExplanationDialog = false
                showDisclosureDialog = true
            },
            onDismiss = {
                showExplanationDialog = false
                isAppPickerOpen = true
            }
        )
    }

    if (showDisclosureDialog) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosureDialog = false
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {}
            },
            onDismiss = { showDisclosureDialog = false }
        )
    }

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "App Blocker",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${uiState.blockedApps.size} apps blocked",
                                color = if (uiState.blockedApps.isNotEmpty()) Indigo400 else White60,
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
                    onClick = {
                        if (!isAccessibilityEnabled) {
                            showExplanationDialog = true
                        } else {
                            isAppPickerOpen = true
                        }
                    },
                    containerColor = Indigo400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Apps to Block",
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

                    // ── Skeleton Loading: shows while Room DB emits first data ──────────────
                    if (uiState.isLoading) {
                        AppListSkeleton(count = 7)
                        return@Box
                    }

                    if (uiState.blockedApps.isEmpty() && uiState.pendingReviewApps.isEmpty() && uiState.unblockedApps.isNotEmpty()) {
                        // Empty State: No apps blocked yet
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
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = Indigo400,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "No Apps Blocked Yet",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap the + button below to select distracting apps (like social media, games, or video apps) you want to lock.",
                                color = White60,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    if (!isAccessibilityEnabled) {
                                        showExplanationDialog = true
                                    } else {
                                        isAppPickerOpen = true
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Select Apps to Block", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Main Screen: Shows Pending Review & Blocked Apps
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isAccessibilityEnabled) {
                                item {
                                    Surface(
                                        color = Color(0x22FBBF24),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.45f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showExplanationDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "App Blocking requires Accessibility access. Tap to connect.",
                                                color = White90,
                                                fontSize = 12.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("Enable", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // ── PENDING REVIEW SECTION ───────────────────────────
                            if (uiState.pendingReviewApps.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "PENDING REVIEW (${uiState.pendingReviewApps.size})",
                                        color = Amber400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                                    )
                                }

                                items(
                                    items = uiState.pendingReviewApps,
                                    key = { "pending_${it.packageName}" },
                                    contentType = { "pending_app" }
                                ) { app ->
                                    PendingReviewAppRow(
                                        app = app,
                                        onUnblock = { onToggleBlock(app, false) }
                                    )
                                }

                                item {
                                    Spacer(Modifier.height(6.dp))
                                }
                            }

                            // ── CURRENTLY BLOCKED SECTION ────────────────────────
                            if (uiState.blockedApps.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "CURRENTLY BLOCKED (${uiState.blockedApps.size})",
                                        color = White40,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                                    )
                                }

                                items(
                                    items = uiState.blockedApps,
                                    key = { it.packageName },
                                    contentType = { "blocked_app" }
                                ) { app ->
                                    BlockedAppRow(
                                        app = app,
                                        onUnblock = { onToggleBlock(app, false) }
                                    )
                                }
                            }

                            item {
                                Spacer(Modifier.height(80.dp))
                            }
                        }
                    }
                }

                // ── MODAL APP PICKER DIALOG (Full installed apps list with search) ─
                if (isAppPickerOpen) {
                    AppPickerDialog(
                        uiState = uiState,
                        onSearchQueryChange = onSearchQueryChange,
                        onToggleBlock = onToggleBlock,
                        onDismiss = {
                            onSearchQueryChange("")
                            isAppPickerOpen = false
                        }
                    )
                }

                // ── ACCOUNTABILITY REQUIRED DIALOG (Prompt to turn on challenge) ─
                val pendingApp = uiState.pendingAccountabilityApp
                if (pendingApp != null) {
                    com.focusguard.app.ui.components.AccountabilityRequiredDialog(
                        featureName = pendingApp.appName,
                        subtitle = "Accountability Challenge Required",
                        message = "To unblock ${pendingApp.appName}, FocusGuard requires an active Accountability Challenge (Sentence Typing).\n\nTurn ON the challenge now to confirm and proceed with unblocking.",
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

/**
 * Row for an auto-blocked app waiting for review (New Install or Unsupported Browser)
 */
@Composable
private fun PendingReviewAppRow(
    app: AppInfo,
    onUnblock: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconView(packageName = app.packageName, size = 44.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Amber400.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEW",
                            color = Amber400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (app.isAutoBlockedNewInstall)
                        "New app — blocked by default until reviewed."
                    else
                        "Unsupported browser — blocked by default.",
                    color = Amber400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }

            OutlinedButton(
                onClick = onUnblock,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Review", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Clean row for an actively blocked app on the main screen
 */
@Composable
private fun BlockedAppRow(
    app: AppInfo,
    onUnblock: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Navy800),
        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo400.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconView(packageName = app.packageName, size = 44.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.isAutoBlockedNewInstall) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Amber400.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "AUTO-BLOCKED",
                                color = Amber400,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = if (app.isAutoBlockedNewInstall) "Newly installed · Auto-blocked" else "Shield active · Tap to unblock",
                    color = if (app.isAutoBlockedNewInstall) Amber400 else Indigo400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedButton(
                onClick = onUnblock,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Unblock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Full-screen Searchable Modal Dialog for adding apps to the blocklist
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    uiState: AppBlockerUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleBlock: (AppInfo, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var appToConfirmBlock by remember { mutableStateOf<AppInfo?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy900),
            color = Navy900
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Select Apps to Block",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Tap any app to add it to your 24/7 focus blocklist",
                            color = White60,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = White60
                        )
                    }
                }

                // Search Bar
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SearchField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholderText = "Search installed apps..."
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                // Apps List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.unblockedApps,
                        key = { it.packageName },
                        contentType = { "picker_app" }
                    ) { app ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Navy800),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { appToConfirmBlock = app }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconView(packageName = app.packageName, size = 38.dp)

                                Text(
                                    text = app.appName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = { appToConfirmBlock = app },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Block", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── APP BLOCK CONFIRMATION DIALOG ──
        if (appToConfirmBlock != null) {
            val targetApp = appToConfirmBlock!!
            AlertDialog(
                onDismissRequest = { appToConfirmBlock = null },
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
                        Text("Block ${targetApp.appName}?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Once added to your blocklist, ${targetApp.appName} will be strictly locked 24/7.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                        if (uiState.isChallengeActive) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x1F22C55E),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Success.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔒 Friction Protection Active: Unblocking this app later will require completing your Accountability Challenge (${uiState.challengeType.name}).",
                                    color = Emerald400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Amber400.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Amber400.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ No Accountability Challenge set: To prevent impulsive unblocking, we recommend setting up an Accountability Challenge in Settings.",
                                    color = Amber400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        Text(
                            text = "Do you want to confirm blocking ${targetApp.appName}?",
                            color = White60,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onToggleBlock(targetApp, true)
                            appToConfirmBlock = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900)
                    ) {
                        Text("Block App", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { appToConfirmBlock = null }) {
                        Text("Cancel", color = White60)
                    }
                },
                containerColor = Navy800,
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}
