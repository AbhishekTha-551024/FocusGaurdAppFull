package com.focusguard.app.feature.schedules.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusguard.app.core.common.validator.InputValidator
import com.focusguard.app.core.common.validator.ValidationResult
import com.focusguard.app.core.model.AppInfo
import java.util.Calendar

private val Navy900    = Color(0xFF07140D)
private val Navy800    = Color(0xFF0E2419)
private val Navy700    = Color(0xFF163827)
private val Emerald400 = Color(0xFF4ADE80)
private val Amber400   = Color(0xFFFBBF24)
private val Rose400    = Color(0xFFFB7185)
private val Success    = Color(0xFF34D399)
private val White90    = Color(0xE6FFFFFF)
private val White60    = Color(0x99FFFFFF)
private val White40    = Color(0x66FFFFFF)

@Composable
fun AddCustomScheduleDialog(
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, targetPackage: String?, appLabel: String?, daysMask: Int, startMin: Int, endMin: Int) -> Unit
) {
    val context = LocalContext.current
    val currentCal = remember { Calendar.getInstance() }
    val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
    val currentMin = currentCal.get(Calendar.MINUTE)

    var title by remember { mutableStateOf("") }
    var allowedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var showConfirmScheduleDialog by remember { mutableStateOf(false) }

    var startMinuteOfDay by remember { mutableIntStateOf(currentHour * 60 + currentMin) }
    var endMinuteOfDay by remember { mutableIntStateOf(((currentHour + 1) % 24) * 60 + currentMin) }
    var daysMask by remember { mutableIntStateOf(127) }

    val titleValidation = remember(title) {
        if (title.isEmpty()) null else InputValidator.validateScheduleTitle(title)
    }
    val isTitleError = titleValidation is ValidationResult.Invalid
    val titleErrorMessage = (titleValidation as? ValidationResult.Invalid)?.error?.message
    val isValid = (titleValidation is ValidationResult.Valid) && daysMask > 0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Navy800),
            border = BorderStroke(1.5.dp, Amber400.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Create Focus Schedule", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Emerald400, modifier = Modifier.size(22.dp))
                }

                SchedulePresetsRow(
                    onSelectPreset = { presetTitle, start, end, mask ->
                        title = presetTitle
                        startMinuteOfDay = start
                        endMinuteOfDay = end
                        daysMask = mask
                    }
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Schedule name (e.g. Work Focus)", color = White40, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = isTitleError,
                    supportingText = if (isTitleError && titleErrorMessage != null) {
                        { Text(titleErrorMessage, color = Rose400, fontSize = 11.5.sp) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Navy700,
                        unfocusedContainerColor = Navy700,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = if (title.isBlank()) Color(0x664ADE80) else Color(0x334ADE80),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        errorBorderColor = Rose400
                    ),
                    singleLine = true
                )

                ScheduleAllowedAppsTrigger(
                    allowedCount = allowedPackages.size,
                    onClick = { isAppPickerOpen = true }
                )

                ScheduleDaysSelector(
                    daysMask = daysMask,
                    onToggleDay = { bit ->
                        daysMask = if ((daysMask and bit) != 0) daysMask and bit.inv() else daysMask or bit
                    }
                )

                ScheduleTimeWindowSelector(
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    onOpenStartPicker = {
                        TimePickerDialog(context, { _, h, m -> startMinuteOfDay = h * 60 + m }, startMinuteOfDay / 60, startMinuteOfDay % 60, false).show()
                    },
                    onOpenEndPicker = {
                        TimePickerDialog(context, { _, h, m -> endMinuteOfDay = h * 60 + m }, endMinuteOfDay / 60, endMinuteOfDay % 60, false).show()
                    }
                )

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
                        onClick = { if (title.trim().isNotBlank()) showConfirmScheduleDialog = true },
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
            ScheduleConfirmDialog(
                title = title.trim(),
                onDismiss = { showConfirmScheduleDialog = false },
                onConfirm = {
                    showConfirmScheduleDialog = false
                    val targetPkg = if (allowedPackages.isNotEmpty()) "ALLOWED:${allowedPackages.joinToString(",")}" else "ALL_APPS"
                    val displayLabel = if (allowedPackages.isNotEmpty()) "Allowed ${allowedPackages.size} apps" else "All Apps"
                    onConfirm(title.trim(), targetPkg, displayLabel, daysMask, startMinuteOfDay, endMinuteOfDay)
                }
            )
        }

        if (isAppPickerOpen) {
            AllowedAppsPickerDialog(
                installedApps = installedApps,
                selectedPackages = allowedPackages,
                onTogglePackage = { pkg ->
                    allowedPackages = if (allowedPackages.contains(pkg)) allowedPackages - pkg else allowedPackages + pkg
                },
                onDismiss = { isAppPickerOpen = false }
            )
        }
    }
}

@Composable
private fun ScheduleAllowedAppsTrigger(
    allowedCount: Int,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Apps Allowed (whitelist):", color = White90, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(10.dp),
            color = Navy700,
            border = BorderStroke(1.dp, Success.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
                Text(
                    text = if (allowedCount == 0) "Block all apps · Tap to allow exceptions" else "$allowedCount apps allowed",
                    color = if (allowedCount == 0) Color.White else Success,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("Select ▾", color = Success, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScheduleConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Amber400, modifier = Modifier.size(22.dp))
                Text("Activate Focus Schedule?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Schedule \"$title\" will automatically lock distracting apps during the selected hours.", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Amber400.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, Amber400.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "🔒 Protected Schedule: Modifying, disabling, or deleting this schedule during active hours will require completing your Accountability Challenge.", color = Amber400, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(10.dp))
                }
                Text("Do you want to confirm activating this schedule?", color = White60, fontSize = 12.sp)
            }
        },
        confirmButton = {
            var isSubmitting by remember { mutableStateOf(false) }
            LaunchedEffect(title) { isSubmitting = false }
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        onConfirm()
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Navy900)
            ) {
                Text("Confirm & Activate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = White60) }
        },
        containerColor = Navy800,
        shape = RoundedCornerShape(18.dp)
    )
}
