package com.focusguard.app.feature.schedules.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.database.entity.ScheduleEntity
import com.focusguard.app.feature.schedules.ui.ScheduleFormatters

private val Navy800  = Color(0xFF0E2419)
private val Navy700  = Color(0xFF163827)
private val Amber400 = Color(0xFFFBBF24)
private val Rose400  = Color(0xFFFB7185)
private val White60  = Color(0x99FFFFFF)
private val White40  = Color(0x66FFFFFF)

@Composable
fun CompactScheduleRow(
    schedule: ScheduleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val formattedSentence = remember(schedule.daysOfWeekMask, schedule.startMinuteOfDay, schedule.endMinuteOfDay) {
        "${ScheduleFormatters.formatDaysOfWeek(schedule.daysOfWeekMask)} · ${ScheduleFormatters.formatMinutes(schedule.startMinuteOfDay)} - ${ScheduleFormatters.formatMinutes(schedule.endMinuteOfDay)}"
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
        border = BorderStroke(
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
                    color = if (schedule.isEnabled) Color.White else White60,
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

            Switch(
                checked = schedule.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.75f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Amber400,
                    checkedTrackColor = Amber400.copy(alpha = 0.35f),
                    uncheckedThumbColor = White40,
                    uncheckedTrackColor = Navy700
                )
            )

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
