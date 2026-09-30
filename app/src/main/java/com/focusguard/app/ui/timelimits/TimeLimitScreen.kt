package com.focusguard.app.ui.timelimits

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.data.db.ChallengeType
import com.focusguard.app.data.db.ChangeType
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.ui.components.SearchField
import com.focusguard.app.capability.Capability
import com.focusguard.app.capability.CapabilityDetails
import com.focusguard.app.capability.CapabilityState
import com.focusguard.app.ui.capability.CapabilityExplanationDialog
import com.focusguard.app.ui.theme.FocusGuardTheme

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Purple400 = Color(0xFF22C55E)
private val Rose400   = Color(0xFFFB7185)
private val Amber400  = Color(0xFFFBBF24)
private val White90   = Color(0xE6FFFFFF)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

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
    onActivateChallenge: () -> Unit = {},
    onDismissAccountability: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isAddLimitPickerOpen by remember { mutableStateOf(false) }
    var showUsageExplanation by remember { mutableStateOf(false) }

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

    val configuredApps = remember(uiState.apps) {
        uiState.apps.filter { it.isLimitConfigured }
    }

    if (showUsageExplanation) {
        CapabilityExplanationDialog(
            details = CapabilityDetails(
                capability = Capability.USAGE_ACCESS,
                state = CapabilityState.NEEDS_ACCESS,
                title = "Screen Time & Daily Limits",
                featureName = "App Time Limits",
                whyNeeded = "To measure your daily app usage and enforce your custom time limits, FocusGuard needs Usage Access.",
                benefits = listOf(
                    "Track exact minutes spent inside addictive apps",
                    "Automatically block apps when your daily cap is reached",
                    "Daily usage trends and habit improvement"
                ),
                privacyPromise = "Usage data stays strictly on your phone and is never sent to external servers.",
                actionButtonText = "Enable Screen Time"
            ),
            onEnable = {
                showUsageExplanation = false
                onRequestUsagePermission()
            },
            onDismiss = {
                showUsageExplanation = false
                isAddLimitPickerOpen = true
            }
        )
    }

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Daily Time Limits",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${configuredApps.size} apps with daily caps",
                                color = if (configuredApps.isNotEmpty()) Purple400 else White60,
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
                        if (!uiState.hasUsagePermission) {
                            showUsageExplanation = true
                        } else {
                            isAddLimitPickerOpen = true
                        }
                    },
                    containerColor = Purple400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Set Daily Limit",
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

                    // Usage Permission Warning if not granted
                    if (!uiState.hasUsagePermission) {
                        Surface(
                            color = Color(0x33FBBF24),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showUsageExplanation = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Amber400, modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Usage Access Permission Required", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Tap to grant permission so FocusGuard can track screen time.", color = White60, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (configuredApps.isEmpty() && uiState.apps.isNotEmpty()) {
                        // Empty State: No daily limits configured
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
                                    .background(Purple400.copy(alpha = 0.15f))
                                    .border(1.5.dp, Purple400.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = Purple400,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "No Daily Limits Configured",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Set daily caps (e.g. 30 min/day on YouTube or Instagram). Once exceeded, apps are locked until midnight.",
                                color = White60,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { isAddLimitPickerOpen = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Purple400, contentColor = Navy900),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Set Daily Limit", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1)
                            }
                        }
                    } else {
                        // Main Screen: Shows ONLY apps that have an active limit configured
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text(
                                    text = "APPS WITH ACTIVE LIMITS (${configuredApps.size})",
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
                                    onDelete = {
                                        onToggleLimit(model.appInfo, model.dailyLimitMinutes, false)
                                    }
                                )
                            }

                            item {
                                Spacer(Modifier.height(80.dp))
                            }
                        }
                    }
                }

                // Modal Picker to set a limit on any installed app
                if (isAddLimitPickerOpen) {
                    AddLimitPickerDialog(
                        uiState = uiState,
                        onSearchQueryChange = onSearchQueryChange,
                        onSetLimit = { app, minutes ->
                            onSetTimeLimit(app, minutes)
                            isAddLimitPickerOpen = false
                        },
                        onDismiss = {
                            onSearchQueryChange("")
                            isAddLimitPickerOpen = false
                        }
                    )
                }

                // ── ACCOUNTABILITY REQUIRED DIALOG (Prompt to turn on challenge) ─
                val pendingLimit = uiState.pendingAccountabilityLimit
                if (pendingLimit != null) {
                    val actionText = if (pendingLimit.actionType == ChangeType.DISABLE_LIMIT) {
                        "remove the daily limit for ${pendingLimit.app.appName}"
                    } else {
                        "increase daily limit for ${pendingLimit.app.appName} to ${pendingLimit.newMinutes}m"
                    }
                    com.focusguard.app.ui.components.AccountabilityRequiredDialog(
                        featureName = pendingLimit.app.appName,
                        subtitle = "Accountability Challenge Required",
                        message = "To $actionText, FocusGuard requires an active Accountability Challenge (Sentence Typing).\n\nTurn ON the challenge now to confirm and proceed.",
                        confirmButtonText = "Turn ON Challenge & Proceed",
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
 * Compact ~68dp row for an active daily limit
 */
@Composable
private fun CompactTimeLimitRow(
    model: AppTimeLimitUiModel,
    onDelete: () -> Unit
) {
    val progress = (model.usedMinutesToday.toFloat() / model.dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
    val isOverLimit = model.usedMinutesToday >= model.dailyLimitMinutes

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverLimit) Color(0x2EFB7185) else Navy800
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOverLimit) Rose400.copy(alpha = 0.6f) else Color(0x14FFFFFF)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIconView(packageName = model.appInfo.packageName, size = 36.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.appInfo.appName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${model.usedMinutesToday}m used of ${model.dailyLimitMinutes}m cap",
                        color = if (isOverLimit) Rose400 else Purple400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Locked limit badge (cannot edit once chosen)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Navy700,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = White40,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${model.dailyLimitMinutes}m",
                            color = White90,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Delete Button instead of Toggle Switch
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Daily Limit",
                        tint = Rose400,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Slim linear usage progress bar
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isOverLimit) Rose400 else Purple400,
                trackColor = Color(0x1AFFFFFF)
            )
        }
    }
}


/**
 * Modal App Picker Dialog for Setting Daily Limits
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLimitPickerDialog(
    uiState: TimeLimitUiState,
    onSearchQueryChange: (String) -> Unit,
    onSetLimit: (AppInfo, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var chosenMinutes by remember { mutableIntStateOf(30) }

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set Daily Time Limit",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pick an app and choose a daily usage cap",
                            color = White60,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = White60)
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

                // App selection list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.filteredApps,
                        key = { it.appInfo.packageName },
                        contentType = { "limit_picker_row" }
                    ) { model ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Navy800),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedApp = model.appInfo }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconView(packageName = model.appInfo.packageName, size = 38.dp)

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.appInfo.appName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (model.isLimitConfigured) "Cap: ${model.dailyLimitMinutes}m/day (${model.usedMinutesToday}m used)" else "No cap set",
                                        color = if (model.isLimitConfigured) Purple400 else White40,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (model.isLimitConfigured) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Navy700,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF))
                                    ) {
                                        Text(
                                            text = "Active · Locked",
                                            color = Purple400,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { selectedApp = model.appInfo },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple400, contentColor = Navy900),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Set Cap", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub-dialog to pick minutes when app is selected
        if (selectedApp != null) {
            var isCustomInput by remember { mutableStateOf(false) }
            var customMinutesText by remember { mutableStateOf("30") }

            Dialog(onDismissRequest = { selectedApp = null }) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy800),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Purple400.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconView(packageName = selectedApp!!.packageName, size = 48.dp)
                        Text(
                            text = "Daily Limit for ${selectedApp!!.appName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Text(
                            text = "$chosenMinutes Minutes / Day",
                            color = Purple400,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )

                        // Presets + Custom Option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 60, 90).forEach { mins ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!isCustomInput && chosenMinutes == mins) Purple400 else Navy700,
                                    modifier = Modifier.weight(1f).clickable {
                                        chosenMinutes = mins
                                        customMinutesText = mins.toString()
                                        isCustomInput = false
                                    }
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (!isCustomInput && chosenMinutes == mins) Navy900 else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                            // Custom Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCustomInput) Purple400 else Navy700,
                                modifier = Modifier.weight(1f).clickable {
                                    isCustomInput = true
                                }
                            ) {
                                Text(
                                    text = "Custom",
                                    color = if (isCustomInput) Navy900 else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }

                        // Custom Numeric Input or Slider
                        if (isCustomInput) {
                            OutlinedTextField(
                                value = customMinutesText,
                                onValueChange = { text ->
                                    val filtered = text.filter { it.isDigit() }.take(4)
                                    customMinutesText = filtered
                                    filtered.toIntOrNull()?.let {
                                        if (it > 0) chosenMinutes = it.coerceIn(1, 1440)
                                    }
                                },
                                label = { Text("Enter minutes (1–1440)", color = White60, fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Purple400,
                                    unfocusedBorderColor = Color(0x33FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Slider(
                                value = chosenMinutes.toFloat(),
                                onValueChange = {
                                    chosenMinutes = it.toInt()
                                    customMinutesText = it.toInt().toString()
                                },
                                valueRange = 5f..180f,
                                steps = 34,
                                colors = SliderDefaults.colors(
                                    thumbColor = Purple400,
                                    activeTrackColor = Purple400,
                                    inactiveTrackColor = Navy700
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedApp = null },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", color = White60)
                            }

                            Button(
                                onClick = {
                                    onSetLimit(selectedApp!!, chosenMinutes)
                                    selectedApp = null
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Purple400, contentColor = Navy900)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

