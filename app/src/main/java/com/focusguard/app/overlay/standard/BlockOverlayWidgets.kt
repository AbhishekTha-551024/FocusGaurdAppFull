/**
 * =====================================================================
 * FILE: BlockOverlayWidgets.kt
 * PACKAGE: com.focusguard.app.overlay.standard
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file standard full-screen block overlay ke reusable UI components,
 * Canvas countdown ring, breathing dot animations, aur preset theme color
 * palettes provide karti hai.
 *
 * 🏗️ COMPONENTS & STYLES:
 * 1. OVERLAY_PRESETS & Color Palettes: Dark Gradient, Deep Space, Mountain,
 *    Minimal Dark, Neon Calm.
 * 2. CountdownRing: Canvas-based 120 FPS hardware accelerated progress arc
 *    aur center countdown seconds indicator.
 * 3. BreathingDots: Smooth pulsing 3-dot animation taaki user calm reset feel kare.
 * =====================================================================
 */

package com.focusguard.app.overlay.standard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Preset color palettes ──────────────────────────────────────────────────
internal val Navy950    = Color(0xFF07140D)
internal val Navy900    = Color(0xFF0B1F15)
internal val Navy800    = Color(0xFF0E2419)
internal val Indigo400  = Color(0xFF22C55E)
internal val Purple400  = Color(0xFF4ADE80)
internal val Amber400   = Color(0xFFFBBF24)
internal val Rose400    = Color(0xFFFB7185)
internal val White90    = Color(0xE6FFFFFF)
internal val White60    = Color(0x99FFFFFF)
internal val White40    = Color(0x66FFFFFF)

data class OverlayPreset(
    val id: Int,
    val name: String,
    val gradient: List<Color>
)

val OVERLAY_PRESETS = listOf(
    OverlayPreset(0, "Dark Gradient", listOf(Navy950, Navy900, Navy950)),
    OverlayPreset(1, "Deep Space",    listOf(Color(0xFF020818), Color(0xFF0D1B4B), Color(0xFF07112E))),
    OverlayPreset(2, "Mountain",      listOf(Color(0xFF0A1F0F), Color(0xFF1B3A2F), Color(0xFF0A1F0F))),
    OverlayPreset(3, "Minimal Dark",  listOf(Color(0xFF000000), Color(0xFF0F0F0F), Color(0xFF000000))),
    OverlayPreset(4, "Neon Calm",     listOf(Color(0xFF0D0D1A), Color(0xFF150D2E), Color(0xFF0D0D1A)))
)

@Composable
internal fun CountdownRing(
    secondsLeft: Int,
    progressProvider: () -> Float,
    accentColor: Color
) {
    Box(
        modifier = Modifier.size(170.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            // Track ring
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc: reads progressProvider() directly in Draw phase for 120 FPS
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progressProvider(),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center number
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (secondsLeft > 0) "$secondsLeft" else "✓",
                color = Color.White,
                fontSize = if (secondsLeft > 0) 48.sp else 52.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (secondsLeft > 0) "SECONDS" else "COMPLETED",
                color = if (secondsLeft > 0) White40 else accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
internal fun BreathingDots(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, delayMillis = 200), RepeatMode.Reverse),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, delayMillis = 400), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(alpha1, alpha2, alpha3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = alpha))
            )
        }
    }
}
