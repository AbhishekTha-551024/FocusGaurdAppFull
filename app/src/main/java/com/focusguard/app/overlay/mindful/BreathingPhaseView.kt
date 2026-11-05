/**
 * =====================================================================
 * FILE: BreathingPhaseView.kt
 * PACKAGE: com.focusguard.app.overlay
 * =====================================================================
 * 
 * 📌 OVERVIEW (YE FILE KYA KARTI HAI?):
 * Ye file Mindful Pause overlay ka "Phase 1: Deep Breathing UI Screen" hai.
 * Jab user kisi dopamine-heavy ya mindful-protected app (jaise Instagram, YouTube, X)
 * ko open karta hai, toh app seedhe open nahi hoti; pehle ye screen trigger hoti hai jo
 * user ko 3-6 second ka "Mindful Breath" pause lene par majboor karti hai, taaki impulsive
 * autopilot scroll behavior break ho sake.
 *
 * 🏗️ ARCHITECTURE & UI COMPONENTS:
 * - App Header Pill: Top par app ka name aur "Mindful Pause" badge render karta hai.
 * - Multi-Layer Expanding Breathing Circle: 3 concentric circles (Outer glowing aura,
 *   Mid glowing animated border, Inner solid core countdown) jo scale aur glow alpha
 *   ke saath expand/contract hote hain, breathing rhythm (Inhale, Hold, Exhale) ke saath sync hokar.
 * - Dynamic Breath Instruction: Real-time dynamic guidance ("Inhale slowly...", "Hold gently...",
 *   "Exhale slowly...") provide karta hai.
 * - Skip to Intent: Agar user jaldi me ho toh vo deep breathing ko skip karke seedhe
 *   Intent Selection phase me jaa sakta hai.
 *
 * ⚙️ KEY PARAMETERS & CALLBACKS:
 * 1. appName: Target application ka display label (e.g. "Instagram").
 * 2. countdownSeconds: Live remaining seconds (3 -> 2 -> 1 -> 0).
 * 3. breathInstruction: Live guide text based on remaining time.
 * 4. scale & glowAlpha: Parent MindfulBreathingContent se animated float values jo smooth 60/120 FPS
 *    breathing expansion drive karti hain.
 * 5. onSkipBreathing: Callback to instantly transition to IntentSelectionView.
 * =====================================================================
 */

package com.focusguard.app.overlay.mindful

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BreathingPhaseView(
    appName: String,
    countdownSeconds: Int,
    breathInstruction: String,
    scale: Float,
    glowAlpha: Float,
    onSkipBreathing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App header pill
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0x2222C55E),
            border = BorderStroke(1.dp, Color(0x4422C55E))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌿 Mindful Pause",
                    color = EMERALD_400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "•  $appName",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Center Breathing Animation Ring
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Outer glowing aura
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(scale * 1.15f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MINT_GLOW.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Mid glowing circle
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .scale(scale)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0x3322C55E),
                                    Color(0x2210B981)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = EMERALD_400.copy(alpha = glowAlpha),
                            shape = CircleShape
                        )
                )

                // Inner core
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF0B291A),
                                    Color(0xFF071B11)
                                )
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, EMERALD_500.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownSeconds",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            Text(
                text = "Take a Deep Breath",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = breathInstruction,
                fontSize = 15.sp,
                color = EMERALD_400,
                fontWeight = FontWeight.Medium
            )
        }

        // Skip prompt (subtle)
        TextButton(
            onClick = onSkipBreathing,
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0x66FFFFFF))
        ) {
            Text(
                text = "Skip to Intent",
                fontSize = 12.sp
            )
        }
    }
}
