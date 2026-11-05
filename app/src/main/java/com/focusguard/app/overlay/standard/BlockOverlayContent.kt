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
 *
 * Text/heading resolution lives in BlockOverlayText.kt, the center stack in
 * BlockOverlayCenter.kt and the exit button in BlockOverlayWidgets.kt.
 * =====================================================================
 */

package com.focusguard.app.overlay.standard

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.overlay.manager.OverlayBlockType

/**
 * Screens shorter than this (landscape phones, foldables in side-by-side) get the compact
 * shield: tighter spacing and no breathing-dots footer, so the exit button stays on screen.
 */
private val COMPACT_SHIELD_HEIGHT: Dp = 520.dp

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
    /**
     * When true the shield does NOT auto-dismiss at the end of the countdown. It stays over
     * the blocked app until the service sees the user leave it (or taps "Leave this app").
     * This is what keeps a blocked app unusable instead of usable again after 5 seconds.
     */
    holdUntilForegroundChanges: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    val heading = remember(blockType, targetName) { resolveHeading(blockType, targetName) }
    val accentColor = heading.accentColor
    val resolvedDescription = remember(blockType, contextLine, targetName) {
        resolveDescription(blockType, contextLine, targetName)
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

    // 2. Reliable 1-second countdown loop (updates secondsLeft and enables the exit button)
    LaunchedEffect(durationSeconds) {
        if (durationSeconds > 0) {
            for (sec in durationSeconds downTo 1) {
                secondsLeft.intValue = sec
                kotlinx.coroutines.delay(1000L)
            }
        }
        secondsLeft.intValue = 0
        isTimeUp.value = true
        // Auto-dismiss after 600ms grace period for transient blocks (reels, keyword, tamper).
        // Held block shields stay mounted while the blocked app is still in the foreground.
        if (!holdUntilForegroundChanges) {
            kotlinx.coroutines.delay(600L)
            onDismiss()
        }
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
        // heightIn(min = maxHeight) keeps the stack centered whenever it fits, while the
        // scroll keeps the exit button reachable on short/landscape screens and at large
        // font scales instead of clipping it off the bottom of the shield.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < COMPACT_SHIELD_HEIGHT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(
                        horizontal = if (compact) 24.dp else 32.dp,
                        vertical = if (compact) 16.dp else 48.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                BlockOverlayCenter(
                    heading = heading,
                    blockType = blockType,
                    targetName = targetName,
                    resolvedDescription = resolvedDescription,
                    secondsLeft = secondsLeft.intValue,
                    progressProvider = { animatedProgress.value },
                    compact = compact
                )

                Spacer(Modifier.height(if (compact) 14.dp else 28.dp))

                // Exit / Close Overlay Button
                // Locked while countdown is active; enabled as soon as time is up!
                BlockExitButton(
                    enabled = isTimeUp.value,
                    label = when {
                        !isTimeUp.value -> "Wait (${secondsLeft.intValue}s)"
                        holdUntilForegroundChanges -> "Leave this app"
                        else -> "Close Overlay"
                    },
                    accentColor = accentColor,
                    onClick = onDismiss
                )

                if (!compact) {
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
                                isTimeUp.value && holdUntilForegroundChanges ->
                                    "Blocked while this app is open. Tap 'Leave this app' when you're ready."
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
    }
}
