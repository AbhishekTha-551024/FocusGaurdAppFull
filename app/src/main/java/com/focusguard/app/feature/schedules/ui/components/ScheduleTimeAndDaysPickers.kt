package com.focusguard.app.feature.schedules.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.feature.schedules.ui.ScheduleFormatters

private val Navy900    = Color(0xFF07140D)
private val Navy700    = Color(0xFF163827)
private val Emerald400 = Color(0xFF4ADE80)
private val White90    = Color(0xE6FFFFFF)
private val White60    = Color(0x99FFFFFF)

@Composable
fun SchedulePresetsRow(
    onSelectPreset: (String, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Quick Presets:", color = White90, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Navy700,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.weight(1f).clickable { onSelectPreset("Deep Work", 9 * 60, 17 * 60, 31) }
            ) {
                Text("💼 Deep Work", color = White90, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp), maxLines = 1)
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Navy700,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.weight(1f).clickable { onSelectPreset("Night Sleep", 22 * 60 + 30, 6 * 60 + 30, 127) }
            ) {
                Text("🌙 Night Sleep", color = White90, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp), maxLines = 1)
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Navy700,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.weight(1f).clickable { onSelectPreset("Digital Detox", 8 * 60, 20 * 60, 96) }
            ) {
                Text("🌿 Detox", color = White90, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp), maxLines = 1)
            }
        }
    }
}

@Composable
fun ScheduleDaysSelector(
    daysMask: Int,
    onToggleDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Active Days:", color = White90, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val days = listOf("M" to 1, "Tu" to 2, "W" to 4, "Th" to 8, "F" to 16, "Sa" to 32, "Su" to 64)
            days.forEach { (label, bit) ->
                val isSelected = (daysMask and bit) != 0
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Emerald400 else Navy700)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFF86EFAC) else Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickable { onToggleDay(bit) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = label, color = if (isSelected) Navy900 else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ScheduleTimeWindowSelector(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    onOpenStartPicker: () -> Unit,
    onOpenEndPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Time Window (Tap to change):", color = White90, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Navy700,
                border = BorderStroke(1.2.dp, Emerald400.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f).clickable { onOpenStartPicker() }
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Start Time", color = White60, fontSize = 10.sp)
                    Text(ScheduleFormatters.formatMinutes(startMinuteOfDay), color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Text("→", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Navy700,
                border = BorderStroke(1.2.dp, Emerald400.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f).clickable { onOpenEndPicker() }
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("End Time", color = White60, fontSize = 10.sp)
                    Text(ScheduleFormatters.formatMinutes(endMinuteOfDay), color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
