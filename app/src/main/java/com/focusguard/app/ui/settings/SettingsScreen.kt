package com.focusguard.app.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.focusguard.app.utils.BatteryOptimizationHelper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.overlay.OVERLAY_PRESETS
import com.focusguard.app.receiver.FocusGuardDeviceAdminReceiver
import com.focusguard.app.security.SupportVerificationResult
import com.focusguard.app.data.model.AuthUser
import com.focusguard.app.data.datastore.DEFAULT_COMMITMENT_SENTENCE
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.challenge.ChangeChallengeTypeDialog
import com.focusguard.app.ui.components.AccountProfileCard
import com.focusguard.app.ui.components.AccountabilityPartnerCard
import com.focusguard.app.ui.components.SupportCodeDialog
import com.focusguard.app.ui.components.UninstallProtectionDurationDialog
import com.focusguard.app.ui.components.FeedControlWarningDialog
import com.focusguard.app.ui.components.AccountabilityRequiredDialog
import com.focusguard.app.ui.components.DeviceAdminRequiredDialog
import com.focusguard.app.ui.auth.AuthDialog
import com.focusguard.app.ui.auth.PartnerInviteDialog
import com.focusguard.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState = SettingsUiState(),
    onRequestChangeChallenge: (ChallengeType, String, Int) -> Unit = { _, _, _ -> },
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onNavigateToOemGuidance: () -> Unit,
    // Phase 7: Overlay wallpaper callbacks
    onPickWallpaper: (Uri) -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onSetPreset: (Int) -> Unit = {},
    onSetDimAlpha: (Float) -> Unit = {},
    onPreviewOverlay: () -> Unit = {},
    // Uninstall Protection toggle callback
    onRequestToggleUninstallProtection: (Boolean) -> Unit = {},
    onConfirmEnableUninstallProtection: (Int) -> Unit = {},
    onDismissUninstallDurationDialog: () -> Unit = {},
    // Granular Shorts & Reels blocking callbacks
    onToggleShortsReelsBlock: (Boolean) -> Unit = {},
    onToggleInstagramReelsBlock: (Boolean) -> Unit = {},
    onToggleFacebookReelsBlock: (Boolean) -> Unit = {},
    onToggleYouTubeShortsBlock: (Boolean) -> Unit = {},
    onToggleIncognitoBlock: (Boolean) -> Unit = {},
    // Auto-Block Newly Installed Apps callback
    onToggleAutoBlockNewApps: (Boolean) -> Unit = {},
    onConfirmEnablePendingFeed: () -> Unit = {},
    onDismissPendingFeedWarning: () -> Unit = {},
    onDismissAccountabilityRequiredDialog: () -> Unit = {},
    onProceedToSetupChallenge: () -> Unit = {},
    onCancelChallengeSetup: () -> Unit = {},
    onDismissDeviceAdminRequiredDialog: () -> Unit = {},
    // Block Overlay Customization callbacks
    onSetCustomBlockMessage: (String) -> Unit = {},
    onSetBlockCountdownSeconds: (Int) -> Unit = {},
    // Support Secret Code System callbacks
    onOpenSupportCodeDialog: () -> Unit = {},
    onDismissSupportCodeDialog: () -> Unit = {},
    onVerifySupportCode: (String) -> SupportVerificationResult = { SupportVerificationResult.InvalidCode },
    onApplySupportAction: (String, String) -> Unit = { _, _ -> },
    // Google Authentication callbacks
    authUser: AuthUser? = null,
    isAuthLoading: Boolean = false,
    onSignInWithGoogle: () -> Unit = {},
    onSignOut: () -> Unit = {},
    // Stage 1: Accountability Partner callbacks
    onSendPartnerInvite: (String) -> Unit = {},
    onCancelPartnerInvite: (String) -> Unit = {},
    onAcceptPartnerInvite: (String) -> Unit = {},
    onDeclinePartnerInvite: (String) -> Unit = {},
    onRequestRevokePartner: (String) -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onNavigateToHomeSetup: () -> Unit = {}
) {
    val context = LocalContext.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        onRefreshPermissions()
    }

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings & Protection",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900)
                )
            },
            containerColor = Navy900
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            listOf(Forest950, Forest900, Forest950)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {


                    // ── 1. ACCOUNT & PROFILE ──
                    Text(
                        text = "ACCOUNT & PROFILE",
                        color = White40,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    AccountProfileCard(
                        user = authUser,
                        isLoading = isAuthLoading,
                        onSignInWithGoogle = onSignInWithGoogle,
                        onSignOut = onSignOut,
                        isSignOutLocked = uiState.isSignOutLocked,
                        signOutLockReason = uiState.signOutLockReason
                    )

                    Spacer(Modifier.height(4.dp))

                    // ── 2. EMERGENCY RECOVERY & SUPPORT ──
                    Text(
                        text = "EMERGENCY RECOVERY & SUPPORT",
                        color = White40,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Forest800),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSupportCodeDialog() }
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Emerald500.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Emerald500,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emergency Support Passcode",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Device ID: ${uiState.deviceId} • Tap to enter 6-digit offline code",
                                    color = Emerald400,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = White40,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── 2.5 OEM AUTO-START & BACKGROUND SHIELD ──
                    Text(
                        text = "BACKGROUND & BATTERY SHIELD",
                        color = White40,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    val oemShortName = remember { BatteryOptimizationHelper.getOemShortName() }
                    val oemHeadline = remember { BatteryOptimizationHelper.getOemDialogHeadline() }
                    val isProblematic = remember { BatteryOptimizationHelper.isProblematicOem() }

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToOemGuidance() }
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$oemShortName Auto-Start & Background Shield",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isProblematic) oemHeadline else "Unrestricted battery setup • Tap for 1-click guide",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = White40,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── 3. ABOUT FOCUSGUARD ──
                    Text(
                        text = "ABOUT FOCUSGUARD",
                        color = White40,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Privacy & Local Execution",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "FocusGuard runs 100% locally on your device. Your app usage, blocked keywords, and schedules are stored securely in your phone's encrypted sandbox and are never uploaded to any remote server.",
                                color = White60,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Version 1.6.0 (Phase 6 · Unblock Friction)",
                                color = White40,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                }


                // ── ACTIVE CHALLENGE VERIFICATION DIALOG ─
                val challenge = uiState.activeChallenge
                if (challenge != null) {
                    if (uiState.challengeType == ChallengeType.PARTNER) {
                        PartnerApprovalPendingDialog(
                            actionDescription = challenge.description,
                            partnerEmail = uiState.partnership?.partnerEmail,
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

                // ── UNINSTALL PROTECTION DURATION DIALOG ──
                if (uiState.showUninstallDurationDialog) {
                    UninstallProtectionDurationDialog(
                        onConfirm = { days ->
                            onConfirmEnableUninstallProtection(days)
                        },
                        onDismiss = onDismissUninstallDurationDialog
                    )
                }

                if (uiState.showSupportCodeDialog) {
                    SupportCodeDialog(
                        deviceId = uiState.deviceId,
                        userEmail = uiState.currentUserEmail ?: authUser?.email,
                        isLoggedIn = uiState.isUserLoggedIn || authUser != null,
                        onSignInWithGoogle = onSignInWithGoogle,
                        cooldownRemainingSeconds = uiState.supportCooldownSeconds,
                        onVerifyCode = onVerifySupportCode,
                        onApplyAction = onApplySupportAction,
                        onDismiss = onDismissSupportCodeDialog
                    )
                }

                // ── INCOMING PARTNER INVITE DIALOG ─
                val incomingInvite = uiState.pendingIncomingInvite
                if (incomingInvite != null) {
                    PartnerInviteDialog(
                        ownerEmail = incomingInvite.ownerEmail,
                        onAccept = { onAcceptPartnerInvite(incomingInvite.partnershipId) },
                        onDecline = { onDeclinePartnerInvite(incomingInvite.partnershipId) },
                        isLoading = uiState.isPartnerInviteLoading
                    )
                }

                // ── DEVICE ADMINISTRATOR REQUIRED DIALOG ──
                if (uiState.showDeviceAdminRequiredDialog) {
                    DeviceAdminRequiredDialog(
                        onActivate = {
                            onDismissDeviceAdminRequiredDialog()
                        },
                        onDismiss = onDismissDeviceAdminRequiredDialog
                    )
                }

                // ── ACCOUNTABILITY REQUIRED DIALOG ──
                if (uiState.showAccountabilityRequiredDialog) {
                    AccountabilityRequiredDialog(
                        featureName = uiState.accountabilityRequiredFeature,
                        subtitle = "Accountability Challenge Required",
                        message = "To enable ${uiState.accountabilityRequiredFeature}, FocusGuard requires an active Accountability Challenge (Sentence Typing).\n\nSet up and turn ON the challenge now to proceed directly with blocking.",
                        confirmButtonText = "Turn ON Challenge & Proceed",
                        onSetUpChallenge = onProceedToSetupChallenge,
                        onDismiss = onDismissAccountabilityRequiredDialog
                    )
                }

                // ── FEED CONTROL ACTIVATION WARNING DIALOG ──
                val pendingFeed = uiState.pendingFeedWarning
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

            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase 7: Overlay Wallpaper Card
// ─────────────────────────────────────────────────────────────────────────────

val Violet500 = Color(0xFF22C55E)
val Cyan400   = Color(0xFF4ADE80)
val Navy950   = Color(0xFF07140D)

// ─────────────────────────────────────────────────────────────────────────────
// Distraction Feed Control Card (Image-like UI with Badge, Rows & Info dialogs)
// ─────────────────────────────────────────────────────────────────────────────

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
    onToggleBlockUnsupportedBrowsers: (Boolean) -> Unit = {}
) {
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }

    val CardBg = Forest800
    val ActiveGreen = Emerald500
    val InactiveTrack = Forest700
    val BorderRed = Color(0x3322C55E)
    val BadgeBg = Color(0xFF0F3822)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, BorderRed),
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
                    activeColor = ActiveGreen,
                    inactiveTrack = InactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Instagram Reels"
                        infoDialogMessage = "Blocks full-screen algorithmic Instagram Reels specifically! Normal feed posts, profiles, and Direct Messages (DMs) stay fully accessible."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                // Row 2: Block Facebook Reels
                DistractionSettingRow(
                    title = "Block Facebook Reels",
                    isChecked = isFacebookReelsBlocked,
                    onCheckedChange = onToggleFacebookReels,
                    activeColor = ActiveGreen,
                    inactiveTrack = InactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Facebook Reels"
                        infoDialogMessage = "Blocks algorithmic Facebook Reels and short video players specifically! Normal news feed, groups, and Messenger stay fully accessible."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                // Row 3: Block YouTube Shorts
                DistractionSettingRow(
                    title = "Block YouTube Shorts",
                    isChecked = isYouTubeShortsBlocked,
                    onCheckedChange = onToggleYouTubeShorts,
                    activeColor = ActiveGreen,
                    inactiveTrack = InactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block YouTube Shorts"
                        infoDialogMessage = "Blocks algorithmic YouTube Shorts specifically. Regular video watch pages, search, and subscriptions stay fully accessible!"
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                // Row 4: Auto-Block New Apps
                DistractionSettingRow(
                    title = "Auto-block newly installed apps",
                    isChecked = isAutoBlockEnabled,
                    onCheckedChange = onToggleAutoBlock,
                    activeColor = ActiveGreen,
                    inactiveTrack = InactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Auto-Block Newly Installed Apps"
                        infoDialogMessage = "Any new apps downloaded from Google Play or APK are automatically placed in Pending Review & blocked by default, preventing bypass downloads."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                // Row 5: Block Incognito Mode
                DistractionSettingRow(
                    title = "Block Incognito & Private Mode",
                    isChecked = isIncognitoBlocked,
                    onCheckedChange = onToggleIncognito,
                    activeColor = ActiveGreen,
                    inactiveTrack = InactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Incognito & Private Tabs"
                        infoDialogMessage = "Prevents opening private browsing tabs and Secret mode across Chrome, Firefox, Edge, Brave, and Samsung Internet to eliminate loophole browsing."
                    }
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 0.dp)
                )

                // Row 6: Block Unsupported Browsers
                DistractionSettingRow(
                    title = "Block Unsupported Browsers",
                    isChecked = isBlockUnsupportedBrowsers,
                    onCheckedChange = onToggleBlockUnsupportedBrowsers,
                    activeColor = ActiveGreen,
                    inactiveTrack = InactiveTrack,
                    onInfoClick = {
                        infoDialogTitle = "Block Unsupported Browsers"
                        infoDialogMessage = "Blocks obscure, unverified, or proxy browsers where URL keyword filtering can be bypassed. Permitted safe browsers include Chrome, Firefox, Firefox Lite, Brave, Opera, DuckDuckGo, Tor Browser, Vivaldi, Spin Safe Browser, Ecosia, Free Adblocker Browser, 1DM, Web Video Cast, Edge, and Samsung Internet."
                    }
                )
            }
        }

        // Pill / Badge Header positioned at top left matching reference image
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = Color(0xFF334155),
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
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

    // Info Alert Dialog
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

// ─────────────────────────────────────────────────────────────────────────────
// Block Overlay & Behavior Card (Matching DistractionFeedControlCard styling)
// ─────────────────────────────────────────────────────────────────────────────

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
    var activeEditDialog by remember { mutableStateOf<String?>(null) } // "MESSAGE", "COUNTDOWN"

    var editingMessage by remember(uiState.customBlockMessage) { mutableStateOf(uiState.customBlockMessage) }
    var pendingCountdownIncrease by remember { mutableStateOf<Int?>(null) }

    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }
    var isWallpaperDialogOpen by remember { mutableStateOf(false) }

    val CardBg = Forest800
    val BorderViolet = Color(0x3322C55E)
    val BadgeBg = Color(0xFF0F3822)

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, BorderViolet),
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

                // Compact Quick Action Bar: Wallpaper Customize & Preview Overlay
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Violet500.copy(alpha = 0.5f))
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

        // Pill / Badge Header positioned at top left matching reference image
        Surface(
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = BadgeBg,
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
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

    // ── Wallpaper & Dim Settings Dialog ──
    if (isWallpaperDialogOpen) {
        AlertDialog(
            onDismissRequest = { isWallpaperDialogOpen = false },
            title = {
                Text("Wallpaper & Scrim Darkness", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Background Theme", color = White60, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OVERLAY_PRESETS.forEach { preset ->
                            val isSelected = !uiState.hasCustomWallpaper && uiState.overlayPresetId == preset.id
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Violet500.copy(alpha = 0.25f) else Navy700,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Violet500 else Color(0x22FFFFFF)
                                ),
                                modifier = Modifier.clickable { onSetPreset(preset.id) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Brush.horizontalGradient(preset.gradient))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = if (isSelected) Color.White else White60,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPickGallery,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Violet500.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp), tint = Violet500)
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Photo", fontSize = 12.sp, color = Color.White)
                        }

                        if (uiState.hasCustomWallpaper) {
                            OutlinedButton(
                                onClick = onClearWallpaper,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
                            ) {
                                Text("Reset", fontSize = 12.sp, color = White60)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Scrim Darkness", color = White60, fontSize = 12.sp)
                            Text("${(uiState.overlayDimAlpha * 100).toInt()}%", color = Violet500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = uiState.overlayDimAlpha,
                            onValueChange = onSetDimAlpha,
                            valueRange = 0.2f..0.9f,
                            colors = SliderDefaults.colors(
                                thumbColor = Violet500,
                                activeTrackColor = Violet500,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { isWallpaperDialogOpen = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Violet500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Navy800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // ── Info Alert Dialog ──
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

    // ── Edit Dialog 1: Custom Message ──
    if (activeEditDialog == "MESSAGE") {
        AlertDialog(
            onDismissRequest = { activeEditDialog = null },
            title = {
                Text("Edit Block Screen Message", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This message is shown in huge glowing text whenever an app is blocked.",
                        color = White60,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = editingMessage,
                        onValueChange = { editingMessage = it },
                        placeholder = { Text("e.g. Go read a book instead!", color = White40, fontSize = 13.sp) },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = White90,
                            focusedBorderColor = Violet500,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedContainerColor = Navy950,
                            unfocusedContainerColor = Navy950
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetCustomMessage(editingMessage.trim())
                        activeEditDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Violet500),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeEditDialog = null }) {
                    Text("Cancel", color = White60)
                }
            },
            containerColor = Navy800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // ── Edit Dialog 2: Countdown Duration ──
    if (activeEditDialog == "COUNTDOWN") {
        AlertDialog(
            onDismissRequest = { activeEditDialog = null },
            title = {
                Text("Select Countdown Timer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Duration the block overlay stays visible before closing the app.",
                        color = White60,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { sec ->
                            val isSelected = uiState.blockCountdownSeconds == sec
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Amber400 else Navy700,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (sec > uiState.blockCountdownSeconds) {
                                            pendingCountdownIncrease = sec
                                        } else {
                                            onSetCountdown(sec)
                                            activeEditDialog = null
                                        }
                                    }
                            ) {
                                Text(
                                    text = "${sec}s",
                                    color = if (isSelected) Navy950 else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeEditDialog = null }) {
                    Text("Cancel", color = White60)
                }
            },
            containerColor = Navy800,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // ── COUNTDOWN INCREASE CONFIRMATION DIALOG ──
    if (pendingCountdownIncrease != null) {
        val targetSec = pendingCountdownIncrease!!
        AlertDialog(
            onDismissRequest = { pendingCountdownIncrease = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Increase Countdown to ${targetSec}s?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "You are increasing the block overlay countdown from ${uiState.blockCountdownSeconds}s to ${targetSec}s.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                    if (uiState.isChallengeActive) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Rose400.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Rose400.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Challenge Protected: Because an Accountability Challenge is active, decreasing this countdown later will require completing your challenge!",
                                color = Rose400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text(
                        text = "Are you sure you want to lock in a longer ${targetSec}s countdown?",
                        color = White60,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetCountdown(targetSec)
                        pendingCountdownIncrease = null
                        activeEditDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber400)
                ) {
                    Text("Yes, Increase to ${targetSec}s", color = Navy950, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCountdownIncrease = null }) {
                    Text("Cancel", color = White60)
                }
            },
            containerColor = Forest800,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun OverlayBehaviorSettingRow(
    title: String,
    subtitle: String,
    onEditClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = White60,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = White60,
                    modifier = Modifier.size(15.dp)
                )
            }

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

