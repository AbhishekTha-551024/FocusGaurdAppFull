package com.focusguard.app.feature.timelimits.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusguard.app.core.model.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.timelimits.ui.components.*
import com.focusguard.app.ui.theme.FocusGuardTheme

private val Navy900   = Color(0xFF07140D)
private val Purple400 = Color(0xFF22C55E)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

/**
 * =====================================================================
 * TimeLimitScreen.kt - Main UI Screen (Jetpack Compose)
 * =====================================================================
 *
 * ## 1. Yeh File Kya Karti Hai? (Core Responsibility)
 * Yeh Compose screen user ko unke apps ke active daily time limits (caps), unka aaj ka
 * used screen time, aur progress bar display karti hai. Isme FloatingActionButton (+)
 * se naya time limit set kiya ja sakta hai.
 *
 * ## 2. Core UI Sections:
 * - `TimeLimitTopAppBar`: Header bar with configured limits count counter.
 * - `TimeLimitEmptyState`: Agar koi limit nahi lagi hai toh user ko prompt karta hai.
 * - `ConfiguredTimeLimitsList`: LazyColumn jo active caps ko [CompactTimeLimitRow] ke through render karta hai.
 * - `TimeLimitDialogsHost`: Usage Access explanation, Accessibility Disclosure, Overlay prompt,
 *   aur Typing Challenge dialogs ko host karta hai.
 *
 * ## 3. Lifecycle Observer (Senior Dev Note):
 * - `LifecycleEventObserver` (ON_RESUME) se jab user Android Settings me jaakar "Usage Access"
 *   ya "Accessibility" permission enable karke app me laut-ta hai, toh screen bina restart ke
 *   instant permissions refresh kar leti hai (`refreshPermissions()`).
 * =====================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLimitScreen(
    uiState: TimeLimitUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleLimit: (AppInfo, Int, Boolean) -> Unit,
    onSetTimeLimit: (AppInfo, Int) -> Unit,
    onRequestUsagePermission: () -> Unit,
    onRefreshPermissions: () -> Unit = {},
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onCancelPendingChange: (Long) -> Unit = {},
    onActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onDismissAccountability: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isAddLimitPickerOpen by remember { mutableStateOf(false) }
    var showUsageExplanation by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    fun refreshPermissions() {
        isAccessibilityEnabled = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityConfiguredInSettings(context)
        isOverlayGranted = Settings.canDrawOverlays(context)
        onRefreshPermissions()
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

    val configuredApps = remember(uiState.apps) {
        uiState.apps.filter { it.isLimitConfigured }
    }

    FocusGuardTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TimeLimitTopAppBar(configuredCount = configuredApps.size)
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        refreshPermissions()
                        if (!uiState.hasUsagePermission) {
                            showUsageExplanation = true
                        } else if (!isAccessibilityEnabled || !isOverlayGranted) {
                            showPermissionsDialog = true
                        } else {
                            isAddLimitPickerOpen = true
                        }
                    },
                    containerColor = Purple400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Time Limit", modifier = Modifier.size(28.dp))
                }
            },
            containerColor = Navy900
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Brush.verticalGradient(listOf(Navy900, Color(0xFF0F172A), Navy900)))
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Purple400)
                            Text("Loading limits...", color = White60, fontSize = 13.sp)
                        }
                    }
                } else if (configuredApps.isEmpty()) {
                    TimeLimitEmptyState(onAddLimitClick = {
                        refreshPermissions()
                        if (!uiState.hasUsagePermission) {
                            showUsageExplanation = true
                        } else if (!isAccessibilityEnabled || !isOverlayGranted) {
                            showPermissionsDialog = true
                        } else {
                            isAddLimitPickerOpen = true
                        }
                    })
                } else {
                    ConfiguredTimeLimitsList(
                        configuredApps = configuredApps,
                        onToggleLimit = onToggleLimit
                    )
                }

                TimeLimitDialogsHost(
                    uiState = uiState,
                    context = context,
                    isAddLimitPickerOpen = isAddLimitPickerOpen,
                    showUsageExplanation = showUsageExplanation,
                    showPermissionsDialog = showPermissionsDialog,
                    showDisclosureDialog = showDisclosureDialog,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayGranted = isOverlayGranted,
                    onSearchQueryChange = onSearchQueryChange,
                    onSetTimeLimit = onSetTimeLimit,
                    onRequestUsagePermission = onRequestUsagePermission,
                    onCloseAddPicker = {
                        onSearchQueryChange("")
                        isAddLimitPickerOpen = false
                    },
                    onCloseUsageExplanation = { showUsageExplanation = false },
                    onOpenAddPicker = { isAddLimitPickerOpen = true },
                    onOpenDisclosure = {
                        showPermissionsDialog = false
                        showDisclosureDialog = true
                    },
                    onClosePermissions = { showPermissionsDialog = false },
                    onCloseDisclosure = { showDisclosureDialog = false },
                    onActivateChallenge = onActivateChallenge,
                    onDismissAccountability = onDismissAccountability,
                    onChallengeSuccess = onChallengeSuccess,
                    onCancelChallenge = onCancelChallenge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeLimitTopAppBar(configuredCount: Int) {
    TopAppBar(
        title = {
            Column {
                Text(text = "App Time Limits", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = if (configuredCount == 0) "No limits active" else "$configuredCount app${if (configuredCount > 1) "s" else ""} limited",
                    color = if (configuredCount > 0) Purple400 else White60,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900)
    )
}

@Composable
private fun ConfiguredTimeLimitsList(
    configuredApps: List<AppTimeLimitUiModel>,
    onToggleLimit: (AppInfo, Int, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "ACTIVE TIME CAPS (${configuredApps.size})",
                color = White40,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        items(
            items = configuredApps,
            key = { it.appInfo.packageName },
            contentType = { "time_limit_row" }
        ) { model ->
            CompactTimeLimitRow(
                model = model,
                onDelete = { onToggleLimit(model.appInfo, model.dailyLimitMinutes, false) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
