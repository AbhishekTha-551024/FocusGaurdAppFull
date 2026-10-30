/**
 * =====================================================================
 * FILE: BlockOverlayContent.kt
 * PACKAGE: com.focusguard.app.overlay.standard
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file Standard Full-Screen Blocking Overlay ka primary Composable Screen hai.
 * Jab bhi koi app, keyword, schedule, time limit, reel, ya uninstall tamper
 * detect hota hai, ye screen background wallpaper/preset, countdown timer,
 * context messages, aur Close button render karti hai.
 *
 * 🏗️ UI ARCHITECTURE (3 LAYERS):
 * - Layer 1: Background image (custom user photo) ya preset vertical gradient.
 * - Layer 2: Glassmorphism scrim with custom dim alpha overlay.
 * - Layer 3: Central Countdown ring, violation context badges, Close button,
 *   aur calming motivational breathing line.
 * =====================================================================
 */

package com.focusguard.app.overlay.standard

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.overlay.manager.OverlayBlockType

@Composable
fun BlockOverlayContent(
    durationSeconds: Int,
    blockType: OverlayBlockType,
    targetName: String,
    contextLine: String? = null,
    customMessage: String? = null,
    wallpaperBitmap: Bitmap? = null,
    presetId: Int = 0,
    dimAlpha: Float = 0.55f,
    onDismiss: () -> Unit = {}
) {
    val (headline, accentColor, icon) = when (blockType) {
        OverlayBlockType.UNINSTALL_ATTEMPT -> Triple("Uninstall Protected", Rose400,    Icons.Default.Security)
        OverlayBlockType.SETTINGS_TAMPER   -> Triple("Settings Protected",  Rose400,    Icons.Default.Lock)
        OverlayBlockType.APP_BLOCK         -> Triple("App Blocked",         Indigo400,  Icons.Default.Block)
        OverlayBlockType.KEYWORD_BLOCK     -> Triple("Keyword Blocked",     Rose400,    Icons.Default.Language)
        OverlayBlockType.SCHEDULE_BLOCK    -> Triple("Focus Schedule",      Amber400,   Icons.Default.Schedule)
        OverlayBlockType.TIME_LIMIT_BLOCK  -> Triple("Daily Limit Reached", Purple400,  Icons.Default.HourglassTop)
        OverlayBlockType.SHORT_OR_REEL_BLOCK -> {
            val title = when {
                targetName.contains("Shorts", ignoreCase = true) -> "Shorts Blocked"
                targetName.contains("Reels", ignoreCase = true) -> "Reels Blocked"
                else -> "Shorts & Reels Blocked"
            }
            Triple(title, Rose400, Icons.Default.PlayCircle)
        }
        OverlayBlockType.INCOGNITO_BLOCK   -> Triple("Incognito Blocked",   Rose400,    Icons.Default.VisibilityOff)
        OverlayBlockType.CLONER_BLOCK      -> Triple("App Cloner Blocked",  Rose400,    Icons.Default.Security)
        OverlayBlockType.SESSION_LOCK_BLOCK -> Triple("Deep Focus Lockdown", Amber400,   Icons.Default.Lock)
    }

    val resolvedDescription = if (!contextLine.isNullOrBlank()) {
        contextLine
    } else {
        when (blockType) {
            OverlayBlockType.UNINSTALL_ATTEMPT -> "Uninstall protection is ON, so FocusGuard can't be removed right now."
            OverlayBlockType.SETTINGS_TAMPER   -> "This settings screen is protected so your blocking can't be switched off."
            OverlayBlockType.APP_BLOCK         -> "This app is on your block list."
            OverlayBlockType.KEYWORD_BLOCK     -> {
                if (targetName.isNotBlank()) "Keyword “$targetName” is in your blocklist."
                else "This content matched a keyword in your blocklist."
            }
            OverlayBlockType.SCHEDULE_BLOCK    -> "This app is blocked during your focus schedule."
            OverlayBlockType.TIME_LIMIT_BLOCK  -> "Your daily limit for this app is used up. It resets at midnight."
            OverlayBlockType.SHORT_OR_REEL_BLOCK -> "Short-form video is blocked."
            OverlayBlockType.INCOGNITO_BLOCK   -> "Private browsing is blocked."
            OverlayBlockType.CLONER_BLOCK      -> "App cloners are blocked."
            OverlayBlockType.SESSION_LOCK_BLOCK -> "A Deep Focus session is running."
        }
    }

    val preset = OVERLAY_PRESETS.getOrElse(presetId) { OVERLAY_PRESETS[0] }

    // Countdown and animation state
    val secondsLeft = remember(durationSeconds) { mutableIntStateOf(durationSeconds) }
    val isTimeUp = remember(durationSeconds) { mutableStateOf(durationSeconds <= 0) }
    val animatedProgress = remember(durationSeconds) { Animatable(1.0f) }

    // 1. Arc smooth 120 FPS animation (runs only in Draw phase for maximum hardware acceleration)
    LaunchedEffect(durationSeconds) {
        if (durationSeconds > 0) {
            animatedProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationSeconds * 1000,
                    easing = LinearEasing
                )
            )
        } else {
            animatedProgress.snapTo(0f)
        }
    }

    // 2. Reliable 1-second countdown loop (updates secondsLeft, enables Close button, and auto-dismisses)
    LaunchedEffect(durationSeconds) {
        if (durationSeconds > 0) {
            for (sec in durationSeconds downTo 1) {
                secondsLeft.intValue = sec
                kotlinx.coroutines.delay(1000L)
            }
        }
        secondsLeft.intValue = 0
        isTimeUp.value = true
        // Auto-dismiss after 600ms grace period so overlay doesn't hang forever blocking accessibility events
        kotlinx.coroutines.delay(600L)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {

        // ── LAYER 1: Background (custom image OR preset gradient) ──────────────
        if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled) {
            Image(
                bitmap = wallpaperBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(preset.gradient)
                    )
            )
        }

        // ── LAYER 2: Glassmorphism scrim ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = dimAlpha * 0.7f),
                            Color.Black.copy(alpha = dimAlpha),
                            Color.Black.copy(alpha = dimAlpha * 0.9f)
                        )
                    )
                )
        )

        // ── LAYER 3: Content ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // CENTER: Countdown ring + info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Accent Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Text(text = headline, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Countdown Ring
                CountdownRing(
                    secondsLeft = secondsLeft.intValue,
                    progressProvider = { animatedProgress.value },
                    accentColor = accentColor
                )

                // Target name + description
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (blockType == OverlayBlockType.KEYWORD_BLOCK || blockType == OverlayBlockType.INCOGNITO_BLOCK || blockType == OverlayBlockType.CLONER_BLOCK || blockType == OverlayBlockType.SESSION_LOCK_BLOCK) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = when (blockType) {
                                    OverlayBlockType.INCOGNITO_BLOCK -> "PRIVATE BROWSING"
                                    OverlayBlockType.CLONER_BLOCK -> "CLONED APP DETECTED"
                                    OverlayBlockType.SESSION_LOCK_BLOCK -> "PHONE LOCKED DOWN"
                                    else -> "BLOCKED KEYWORD"
                                },
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (blockType == OverlayBlockType.KEYWORD_BLOCK) "“$targetName”" else targetName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = resolvedDescription,
                        color = White90,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Exit / Close Overlay Button
            // Locked while countdown is active; enabled as soon as time is up!
            Button(
                onClick = {
                    if (isTimeUp.value) {
                        onDismiss()
                    }
                },
                enabled = isTimeUp.value,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTimeUp.value) accentColor else accentColor.copy(alpha = 0.18f),
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    disabledContentColor = White40
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (isTimeUp.value) accentColor else Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = if (isTimeUp.value) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isTimeUp.value) Color.White else White40,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isTimeUp.value) "Close Overlay" else "Wait (${secondsLeft.intValue}s)",
                    color = if (isTimeUp.value) Color.White else White40,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // BOTTOM: Motivational breathing line or user custom message
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                BreathingDots(accentColor = accentColor)
                Text(
                    text = when {
                        !customMessage.isNullOrBlank() -> customMessage
                        isTimeUp.value -> "Time complete. Tap 'Close Overlay' to return."
                        else -> "Take a breath. Resetting your focus..."
                    },
                    color = if (!customMessage.isNullOrBlank() || isTimeUp.value) Color.White.copy(alpha = 0.85f) else White40,
                    fontSize = if (!customMessage.isNullOrBlank() || isTimeUp.value) 13.sp else 12.sp,
                    fontWeight = if (!customMessage.isNullOrBlank() || isTimeUp.value) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
