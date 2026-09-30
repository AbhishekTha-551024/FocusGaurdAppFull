package com.focusguard.app.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.data.model.AppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardBg = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val Amber400 = Color(0xFFFBBF24)
private val TextMuted = Color(0x99FFFFFF)

@Composable
fun DeepFocusCard(
    sessionState: SessionUiState,
    availableApps: List<AppInfo>,
    onSelectDuration: (Int) -> Unit,
    onOpenPacking: () -> Unit,
    onStartSession: () -> Unit,
    onRequestAbort: () -> Unit,
    onConfirmAbort: () -> Unit,
    onDismissAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomClockDialog by remember { mutableStateOf(false) }

    if (showCustomClockDialog) {
        CustomFocusClockDialog(
            initialMinutes = sessionState.selectedDurationMinutes,
            onConfirmDuration = { newMinutes ->
                onSelectDuration(newMinutes)
                showCustomClockDialog = false
            },
            onDismiss = { showCustomClockDialog = false }
        )
    }

    if (sessionState.isActive) {
        ActiveSessionHudCard(
            sessionState = sessionState,
            availableApps = availableApps,
            onOpenPacking = onOpenPacking,
            onRequestAbort = onRequestAbort,
            onConfirmAbort = onConfirmAbort,
            onDismissAbort = onDismissAbort,
            modifier = modifier
        )
    } else {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBg,
            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0x3322C55E), Color(0x22FBBF24)))),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x2222C55E),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Emerald400, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Deep Focus Lockdown",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Unbreakable #blockit Session",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Parachute Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x22FBBF24),
                        border = BorderStroke(1.dp, Color(0x44FBBF24))
                    ) {
                        Text(
                            text = "🪂 ${sessionState.parachutesCount} Left",
                            color = Amber400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Locks the entire phone. Only Phone Calls, Emergency Services, and explicitly packed apps in your Suitcase can be used.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(14.dp))

                // ── CUSTOM FOCUS CLOCK INTERACTIVE CARD (ALL MINUTES SUPPORTED) ──
                val currentMins = sessionState.selectedDurationMinutes.coerceIn(1, 1440)
                val formattedDisplay = if (currentMins >= 60) {
                    val h = currentMins / 60
                    val m = currentMins % 60
                    "${h}h ${if (m > 0) "${m}m" else ""}".trim()
                } else {
                    "$currentMins min"
                }

                val projectedEndTime = remember(currentMins) {
                    val end = System.currentTimeMillis() + (currentMins * 60 * 1000L)
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(end))
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x18FFFFFF),
                    border = BorderStroke(1.dp, Color(0x3322C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CUSTOM FOCUS CLOCK",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Ends ~$projectedEndTime",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Large Digital Time Display with +/- 1m steppers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -1 min button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x22FFFFFF),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelectDuration((currentMins - 1).coerceAtLeast(1))
                                    }
                            ) {
                                Text(
                                    text = "-1m",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            // Center Digital Time Display (Tap to open full dial)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x2222C55E),
                                border = BorderStroke(1.dp, Color(0x5522C55E)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showCustomClockDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockClock,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = formattedDisplay,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // +1 min button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x22FFFFFF),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelectDuration((currentMins + 1).coerceAtMost(1440))
                                    }
                            ) {
                                Text(
                                    text = "+1m",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Quick step pills (+5m, +15m, +30m, +1h, Dial)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "+5m" to 5,
                                "+15m" to 15,
                                "+30m" to 30,
                                "+1h" to 60
                            ).forEach { (label, addMins) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x18FFFFFF),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectDuration((currentMins + addMins).coerceAtMost(1440))
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        color = Color(0xDDFFFFFF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            // Open full circular dial
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x3322C55E),
                                border = BorderStroke(1.dp, Color(0x6622C55E)),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showCustomClockDialog = true }
                            ) {
                                Text(
                                    text = "Dial ⏱️",
                                    color = Emerald400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Suitcase Whitelist Info Bar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x18FFFFFF),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPacking() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Luggage, contentDescription = null, tint = Emerald400, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "The Suitcase (${sessionState.packedPackages.size} Apps Packed)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "+ Phone Calls & Emergency (Permanent)",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = "Pack Apps >",
                            color = Emerald400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Primary Start Button
                Button(
                    onClick = onStartSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lock Phone & Focus ($formattedDisplay)",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
