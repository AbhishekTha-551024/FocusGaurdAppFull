package com.focusguard.app.feature.schedules.ui

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
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.feature.schedules.ui.components.*
import com.focusguard.app.ui.theme.FocusGuardTheme

private val Navy900  = Color(0xFF07140D)
private val Amber400 = Color(0xFFFBBF24)
private val White60  = Color(0x99FFFFFF)
private val White40  = Color(0x66FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    onOpenAddDialog: () -> Unit,
    onCloseAddDialog: () -> Unit,
    onAddSchedule: (String, String?, String?, Int, Int, Int) -> Unit,
    onDeleteSchedule: (Long, String) -> Unit,
    onToggleSchedule: (ScheduleEntity, Boolean) -> Unit,
    onChallengeSuccess: (Context) -> Unit = {},
    onCancelChallenge: () -> Unit = {},
    onCancelPendingChange: (Long) -> Unit = {},
    onActivateChallenge: (ChallengeType, String) -> Unit = { _, _ -> },
    onDismissAccountability: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { ScheduleTopBar(count = uiState.schedules.size) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        refreshPermissions()
                        if (!isAccessibilityEnabled || !isOverlayGranted) {
                            showPermissionsDialog = true
                        } else {
                            onOpenAddDialog()
                        }
                    },
                    containerColor = Amber400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Schedule", modifier = Modifier.size(28.dp))
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
                            CircularProgressIndicator(color = Amber400)
                            Text("Loading schedules...", color = White60, fontSize = 13.sp)
                        }
                    }
                } else if (uiState.schedules.isEmpty()) {
                    ScheduleEmptyState(onOpenAddDialog = {
                        refreshPermissions()
                        if (!isAccessibilityEnabled || !isOverlayGranted) showPermissionsDialog = true else onOpenAddDialog()
                    })
                } else {
                    ScheduleList(
                        schedules = uiState.schedules,
                        onToggleSchedule = onToggleSchedule,
                        onDeleteSchedule = onDeleteSchedule
                    )
                }

                ScheduleDialogsHost(
                    uiState = uiState,
                    context = context,
                    showPermissionsDialog = showPermissionsDialog,
                    showDisclosureDialog = showDisclosureDialog,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isOverlayGranted = isOverlayGranted,
                    onCloseAddDialog = onCloseAddDialog,
                    onAddSchedule = onAddSchedule,
                    onClosePermissions = { showPermissionsDialog = false },
                    onOpenDisclosure = {
                        showPermissionsDialog = false
                        showDisclosureDialog = true
                    },
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
private fun ScheduleTopBar(count: Int) {
    TopAppBar(
        title = {
            Column {
                Text(text = "Focus Schedules", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = if (count == 0) "No schedules active" else "$count schedule${if (count > 1) "s" else ""} configured",
                    color = if (count > 0) Amber400 else White60,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy900)
    )
}

@Composable
private fun ScheduleList(
    schedules: List<ScheduleEntity>,
    onToggleSchedule: (ScheduleEntity, Boolean) -> Unit,
    onDeleteSchedule: (Long, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "ACTIVE SCHEDULES (${schedules.size})",
                color = White40,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        items(schedules, key = { "sched_${it.id}_${it.title}" }) { schedule ->
            CompactScheduleRow(
                schedule = schedule,
                onToggle = { enabled -> onToggleSchedule(schedule, enabled) },
                onDelete = { onDeleteSchedule(schedule.id, schedule.title) }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
