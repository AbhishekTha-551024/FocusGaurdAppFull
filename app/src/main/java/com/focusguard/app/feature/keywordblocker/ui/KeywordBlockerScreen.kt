package com.focusguard.app.feature.keywordblocker.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
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
import com.focusguard.app.core.designsystem.theme.focusPageBackground
import com.focusguard.app.feature.keywordblocker.domain.model.BlockedKeyword
import com.focusguard.app.feature.keywordblocker.ui.components.BlockedWebsiteRow
import com.focusguard.app.feature.keywordblocker.ui.components.KeywordDialogHost
import com.focusguard.app.ui.theme.FocusGuardTheme

private val Navy900   = Color(0xFF07140D)
private val Indigo400 = Color(0xFF22C55E)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

/**
 * =====================================================================
 * KeywordBlockerScreen.kt - Main UI Screen (Jetpack Compose)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose screen user ko unki blocked websites aur keywords ki list dikhati hai,
 * floating action button (+) ke through naya website/keyword block karne deti hai,
 * aur Accessibility Service & System Alert Window (Overlay) permissions check/request karti hai.
 *
 * ## 2. Core UI Components:
 * - `KeywordBlockerTopBar`: Header bar jo total blocked websites ka count live dikhata hai.
 * - `EmptyKeywordState`: Jab koi website blocked nahi hoti, tab user ko motivate karta hai
 *   aur setup guide provide karta hai.
 * - `BlockedKeywordList`: LazyColumn jo blocked websites ko render karta hai with smooth scrolling.
 * - `KeywordDialogHost`: Saare modal dialogs (Add dialog, Permission prompt, Accessibility disclosure,
 *   Challenge typing dialog) ko handle karta hai.
 *
 * ## 3. Permission & Lifecycle Handling (Senior Dev Perspective):
 * - `LifecycleEventObserver` (ON_RESUME): User jab Android Settings me jaakar Accessibility ya Overlay
 *   permission on karke app me wapas aata hai, toh app bina restart kiye permissions ko
 *   instantly auto-refresh kar leti hai (`refreshPermissions()`).
 * - FloatingActionButton click par permission check hota hai—agar permission missing hai toh user
 *   ko feature permission dialog dikhta hai, jisse broken states prevent hoti hain.
 * =====================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordBlockerScreen(
    uiState: KeywordBlockerUiState,
    onKeywordInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (BlockedKeyword) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onCancelPendingChange: (Long) -> Unit = {},
    onActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onDismissAccountability: () -> Unit = {},
    onClearError: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isAddWebsiteDialogOpen by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            onClearError()
        }
    }

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
            topBar = { KeywordBlockerTopBar(count = uiState.blockedKeywords.size) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        refreshPermissions()
                        if (!isAccessibilityEnabled || !isOverlayGranted) {
                            showPermissionsDialog = true
                        } else {
                            isAddWebsiteDialogOpen = true
                        }
                    },
                    containerColor = Indigo400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Block a Website", modifier = Modifier.size(28.dp))
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Indigo400)
                            Text("Loading blocked websites...", color = White60, fontSize = 13.sp)
                        }
                    }
                } else if (uiState.blockedKeywords.isEmpty()) {
                    EmptyKeywordState(onBlockClick = {
                        refreshPermissions()
                        if (!isAccessibilityEnabled || !isOverlayGranted) {
                            showPermissionsDialog = true
                        } else {
                            isAddWebsiteDialogOpen = true
                        }
                    })
                } else {
                    BlockedKeywordList(
                        keywords = uiState.blockedKeywords,
                        onDelete = onRemoveKeyword
                    )
                }

                KeywordDialogHost(
                    uiState = uiState,
                    isAddDialogOpen = isAddWebsiteDialogOpen,
                    showPermissionsDialog = showPermissionsDialog,
                    showDisclosureDialog = showDisclosureDialog,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayGranted = isOverlayGranted,
                    context = context,
                    onKeywordInputChange = onKeywordInputChange,
                    onAddKeyword = onAddKeyword,
                    onActivateChallenge = onActivateChallenge,
                    onDismissAccountability = onDismissAccountability,
                    onChallengeSuccess = onChallengeSuccess,
                    onCancelChallenge = onCancelChallenge,
                    onCloseAddDialog = { isAddWebsiteDialogOpen = false },
                    onClosePermissions = { showPermissionsDialog = false },
                    onOpenDisclosure = {
                        showPermissionsDialog = false
                        showDisclosureDialog = true
                    },
                    onCloseDisclosure = { showDisclosureDialog = false },
                    onRequirePermissions = {
                        showPermissionsDialog = true
                        isAddWebsiteDialogOpen = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordBlockerTopBar(count: Int) {
    TopAppBar(
        title = {
            Column {
                Text(text = "Websites & Keywords", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "$count websites blocked",
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
private fun EmptyKeywordState(onBlockClick: () -> Unit) {
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
            Icon(Icons.Default.Language, contentDescription = null, tint = Indigo400, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No Websites Blocked Yet", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
            onClick = onBlockClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo400, contentColor = Navy900),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Block", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BlockedKeywordList(
    keywords: List<BlockedKeyword>,
    onDelete: (BlockedKeyword) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "BLOCKED WEBSITES & KEYWORDS (${keywords.size})",
                color = White40,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        items(keywords, key = { "kw_${it.id}_${it.keyword}" }) { item ->
            BlockedWebsiteRow(keyword = item, onDelete = { onDelete(item) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
