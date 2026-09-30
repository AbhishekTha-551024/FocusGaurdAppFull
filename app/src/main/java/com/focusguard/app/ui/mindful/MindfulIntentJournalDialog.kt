package com.focusguard.app.ui.mindful

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.app.data.db.MindfulIntentLogEntity
import java.text.SimpleDateFormat
import java.util.*

private val OLED_CARD_BG = Color(0xFF071810)
private val BORDER_GREEN = Color(0x3322C55E)
private val EMERALD_500 = Color(0xFF22C55E)
private val EMERALD_400 = Color(0xFF4ADE80)
private val ROSE_400 = Color(0xFFFB7185)

@Composable
fun MindfulIntentJournalDialog(
    logs: List<MindfulIntentLogEntity>,
    walkedAwayCount: Int,
    consciousOpensCount: Int,
    onDismiss: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a, dd MMM", Locale.getDefault())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OLED_CARD_BG),
            border = BorderStroke(1.dp, BORDER_GREEN)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x2222C55E),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = EMERALD_400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mindful Intent Journal",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Your daily conscious intentions",
                                fontSize = 12.sp,
                                color = Color(0x88FFFFFF)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0x88FFFFFF)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Daily Summary Pill
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x1A22C55E),
                    border = BorderStroke(1.dp, Color(0x3322C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$walkedAwayCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = EMERALD_400
                            )
                            Text(
                                text = "🔥 Walked Away",
                                fontSize = 11.sp,
                                color = Color(0xAAFFFFFF)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color(0x33FFFFFF))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$consciousOpensCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = "🌿 Conscious Opens",
                                fontSize = 11.sp,
                                color = Color(0xAAFFFFFF)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color(0x33FFFFFF))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${walkedAwayCount * 15}m",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                text = "⏱️ Time Saved",
                                fontSize = 11.sp,
                                color = Color(0xAAFFFFFF)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // List of logs
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No mindful opening events recorded yet.\nOpen a mindful app to start your conscious journal!",
                            color = Color(0x66FFFFFF),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x14FFFFFF),
                                border = BorderStroke(
                                    1.dp,
                                    if (log.wasWalkedAway) Color(0x3322C55E) else Color(0x22FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.appName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (log.wasWalkedAway) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0x2222C55E)
                                            ) {
                                                Text(
                                                    text = "🛑 Walked Away",
                                                    color = EMERALD_400,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0x2238BDF8)
                                            ) {
                                                Text(
                                                    text = "🌿 ${log.allowanceMinutes}m Allowance",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        text = "\"${log.intentText}\"",
                                        color = if (log.wasWalkedAway) Color(0xAA86EFAC) else Color(0xDDFFFFFF),
                                        fontSize = 12.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = timeFormat.format(Date(log.timestamp)),
                                        color = Color(0x55FFFFFF),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EMERALD_500, contentColor = Color(0xFF031A0F)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Journal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
