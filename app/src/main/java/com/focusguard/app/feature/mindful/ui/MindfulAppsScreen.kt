package com.focusguard.app.feature.mindful.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusguard.app.core.database.entity.ChallengeType
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.mindful.ui.components.*
import com.focusguard.app.utils.AppIconLoader

private val Forest900 = Color(0xFF07140D)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val TextMuted  = Color(0x99FFFFFF)

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
    onActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onDismissAccountability: () -> Unit = {},
    onUpdateAllowanceMinutes: (packageName: String, minutes: Int) -> Unit = { _, _ -> },
    onOpenJournal: () -> Unit = {},
    onDismissJournal: () -> Unit = {}
) {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        isAccessibilityEnabled = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(context)
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
        MindfulVictoryBanner(
            walkedAwayCount = uiState.walkedAwayCount,
            onOpenJournal = onOpenJournal
        )
        Spacer(Modifier.height(10.dp))
        MindfulPatternInsightsCard(
            logs = uiState.recentLogs,
            consciousOpensCount = uiState.consciousOpensCount
        )
        MindfulSearchField(
            query = uiState.searchQuery,
            onQueryChange = onSearchQueryChange
        )
        Spacer(Modifier.height(14.dp))
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Emerald400)
                    Text("Loading mindful apps...", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            MindfulAppsList(
                uiState = uiState,
                iconLoader = iconLoader,
                onCheckPermissionAndToggle = { app ->
                    refreshPermissions()
                    if (!isAccessibilityEnabled || !isOverlayGranted) {
                        showPermissionsDialog = true
                    } else {
                        onToggleMindfulApp(app, true)
                    }
                },
                onToggleMindfulApp = onToggleMindfulApp,
                onUpdateAllowanceMinutes = onUpdateAllowanceMinutes,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    MindfulDialogsHost(
        uiState = uiState,
        context = context,
        showPermissionsDialog = showPermissionsDialog,
        showDisclosureDialog = showDisclosureDialog,
        isAccessibilityEnabled = isAccessibilityEnabled,
        isOverlayGranted = isOverlayGranted,
        onRefreshPermissions = { refreshPermissions() },
        onOpenPermissions = { showPermissionsDialog = true },
        onClosePermissions = { showPermissionsDialog = false },
        onOpenDisclosure = { showDisclosureDialog = true },
        onCloseDisclosure = { showDisclosureDialog = false },
        onConfirmAddMindfulApp = onConfirmAddMindfulApp,
        onDismissConfirmAddMindfulApp = onDismissConfirmAddMindfulApp,
        onActivateChallenge = onActivateChallenge,
        onDismissAccountability = onDismissAccountability,
        onChallengeSuccess = onChallengeSuccess,
        onCancelChallenge = onCancelChallenge
    )
}

@Composable
private fun MindfulSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search communication / work apps...", color = Color(0x55FFFFFF), fontSize = 13.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
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
}

@Composable
private fun MindfulAppsList(
    uiState: MindfulAppsUiState,
    iconLoader: AppIconLoader,
    onCheckPermissionAndToggle: (AppInfo) -> Unit,
    onToggleMindfulApp: (AppInfo, Boolean) -> Unit,
    onUpdateAllowanceMinutes: (packageName: String, minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
        if (uiState.mindfulApps.isEmpty() && uiState.availableApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "No apps found matching \"${uiState.searchQuery}\"" else "No apps available",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
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
                    onToggle = { onCheckPermissionAndToggle(app) },
                    onUpdateAllowance = { }
                )
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}
