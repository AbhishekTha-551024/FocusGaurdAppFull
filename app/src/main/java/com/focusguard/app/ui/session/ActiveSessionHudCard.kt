package com.focusguard.app.ui.session

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusguard.app.data.model.AppInfo
import com.focusguard.app.ui.components.AppIconView
import kotlinx.coroutines.delay

private val CardBg = Color(0xFF0D2117)
private val Emerald500 = Color(0xFF22C55E)
private val Emerald400 = Color(0xFF4ADE80)
private val Amber400 = Color(0xFFFBBF24)
private val Rose400 = Color(0xFFFB7185)
private val TextMuted = Color(0x99FFFFFF)

@Composable
fun ActiveSessionHudCard(
    sessionState: SessionUiState,
    availableApps: List<AppInfo>,
    onOpenPacking: () -> Unit,
    onRequestAbort: () -> Unit,
    onConfirmAbort: () -> Unit,
    onDismissAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Live tick every second for smooth seconds countdown
    var currentRemainingMillis by remember(sessionState.endTimeMillis) {
        mutableLongStateOf(
            (sessionState.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        )
    }

    LaunchedEffect(sessionState.endTimeMillis) {
        while (true) {
            val remaining = (sessionState.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            currentRemainingMillis = remaining
            if (remaining <= 0L) break
            val delayMillis = 1000L - (System.currentTimeMillis() % 1000L)
            delay(if (delayMillis <= 0L) 1000L else delayMillis)
        }
    }

    val totalMillis = (sessionState.totalMinutes * 60_000L).coerceAtLeast(1L)
    val progress = (currentRemainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "hud_progress"
    )

    val seconds = (currentRemainingMillis / 1000) % 60
    val minutes = (currentRemainingMillis / (1000 * 60)) % 60
    val hours = currentRemainingMillis / (1000 * 60 * 60)

    val formattedTime = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    // Filter packed apps metadata
    val packedAppInfos = remember(sessionState.packedPackages, availableApps) {
        val set = sessionState.packedPackages
        availableApps.filter { set.contains(it.packageName) }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0x4422C55E), Color(0x22FBBF24)))),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x2222C55E),
                    border = BorderStroke(1.dp, Color(0x4422C55E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Emerald400)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "DEEP FOCUS ACTIVE",
                            color = Emerald400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Parachute counter badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (sessionState.parachutesCount > 0) Color(0x22FBBF24) else Color(0x22FB7185),
                    border = BorderStroke(1.dp, if (sessionState.parachutesCount > 0) Color(0x44FBBF24) else Color(0x44FB7185))
                ) {
                    Text(
                        text = "🪂 ${sessionState.parachutesCount} Parachutes",
                        color = if (sessionState.parachutesCount > 0) Amber400 else Rose400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Big Timer Countdown Display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Emerald400,
                    trackColor = Color(0x2222C55E),
                    strokeWidth = 10.dp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedTime,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "REMAINING",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Phone is locked down. Only Calls & Packed Apps are accessible.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Quick Access Packed Apps Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACCESSIBLE APPS & TOOLS",
                    color = Emerald400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "Edit Suitcase",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenPacking() }
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Permanent Phone Dialer shortcut
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                val dialIntent = Intent(Intent.ACTION_DIAL)
                                dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(dialIntent)
                            }
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x3322C55E),
                            border = BorderStroke(1.dp, Color(0x6622C55E)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Phone, contentDescription = "Phone Calls", tint = Emerald400, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(text = "Calls", color = Color.White, fontSize = 11.sp)
                    }
                }

                // Packed Apps
                items(packedAppInfos, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            }
                            .padding(4.dp)
                    ) {
                        AppIconView(packageName = app.packageName, size = 42.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = app.appName.take(7),
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Strict Lockdown Indicator
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Emerald500.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, Emerald500.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Emerald500,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Strict Mode Active · Unlocks when timer ends",
                        color = Emerald500,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
