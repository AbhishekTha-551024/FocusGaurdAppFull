package com.focusguard.app.ui.schedules

import android.app.TimePickerDialog
import android.content.Context
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
import androidx.compose.ui.draw.scale
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
import com.focusguard.app.data.db.ScheduleEntity
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.ui.challenge.SentenceTypingChallengeDialog
import com.focusguard.app.ui.challenge.PartnerApprovalPendingDialog
import com.focusguard.app.ui.components.AppIconView
import com.focusguard.app.ui.components.SearchField
import com.focusguard.app.ui.theme.FocusGuardTheme
import java.util.Calendar
import java.util.Locale

private val Navy900   = Color(0xFF07140D)
private val Navy800   = Color(0xFF0E2419)
private val Navy700   = Color(0xFF163827)
private val Amber400  = Color(0xFFFBBF24)
private val Indigo400 = Color(0xFF22C55E)
private val Success   = Color(0xFF22C55E)
private val Rose400   = Color(0xFFFB7185)
private val White90   = Color(0xE6FFFFFF)
private val White60   = Color(0x99FFFFFF)
private val White40   = Color(0x66FFFFFF)

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
    onActivateChallenge: () -> Unit = {},
    onDismissAccountability: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    FocusGuardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Focus Schedules",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${uiState.schedules.size} schedules active",
                                color = if (uiState.schedules.isNotEmpty()) Amber400 else White60,
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
                    onClick = onOpenAddDialog,
                    containerColor = Amber400,
                    contentColor = Navy900,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Schedule",
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

                    if (uiState.schedules.isEmpty()) {
                        // Clean Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Amber400.copy(alpha = 0.15f))
                                    .border(1.5.dp, Amber400.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No Focus Schedules Set",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Set focus hours (e.g. Mon–Fri, 9:00 AM to 5:00 PM) to automatically block distractions while allowing your essential apps.",
                                color = White60,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = onOpenAddDialog,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create Focus Schedule", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text(
                                    text = "ACTIVE SCHEDULES (${uiState.schedules.size})",
                                    color = White40,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }

                            items(uiState.schedules, key = { "sched_${it.id}_${it.title}" }) { schedule ->
                                CompactScheduleRow(
                                    schedule = schedule,
                                    onDelete = { onDeleteSchedule(schedule.id, schedule.title) }
                                )
                            }

                            item {
                                Spacer(Modifier.height(80.dp))
                            }
                        }
                    }
                }

                // Add Schedule Dialog
                if (uiState.isAddDialogOpen) {
                    AddCustomScheduleDialog(
                        installedApps = uiState.installedApps,
                        onDismiss = onCloseAddDialog,
                        onConfirm = onAddSchedule
                    )
                }

                // ── ACCOUNTABILITY REQUIRED DIALOG (Prompt to turn on challenge) ─
                val pendingSched = uiState.pendingAccountabilitySchedule
                if (pendingSched != null) {
                    val actionText = if (pendingSched.isDelete) {
                        "delete schedule '${pendingSched.schedule.title}'"
                    } else {
                        "disable schedule '${pendingSched.schedule.title}'"
                    }
                    com.focusguard.app.ui.components.AccountabilityRequiredDialog(
                        featureName = pendingSched.schedule.title,
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

@Composable
private fun CompactScheduleRow(
    schedule: ScheduleEntity,
    onDelete: () -> Unit
) {
    val formattedSentence = remember(schedule.daysOfWeekMask, schedule.startMinuteOfDay, schedule.endMinuteOfDay) {
        "${formatDaysOfWeek(schedule.daysOfWeekMask)} · ${formatMinutes(schedule.startMinuteOfDay)} - ${formatMinutes(schedule.endMinuteOfDay)}"
    }

    val appScopeLabel = remember(schedule.packageName, schedule.appName) {
        when {
            schedule.packageName?.startsWith("ALLOWED:") == true -> {
                val count = schedule.packageName.removePrefix("ALLOWED:").split(",").filter { it.isNotBlank() }.size
                "Allowed $count essential apps"
            }
            schedule.appName != null -> schedule.appName
            else -> "All Distracting Apps"
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isEnabled) Color(0x22FBBF24) else Navy800
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (schedule.isEnabled) Amber400.copy(alpha = 0.4f) else Color(0x14FFFFFF)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (schedule.isEnabled) Amber400.copy(alpha = 0.2f) else Navy700),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (schedule.isEnabled) Amber400 else White40,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${schedule.title} ($appScopeLabel)",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formattedSentence,
                    color = if (schedule.isEnabled) Amber400 else White60,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Schedule",
                    tint = Rose400,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomScheduleDialog(
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, targetPackage: String?, appLabel: String?, daysMask: Int, startMin: Int, endMin: Int) -> Unit
) {
    val context = LocalContext.current
    val currentCal = remember { Calendar.getInstance() }
    val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
    val currentMin = currentCal.get(Calendar.MINUTE)

    var title by remember { mutableStateOf("") }
    
    // Whitelist (Apps NOT to block during this schedule)
    var allowedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var showConfirmScheduleDialog by remember { mutableStateOf(false) }

    // Start & End minute of day (0..1439)
    var startMinuteOfDay by remember { mutableIntStateOf(currentHour * 60 + currentMin) }
    var endMinuteOfDay by remember { mutableIntStateOf(((currentHour + 1) % 24) * 60 + currentMin) }
    var daysMask by remember { mutableIntStateOf(127) }

    val isTitleValid = title.trim().isNotBlank()
    val isValid = isTitleValid && daysMask > 0

    // Direct in-place Android TimePickerDialog (No separate sub-dialog page!)
    fun openStartTimePicker() {
        val h = startMinuteOfDay / 60
        val m = startMinuteOfDay % 60
        TimePickerDialog(context, { _, hourOfDay, minute ->
            startMinuteOfDay = hourOfDay * 60 + minute
        }, h, m, false).show()
    }

    fun openEndTimePicker() {
        val h = endMinuteOfDay / 60
        val m = endMinuteOfDay % 60
        TimePickerDialog(context, { _, hourOfDay, minute ->
            endMinuteOfDay = hourOfDay * 60 + minute
        }, h, m, false).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Amber400.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create Focus Schedule",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                // Schedule Name (Required)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Schedule name (required, e.g. Focus Hours)", color = White40, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = title.isNotBlank() && title.trim().isEmpty(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Navy700,
                        unfocusedContainerColor = Navy700,
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = if (title.isBlank()) Color(0x66FBBF24) else Color(0x33FBBF24),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Apps NOT to Block (Allowed Apps during Schedule)
                Text(text = "Apps Allowed (whitelist):", color = White90, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Navy700,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Success.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAppPickerOpen = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
                        
                        Text(
                            text = if (allowedPackages.isEmpty())
                                "Block all apps · Tap to allow"
                            else
                                "${allowedPackages.size} apps allowed",
                            color = if (allowedPackages.isEmpty()) Color.White else Success,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "Select ▾",
                            color = Success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Days of Week Chips
                Text(text = "Active Days:", color = White90, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val days = listOf("M" to 1, "Tu" to 2, "W" to 4, "Th" to 8, "F" to 16, "Sa" to 32, "Su" to 64)
                    days.forEach { (label, bit) ->
                        val isSelected = (daysMask and bit) != 0
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Amber400 else Navy700)
                                .clickable {
                                    daysMask = if (isSelected) daysMask and bit.inv() else daysMask or bit
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Navy900 else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // In-place Time Pickers
                Text(text = "Time Window (Tap to change time):", color = White90, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Navy700,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).clickable { openStartTimePicker() }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Start Time", color = White60, fontSize = 10.sp)
                            Text(formatMinutes(startMinuteOfDay), color = Amber400, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Text("→", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Navy700,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).clickable { openEndTimePicker() }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("End Time", color = White60, fontSize = 10.sp)
                            Text(formatMinutes(endMinuteOfDay), color = Amber400, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = White60, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            if (cleanTitle.isNotBlank()) {
                                showConfirmScheduleDialog = true
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        if (showConfirmScheduleDialog) {
            val cleanTitle = title.trim()
            AlertDialog(
                onDismissRequest = { showConfirmScheduleDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("Activate Focus Schedule?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Schedule \"$cleanTitle\" will automatically lock distracting apps during the selected hours.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Amber400.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Amber400.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🔒 Protected Schedule: Modifying, disabling, or deleting this schedule during active hours will require completing your Accountability Challenge.",
                                color = Amber400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Text(
                            text = "Do you want to confirm activating this schedule?",
                            color = White60,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmScheduleDialog = false
                            val targetPkg = if (allowedPackages.isNotEmpty()) {
                                "ALLOWED:${allowedPackages.joinToString(",")}"
                            } else {
                                "ALL_APPS"
                            }
                            val displayLabel = if (allowedPackages.isNotEmpty()) {
                                "Allowed ${allowedPackages.size} apps"
                            } else {
                                "All Apps"
                            }

                            onConfirm(
                                cleanTitle,
                                targetPkg,
                                displayLabel,
                                daysMask,
                                startMinuteOfDay,
                                endMinuteOfDay
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900)
                    ) {
                        Text("Confirm & Activate", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmScheduleDialog = false }) {
                        Text("Cancel", color = White60)
                    }
                },
                containerColor = Navy800,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Multi-select App Picker for selecting which apps NOT to block (Allowed Apps)
        if (isAppPickerOpen) {
            AllowedAppsPickerDialog(
                installedApps = installedApps,
                selectedPackages = allowedPackages,
                onTogglePackage = { pkg ->
                    allowedPackages = if (allowedPackages.contains(pkg)) {
                        allowedPackages - pkg
                    } else {
                        allowedPackages + pkg
                    }
                },
                onDismiss = { isAppPickerOpen = false }
            )
        }
    }
}

/**
 * Clean Multi-Select Picker for Apps NOT to Block (Allowed Apps)
 */
@Composable
private fun AllowedAppsPickerDialog(
    installedApps: List<AppInfo>,
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, installedApps) {
        if (query.isBlank()) installedApps
        else installedApps.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().background(Navy900),
            color = Navy900
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 8.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Select Apps to Allow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Checked apps will NOT be blocked", color = Success, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Navy900),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholderText = "Search apps to allow (e.g. WhatsApp, Maps)..."
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isAllowed = selectedPackages.contains(app.packageName)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAllowed) Color(0x2E34D399) else Navy800
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAllowed) Success.copy(alpha = 0.6f) else Color(0x14FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIconView(packageName = app.packageName, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isAllowed) "✓ Allowed (Not blocked)" else "Will be blocked during focus",
                                        color = if (isAllowed) Success else White60,
                                        fontSize = 11.sp
                                    )
                                }
                                Checkbox(
                                    checked = isAllowed,
                                    onCheckedChange = { onTogglePackage(app.packageName) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Success,
                                        checkmarkColor = Navy900,
                                        uncheckedColor = White60
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinutes(minutesOfDay: Int): String {
    val h = minutesOfDay / 60
    val m = minutesOfDay % 60
    val amPm = if (h >= 12) "PM" else "AM"
    val displayH = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return String.format(Locale.getDefault(), "%02d:%02d %s", displayH, m, amPm)
}

private fun formatDaysOfWeek(mask: Int): String {
    return when (mask) {
        127 -> "Every day"
        31 -> "Weekdays"
        96 -> "Weekends"
        else -> {
            val list = mutableListOf<String>()
            if ((mask and 1) != 0) list.add("Mon")
            if ((mask and 2) != 0) list.add("Tue")
            if ((mask and 4) != 0) list.add("Wed")
            if ((mask and 8) != 0) list.add("Thu")
            if ((mask and 16) != 0) list.add("Fri")
            if ((mask and 32) != 0) list.add("Sat")
            if ((mask and 64) != 0) list.add("Sun")
            list.joinToString(", ")
        }
    }
}
