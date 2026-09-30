package com.focusguard.app.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.focusguard.app.ui.components.AppIconView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// OLED Stealth Minimalist Palette
private val OledBlack = Color(0xFF000000)
private val DarkCardBg = Color(0xFF09140E)
private val DarkCardBorder = Color(0x3322C55E)
private val EmeraldNeon = Color(0xFF22C55E)
private val EmeraldNeonGlow = Color(0xFF4ADE80)
private val TextMuted = Color(0x99FFFFFF)
private val TextDim = Color(0x55FFFFFF)
private val CoralRed = Color(0xFFEF4444)
private val CoralRedBg = Color(0xFF2B0E10)

@Composable
fun DeepFocusLockOverlayContent(
    endTimeMillis: Long,
    packedPackages: Set<String>,
    parachutesCount: Int,
    onLaunchPackage: (String) -> Unit,
    onLaunchDialer: () -> Unit,
    onEmergencyAbort: () -> Unit = {}
) {
    val context = LocalContext.current

    // Live continuous tick every 500ms for smooth reverse countdown
    var currentRemainingMillis by remember(endTimeMillis) {
        mutableLongStateOf((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(endTimeMillis) {
        while (true) {
            val remaining = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            currentRemainingMillis = remaining
            if (remaining <= 0L) break
            delay(500L)
        }
    }

    // Breathing pulse animation for lock status dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Blink colon animation
    val colonBlink by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colonBlink"
    )

    val totalSeconds = (currentRemainingMillis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val hoursStr = String.format(Locale.US, "%02d", hours)
    val minsStr = String.format(Locale.US, "%02d", minutes)
    val secsStr = String.format(Locale.US, "%02d", seconds)

    val endSessionTimeString = remember(endTimeMillis) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(endTimeMillis)).uppercase()
    }

    // Filter out FocusGuard self package to display user allowed apps (max 3)
    val displayPackedApps = remember(packedPackages) {
        packedPackages.filter { it != context.packageName }.take(3)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .statusBarsPadding()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the center countdown
        Box(
            modifier = Modifier
                .size(380.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x2210B981),
                            Color(0x0A064E3B),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. HEADER: STATUS BADGE ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF081C12),
                    border = BorderStroke(1.dp, Color(0x5522C55E)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .background(EmeraldNeon, CircleShape)
                        )
                        Text(
                            text = "DEEP FOCUS LOCKDOWN",
                            color = EmeraldNeonGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Text(
                    text = "Distraction Shield Active",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // ── 2. CENTER: HIGH-CONTRAST DIGITAL COUNTDOWN CARDS ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REMAINING TIME",
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Hours Box
                    TimeDigitBox(digits = hoursStr, label = "HRS")

                    Text(
                        text = ":",
                        color = EmeraldNeon.copy(alpha = colonBlink),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )

                    // Minutes Box
                    TimeDigitBox(digits = minsStr, label = "MIN")

                    Text(
                        text = ":",
                        color = EmeraldNeon.copy(alpha = colonBlink),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )

                    // Seconds Box
                    TimeDigitBox(digits = secsStr, label = "SEC")
                }
            }

            // ── 3. ALLOWED TOOLS (THE SUITCASE - EXACTLY 3 SLOTS) ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "ALLOWED APPS (${displayPackedApps.size}/3 SLOTS)",
                        color = TextDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                // 3 Fixed Glassmorphic App Slots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (slotIndex in 0 until 3) {
                        val pkg = displayPackedApps.getOrNull(slotIndex)
                        if (pkg != null) {
                            val appName = remember(pkg) { getAppNameSafely(context, pkg) }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DarkCardBg,
                                border = BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onLaunchPackage(pkg) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    AppIconView(packageName = pkg, size = 40.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = appName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Empty Reserved Slot
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0x0AFFFFFF),
                                border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(90.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Empty",
                                        color = TextDim,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Text(
                                        text = "Slot ${slotIndex + 1}",
                                        color = TextDim.copy(alpha = 0.5f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. ACTION CONTROLS: EMERGENCY PHONE + STRICT LOCKDOWN STATUS ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Emergency Phone Dialer Button
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { onLaunchDialer() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Open Phone Dialer",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "EMERGENCY PHONE CALL",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Strict Lockdown Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x1422C55E),
                    border = BorderStroke(1.dp, Color(0x3322C55E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Strict Lockdown · Auto-unlocks at $endSessionTimeString",
                            color = Color(0xFF86EFAC),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeDigitBox(
    digits: String,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkCardBg,
        border = BorderStroke(1.dp, DarkCardBorder),
        modifier = Modifier.width(88.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digits,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = EmeraldNeonGlow,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun getAppNameSafely(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName.substringAfterLast('.').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
