package com.focusguard.app.feature.appblocker.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.core.designsystem.theme.focusPageBackground
import com.focusguard.app.feature.appblocker.ui.components.AppBlockerDialogHost
import com.focusguard.app.feature.appblocker.ui.components.BlockedAppList
import com.focusguard.app.ui.components.AppListSkeleton
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.utils.AppIconLoader

private val Navy900   = Color(0xFF07140D)
private val Indigo400 = Color(0xFF22C55E)
private val White60   = Color(0x99FFFFFF)

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
    onActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onDismissAccountability: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        isAccessibilityEnabled = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityConfiguredInSettings(context)
        isOverlayGranted = Settings.canDrawOverlays(context)
    }

    LaunchedEffect(Unit) { refreshPermissions() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FocusGuardTheme {
        Scaffold(
            topBar = { AppBlockerTopBar(count = uiState.blockedApps.size) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        refreshPermissions()
                        if (!isAccessibilityEnabled || !isOverlayGranted) {
                            showPermissionsDialog = true
                        } else {
                            isAppPickerOpen = true
                        }
                    },
                    containerColor = Indigo400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Apps to Block", modifier = Modifier.size(28.dp))
                }
            },
            containerColor = Navy900
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .focusPageBackground()
            ) {
                if (uiState.isLoading) {
                    AppListSkeleton(count = 7)
                    return@Box
                }

                if (uiState.blockedApps.isEmpty() && uiState.pendingReviewApps.isEmpty() && uiState.unblockedApps.isNotEmpty()) {
                    EmptyAppBlockerState(onSelectAppsClick = {
                        refreshPermissions()
                        if (!isAccessibilityEnabled || !isOverlayGranted) showPermissionsDialog = true else isAppPickerOpen = true
                    })
                } else {
                    BlockedAppList(
                        uiState = uiState,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isOverlayGranted = isOverlayGranted,
                        onOpenPermissions = {
                            refreshPermissions()
                            showPermissionsDialog = true
                        },
                        onToggleBlock = onToggleBlock
                    )
                }

                AppBlockerDialogHost(
                    uiState = uiState,
                    isAppPickerOpen = isAppPickerOpen,
                    showPermissionsDialog = showPermissionsDialog,
                    showDisclosureDialog = showDisclosureDialog,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayGranted = isOverlayGranted,
                    context = context,
                    onRefreshPermissions = { refreshPermissions() },
                    onRequestPermissions = { showPermissionsDialog = true },
                    onSearchQueryChange = onSearchQueryChange,
                    onToggleBlock = onToggleBlock,
                    onActivateChallenge = onActivateChallenge,
                    onDismissAccountability = onDismissAccountability,
                    onChallengeSuccess = onChallengeSuccess,
                    onCancelChallenge = onCancelChallenge,
                    onClosePicker = {
                        onSearchQueryChange("")
                        isAppPickerOpen = false
                    },
                    onClosePermissions = { showPermissionsDialog = false },
                    onOpenDisclosure = {
                        showPermissionsDialog = false
                        showDisclosureDialog = true
                    },
                    onCloseDisclosure = { showDisclosureDialog = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBlockerTopBar(count: Int) {
    TopAppBar(
        title = {
            Column {
                Text(text = "App Blocker", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "$count apps blocked",
                    color = if (count > 0) Indigo400 else White60,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900)
    )
}

@Composable
private fun EmptyAppBlockerState(onSelectAppsClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
            Icon(Icons.Default.Block, contentDescription = null, tint = Indigo400, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No Apps Blocked Yet", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
            onClick = onSelectAppsClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Select Apps to Block", fontWeight = FontWeight.Bold)
        }
    }
}
