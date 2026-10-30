package com.focusguard.app.feature.mindful.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.core.model.AppInfo

private val Forest900 = Color(0xFF07140D)
private val CardBg = Color(0xFF0D2117)
private val Emerald400 = Color(0xFF4ADE80)
private val TextMuted = Color(0x99FFFFFF)

@Composable
fun MindfulConfirmAddDialog(
    app: AppInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Add to Mindful Apps?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "When you open ${app.appName}, FocusGuard will introduce an intentional breathing pause, ask your conscious reason, and set a time allowance before granting access.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1A22C55E),
                    border = BorderStroke(0.8.dp, Emerald400.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Mindful Intention: Helps you break dopamine loops and pause before opening habitually.",
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "Do you want to add ${app.appName} to Mindful Pause?",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            var isSubmitting by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (!isSubmitting) {
                        isSubmitting = true
                        onConfirm()
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald400,
                    contentColor = Forest900
                )
            ) {
                Text("Add to Mindful", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = CardBg,
        shape = RoundedCornerShape(18.dp)
    )
}
