/**
 * =====================================================================
 * FILE: DeepFocusLockOverlayContent.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file "Deep Focus Strict Lockdown Mode" ka full-screen OLED overlay screen hai.
 * Jab user koi Deep Focus session (pomodoro/deep work block) activate karta hai,
 * toh phone ek minimalist locked terminal me convert ho jata hai. Sirf zaroori
 * tools ("The Suitcase" - max 3 allowed apps) aur emergency phone calls allow hoti hain;
 * baki sab kuch fully block rehta hai jab tak session timer khatam na ho jaye.
 *
 * 🏗️ ARCHITECTURE & UI SECTIONS:
 * 1. Status Badge & Ambient Radial Glow: Pulsing emerald beacon dot ke saath
 *    "DEEP FOCUS LOCKDOWN" indicator jo lock status reflect karta hai.
 * 2. High-Contrast Monospace Countdown: Hours, Minutes, aur Seconds ke 3 digital boxes
 *    aur blinking colon separator (Live continuous tick every 500ms).
 * 3. Suitcase Allowed Apps (The 3 Slots): Max 3 apps jo user ne session start karte waqt
 *    pack ki thi (e.g. Phone, Notes, Calculator) unke interactive cards.
 * 4. Emergency Phone & Strict Lockdown Footer: Immediate dialer access button taaki koi
 *    life emergency me phone lock na rahe, plus auto-unlock target time indicator.
 *
 * ⚙️ KEY PARAMETERS & CALLBACKS:
 * - endTimeMillis: Unix timestamp jab session expire hoga.
 * - packedPackages: Allowed package names ka Set (FocusGuard self-package exclude karke).
 * - parachutesCount: Emergency exits left (if applicable).
 * - onLaunchPackage: Allowed app click par us app ko foreground me laane ka callback.
 * - onLaunchDialer: Default system phone dialer launch karne ka callback.
 * - onEmergencyAbort: Session emergency break trigger.
 * =====================================================================
 */

package com.focusguard.app.overlay.deepfocus

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
private val EmeraldNeon = Color(0xFF22C55E)
private val TextMuted = Color(0x99FFFFFF)
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
            DeepFocusAllowedSlots(
                context = context,
                displayPackedApps = displayPackedApps,
                onLaunchPackage = onLaunchPackage
            )

            // ── 4. ACTION CONTROLS: EMERGENCY PHONE + STRICT LOCKDOWN STATUS ──
            DeepFocusActionControls(
                endSessionTimeString = endSessionTimeString,
                onLaunchDialer = onLaunchDialer
            )
        }
    }
}
