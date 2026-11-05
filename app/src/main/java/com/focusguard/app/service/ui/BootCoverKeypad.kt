package com.focusguard.app.service.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.Emerald400
import com.focusguard.app.ui.theme.Forest900
import com.focusguard.app.ui.theme.White60
import com.focusguard.app.ui.theme.White90

@Composable
internal fun DigitIndicatorRow(code: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 6) {
            val digit = code.getOrNull(i)
            val isFilled = digit != null
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFilled) Forest900 else Forest900.copy(alpha = 0.5f))
                    .border(
                        1.5.dp,
                        if (isFilled) Emerald400 else White60.copy(alpha = 0.25f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = digit?.toString() ?: "",
                    color = White90,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
internal fun KeypadGrid(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9')
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (digit in row) {
                    KeypadButton(text = digit.toString(), enabled = enabled) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(68.dp, 48.dp)) // empty spacer
            KeypadButton(text = "0", enabled = enabled) { onDigit('0') }
            Surface(
                modifier = Modifier
                    .size(68.dp, 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, onClick = onBackspace),
                color = Forest900.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = White90,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(68.dp, 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = Forest900.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = White90,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

