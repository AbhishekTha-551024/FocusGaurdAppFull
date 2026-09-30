package com.focusguard.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.compose.animation.*
import com.focusguard.app.utils.BatteryOptimizationHelper
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.DeviceAdminRequiredDialog
import com.focusguard.app.ui.components.AccountabilityRequiredDialog
import com.focusguard.app.ui.components.FeedControlWarningDialog
import com.focusguard.app.ui.components.UninstallProtectionDurationDialog
import com.focusguard.app.ui.settings.DistractionFeedControlCard
import com.focusguard.app.ui.settings.OverlayWallpaperCard
import com.focusguard.app.ui.settings.SettingsUiState
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityDetails
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.capability.ProtectionHealthSummary
import com.focusguard.app.ui.capability.CapabilityExplanationDialog
import com.focusguard.app.ui.theme.*
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    settingsUiState: SettingsUiState,
    onNavigateToTab: (Int, Int) -> Unit, // tabIndex, subTabIndex
    onNavigateToSettings: () -> Unit,
    onNavigateToFocus: () -> Unit = {},
    // Protection & settings callbacks
    onRequestToggleUninstallProtection: (Boolean) -> Unit = {},
    onConfirmEnableUninstallProtection: (Int) -> Unit = {},
    onDismissUninstallDurationDialog: () -> Unit = {},
    onDismissDeviceAdminRequiredDialog: () -> Unit = {},
    onDismissAccountabilityRequiredDialog: () -> Unit = {},
    onConfirmEnablePendingFeed: () -> Unit = {},
    onDismissPendingFeedWarning: () -> Unit = {},
    onToggleShortsReelsBlock: (Boolean) -> Unit = {},
    onToggleInstagramReelsBlock: (Boolean) -> Unit = {},
    onToggleFacebookReelsBlock: (Boolean) -> Unit = {},
    onToggleYouTubeShortsBlock: (Boolean) -> Unit = {},
    onToggleIncognitoBlock: (Boolean) -> Unit = {},
    onToggleBlockUnsupportedBrowsers: (Boolean) -> Unit = {},
    onToggleAutoBlockNewApps: (Boolean) -> Unit = {},
    onRequestRemoveChallenge: () -> Unit = {},
    onRequestChangeChallenge: (ChallengeType, String, Int) -> Unit = { _, _, _ -> },
    onActivateChallenge: () -> Unit = {},
    onSetCommitmentSentence: (String) -> Unit = {},
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onPickWallpaper: (Uri) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onSetPreset: (Int) -> Unit = {},
    onSetDimAlpha: (Float) -> Unit = {},
    onSetCustomBlockMessage: (String) -> Unit = {},
    onSetBlockCountdownSeconds: (Int) -> Unit = {},
    onPreviewOverlay: () -> Unit = {},
    authUser: com.focusguard.app.data.model.AuthUser? = null,
    isAuthLoading: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSendPartnerInvite: (String) -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onNavigateToOemGuidance: () -> Unit = {},
    onRequireSignIn: (String) -> Unit = {},
    onProceedToSetupChallenge: () -> Unit = {},
    onCancelChallengeSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val isFullyProtected = uiState.isAccessibilityEnabled && uiState.isDeviceAdminActive
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var explainingCapability by remember { mutableStateOf<Capability?>(null) }

    LifecycleResumeEffect(Unit) {
        onRefreshPermissions()
        onPauseOrDispose { }
    }

    // Device admin activation launcher
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshPermissions()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            onRequestToggleUninstallProtection(true)
        }
    }

    fun launchDeviceAdminActivation() {
        com.focusguard.app.service.FocusGuardAccessibilityService.allowAdminActivationWindow()
        val comp = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate FocusGuard Device Admin to enable Uninstall Protection."
            )
        }
        try {
            deviceAdminLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e2: Exception) { /* ignore */ }
        }
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickWallpaper(uri)
    }

    var isAddChallengeDialogOpen by remember { mutableStateOf(false) }
    var showOemProtectionDialog by remember { mutableStateOf(false) }

    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(settingsUiState.isUninstallProtectionEnabled, settingsUiState.uninstallProtectionExpiryTimestamp) {
        if (settingsUiState.isUninstallProtectionEnabled && settingsUiState.uninstallProtectionExpiryTimestamp > 0L) {
            while (true) {
                currentTimeMs = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    FocusGuardTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Forest950, Forest900, Forest950)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Indigo400.copy(alpha = 0.15f))
                            .border(1.dp, Indigo400.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Indigo400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "FocusGuard",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Digital Wellbeing Shield",
                            color = White60,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Account Indicator / Shortcut to Settings
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (authUser != null) Color(0xFF143823) else Forest800)
                            .border(
                                1.dp,
                                if (authUser != null) Emerald500.copy(alpha = 0.5f) else Color(0x3322C55E),
                                CircleShape
                            )
                            .clickable { onNavigateToSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (authUser != null) {
                            Text(
                                text = authUser.displayInitial,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account",
                                tint = White60,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Focus Button (Replaces Settings icon at top of Home)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2222C55E),
                        border = BorderStroke(1.dp, Color(0x6622C55E)),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onNavigateToFocus() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockClock,
                                contentDescription = "Deep Focus",
                                tint = Emerald400,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Focus",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }

            // ── REQUIRED PERMISSIONS ALERT BANNER ───────────────────────
            MissingPermissionsCard(
                uiState = uiState,
                onOpenAccessibilityDisclosure = { showAccessibilityDisclosure = true },
                onOpenOemProtectionDialog = { showOemProtectionDialog = true }
            )

            // ── 0. DISTRACTION FEED CONTROL (YouTube Shorts, Instagram Reels, Facebook Reels) ──
            DistractionFeedControlCard(
                isInstagramReelsBlocked = settingsUiState.isInstagramReelsBlockEnabled,
                onToggleInstagramReels = onToggleInstagramReelsBlock,
                isFacebookReelsBlocked = settingsUiState.isFacebookReelsBlockEnabled,
                onToggleFacebookReels = onToggleFacebookReelsBlock,
                isYouTubeShortsBlocked = settingsUiState.isYouTubeShortsBlockEnabled,
                onToggleYouTubeShorts = onToggleYouTubeShortsBlock,
                isAutoBlockEnabled = settingsUiState.isAutoBlockNewAppsEnabled,
                onToggleAutoBlock = onToggleAutoBlockNewApps,
                isIncognitoBlocked = settingsUiState.isIncognitoBlockEnabled,
                onToggleIncognito = onToggleIncognitoBlock,
                isBlockUnsupportedBrowsers = settingsUiState.isBlockUnsupportedBrowsersEnabled,
                onToggleBlockUnsupportedBrowsers = onToggleBlockUnsupportedBrowsers
            )

            // ── 1. ACCOUNTABILITY CHALLENGE CARD ────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                if (settingsUiState.isChallengeActive) {
                    val isPartnerTier = settingsUiState.challengeType == ChallengeType.PARTNER
                    // ── ACTIVE STATE: Show Active Challenge + Sentence Quote + Remove Button ──
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Forest800),
                        border = androidx.compose.foundation.BorderStroke(
                            1.2.dp,
                            Success.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 15.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPartnerTier) "True Hard Mode" else "Self-Commitment Mode",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isPartnerTier) Amber400 else Success)
                                        )
                                        Text(
                                            text = if (isPartnerTier) {
                                                val pEmail = settingsUiState.partnership?.partnerEmail
                                                if (pEmail != null) "Active · Partner ($pEmail)" else "Active · Partner Approval Required"
                                            } else {
                                                "Active · Typing barrier on (Offline)"
                                            },
                                            color = if (isPartnerTier) Amber400 else Success,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onRequestRemoveChallenge,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose400.copy(alpha = 0.65f)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Rose400.copy(alpha = 0.12f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Rose400,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove", color = Rose400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (isPartnerTier) {
                                val partnerEmail = settingsUiState.partnership?.partnerEmail ?: "No partner linked"
                                val isPending = settingsUiState.partnership?.status == com.focusguard.app.data.model.PartnershipStatus.PENDING_INVITE
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Amber400.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Amber400.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SupervisorAccount,
                                            contentDescription = null,
                                            tint = Amber400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Partner: $partnerEmail",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                            Text(
                                                text = if (isPending) "Invite pending · Awaiting partner acceptance" else "Approval from this partner is required to unblock or modify focus rules",
                                                color = White60,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Top-Left Badge (Active)
                    Surface(
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
                        color = if (isPartnerTier) Color(0xFF2E2006) else Color(0xFF0F3822),
                        shadowElevation = 3.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isPartnerTier) Amber400.copy(alpha = 0.5f) else Color(0x3322C55E)),
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
                                imageVector = if (isPartnerTier) Icons.Default.SupervisorAccount else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isPartnerTier) Amber400 else Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (isPartnerTier) "PARTNER GATED (HARD MODE)" else "FRICTION ON",
                                color = if (isPartnerTier) Amber400 else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                } else {
                    // ── INACTIVE / REMOVED STATE: Show "Add Accountability" Card ──
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Forest800),
                        border = androidx.compose.foundation.BorderStroke(
                            1.2.dp,
                            Amber400.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable {
                                isAddChallengeDialogOpen = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, bottom = 14.dp, start = 14.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Amber400.copy(alpha = 0.12f))
                                    .border(1.dp, Amber400.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accountability Challenge",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "No friction active. Add a barrier to keep your promises.",
                                    color = White60,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            Button(
                                onClick = {
                                    isAddChallengeDialogOpen = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Navy900),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Navy900,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Top-Left Badge (Inactive)
                    Surface(
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
                        color = Color(0xFF231C08),
                        shadowElevation = 3.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.4f)),
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
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = Amber400,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "FRICTION OFF",
                                color = Amber400,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                        }
                    }
                }
            }

            // ── 1. UNINSTALL & TAMPER PROTECTION ────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Forest800),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (!uiState.isDeviceAdminActive) Amber400.copy(alpha = 0.55f)
                        else if (settingsUiState.isUninstallProtectionEnabled) Rose400.copy(alpha = 0.45f)
                        else Color(0x2E22C55E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable {
                            if (!uiState.isDeviceAdminActive) {
                                explainingCapability = Capability.DEVICE_ADMIN
                            } else if (!settingsUiState.isUninstallProtectionEnabled) {
                                onRequestToggleUninstallProtection(true)
                            }
                        }
                ) {
                    val isLocked = settingsUiState.isUninstallProtectionEnabled && uiState.isDeviceAdminActive
                    val expiry = settingsUiState.uninstallProtectionExpiryTimestamp
                    val totalDays = if (settingsUiState.uninstallProtectionDays > 0) settingsUiState.uninstallProtectionDays else 1
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
                                !uiState.isDeviceAdminActive -> "Device Admin is OFF · Tap to activate"
                                isLocked && expiry > 0L -> "Remaining: $remainingFormatted"
                                isLocked -> "Active · Challenge required to turn off"
                                else -> "Disabled (Unlocked) · Can be uninstalled"
                            }
                            Text(
                                text = subtitleText,
                                color = when {
                                    !uiState.isDeviceAdminActive -> Amber400
                                    isLocked -> Rose400
                                    else -> Success
                                },
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                            checked = settingsUiState.isUninstallProtectionEnabled && uiState.isDeviceAdminActive,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (!uiState.isDeviceAdminActive) {
                                        // JIT: Contextual explanation before launching Android system settings
                                        explainingCapability = Capability.DEVICE_ADMIN
                                    } else {
                                        onRequestToggleUninstallProtection(true)
                                    }
                                } else {
                                    onRequestToggleUninstallProtection(false)
                                }
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


            // ── 3. BLOCK OVERLAY & BEHAVIOR ─────────────────────────────
            OverlayWallpaperCard(
                uiState = settingsUiState,
                onPickGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearWallpaper = onClearWallpaper,
                onSetPreset = onSetPreset,
                onSetDimAlpha = onSetDimAlpha,
                onSetCustomMessage = onSetCustomBlockMessage,
                onSetCountdown = onSetBlockCountdownSeconds,
                onPreviewOverlay = onPreviewOverlay
            )
        }


        // ── DEVICE ADMINISTRATOR REQUIRED DIALOG (Uninstall Protection Pre-Check) ──
        if (settingsUiState.showDeviceAdminRequiredDialog) {
            DeviceAdminRequiredDialog(
                onActivate = {
                    onDismissDeviceAdminRequiredDialog()
                    launchDeviceAdminActivation()
                },
                onDismiss = onDismissDeviceAdminRequiredDialog
            )
        }

        // ── ACCOUNTABILITY REQUIRED DIALOG (Protection Pre-Check) ──
        if (settingsUiState.showAccountabilityRequiredDialog) {
            AccountabilityRequiredDialog(
                featureName = settingsUiState.accountabilityRequiredFeature,
                subtitle = "Accountability Challenge Required",
                message = "To enable ${settingsUiState.accountabilityRequiredFeature}, FocusGuard requires an active Accountability Challenge (Sentence Typing).\n\nSet up and turn ON the challenge now to proceed directly with blocking.",
                confirmButtonText = "Turn ON Challenge & Proceed",
                onSetUpChallenge = {
                    onProceedToSetupChallenge()
                    isAddChallengeDialogOpen = true
                },
                onDismiss = onDismissAccountabilityRequiredDialog
            )
        }

        // ── ACCESSIBILITY SERVICE PROMINENT DISCLOSURE DIALOG ──
        if (showAccessibilityDisclosure) {
            AccessibilityDisclosureDialog(
                onAccept = {
                    showAccessibilityDisclosure = false
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (_: Exception) {}
                },
                onDismiss = { showAccessibilityDisclosure = false }
            )
        }

        // ── UNINSTALL PROTECTION DURATION DIALOG ──
        if (settingsUiState.showUninstallDurationDialog) {
            UninstallProtectionDurationDialog(
                onConfirm = { days ->
                    onConfirmEnableUninstallProtection(days)
                },
                onDismiss = onDismissUninstallDurationDialog
            )
        }

        // ── FEED CONTROL ACTIVATION WARNING DIALOG ──
        val pendingFeed = settingsUiState.pendingFeedWarning
        if (pendingFeed != null) {
            val featureName = when (pendingFeed) {
                "shorts_reels" -> "Shorts & Reels Blocker"
                "incognito" -> "Incognito Mode Block"
                "instagram" -> "Instagram Reels Block"
                "facebook" -> "Facebook Reels Block"
                "youtube" -> "YouTube Shorts Block"
                "auto_block" -> "Auto-Block Newly Installed Apps"
                "unsupported_browsers" -> "Block Unsupported Browsers"
                "adult_content" -> "Strict Adult Blocker & SafeSearch"
                else -> "Distraction Feed Control"
            }
            FeedControlWarningDialog(
                featureName = featureName,
                onConfirm = onConfirmEnablePendingFeed,
                onDismiss = onDismissPendingFeedWarning
            )
        }

        // ── ACTIVE CHALLENGE VERIFICATION DIALOG ──
        val challenge = settingsUiState.activeChallenge
        if (challenge != null) {
            if (settingsUiState.challengeType == ChallengeType.PARTNER) {
                PartnerApprovalPendingDialog(
                    actionDescription = challenge.description,
                    partnerEmail = settingsUiState.partnership?.partnerEmail,
                    onDismiss = onCancelChallenge
                )
            } else {
                SentenceTypingChallengeDialog(
                    actionDescription = challenge.description,
                    targetSentence = settingsUiState.commitmentSentence,
                    onConfirm = { onChallengeSuccess(context) },
                    onCancel = onCancelChallenge
                )
            }
        }
        // ── CHOOSE ACCOUNTABILITY CHALLENGE DIALOG ──
        if (isAddChallengeDialogOpen) {
            ChangeChallengeTypeDialog(
                currentType = settingsUiState.challengeType,
                currentSentence = settingsUiState.commitmentSentence,
                isUserLoggedIn = settingsUiState.isUserLoggedIn,
                currentUserEmail = settingsUiState.currentUserEmail,
                isAuthLoading = isAuthLoading,
                partnership = settingsUiState.partnership,
                isPartnerInviteLoading = settingsUiState.isPartnerInviteLoading,
                partnerInviteError = settingsUiState.partnerInviteError,
                title = "Choose Accountability Challenge",
                confirmButtonText = "Activate Challenge",
                onSignInWithGoogle = onSignInWithGoogle,
                onSendPartnerInvite = onSendPartnerInvite,
                onConfirmChange = { newType, sentence, _ ->
                    onRequestChangeChallenge(newType, sentence, 0)
                    isAddChallengeDialogOpen = false
                },
                onDismiss = {
                    isAddChallengeDialogOpen = false
                    onCancelChallengeSetup()
                }
            )
        }


        explainingCapability?.let { cap ->
            val details = remember(cap) {
                uiState.healthSummary.items.find { it.capability == cap } ?: CapabilityDetails(
                    capability = cap,
                    state = CapabilityState.NEEDS_ACCESS,
                    title = cap.name,
                    featureName = cap.name,
                    whyNeeded = "FocusGuard needs this access to power this feature.",
                    benefits = emptyList(),
                    privacyPromise = "100% on-device. No personal data collected.",
                    actionButtonText = "Enable"
                )
            }
            CapabilityExplanationDialog(
                details = details,
                onEnable = {
                    explainingCapability = null
                    when (cap) {
                        Capability.ACCESSIBILITY -> {
                            showAccessibilityDisclosure = true
                        }
                        Capability.DEVICE_ADMIN -> {
                            launchDeviceAdminActivation()
                        }
                        Capability.USAGE_ACCESS -> {
                            try {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            } catch (_: Exception) {}
                        }
                        Capability.OVERLAY -> {
                            try {
                                context.startActivity(Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                            } catch (_: Exception) {}
                        }
                        Capability.NOTIFICATIONS -> {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                }
                            } catch (_: Exception) {}
                        }
                        Capability.BATTERY_OPTIMIZATION, Capability.OEM_BACKGROUND -> {
                            showOemProtectionDialog = true
                        }
                    }
                },
                onDismiss = { explainingCapability = null }
            )
        }

        if (showOemProtectionDialog) {
            com.focusguard.app.ui.protection.OemProtectionDialog(
                onDismiss = { showOemProtectionDialog = false },
                onAcknowledge = {
                    onRefreshPermissions()
                }
            )
        }
    }
}
