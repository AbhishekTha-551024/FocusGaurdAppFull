package com.focusguard.app.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.data.db.PendingChangeEntity
import kotlinx.coroutines.delay

private val Amber400 = Color(0xFFFBBF24)
private val Rose400  = Color(0xFFFB7185)
private val Success  = Color(0xFF22C55E)
private val Navy900  = Color(0xFF07140D)
private val Navy800  = Color(0xFF0E2419)

@Composable
fun PendingChangesBanner(
    pendingChanges: List<PendingChangeEntity>,
    onCancelPendingChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingChanges.isEmpty()) return

    // Live 1-second clock ticker so countdown updates in real time
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        pendingChanges.forEach { change ->
            val scheduled = change.scheduledApplyAt ?: (change.requestedAt + 86400000L)
            val diffMs = scheduled - now
            val isDue = diffMs <= 0

            val remainingFormatted = if (isDue) {
                "Ready"
            } else {
                val hours = diffMs / (1000 * 60 * 60)
                val mins = (diffMs / (1000 * 60)) % 60
                val secs = (diffMs / 1000) % 60
                when {
                    hours > 0 -> "${hours}h ${mins}m"
                    mins > 0 -> "${mins}m ${secs}s"
                    else -> "${secs}s"
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDue) Success.copy(alpha = 0.15f) else Amber400.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDue) Success.copy(alpha = 0.6f) else Amber400.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isDue) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = if (isDue) Success else Amber400,
                        modifier = Modifier.size(18.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDue) "Unblock Ready: ${change.targetName.ifBlank { change.targetId }}"
                            else "Unblock Pending: ${change.targetName.ifBlank { change.targetId }}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (isDue) "Cooldown completed! Unblock applied."
                            else "Applies in $remainingFormatted · Stay strong!",
                            color = if (isDue) Success else Amber400,
                            fontSize = 11.sp,
                            fontWeight = if (isDue) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    TextButton(
                        onClick = { onCancelPendingChange(change.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (isDue) Success else Rose400),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (isDue) "Dismiss" else "Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
