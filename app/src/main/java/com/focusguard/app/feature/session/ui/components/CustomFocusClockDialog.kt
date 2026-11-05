package com.focusguard.app.feature.session.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest900 = Color(0xFF07140D)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val TextMuted = Color(0x99FFFFFF)

@Composable
fun CustomFocusClockDialog(
    initialMinutes: Int,
    onConfirmDuration: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val safeInitial = initialMinutes.coerceIn(1, 1440)
    var selectedHours by remember { mutableIntStateOf(safeInitial / 60) }
    var selectedMinutes by remember { mutableIntStateOf(safeInitial % 60) }

    val totalMinutes = (selectedHours * 60 + selectedMinutes).coerceIn(1, 1440)
    val projectedEndTime = remember(totalMinutes) {
        val end = System.currentTimeMillis() + (totalMinutes * 60 * 1000L)
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(end))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Forest900,
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0x5522C55E), Color(0x22FBBF24)))),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ClockHeader()
                Spacer(Modifier.height(16.dp))

                RadialClockDial(
                    selectedMinutes = selectedMinutes,
                    onMinutesChange = { selectedMinutes = it },
                    haptic = haptic,
                    sizeDp = 180
                )

                Spacer(Modifier.height(16.dp))

                ClockSteppersRow(
                    selectedHours = selectedHours,
                    selectedMinutes = selectedMinutes,
                    onHoursChange = { selectedHours = it },
                    onMinutesChange = { selectedMinutes = it }
                )

                Spacer(Modifier.height(14.dp))

                DurationPresetsRow(
                    selectedTotal = totalMinutes,
                    onSelect = { h, m ->
                        selectedHours = h
                        selectedMinutes = m
                    }
                )

                Spacer(Modifier.height(14.dp))
                ProjectionPill(totalMinutes = totalMinutes, projectedEndTime = projectedEndTime)
                Spacer(Modifier.height(16.dp))

                ClockActionsRow(
                    totalMinutes = totalMinutes,
                    onDismiss = onDismiss,
                    onConfirm = {
                        onConfirmDuration(totalMinutes)
                    }
                )
            }
        }
    }
}

@Composable
private fun ClockHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LockClock, contentDescription = null, tint = Emerald400, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Custom Focus Clock", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Precision dial from 1m to 24h", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ClockSteppersRow(
    selectedHours: Int,
    selectedMinutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit
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
}

@Composable
private fun DurationPresetsRow(
    selectedTotal: Int,
    onSelect: (Int, Int) -> Unit
) {
    val presets = listOf(15, 25, 45, 60, 90, 120, 180)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(presets) { mins ->
            val isSelected = selectedTotal == mins
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) Emerald500 else Color(0x18FFFFFF),
                border = BorderStroke(1.dp, if (isSelected) Emerald400 else Color(0x22FFFFFF)),
                onClick = { onSelect(mins / 60, mins % 60) }
            ) {
                Text(
                    text = if (mins >= 60) "${mins / 60}h" else "${mins}m",
                    color = if (isSelected) Forest900 else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ProjectionPill(totalMinutes: Int, projectedEndTime: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1422C55E),
        border = BorderStroke(1.dp, Color(0x3322C55E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total: $totalMinutes min", color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Ends ~$projectedEndTime", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ClockActionsRow(
    totalMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Text("Cancel", color = TextMuted)
        }
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500, contentColor = Forest900),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Text("Set $totalMinutes Min", fontWeight = FontWeight.Bold)
        }
    }
}
