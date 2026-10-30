package com.focusguard.app.feature.session.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusguard.app.core.model.AppInfo
import com.focusguard.app.feature.session.ui.components.*
import com.focusguard.app.ui.components.AccessibilityDisclosureDialog
import com.focusguard.app.ui.components.FeaturePermissionsDialog
import com.focusguard.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeepFocusScreen(
    sessionState: SessionUiState,
    availableApps: List<AppInfo>,
    onSelectDuration: (Int) -> Unit,
    onOpenPacking: () -> Unit,
    onClosePacking: () -> Unit,
    onStartSession: () -> Unit,
    onRequestAbort: () -> Unit,
    onConfirmAbort: () -> Unit,
    onDismissAbort: () -> Unit,
    onTogglePackageInSuitcase: (String) -> Unit,
    onQuickPackUtilities: (List<AppInfo>) -> Unit,
    onSearchSuitcaseQueryChange: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentMins = sessionState.selectedDurationMinutes.coerceIn(1, 1440)

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        isAccessibilityEnabled = com.focusguard.app.recovery.AccessibilityRecoveryHelper.isAccessibilityActive(context)
        isOverlayGranted = Settings.canDrawOverlays(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        refreshPermissions()
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshPermissions() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedHours by remember(currentMins) { mutableIntStateOf(currentMins / 60) }
    var selectedMinutes by remember(currentMins) { mutableIntStateOf(currentMins % 60) }

    fun updateDuration(h: Int, m: Int) {
        selectedHours = h
        selectedMinutes = m
        onSelectDuration((h * 60 + m).coerceIn(1, 1440))
    }

    val projectedEndTime = remember(currentMins) {
        val end = System.currentTimeMillis() + (currentMins * 60 * 1000L)
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(end))
    }

    if (sessionState.showPackingDialog) {
        SuitcasePackingDialog(
            availableApps = availableApps,
            packedPackages = sessionState.packedPackages,
            searchQuery = sessionState.searchQuery,
            onSearchQueryChange = onSearchSuitcaseQueryChange,
            onTogglePackage = onTogglePackageInSuitcase,
            onQuickPackUtilities = onQuickPackUtilities,
            onDismiss = onClosePacking
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Forest950)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DeepFocusTopBar(
                isActive = sessionState.isActive,
                onBack = onBack
            )

            if (sessionState.isActive) {
                ActiveSessionHudCard(
                    sessionState = sessionState,
                    availableApps = availableApps,
                    onOpenPacking = onOpenPacking,
                    onRequestAbort = onRequestAbort,
                    onConfirmAbort = onConfirmAbort,
                    onDismissAbort = onDismissAbort
                )
            } else {
                RadialClockDial(
                    selectedMinutes = selectedMinutes,
                    onMinutesChange = { updateDuration(selectedHours, it) },
                    haptic = haptic,
                    sizeDp = 180
                )

                DeepFocusSteppersAndPresets(
                    selectedHours = selectedHours,
                    selectedMinutes = selectedMinutes,
                    onHoursChange = { updateDuration(it, selectedMinutes) },
                    onMinutesChange = { updateDuration(selectedHours, it) },
                    currentMins = currentMins,
                    projectedEndTime = projectedEndTime
                )

                SuitcasePreviewRow(
                    packedPackages = sessionState.packedPackages,
                    onOpenPacking = onOpenPacking
                )

                var isStarting by remember { mutableStateOf(false) }
                LaunchedEffect(sessionState.isActive) { isStarting = false }

                Button(
                    onClick = {
                        if (!isStarting) {
                            isStarting = true
                            refreshPermissions()
                            if (!isAccessibilityEnabled || !isOverlayGranted) {
                                isStarting = false
                                showPermissionsDialog = true
                            } else {
                                onStartSession()
                            }
                        }
                    },
                    enabled = !isStarting,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Color(0xFF031A0F)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("START DEEP FOCUS ($currentMins MIN)", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }

    if (showPermissionsDialog) {
        FeaturePermissionsDialog(
            featureName = "Deep Focus Kiosk",
            isAccessibilityGranted = isAccessibilityEnabled,
            isOverlayGranted = isOverlayGranted,
            onEnableAccessibility = {
                showPermissionsDialog = false
                showDisclosureDialog = true
            },
            onEnableOverlay = {
                showPermissionsDialog = false
                val directIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                if (context.packageManager.resolveActivity(directIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    context.startActivity(directIntent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
            },
            onDismiss = { showPermissionsDialog = false }
        )
    }

    if (showDisclosureDialog) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosureDialog = false
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                } catch (e: Exception) {
                    android.util.Log.d("DeepFocusScreen", "Launch a11y error: ${e.message}")
                }
            },
            onDismiss = { showDisclosureDialog = false }
        )
    }
}

@Composable
private fun DeepFocusTopBar(
    isActive: Boolean,
    onBack: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0x1AFFFFFF))
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = "Deep Focus Lockdown", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (isActive) "Lockdown in progress" else "Strict single-tasking session",
                color = if (isActive) Emerald400 else White60,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DeepFocusSteppersAndPresets(
    selectedHours: Int,
    selectedMinutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    currentMins: Int,
    projectedEndTime: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactStepper(
                label = "HOURS",
                value = selectedHours,
                onDecrement = { onHoursChange((selectedHours - 1).coerceAtLeast(0)) },
                onIncrement = { onHoursChange((selectedHours + 1).coerceAtMost(23)) },
                modifier = Modifier.weight(1f)
            )
            CompactStepper(
                label = "MINUTES",
                value = selectedMinutes,
                onDecrement = { onMinutesChange(if (selectedMinutes > 0) selectedMinutes - 1 else 59) },
                onIncrement = { onMinutesChange(if (selectedMinutes < 59) selectedMinutes + 1 else 0) },
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0x1422C55E),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3322C55E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Duration: $currentMins min",
                    color = Emerald400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(text = "Unlocks ~$projectedEndTime", color = White60, fontSize = 11.sp)
            }
        }
    }
}
